package com.example.base.service.post;

import com.example.base.entity.Post;
import com.example.base.entity.PostImage;
import com.example.base.repository.PostRepository;
import com.example.base.storage.ImageStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * LỚP 1 TRONG KIẾN TRÚC 3 LỚP (Incremental Async Indexing Service):
 * - Đồng bộ bài đăng mới/sửa sang AI-service (`POST /api/index/upsert`) bất đồng bộ (@Async).
 * - Đồng bộ phần chênh lệch (Incremental Diff Sync) giữa PostgreSQL và AI Vector Index qua `GET /api/index/ids`:
 *   + Không bao giờ bỏ sót bài khi restart giữa chừng (chỉ đẩy đúng các ID còn thiếu!).
 *   + Ưu tiên đồng bộ loại bài đang bị ít hơn (vd: FOUND còn thiếu nhiều hơn LOST -> đẩy hết FOUND trước!).
 *   + Tự động dọn bài đã đóng/trả đồ/ẩn khỏi AI Index (`POST /api/index/prune`).
 */
@Service
public class AiIndexSyncService {

    private static final Logger log = LoggerFactory.getLogger(AiIndexSyncService.class);
    private static final int BULK_BATCH_SIZE = 25;

    private final PostRepository postRepository;
    private final ImageStorage imageStorage;
    private final AtomicBoolean syncInProgress = new AtomicBoolean(false);
    private final AtomicInteger indexedLostCount = new AtomicInteger(0);
    private final AtomicInteger indexedFoundCount = new AtomicInteger(0);
    private volatile long lastSyncFinishedMs = 0L;

    @Value("${app.ai.service-url:http://localhost:8000}")
    private String aiServiceUrl;

    public AiIndexSyncService(PostRepository postRepository, ImageStorage imageStorage) {
        this.postRepository = postRepository;
        this.imageStorage = imageStorage;
    }

    /**
     * Tự động chạy nền ngay khi Spring Boot khởi động xong để đồng bộ phần chênh lệch vào AI Vector Index.
     */
    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void warmupCorpusOnStartup() {
        ensureCorpusSyncedIfNeeded();
    }

    /**
     * Kích hoạt đồng bộ nền không chặn request người dùng (non-blocking, có cooldown 15 giây).
     */
    @Async
    public void triggerWarmupAsync() {
        if (System.currentTimeMillis() - lastSyncFinishedMs < 15_000L && !syncInProgress.get()) {
            return;
        }
        ensureCorpusSyncedIfNeeded();
    }

    /**
     * Kiểm tra riêng loại bài đối diện (LOST hoặc FOUND) đã được đồng bộ gần đủ (>= 90% DB) hay chưa.
     * Nếu AI-service chưa đồng bộ đủ loại bài này (vd mới có 3 bài FOUND trong khi DB có 200 bài FOUND),
     * SmartMatchService sẽ vừa chạy đồng bộ nền vừa đối sánh trực tiếp từ DB để KHÔNG BAO GIỜ bị rỗng kết quả.
     */
    @Transactional(readOnly = true)
    public boolean isOppositeTypeSynced(String oppositeType) {
        long dbCount = postRepository.countOpenVisibleByType(oppositeType);
        if (dbCount <= 0) {
            return true;
        }
        int cachedIndexed = "FOUND".equalsIgnoreCase(oppositeType)
                ? indexedFoundCount.get()
                : indexedLostCount.get();
        if (cachedIndexed <= 0) {
            refreshIndexedCountsQuick();
            cachedIndexed = "FOUND".equalsIgnoreCase(oppositeType)
                    ? indexedFoundCount.get()
                    : indexedLostCount.get();
        }
        // Nếu số lượng bài trong AI Index lệch so với DB (có bài mới đăng hoặc bài vừa đóng), kích hoạt đồng bộ nền ngay
        if (cachedIndexed != dbCount) {
            triggerWarmupAsync();
        }
        return cachedIndexed >= Math.max(1L, (long) Math.floor(dbCount * 0.85));
    }

    @SuppressWarnings("unchecked")
    private void refreshIndexedCountsQuick() {
        try {
            RestTemplate rest = createRestTemplate(1500, 3000);
            String endpoint = aiServiceUrl.replaceAll("/+$", "") + "/health";
            Map<String, Object> health = rest.getForObject(endpoint, Map.class);
            if (health != null && health.get("vectorIndex") instanceof Map<?, ?> idxStats) {
                Object lostObj = idxStats.get("lostIndexedPosts");
                Object foundObj = idxStats.get("foundIndexedPosts");
                if (lostObj instanceof Number n) indexedLostCount.set(n.intValue());
                if (foundObj instanceof Number n) indexedFoundCount.set(n.intValue());
            }
        } catch (Exception ignored) {
            // Keep existing counts if health check fails transiently
        }
    }

