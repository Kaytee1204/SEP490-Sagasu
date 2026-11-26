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
import org.springframework.beans.factory.annotation.Autowired;
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
    private final AiIndexSyncService aiIndexSyncService;

    @Value("${app.ai.service-url:http://localhost:8000}")
    private String aiServiceUrl;

    @Autowired
    public SmartMatchService(PostRepository postRepository, ImageStorage imageStorage, AiIndexSyncService aiIndexSyncService) {
        this.postRepository = postRepository;
        this.imageStorage = imageStorage;
        this.aiIndexSyncService = aiIndexSyncService;
    }

    public SmartMatchService(PostRepository postRepository, ImageStorage imageStorage) {
        this(postRepository, imageStorage, null);
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

        // ── KIẾN TRÚC 3 LỚP (Đảm bảo đủ cả LOST & FOUND, không bao giờ chặn request người dùng) ──
        // - Kiểm tra riêng loại bài đối diện (`oppositeType`) đã được đồng bộ đủ vào AI Index chưa.
        // - Nếu đã đủ -> gọi thẳng `POST /api/search` (quét 100% toàn bộ DB qua 4 kênh Recall -> Top 10 trong ~30-70ms).
        // - Nếu đang đồng bộ nền còn thiếu -> kích hoạt đồng bộ nền `triggerWarmupAsync()` ĐỒNG THỜI
        //   gọi nhanh `POST /api/match` trên 60 bài ngược loại mới nhất từ DB để trả ngay Top 10 chuẩn xác!
        String oppositeType = "LOST".equalsIgnoreCase(source.getPostType()) ? "FOUND" : "LOST";

        // Lấy sẵn 60 bài ngược loại mới nhất từ PostgreSQL (kèm presigned URL ảnh mới nhất)
        // để đẩy kèm vào AI-service nhằm đảm bảo mọi bài vừa tạo xong đều có mặt 100% trong chỉ mục.
        List<Long> quickIds = postRepository.findAllCandidateIdsForCrossMatching(
                oppositeType, postId, null, PageRequest.of(0, 60)
        );
        List<Post> quickCandidates = quickIds.isEmpty()
                ? Collections.emptyList()
                : postRepository.findVisibleByIds(quickIds).stream()
                        .filter(c -> !Objects.equals(c.getId(), source.getId()))
                        .filter(c -> oppositeType.equalsIgnoreCase(c.getPostType()))
                        .toList();

        try {
            boolean oppositeSynced = aiIndexSyncService != null && aiIndexSyncService.isOppositeTypeSynced(oppositeType);
            if (oppositeSynced) {
                List<SmartMatchItemDto> indexedResults = callPythonAiSearch(source, quickCandidates);
                if (indexedResults != null && (!indexedResults.isEmpty() || quickCandidates.isEmpty())) {
                    return indexedResults;
                }
            } else if (aiIndexSyncService != null) {
                aiIndexSyncService.triggerWarmupAsync();
            }

            if (!quickCandidates.isEmpty()) {
                List<SmartMatchItemDto> quickResults = callPythonAiService(source, quickCandidates);
                if (quickResults != null) {
                    return quickResults;
                }
            }
        } catch (Exception ex) {
            log.warn("AI Service ({}) unavailable: {}. Falling back to native Java matching.",
                    aiServiceUrl, ex.getMessage());
        }

        // Fallback Java thuần (chỉ chạy khi Python AI-service bị tắt/mất kết nối)
        List<Long> candidateIds = postRepository.findAllCandidateIdsForCrossMatching(
                oppositeType,
                postId,
                null,
                PageRequest.of(0, 200)
        );

        if (candidateIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<Post> candidates = postRepository.findVisibleByIds(candidateIds);
        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }

        candidates = new ArrayList<>(candidates.stream()
                .filter(c -> !Objects.equals(c.getId(), source.getId()))
                .filter(c -> oppositeType.equalsIgnoreCase(c.getPostType()))
                .toList());
        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }

        return computeNativeFallbackMatches(source, candidates);
    }

    /**
     * Gọi endpoint `POST /api/search` của Kiến trúc 3 lớp:
     * Gửi bài gốc (`sourceItem`) kèm danh sách bài ngược loại mới nhất (`recentCandidates`) để đồng bộ tức thì
     * các bài vừa đăng trước khi quét toàn bộ kho chỉ mục.
     */
    @SuppressWarnings("unchecked")
    private List<SmartMatchItemDto> callPythonAiSearch(Post source, List<Post> recentCandidates) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(30000);
        RestTemplate restTemplate = new RestTemplate(factory);

        List<Map<String, Object>> recentList = recentCandidates == null
                ? Collections.emptyList()
                : recentCandidates.stream().map(this::toItemMap).toList();

        Map<String, Object> requestPayload = Map.of(
                "sourceItem", toItemMap(source),
                "recentCandidates", recentList,
                "topKPerChannel", 60,
                "topN", 20
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestPayload, headers);

        String endpoint = aiServiceUrl.replaceAll("/+$", "") + "/api/search";
        Map<String, Object> response = restTemplate.postForObject(endpoint, entity, Map.class);
        return parseAiMatchesResponse(response);
    }

    @SuppressWarnings("unchecked")
    private List<SmartMatchItemDto> parseAiMatchesResponse(Map<String, Object> response) {
        if (response != null && response.get("matches") instanceof List<?> list) {
            List<Long> matchedIds = new ArrayList<>();
            for (Object obj : list) {
                if (obj instanceof Map<?, ?> item) {
                    Object idObj = item.get("id");
                    if (idObj instanceof Number n) {
                        matchedIds.add(n.longValue());
                    } else if (idObj != null) {
                        try {
                            matchedIds.add(Long.parseLong(String.valueOf(idObj)));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }

            // Đối chiếu với PostgreSQL thực tế để lấy URL ảnh presigned R2 mới nhất và loại bỏ các ID cũ không còn tồn tại/OPEN
            Map<Long, Post> livePostsById = new HashMap<>();
            if (!matchedIds.isEmpty()) {
                for (Post p : postRepository.findVisibleByIds(matchedIds)) {
                    if (p != null && p.getId() != null) {
                        livePostsById.put(p.getId(), p);
                    }
                }
            }

            List<SmartMatchItemDto> dtoList = new ArrayList<>();
            for (Object obj : list) {
                if (obj instanceof Map<?, ?> item) {
                    Map<String, Object> m = (Map<String, Object>) item;
                    Long postId = null;
                    Object idObj = m.get("id");
                    if (idObj instanceof Number n) {
                        postId = n.longValue();
                    } else if (idObj != null) {
                        try {
                            postId = Long.parseLong(String.valueOf(idObj));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    Number scoreNum = (Number) m.getOrDefault("matchScore", 50.0);
                    Map<String, Object> breakdown = (Map<String, Object>) m.get("matchBreakdown");
                    String reason = (String) m.getOrDefault("matchReason", "Trùng khớp đặc điểm");
                    String srcDetected = (String) m.get("sourceDetectedItem");
                    if (srcDetected == null && breakdown != null && breakdown.get("sourceDetectedItem") instanceof String s) {
                        srcDetected = s;
                    }
                    String candDetected = (String) m.get("candDetectedItem");
                    if (candDetected == null && breakdown != null && breakdown.get("candDetectedItem") instanceof String s) {
                        candDetected = s;
                    }

                    Post live = postId != null ? livePostsById.get(postId) : null;
                    if (postId != null && !livePostsById.isEmpty() && live == null) {
                        // Bài trong SQLite cũ không còn OPEN/VISIBLE trong PostgreSQL -> dọn khỏi AI index
                        if (aiIndexSyncService != null) {
                            aiIndexSyncService.deletePostAsync(postId);
                        }
                        continue;
                    }

                    if (live != null) {
                        List<String> liveImages = extractAllImageUrls(live);
                        SmartMatchItemDto dto = new SmartMatchItemDto(
                                live.getId(),
                                live.getTitle(),
                                live.getPostType(),
                                live.getCategory() != null ? live.getCategory().getName() : (String) m.get("categoryName"),
                                live.getLocationText(),
                                live.getEventAt() != null ? live.getEventAt().toString() : (String) m.get("eventAt"),
                                liveImages.isEmpty() ? null : liveImages.get(0),
                                live.getCreatedBy() != null ? live.getCreatedBy().getFullName() : (String) m.get("authorName"),
                                live.getStatus(),
                                scoreNum != null ? scoreNum.doubleValue() : 50.0,
                                breakdown,
                                reason
                        );
                        dto.setDescription(live.getDescription());
                        dto.setImageUrls(liveImages);
                        dto.setSourceDetectedItem(srcDetected);
                        dto.setCandDetectedItem(candDetected);
                        dtoList.add(dto);
                    } else {
                        SmartMatchItemDto dto = new SmartMatchItemDto(
                                postId != null ? postId : 0L,
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
                        );
                        dto.setDescription((String) m.get("description"));
                        if (m.get("imageUrls") instanceof List<?> rawImgs) {
                            dto.setImageUrls(rawImgs.stream().filter(String.class::isInstance).map(String.class::cast).toList());
                        } else if (dto.getImageUrl() != null) {
                            dto.setImageUrls(List.of(dto.getImageUrl()));
                        }
                        dto.setSourceDetectedItem(srcDetected);
                        dto.setCandDetectedItem(candDetected);
                        dtoList.add(dto);
                    }
                }
            }
            return dtoList.size() > 10 ? dtoList.subList(0, 10) : dtoList;
        }
        return null;
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
                "candidates", candidateList,
                "topN", 10
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestPayload, headers);

        String endpoint = aiServiceUrl.replaceAll("/+$", "") + "/api/match";
        Map<String, Object> response = restTemplate.postForObject(endpoint, entity, Map.class);
        return parseAiMatchesResponse(response);
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
            if (Objects.equals(source.getId(), cand.getId())) {
                continue;
            }
            String candText = (cand.getTitle() + " " + cand.getDescription()).toLowerCase();
            Set<String> candTokens = tokenize(candText);
            String candCat = cand.getCategory() != null ? cand.getCategory().getName() : "";
            String candLoc = cand.getLocationText() != null ? cand.getLocationText().toLowerCase() : "";
            OffsetDateTime candTime = cand.getEventAt();
            String candImg = extractFirstImageUrl(cand);

            // 1. Text Similarity (Jaccard token overlap - không thiên vị category)
            Set<String> intersection = new HashSet<>(srcTokens);
            intersection.retainAll(candTokens);
            Set<String> union = new HashSet<>(srcTokens);
            union.addAll(candTokens);
            double textSim = union.isEmpty() ? 0.35 : (double) intersection.size() / union.size();

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

            // 3. Visual & Color estimation (fallback khi AI service offline)
            boolean bothHaveImages = srcImg != null && candImg != null;
            double visSim = bothHaveImages
                    ? Math.min(0.95, 0.50 + textSim * 0.40)
                    : 0.45;
            double colorSim = bothHaveImages
                    ? Math.min(0.95, 0.55 + textSim * 0.35)
                    : 0.50;

            if (bothHaveImages && visSim < 0.55) {
                continue;
            }

            double finalScore = bothHaveImages
                    ? (0.45 * visSim + 0.15 * colorSim + 0.25 * textSim + 0.15 * stSim) * 100.0
                    : (srcImg != null || candImg != null)
                    ? (0.30 * 0.55 + 0.45 * textSim + 0.25 * stSim) * 100.0
                    : (0.65 * textSim + 0.35 * stSim) * 100.0;

            finalScore = Math.round(Math.min(97.5, Math.max(25.0, finalScore)) * 10.0) / 10.0;

            if (finalScore < 60.0) {
                continue;
            }

            List<String> reasons = new ArrayList<>();
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

            List<String> candImages = extractAllImageUrls(cand);
            SmartMatchItemDto fallbackDto = new SmartMatchItemDto(
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
            );
            fallbackDto.setDescription(cand.getDescription());
            fallbackDto.setImageUrls(candImages);
            fallbackDto.setSourceDetectedItem(!srcCat.isBlank() ? srcCat : source.getTitle());
            fallbackDto.setCandDetectedItem(!candCat.isBlank() ? candCat : cand.getTitle());
            list.add(fallbackDto);
        }

        list.sort((a, b) -> Double.compare(b.getMatchScore(), a.getMatchScore()));
        return list.size() > 10 ? new ArrayList<>(list.subList(0, 10)) : list;
    }

    private Map<String, Object> toItemMap(Post post) {
        Map<String, Object> map = new HashMap<>();
        List<String> allImageUrls = extractAllImageUrls(post);
        map.put("id", post.getId());
        map.put("title", post.getTitle());
        map.put("description", post.getDescription());
        map.put("categoryName", post.getCategory() != null ? post.getCategory().getName() : "");
        map.put("locationText", post.getLocationText());
        map.put("eventAt", post.getEventAt() != null ? post.getEventAt().toString() : "");
        map.put("imageUrl", allImageUrls.isEmpty() ? null : allImageUrls.get(0));
        map.put("imageUrls", allImageUrls);
        map.put("authorId", extractAuthorId(post));
        map.put("authorName", post.getCreatedBy() != null ? post.getCreatedBy().getFullName() : "");
        map.put("postType", post.getPostType());
        map.put("status", post.getStatus());
        return map;
    }

    private Long extractAuthorId(Post post) {
        if (post == null) return null;
        if (post.getCreatedBy() != null && post.getCreatedBy().getId() != null) {
            return post.getCreatedBy().getId();
        }
        if (post.getAccount() != null && post.getAccount().getId() != null) {
            return post.getAccount().getId();
        }
        return null;
    }

    private boolean isSameAuthor(Post a, Post b) {
        Long authorA = extractAuthorId(a);
        Long authorB = extractAuthorId(b);
        if (authorA != null && authorB != null) {
            return Objects.equals(authorA, authorB);
        }
        return false;
    }

    private List<String> extractAllImageUrls(Post post) {
        List<String> urls = new ArrayList<>();
        if (post.getImages() != null && imageStorage != null) {
            for (PostImage img : post.getImages()) {
                if (img != null) {
                    String resolved = imageStorage.readUrl(img.getStorageKey(), img.getImageUrl());
                    if (resolved != null && !resolved.isBlank() && !urls.contains(resolved)) {
                        urls.add(resolved);
                    }
                }
            }
        }
        return urls;
    }

    private String extractFirstImageUrl(Post post) {
        List<String> urls = extractAllImageUrls(post);
        return urls.isEmpty() ? null : urls.get(0);
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
