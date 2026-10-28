package com.example.base.service.post;

import com.example.base.dto.post.CreatedPostDto;
import com.example.base.dto.post.EditPostDto;
import com.example.base.dto.post.MyPostDto;
import com.example.base.dto.post.MyPostPageDto;
import com.example.base.dto.post.PostDetailDto;
import com.example.base.dto.post.DeletePostRequest;
import com.example.base.dto.post.UpdatePostRequest;
import com.example.base.entity.Post;
import com.example.base.entity.PostImage;
import com.example.base.exception.AppException;
import com.example.base.exception.ErrorCode;
import com.example.base.repository.AccountRepository;
import com.example.base.repository.CategoryRepository;
import com.example.base.repository.PostRepository;
import com.example.base.security.CustomUserDetails;
import com.example.base.storage.ImageStorage;
import com.example.base.storage.ImageValidator;
import com.example.base.storage.ImageValidator.ValidatedImage;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostManagementService {
    private static final Set<String> MY_POST_FILTERS = Set.of(
            "ALL", "PENDING", "VISIBLE", "REJECTED", "HIDDEN");

    private final PostRepository posts;
    private final AccountRepository accounts;
    private final CategoryRepository categories;
    private final ImageStorage storage;
    private final ImageValidator validator;
    private final PlatformTransactionManager transactionManager;
    private final EntityManager entityManager;

    /** UC18: page IDs first, then load images/category without collection-fetch pagination. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public MyPostPageDto listMine(int page, int size, String filter, CustomUserDetails user) {
        long authorId = PostAccessPolicy.requireAuthor(user);
        validateListRequest(page, size, filter);

        var pageOfIds = posts.findMyIds(authorId, filter, PageRequest.of(page - 1, size));
        Map<Long, Post> postsById = new HashMap<>();
        if (!pageOfIds.isEmpty()) {
            for (Post post : posts.findMyByIds(pageOfIds.getContent(), authorId)) {
                postsById.put(post.getId(), post);
            }
        }

        List<MyPostDto> result = new ArrayList<>();
        for (Long postId : pageOfIds.getContent()) {
            result.add(toMyPostDto(postsById.get(postId), authorId));
        }
        return new MyPostPageDto(result, page, size, pageOfIds.getTotalElements(),
                pageOfIds.getTotalPages(), pageOfIds.isFirst(), pageOfIds.isLast());
    }

    /** UC19: populate the owner's edit form; private identifiers have their own endpoint. */
    @Transactional(readOnly = true)
    public EditPostDto getForEdit(long postId, CustomUserDetails user) {
        Post post = findOwnedPost(postId, PostAccessPolicy.requireAuthor(user));
        requireEditable(post);

        List<PostDetailDto.ImageDto> images = new ArrayList<>();
        for (PostImage image : post.getImages()) {
            images.add(new PostDetailDto.ImageDto(image.getId(),
                    storage.readUrl(image.getStorageKey(), image.getImageUrl()), image.getSortOrder()));
        }
        return new EditPostDto(post.getId(), post.getPostType(), post.getTitle(), post.getDescription(),
                post.getCategory().getCategoryId(), post.getCategory().getName(), post.getLocationText(),
                post.getEventAt(), post.getCustodyMode(), post.getUpdatedAt(), images, post.getHandoverRequestedAt());
    }

    /** UC19: validate -> upload -> lock/recheck/save -> remove old image objects after commit. */
    public CreatedPostDto update(long postId, UpdatePostRequest request, List<MultipartFile> files,
                                 CustomUserDetails user) {
        long ownerId = PostAccessPolicy.requireAuthor(user);
        Post currentPost = findOwnedPost(postId, ownerId);
        requireEditable(currentPost);
        validateUpdate(currentPost, request);

        List<ValidatedImage> newImages = validator.validate(files);
        if (newImages.size() + request.keptImageIds().size() > 5) {
            throw new AppException(ErrorCode.IMAGE_COUNT_EXCEEDED);
        }

        List<String> uploadedKeys = new ArrayList<>();
        UpdateResult saved;
        try {
            uploadNewImages(ownerId, newImages, uploadedKeys);
            saved = new TransactionTemplate(transactionManager).execute(transaction ->
                    saveUpdate(postId, ownerId, request, newImages, uploadedKeys));
        } catch (RuntimeException ex) {
            deleteImagesQuietly(uploadedKeys);
            throw ex;
        }

        // TransactionTemplate returns only after commit succeeds.
        deleteImagesQuietly(saved.removedImageKeys());
        return saved.post();
    }



    /** UC20: soft deletion retains images, post history and the deletion reason. */
    @Transactional
    public void delete(long postId, DeletePostRequest request, CustomUserDetails user) {
        Post post = lockOwnedPost(postId, PostAccessPolicy.requireAuthor(user));
        post.softDelete(request.reason());
        posts.flush();
    }

    private UpdateResult saveUpdate(long postId, long ownerId, UpdatePostRequest request,
                                    List<ValidatedImage> newImages, List<String> uploadedKeys) {
        var author = accounts.findActiveAccountForPostCreation(ownerId)
                .orElseThrow(() -> new AppException(ErrorCode.FORBIDDEN));
        Post post = lockOwnedPost(postId, ownerId);
        requireEditable(post);
        validateUpdate(post, request);
        var category = categories.findActiveForPostCreation(request.categoryId())
                .orElseThrow(() -> new AppException(ErrorCode.POST_CATEGORY_UNAVAILABLE));

        List<String> removedKeys = findRemovedImageKeys(post, request.keptImageIds());
        post.edit(category, request.title(), request.description(), request.locationText(),
                request.eventAt(), request.custodyMode(), request.handoverRequestedAt(), author.getRole());
        int finalImageCount = request.keptImageIds().size() + newImages.size();
        moveImagesToTemporaryPositions(post, finalImageCount);
        post.retainImages(request.keptImageIds());
        attachNewImages(post, newImages, uploadedKeys);
        posts.flush();

        var response = new CreatedPostDto(post.getId(), post.getPostType(), post.getStatus(), post.getModerationStatus());
        return new UpdateResult(response, removedKeys);
    }

    private Post findOwnedPost(long postId, long ownerId) {
        PostAccessPolicy.requireValidPostId(postId);
        return posts.findManagedDetail(postId, ownerId)
                .orElseThrow(() -> new AppException(ErrorCode.POST_OWNER_REQUIRED));
    }

    private Post lockOwnedPost(long postId, long ownerId) {
        PostAccessPolicy.requireValidPostId(postId);
        Post post = posts.findManagedForUpdate(postId, ownerId)
                .orElseThrow(() -> new AppException(ErrorCode.POST_OWNER_REQUIRED));

        // Open EntityManager in View may still hold the earlier pre-upload snapshot.
        // A row lock alone does not refresh that snapshot: explicitly reload while locked.
        entityManager.refresh(post, LockModeType.PESSIMISTIC_WRITE);
        if (post.getDeletedAt() != null || !Objects.equals(post.getAccount().getId(), ownerId)) {
            throw new AppException(ErrorCode.POST_OWNER_REQUIRED);
        }
        return post;
    }

    private void validateListRequest(int page, int size, String filter) {
        if (page < 1 || size < 1 || size > 100) {
            throw new AppException(ErrorCode.POST_PAGINATION_INVALID);
        }
        if (filter == null || !MY_POST_FILTERS.contains(filter)) {
            throw new AppException(ErrorCode.POST_FILTER_INVALID);
        }
    }

    // TODO: Add claim/dispute checks when those use cases and their data model are implemented.
    private void requireEditable(Post post) {
        if (post.getDeletedAt() != null || !"OPEN".equals(post.getStatus())
                || "HIDDEN".equals(post.getModerationStatus())) {
            throw new AppException(ErrorCode.POST_NOT_EDITABLE);
        }
    }


    private void validateUpdate(Post post, UpdatePostRequest request) {
        PostHandoverPolicy.validateRequestedTime(post.getPostType(), request.custodyMode(), request.handoverRequestedAt());
        if (!post.getUpdatedAt().isEqual(request.expectedUpdatedAt())) {
            throw new AppException(ErrorCode.POST_STALE);
        }
        Set<Long> requestedIds = new HashSet<>(request.keptImageIds());
        List<Long> existingIds = post.getImages().stream().map(PostImage::getId).toList();
        if (requestedIds.size() != request.keptImageIds().size() || !existingIds.containsAll(requestedIds)) {
            throw new AppException(ErrorCode.POST_IMAGES_INVALID);
        }
        if ("LOST".equals(post.getPostType()) && !"P2P".equals(request.custodyMode())) {
            throw new AppException(ErrorCode.POST_CUSTODY_INVALID);
        }
        if (!categories.existsByCategoryIdAndActiveTrue(request.categoryId())) {
            throw new AppException(ErrorCode.POST_CATEGORY_UNAVAILABLE);
        }
    }

    private void uploadNewImages(long ownerId, List<ValidatedImage> images, List<String> uploadedKeys) {
        for (ValidatedImage image : images) {
            String key = "posts/" + ownerId + "/" + UUID.randomUUID() + "." + image.extension();
            uploadedKeys.add(key); // Also clean up an uncertain PUT, e.g. a timed-out upload.
            storage.put(key, image.bytes(), image.mime());
        }
    }

    private List<String> findRemovedImageKeys(Post post, List<Long> keptIds) {
        List<String> result = new ArrayList<>();
        for (PostImage image : post.getImages()) {
            if (!keptIds.contains(image.getId()) && image.getStorageKey() != null) {
                result.add(image.getStorageKey());
            }
        }
        return result;
    }

    private void moveImagesToTemporaryPositions(Post post, int finalImageCount) {
        if (post.getImages().isEmpty()) return;
        int currentMaximum = post.getImages().stream().mapToInt(PostImage::getSortOrder).max().orElse(0);
        int temporaryPosition = Math.max(currentMaximum, finalImageCount - 1) + 1;
        for (PostImage image : post.getImages()) image.reorder(temporaryPosition++);
        // IDENTITY inserts may run before retained rows get their final positions.
        // Reserve the entire final range for both retained and newly uploaded images.
        posts.flush();
    }

    private void attachNewImages(Post post, List<ValidatedImage> images, List<String> keys) {
        int firstPosition = post.getImages().size();
        for (int index = 0; index < images.size(); index++) {
            ValidatedImage image = images.get(index);
            post.addImage(new PostImage(keys.get(index), firstPosition + index, image.mime(),
                    image.bytes().length, image.width(), image.height(), image.hash()));
        }
    }

    private MyPostDto toMyPostDto(Post post, long viewerId) {
        String editReason = getEditBlockedReason(post, viewerId);
        String deleteReason = getDeleteBlockedReason(post, viewerId);
        String thumbnail = null;
        if (!post.getImages().isEmpty()) {
            PostImage image = post.getImages().getFirst();
            thumbnail = storage.readUrl(image.getStorageKey(), image.getImageUrl());
        }
        return new MyPostDto(post.getId(), post.getPostType(), post.getTitle(), post.getCategory().getName(),
                post.getLocationText(), post.getStatus(), post.getModerationStatus(), post.getCreatedAt(),
                post.getUpdatedAt(), thumbnail, editReason == null,
                deleteReason == null,
                editReason, deleteReason, post.getHandoverRequestedAt());
    }

    private String getEditBlockedReason(Post post, long viewerId) {
        if (!Objects.equals(post.getAccount().getId(), viewerId)) return "Bài được đăng hộ tài khoản khác";
        if (!"OPEN".equals(post.getStatus())) return "Bài không còn ở trạng thái mở";
        if ("HIDDEN".equals(post.getModerationStatus())) return "Bài đã bị ẩn bởi kiểm duyệt";
        return null;
    }

    private String getDeleteBlockedReason(Post post, long viewerId) {
        if (!Objects.equals(post.getAccount().getId(), viewerId)) return "Bài được đăng hộ tài khoản khác";
        return null;
    }

    private void deleteImagesQuietly(List<String> keys) {
        for (String key : keys) {
            try {
                storage.delete(key);
            } catch (RuntimeException ex) {
                log.error("Could not clean up post image {}", key);
            }
        }
    }

    private record UpdateResult(CreatedPostDto post, List<String> removedImageKeys) {}
}
