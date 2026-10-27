package com.example.base.service.post;

import com.example.base.dto.post.PrivateIdentifiersDto;
import com.example.base.exception.AppException;
import com.example.base.exception.ErrorCode;
import com.example.base.repository.PostRepository;
import com.example.base.security.CustomUserDetails;
import com.example.base.entity.AccountStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PostPrivateIdentifiersService {
    private final PostRepository posts;

    @Transactional(readOnly = true)
    public PrivateIdentifiersDto read(long id, CustomUserDetails user) {
        long ownerId = requireOwner(id, user);
        var post = posts.findByIdAndAccount_IdAndDeletedAtIsNullAndWithdrawnAtIsNull(id, ownerId)
                .orElseThrow(() -> new AppException(ErrorCode.POST_OWNER_REQUIRED));
        return new PrivateIdentifiersDto(post.getPrivateIdentifiers() == null ? "" : post.getPrivateIdentifiers());
    }

    @Transactional
    public void update(long id, PrivateIdentifiersDto request, CustomUserDetails user) {
        long ownerId = requireOwner(id, user);
        var post = posts.findOwnedForUpdate(id, ownerId)
                .orElseThrow(() -> new AppException(ErrorCode.POST_OWNER_REQUIRED));
        post.updatePrivateIdentifiers(request.privateIdentifiers());
        posts.flush();
    }

    private long requireOwner(long id, CustomUserDetails user) {
        if (user == null) throw new AppException(ErrorCode.UNAUTHORIZED);
        if (user.getStatus() != AccountStatus.ACTIVE || !user.isAccountNonExpired() || !user.isAccountNonLocked()) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }
        if (id <= 0) throw new AppException(ErrorCode.POST_ID_INVALID);
        return user.getId();
    }
}
