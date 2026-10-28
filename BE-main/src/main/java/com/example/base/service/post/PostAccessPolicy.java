package com.example.base.service.post;

import com.example.base.entity.AccountRole;
import com.example.base.entity.AccountStatus;
import com.example.base.exception.AppException;
import com.example.base.exception.ErrorCode;
import com.example.base.security.CustomUserDetails;
import java.util.Set;

/** Shared account checks for posting and managing a post. Ownership is checked by repository queries. */
final class PostAccessPolicy {
    private static final Set<AccountRole> AUTHOR_ROLES = Set.of(
            AccountRole.USER, AccountRole.EXTERNAL_USER, AccountRole.CHECKER);

    private PostAccessPolicy() {}

    static long requireActiveAccount(CustomUserDetails user) {
        if (user == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        if (user.getStatus() != AccountStatus.ACTIVE || !user.isAccountNonExpired() || !user.isAccountNonLocked()) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }
        return user.getId();
    }

    static long requireAuthor(CustomUserDetails user) {
        long accountId = requireActiveAccount(user);
        if (!AUTHOR_ROLES.contains(user.getRole())) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }
        return accountId;
    }

    static void requireValidPostId(long postId) {
        if (postId <= 0) {
            throw new AppException(ErrorCode.POST_ID_INVALID);
        }
    }
}
