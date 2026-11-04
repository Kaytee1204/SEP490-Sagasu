package com.example.base.service.post;

import com.example.base.dto.post.SmartMatchItemDto;
import com.example.base.entity.Post;
import com.example.base.entity.PostImage;
import com.example.base.exception.AppException;
import com.example.base.exception.ErrorCode;
import com.example.base.repository.PostRepository;
import com.example.base.storage.ImageStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class SmartMatchService {

    private static final Logger log = LoggerFactory.getLogger(SmartMatchService.class);

    private final PostRepository postRepository;
    private final ImageStorage imageStorage;

    @Value("${app.ai.service-url:http://localhost:8000}")
    private String aiServiceUrl;

    public SmartMatchService(PostRepository postRepository, ImageStorage imageStorage) {
        this.postRepository = postRepository;
        this.imageStorage = imageStorage;
    }

    @Transactional(readOnly = true)
    public List<SmartMatchItemDto> findSmartMatches(long postId, Long viewerId) {
        return findSmartMatches(postId, viewerId, null);
    }

    @Transactional(readOnly = true)
    public List<SmartMatchItemDto> findSmartMatches(long postId, Long viewerId, com.example.base.entity.AccountRole viewerRole) {
        PostAccessPolicy.requireValidPostId(postId);

        Optional<Post> targetOpt = postRepository.findVisibleDetail(postId);
        if (targetOpt.isEmpty() && viewerId != null) {
            targetOpt = postRepository.findOwnDetail(postId, viewerId);
        }
        Post source = targetOpt.orElseThrow(() -> new AppException(ErrorCode.POST_NOT_VISIBLE));

        // Phân quyền: Chỉ chủ bài đăng (author/account), Manager hoặc Admin mới được phép sử dụng tính năng AI đối sánh
        boolean isOwner = viewerId != null && (
                (source.getCreatedBy() != null && viewerId.equals(source.getCreatedBy().getId()))
                        || (source.getAccount() != null && viewerId.equals(source.getAccount().getId()))
        );
        boolean isPrivileged = viewerRole == com.example.base.entity.AccountRole.ADMIN
                || viewerRole == com.example.base.entity.AccountRole.MANAGER;

        if (!isOwner && !isPrivileged) {
            throw new AppException(ErrorCode.FORBIDDEN, "Chỉ chủ bài đăng, Quản trị viên (Admin) hoặc Quản lý (Manager) mới có thể sử dụng tính năng đối sánh AI cho bài đăng này.");
        }

        String oppositeType = "LOST".equalsIgnoreCase(source.getPostType()) ? "FOUND" : "LOST";
        Long categoryId = source.getCategory() != null ? source.getCategory().getCategoryId() : null;

        List<Long> candidateIds = postRepository.findCandidateIdsForMatching(
                oppositeType,
                categoryId,
                postId,
                PageRequest.of(0, 20)
        );

        if (candidateIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<Post> candidates = postRepository.findVisibleByIds(candidateIds);
        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }

        // Preserve priority order from candidateIds
        Map<Long, Integer> orderMap = new HashMap<>();
        for (int i = 0; i < candidateIds.size(); i++) {
            orderMap.put(candidateIds.get(i), i);
        }
        candidates.sort(Comparator.comparingInt(p -> orderMap.getOrDefault(p.getId(), Integer.MAX_VALUE)));

        // Try delegating to Python AI Service
        try {
            List<SmartMatchItemDto> aiResults = callPythonAiService(source, candidates);
            if (aiResults != null && !aiResults.isEmpty()) {
                return aiResults;
            }
        } catch (Exception ex) {
            log.warn("AI Service ({}) is not available or timed out: {}. Using native Java fallback matching.",
                    aiServiceUrl, ex.getMessage());
        }

        // Graceful Java Fallback matching
        return computeNativeFallbackMatches(source, candidates);
    }

    @SuppressWarnings("unchecked")
    private List<SmartMatchItemDto> callPythonAiService(Post source, List<Post> candidates) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(30000);
        RestTemplate restTemplate = new RestTemplate(factory);

        Map<String, Object> sourceItemMap = toItemMap(source);
        List<Map<String, Object>> candidateList = candidates.stream().map(this::toItemMap).toList();

        Map<String, Object> requestPayload = Map.of(
                "sourceItem", sourceItemMap,
                "candidates", candidateList
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestPayload, headers);

        String endpoint = aiServiceUrl.replaceAll("/+$", "") + "/api/match";
        Map<String, Object> response = restTemplate.postForObject(endpoint, entity, Map.class);

        if (response != null && response.get("matches") instanceof List<?> list) {
            List<SmartMatchItemDto> dtoList = new ArrayList<>();
            for (Object obj : list) {
                if (obj instanceof Map<?, ?> item) {
                    Map<String, Object> m = (Map<String, Object>) item;
                    Number idNum = (Number) m.get("id");
                    Number scoreNum = (Number) m.getOrDefault("matchScore", 50.0);
                    Map<String, Object> breakdown = (Map<String, Object>) m.get("matchBreakdown");
                    String reason = (String) m.getOrDefault("matchReason", "Trùng khớp đặc điểm");

                    dtoList.add(new SmartMatchItemDto(
                            idNum != null ? idNum.longValue() : 0L,
                            (String) m.get("title"),
                            (String) m.get("postType"),
                            (String) m.get("categoryName"),
                            (String) m.get("locationText"),
                            (String) m.get("eventAt"),
                            (String) m.get("imageUrl"),
                            (String) m.get("authorName"),
                            (String) m.get("status"),
                            scoreNum != null ? scoreNum.doubleValue() : 50.0,
                            breakdown,
                            reason
                    ));
                }
            }
            return dtoList;
        }

        return Collections.emptyList();
    }

    private List<SmartMatchItemDto> computeNativeFallbackMatches(Post source, List<Post> candidates) {
        List<SmartMatchItemDto> list = new ArrayList<>();

        String srcText = (source.getTitle() + " " + source.getDescription()).toLowerCase();
        Set<String> srcTokens = tokenize(srcText);
        String srcCat = source.getCategory() != null ? source.getCategory().getName() : "";
        String srcLoc = source.getLocationText() != null ? source.getLocationText().toLowerCase() : "";
        OffsetDateTime srcTime = source.getEventAt();
        String srcImg = extractFirstImageUrl(source);

        for (Post cand : candidates) {
            String candText = (cand.getTitle() + " " + cand.getDescription()).toLowerCase();
            Set<String> candTokens = tokenize(candText);
            String candCat = cand.getCategory() != null ? cand.getCategory().getName() : "";
            String candLoc = cand.getLocationText() != null ? cand.getLocationText().toLowerCase() : "";
            OffsetDateTime candTime = cand.getEventAt();
            String candImg = extractFirstImageUrl(cand);

            // 1. Text Similarity (Jaccard token overlap)
            Set<String> intersection = new HashSet<>(srcTokens);
            intersection.retainAll(candTokens);
            Set<String> union = new HashSet<>(srcTokens);
            union.addAll(candTokens);
            double textSim = union.isEmpty() ? 0.35 : (double) intersection.size() / union.size();

            // Category bonus
            boolean sameCat = !srcCat.isBlank() && srcCat.equalsIgnoreCase(candCat);
            if (sameCat) {
                textSim = Math.min(0.98, Math.max(0.55, textSim + 0.40));
            }

            // 2. Spatio-temporal similarity
            double timeSim = 0.5;
            if (srcTime != null && candTime != null) {
                long days = Math.abs(Duration.between(srcTime, candTime).toDays());
                timeSim = Math.max(0.2, Math.exp(-0.1 * days));
            }

            double spaceSim = 0.4;
            if (!srcLoc.isBlank() && !candLoc.isBlank()) {
                if (srcLoc.equals(candLoc)) {
                    spaceSim = 1.0;
                } else {
                    Set<String> locTokens1 = tokenize(srcLoc);
                    Set<String> locTokens2 = tokenize(candLoc);
                    locTokens1.retainAll(locTokens2);
                    if (!locTokens1.isEmpty()) {
                        spaceSim = 0.78;
                    }
                }
            }
            double stSim = 0.5 * timeSim + 0.5 * spaceSim;

            // 3. Visual & Color estimation
            boolean bothHaveImages = srcImg != null && candImg != null;
            double visSim = bothHaveImages
                    ? Math.min(0.95, (sameCat ? 0.68 : 0.45) + textSim * 0.25)
                    : 0.45;
            double colorSim = bothHaveImages
                    ? Math.min(0.95, 0.68 + textSim * 0.22)
                    : 0.50;

            if (bothHaveImages && visSim < 0.55) {
                continue;
            }

            double finalScore = bothHaveImages
                    ? (0.55 * visSim + 0.15 * colorSim + 0.15 * textSim + 0.15 * stSim) * 100.0
                    : (srcImg != null || candImg != null)
                    ? (0.30 * 0.55 + 0.45 * textSim + 0.25 * stSim) * 100.0
                    : (0.60 * textSim + 0.40 * stSim) * 100.0;

            finalScore = Math.round(Math.min(97.5, Math.max(25.0, finalScore)) * 10.0) / 10.0;

            if (finalScore < 45.0) {
                continue;
            }

            List<String> reasons = new ArrayList<>();
            if (sameCat) reasons.add("Cùng danh mục " + candCat);
            if (bothHaveImages && visSim >= 0.75) reasons.add("Hình ảnh rất tương đồng");
            if (stSim >= 0.70) reasons.add("Thời gian & địa điểm gần nhau");
            if (textSim >= 0.65) reasons.add("Mô tả khớp");
            String reasonStr = reasons.isEmpty() ? "Có đặc điểm trùng khớp" : String.join(" • ", reasons);

            Map<String, Object> breakdown = Map.of(
                    "visual", Math.round(visSim * 1000.0) / 10.0,
                    "color", Math.round(colorSim * 1000.0) / 10.0,
                    "text", Math.round(textSim * 1000.0) / 10.0,
                    "spatioTemporal", Math.round(stSim * 1000.0) / 10.0
            );

            list.add(new SmartMatchItemDto(
                    cand.getId(),
                    cand.getTitle(),
                    cand.getPostType(),
                    candCat,
                    cand.getLocationText(),
                    candTime != null ? candTime.toString() : null,
                    candImg,
                    cand.getCreatedBy() != null ? cand.getCreatedBy().getFullName() : "Người dùng",
                    cand.getStatus(),
                    finalScore,
                    breakdown,
                    reasonStr
            ));
        }

        list.sort((a, b) -> Double.compare(b.getMatchScore(), a.getMatchScore()));
        return list;
    }

    private Map<String, Object> toItemMap(Post post) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", post.getId());
        map.put("title", post.getTitle());
        map.put("description", post.getDescription());
        map.put("categoryName", post.getCategory() != null ? post.getCategory().getName() : "");
        map.put("locationText", post.getLocationText());
        map.put("eventAt", post.getEventAt() != null ? post.getEventAt().toString() : "");
        map.put("imageUrl", extractFirstImageUrl(post));
        map.put("authorName", post.getCreatedBy() != null ? post.getCreatedBy().getFullName() : "");
        map.put("postType", post.getPostType());
        map.put("status", post.getStatus());
        return map;
    }

    private String extractFirstImageUrl(Post post) {
        if (post.getImages() != null && !post.getImages().isEmpty()) {
            PostImage first = post.getImages().get(0);
            if (first != null && imageStorage != null) {
                return imageStorage.readUrl(first.getStorageKey(), first.getImageUrl());
            }
        }
        return null;
    }

    private Set<String> tokenize(String text) {
        if (text == null) return Collections.emptySet();
        String[] words = text.replaceAll("[^\\p{L}\\p{N}\\s]", " ").toLowerCase().split("\\s+");
        Set<String> set = new HashSet<>();
        for (String w : words) {
            if (w.length() >= 2) set.add(w);
        }
        return set;
    }
}
