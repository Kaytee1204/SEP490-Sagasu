package com.example.base.service.post;

import com.example.base.dto.post.PrivateIdentifiersDto;
import com.example.base.exception.AppException;
import com.example.base.exception.ErrorCode;
import com.example.base.repository.PostRepository;
import com.example.base.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UC17: secrets use owner-only endpoints and never enter a public post DTO. */
@Service
@RequiredArgsConstructor
public class PostPrivateIdentifiersService {
    private final PostRepository posts;

    @Transactional(readOnly = true)
    public PrivateIdentifiersDto read(long postId, CustomUserDetails user) {
        long ownerId = PostAccessPolicy.requireActiveAccount(user);
        PostAccessPolicy.requireValidPostId(postId);
        var post = posts.findByIdAndAccount_IdAndDeletedAtIsNull(postId, ownerId)
                .orElseThrow(() -> new AppException(ErrorCode.POST_OWNER_REQUIRED));
        String identifiers = post.getPrivateIdentifiers();
        return new PrivateIdentifiersDto(identifiers == null ? "" : identifiers);
    }

    @Transactional
    public void update(long postId, PrivateIdentifiersDto request, CustomUserDetails user) {
        long ownerId = PostAccessPolicy.requireActiveAccount(user);
        PostAccessPolicy.requireValidPostId(postId);
        var post = posts.findOwnedForUpdate(postId, ownerId)
                .orElseThrow(() -> new AppException(ErrorCode.POST_OWNER_REQUIRED));
        post.updatePrivateIdentifiers(request.privateIdentifiers());
        posts.flush();
    }
}