    /**
     * Đồng bộ bất đồng bộ 1 bài đăng theo ID sau khi transaction tạo/sửa bài đã commit thành công.
     */
    @Async
    @Transactional(readOnly = true)
    public void syncPostByIdAsync(Long postId) {
        if (postId == null) return;
        try {
            List<Post> found = postRepository.findVisibleByIds(List.of(postId));
            if (found.isEmpty()) {
                deletePostAsync(postId);
                return;
            }
            syncPostAsync(found.get(0));
        } catch (Exception ex) {
            log.debug("Async AI index sync by id skipped for post {}: {}", postId, ex.getMessage());
        }
    }

    /**
     * Đồng bộ bất đồng bộ 1 bài đăng sang AI-service ngay khi tạo mới / cập nhật.
     */
    @Async
    public void syncPostAsync(Post post) {
        if (post == null || post.getId() == null) return;
        try {
            boolean isActive = "OPEN".equalsIgnoreCase(post.getStatus())
                    && "VISIBLE".equalsIgnoreCase(post.getModerationStatus())
                    && post.getDeletedAt() == null;
            if (!isActive) {
                deletePostAsync(post.getId());
                return;
            }
            RestTemplate rest = createRestTemplate(3000, 15000);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(toItemMap(post), headers);
            String endpoint = aiServiceUrl.replaceAll("/+$", "") + "/api/index/upsert";
            rest.postForObject(endpoint, entity, Map.class);
        } catch (Exception ex) {
            log.debug("Async AI index upsert skipped for post {}: {}", post.getId(), ex.getMessage());
        }
    }

    /**
     * Xóa bất đồng bộ 1 bài đăng khỏi AI Vector Index khi bài bị xóa / đóng / đã trả đồ.
     */
    @Async
    public void deletePostAsync(long postId) {
        try {
            RestTemplate rest = createRestTemplate(3000, 5000);
            String endpoint = aiServiceUrl.replaceAll("/+$", "") + "/api/index/" + postId;
            rest.delete(endpoint);
        } catch (Exception ex) {
            log.debug("Async AI index delete skipped for post {}: {}", postId, ex.getMessage());
        }
    }

