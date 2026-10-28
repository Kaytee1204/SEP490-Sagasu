package com.example.base.service.post;

import com.example.base.dto.post.CreateFoundPostRequest;
import com.example.base.dto.post.CreateLostPostRequest;
import com.example.base.dto.post.CreatedPostDto;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostCreationService {
    private final PostRepository posts;
    private final CategoryRepository categories;
    private final AccountRepository accounts;
    private final ImageStorage storage;
    private final ImageValidator validator;
    private final PlatformTransactionManager transactionManager;

    /** UC15: a Lost post always uses P2P. */
    public CreatedPostDto createLost(CreateLostPostRequest request, List<MultipartFile> files, CustomUserDetails user) {
        PostDraft draft = new PostDraft("LOST", request.title(), request.description(), request.categoryId(),
                request.locationText(), request.eventAt(), "P2P", request.privateIdentifiers(), null);
        return create(draft, files, user);
    }

    /** UC16: Found OFFICE always waits for Approver review and includes a proposed handover time. */
    public CreatedPostDto createFound(CreateFoundPostRequest request, List<MultipartFile> files, CustomUserDetails user) {
        if (!"P2P".equals(request.custodyMode()) && !"OFFICE".equals(request.custodyMode())) {
            throw new AppException(ErrorCode.POST_CUSTODY_INVALID);
        }
        PostDraft draft = new PostDraft("FOUND", request.title(), request.description(), request.categoryId(),
                request.locationText(), request.eventAt(), request.custodyMode(), request.privateIdentifiers(), request.handoverRequestedAt());
        return create(draft, files, user);
    }

    private CreatedPostDto create(PostDraft draft, List<MultipartFile> files, CustomUserDetails user) {
        long authorId = PostAccessPolicy.requireAuthor(user);
        PostHandoverPolicy.validateRequestedTime(draft.postType(), draft.custodyMode(), draft.handoverRequestedAt());
        if (!categories.existsByCategoryIdAndActiveTrue(draft.categoryId())) {
            throw new AppException(ErrorCode.POST_CATEGORY_UNAVAILABLE);
        }
        List<ValidatedImage> images = validator.validate(files);
        List<String> uploadedKeys = new ArrayList<>();

        try {
            // Do not hold database locks while waiting for R2 uploads.
            uploadImages(authorId, images, uploadedKeys);
            return new TransactionTemplate(transactionManager).execute(transaction ->
                    savePost(draft, authorId, images, uploadedKeys));
        } catch (RuntimeException ex) {
            deleteUploadedImages(uploadedKeys);
            throw ex;
        }
    }

    private CreatedPostDto savePost(PostDraft draft, long authorId, List<ValidatedImage> images,
                                   List<String> uploadedKeys) {
        // Re-read role/status after uploads: use the current database account, not client data.
        var author = accounts.findActiveAccountForPostCreation(authorId)
                .orElseThrow(() -> new AppException(ErrorCode.FORBIDDEN));
        var category = categories.findActiveForPostCreation(draft.categoryId())
                .orElseThrow(() -> new AppException(ErrorCode.POST_CATEGORY_UNAVAILABLE));
        PostHandoverPolicy.validateRequestedTime(draft.postType(), draft.custodyMode(), draft.handoverRequestedAt());

        Post post;
        if ("FOUND".equals(draft.postType())) {
            post = Post.found(author, category, draft.title().trim(), draft.description().trim(),
                    draft.locationText().trim(), draft.eventAt(), draft.custodyMode(), draft.handoverRequestedAt());
        } else {
            post = Post.lost(author, category, draft.title().trim(), draft.description().trim(),
                    draft.locationText().trim(), draft.eventAt());
        }
        post.updatePrivateIdentifiers(draft.privateIdentifiers());
        attachImages(post, images, uploadedKeys);
        posts.saveAndFlush(post);
        return new CreatedPostDto(post.getId(), post.getPostType(), post.getStatus(), post.getModerationStatus());
    }

    private void uploadImages(long authorId, List<ValidatedImage> images, List<String> uploadedKeys) {
        for (ValidatedImage image : images) {
            String key = "posts/" + authorId + "/" + UUID.randomUUID() + "." + image.extension();
            uploadedKeys.add(key); // Include uncertain PUTs so they can be cleaned on failure.
            storage.put(key, image.bytes(), image.mime());
        }
    }

    private void attachImages(Post post, List<ValidatedImage> images, List<String> uploadedKeys) {
        for (int index = 0; index < images.size(); index++) {
            ValidatedImage image = images.get(index);
            post.addImage(new PostImage(uploadedKeys.get(index), index, image.mime(), image.bytes().length,
                    image.width(), image.height(), image.hash()));
        }
    }

    private void deleteUploadedImages(List<String> keys) {
        for (String key : keys) {
            try {
                storage.delete(key);
            } catch (RuntimeException ex) {
                log.error("Failed to clean up newly uploaded image {}", key);
            }
        }
    }

    /** Common internal input for Lost/Found; never used as a public response. */
    private record PostDraft(String postType, String title, String description, Long categoryId,
                             String locationText, OffsetDateTime eventAt, String custodyMode,
                             String privateIdentifiers, OffsetDateTime handoverRequestedAt) {}
}
