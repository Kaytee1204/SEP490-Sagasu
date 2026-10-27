package com.example.base.service.post;

import com.example.base.dto.post.CreateLostPostRequest;
import com.example.base.dto.post.CreateFoundPostRequest;
import com.example.base.dto.post.CreatedPostDto;
import com.example.base.entity.AccountRole;
import com.example.base.entity.AccountStatus;
import com.example.base.entity.Post;
import com.example.base.entity.PostImage;
import com.example.base.repository.AccountRepository;
import com.example.base.repository.CategoryRepository;
import com.example.base.exception.AppException;
import com.example.base.exception.ErrorCode;
import com.example.base.repository.PostRepository;
import com.example.base.security.CustomUserDetails;
import com.example.base.storage.ImageStorage;
import com.example.base.storage.ImageValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

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

    public CreatedPostDto createLost(CreateLostPostRequest request, List<MultipartFile> files, CustomUserDetails user) {
        return create(request, "LOST", "P2P", files, user);
    }

    public CreatedPostDto createFound(CreateFoundPostRequest request, List<MultipartFile> files, CustomUserDetails user) {
        if (!"P2P".equals(request.custodyMode()) && !"OFFICE".equals(request.custodyMode())) {
            throw new AppException(ErrorCode.POST_CUSTODY_INVALID);
        }
        return create(new CreateLostPostRequest(request.title(), request.description(), request.categoryId(),
                request.locationText(), request.eventAt(), request.privateIdentifiers()),
                "FOUND", request.custodyMode(), files, user);
    }

    private CreatedPostDto create(CreateLostPostRequest request, String type, String custody,
                                  List<MultipartFile> files, CustomUserDetails user) {
        if (user == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        if ((user.getRole() != AccountRole.USER && user.getRole() != AccountRole.EXTERNAL_USER
                && user.getRole() != AccountRole.CHECKER) || user.getStatus() != AccountStatus.ACTIVE
                || !user.isAccountNonExpired() || !user.isAccountNonLocked()) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }
        if (!categories.existsByCategoryIdAndActiveTrue(request.categoryId())) {
            throw new AppException(ErrorCode.POST_CATEGORY_UNAVAILABLE);
        }
        var images = validator.validate(files);
        var keys = new ArrayList<String>();
        try {
            // Upload before opening a DB transaction. Never accept object keys supplied by a client.
            for (var image : images) {
                String key = "posts/" + user.getId() + "/" + UUID.randomUUID() + "." + image.extension();
                keys.add(key); // Include uncertain writes (e.g. a timed-out PUT) in compensation.
                storage.put(key, image.bytes(), image.mime());
            }
            return new TransactionTemplate(transactionManager).execute(status -> {
                var owner = accounts.findActiveAccountForPostCreation(user.getId())
                        .orElseThrow(() -> new AppException(ErrorCode.FORBIDDEN));
                var category = categories.findActiveForPostCreation(request.categoryId())
                        .orElseThrow(() -> new AppException(ErrorCode.POST_CATEGORY_UNAVAILABLE));
                var post = "FOUND".equals(type)
                        ? Post.found(owner, category, request.title().trim(), request.description().trim(),
                            request.locationText().trim(), request.eventAt(), custody)
                        : Post.lost(owner, category, request.title().trim(), request.description().trim(),
                            request.locationText().trim(), request.eventAt());
                post.updatePrivateIdentifiers(request.privateIdentifiers());
                for (int i = 0; i < images.size(); i++) {
                    var image = images.get(i);
                    post.addImage(new PostImage(keys.get(i), i, image.mime(), image.bytes().length,
                            image.width(), image.height(), image.hash()));
                }
                var saved = posts.saveAndFlush(post);
                return new CreatedPostDto(saved.getId(), saved.getPostType(), saved.getStatus(), saved.getModerationStatus());
            });
        } catch (RuntimeException ex) {
            for (String key : keys) {
                try {
                    storage.delete(key);
                } catch (RuntimeException cleanup) {
                    log.error("Failed to clean up newly uploaded image {}", key);
                }
            }
            throw ex;
        }
    }
}