    /**
     * Đồng bộ phần chênh lệch (Incremental Diff Sync):
     * 1. Lấy tập ID hiện có trong AI Vector Index (`GET /api/index/ids`).
     * 2. Lấy toàn bộ ID đang OPEN & VISIBLE của FOUND và LOST từ PostgreSQL.
     * 3. Xóa các bài đã đóng/ẩn/xóa (`staleIds`) khỏi AI Index.
     * 4. Ưu tiên đồng bộ loại bài đang ít hơn (vd FOUND mới có 3 bài -> đẩy hết FOUND trước!),
     *    sau đó mới đồng bộ nốt các bài LOST còn thiếu theo lô nhỏ 25 bài.
     */
    @SuppressWarnings("unchecked")
    public void ensureCorpusSyncedIfNeeded() {
        if (!syncInProgress.compareAndSet(false, true)) {
            return;
        }
        try {
            RestTemplate rest = createRestTemplate(3000, 10000);
            String baseUrl = aiServiceUrl.replaceAll("/+$", "");
            Map<String, Object> idsResp = rest.getForObject(baseUrl + "/api/index/ids", Map.class);
            Set<Long> indexedIds = new HashSet<>();
            int curLost = 0;
            int curFound = 0;

            if (idsResp != null && idsResp.get("data") instanceof Map<?, ?> dataMap) {
                Object rawIds = dataMap.get("ids");
                if (rawIds instanceof List<?> list) {
                    for (Object o : list) {
                        try {
                            indexedIds.add(Long.parseLong(String.valueOf(o)));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
                if (dataMap.get("lostCount") instanceof Number n) curLost = n.intValue();
                if (dataMap.get("foundCount") instanceof Number n) curFound = n.intValue();
            }
            indexedLostCount.set(curLost);
            indexedFoundCount.set(curFound);

            List<Long> dbFoundIds = postRepository.findAllOpenVisibleIdsByType("FOUND");
            List<Long> dbLostIds = postRepository.findAllOpenVisibleIdsByType("LOST");

            Set<Long> allDbIds = new HashSet<>(dbFoundIds.size() + dbLostIds.size());
            allDbIds.addAll(dbFoundIds);
            allDbIds.addAll(dbLostIds);

            // 1. Dọn các bài đã đóng/trả đồ/ẩn khỏi AI Index
            List<Long> staleIds = indexedIds.stream().filter(id -> !allDbIds.contains(id)).toList();
            if (!staleIds.isEmpty()) {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                rest.postForObject(baseUrl + "/api/index/prune", new HttpEntity<>(Map.of("ids", staleIds), headers), Map.class);
                log.info("Pruned {} closed/hidden posts from AI Vector Index.", staleIds.size());
            }

            // 2. Lọc ra các ID còn thiếu của FOUND và LOST
            List<Long> missingFound = dbFoundIds.stream().filter(id -> !indexedIds.contains(id)).toList();
            List<Long> missingLost = dbLostIds.stream().filter(id -> !indexedIds.contains(id)).toList();

            if (missingFound.isEmpty() && missingLost.isEmpty()) {
                indexedFoundCount.set(dbFoundIds.size());
                indexedLostCount.set(dbLostIds.size());
                lastSyncFinishedMs = System.currentTimeMillis();
                return;
            }

            // 3. Ưu tiên đẩy hết loại bài đang bị thiếu nhiều / có ít bài trong index trước (vd FOUND trước)
            List<Long> orderedMissing = new ArrayList<>(missingFound.size() + missingLost.size());
            if (curFound <= curLost) {
                orderedMissing.addAll(missingFound);
                orderedMissing.addAll(missingLost);
            } else {
                orderedMissing.addAll(missingLost);
                orderedMissing.addAll(missingFound);
            }

            log.info(
                    "Starting incremental AI index sync: missing FOUND={}, missing LOST={} (already indexed: FOUND={}, LOST={}).",
                    missingFound.size(), missingLost.size(), curFound, curLost
            );

            RestTemplate bulkRest = createRestTemplate(5000, 45000);
            String bulkEndpoint = baseUrl + "/api/index/bulk";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            int syncedCount = 0;
            for (int start = 0; start < orderedMissing.size(); start += BULK_BATCH_SIZE) {
                int end = Math.min(start + BULK_BATCH_SIZE, orderedMissing.size());
                List<Long> batchIds = orderedMissing.subList(start, end);
                List<Post> batchPosts = postRepository.findVisibleByIds(batchIds);
                if (batchPosts.isEmpty()) continue;

                List<Map<String, Object>> payloadItems = batchPosts.stream().map(this::toItemMap).toList();
                try {
                    bulkRest.postForObject(bulkEndpoint, new HttpEntity<>(Map.of("items", payloadItems), headers), Map.class);
                    syncedCount += payloadItems.size();
                    for (Post p : batchPosts) {
                        if ("FOUND".equalsIgnoreCase(p.getPostType())) indexedFoundCount.incrementAndGet();
                        else if ("LOST".equalsIgnoreCase(p.getPostType())) indexedLostCount.incrementAndGet();
                    }
                } catch (Exception batchEx) {
                    log.warn("Skipped AI bulk batch [{}-{}] due to error: {}", start, end, batchEx.getMessage());
                }
            }

            log.info(
                    "Completed incremental AI index sync: +{} posts synced (total indexed: FOUND={}, LOST={}).",
                    syncedCount, indexedFoundCount.get(), indexedLostCount.get()
            );
            lastSyncFinishedMs = System.currentTimeMillis();
        } catch (Exception ex) {
            log.warn("Could not run incremental AI index sync: {}", ex.getMessage());
        } finally {
            syncInProgress.set(false);
        }
    }

    public Map<String, Object> toItemMap(Post post) {
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

    public Long extractAuthorId(Post post) {
        if (post == null) return null;
        if (post.getCreatedBy() != null && post.getCreatedBy().getId() != null) {
            return post.getCreatedBy().getId();
        }
        if (post.getAccount() != null && post.getAccount().getId() != null) {
            return post.getAccount().getId();
        }
        return null;
    }

    public List<String> extractAllImageUrls(Post post) {
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

    private RestTemplate createRestTemplate(int connectTimeoutMs, int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        return new RestTemplate(factory);
    }
}
