package com.example.base.service.post;

import com.example.base.entity.Account;
import com.example.base.entity.AccountRole;
import com.example.base.entity.AccountStatus;
import com.example.base.exception.AppException;
import com.example.base.exception.ErrorCode;
import com.example.base.security.CustomUserDetails;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PostAccessPolicyTest {

    // UTC001 - requireActiveAccount: null user throws UNAUTHORIZED
    @Test
    void requireActiveAccount_whenUserNull_throwsUnauthorized() {
        AppException ex = assertThrows(AppException.class, () -> PostAccessPolicy.requireActiveAccount(null));
        assertEquals(ErrorCode.UNAUTHORIZED, ex.getErrorCode());
    }

    // UTC002 - requireActiveAccount: SUSPENDED status throws FORBIDDEN
    @Test
    void requireActiveAccount_whenSuspended_throwsForbidden() {
        CustomUserDetails user = user(7L, AccountRole.USER, AccountStatus.SUSPENDED, null, null);
        AppException ex = assertThrows(AppException.class, () -> PostAccessPolicy.requireActiveAccount(user));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    // UTC003 - requireActiveAccount: ACTIVE account with future lockedUntil throws FORBIDDEN
    @Test
    void requireActiveAccount_whenLockedUntilFuture_throwsForbidden() {
        CustomUserDetails user = user(7L, AccountRole.USER, AccountStatus.ACTIVE,
                OffsetDateTime.now().plusHours(1), null);
        AppException ex = assertThrows(AppException.class, () -> PostAccessPolicy.requireActiveAccount(user));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    // Supplementary (PostAccessPolicy.requireActiveAccount): expired validUntil throws FORBIDDEN
    @Test
    void requireActiveAccount_whenExpired_throwsForbidden() {
        CustomUserDetails user = user(7L, AccountRole.USER, AccountStatus.ACTIVE,
                null, OffsetDateTime.now().minusMinutes(1));
        AppException ex = assertThrows(AppException.class, () -> PostAccessPolicy.requireActiveAccount(user));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    // UTC004 - requireAuthor: ACTIVE user with role returns account id
    @Test
    void requireAuthor_whenActiveUser_returnsAccountId() {
        CustomUserDetails user = user(7L, AccountRole.USER, AccountStatus.ACTIVE, null, null);
        assertEquals(7L, PostAccessPolicy.requireAuthor(user));
    }

    // Supplementary (PostAccessPolicy.requireAuthor): null role throws NullPointerException
    @Test
    void requireAuthor_whenRoleNull_throwsNullPointerException() {
        CustomUserDetails user = user(7L, null, AccountStatus.ACTIVE, null, null);
        assertThrows(NullPointerException.class, () -> PostAccessPolicy.requireAuthor(user));
    }

    // UTC005, UTC007 - requireValidPostId: postId=0 and postId=-1 throw POST_ID_INVALID
    @Test
    void requireValidPostId_whenZeroOrNegative_throwsPostIdInvalid() {
        // UTC005 - postId=0
        AppException zero = assertThrows(AppException.class, () -> PostAccessPolicy.requireValidPostId(0L));
        assertEquals(ErrorCode.POST_ID_INVALID, zero.getErrorCode());

        // UTC007 - postId=-1
        AppException negative = assertThrows(AppException.class, () -> PostAccessPolicy.requireValidPostId(-1L));
        assertEquals(ErrorCode.POST_ID_INVALID, negative.getErrorCode());
    }

    // UTC006 - requireValidPostId: boundary postId=1 succeeds
    @Test
    void requireValidPostId_whenPositive_succeeds() {
        assertDoesNotThrow(() -> PostAccessPolicy.requireValidPostId(1L));
    }

    private static CustomUserDetails user(Long id, AccountRole role, AccountStatus status,
                                          OffsetDateTime lockedUntil, OffsetDateTime validUntil) {
        Account account = new Account();
        account.setId(id);
        account.setEmail("user" + id + "@fpt.edu.vn");
        account.setFullName("User " + id);
        account.setRole(role);
        account.setStatus(status);
        account.setLockedUntil(lockedUntil);
        account.setValidUntil(validUntil);
        return new CustomUserDetails(account);
    }
}

