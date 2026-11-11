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

    @Test
    void requireActiveAccount_whenUserNull_throwsUnauthorized() {
        AppException ex = assertThrows(AppException.class, () -> PostAccessPolicy.requireActiveAccount(null));
        assertEquals(ErrorCode.UNAUTHORIZED, ex.getErrorCode());
    }

    @Test
    void requireActiveAccount_whenSuspended_throwsForbidden() {
        CustomUserDetails user = user(7L, AccountRole.USER, AccountStatus.SUSPENDED, null, null);
        AppException ex = assertThrows(AppException.class, () -> PostAccessPolicy.requireActiveAccount(user));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    @Test
    void requireActiveAccount_whenLockedUntilFuture_throwsForbidden() {
        CustomUserDetails user = user(7L, AccountRole.USER, AccountStatus.ACTIVE,
                OffsetDateTime.now().plusHours(1), null);
        AppException ex = assertThrows(AppException.class, () -> PostAccessPolicy.requireActiveAccount(user));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    @Test
    void requireActiveAccount_whenExpired_throwsForbidden() {
        CustomUserDetails user = user(7L, AccountRole.USER, AccountStatus.ACTIVE,
                null, OffsetDateTime.now().minusMinutes(1));
        AppException ex = assertThrows(AppException.class, () -> PostAccessPolicy.requireActiveAccount(user));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    @Test
    void requireAuthor_whenActiveUser_returnsAccountId() {
        CustomUserDetails user = user(7L, AccountRole.USER, AccountStatus.ACTIVE, null, null);
        assertEquals(7L, PostAccessPolicy.requireAuthor(user));
    }

    @Test
    void requireAuthor_whenRoleNull_throwsNullPointerException() {
        CustomUserDetails user = user(7L, null, AccountStatus.ACTIVE, null, null);
        assertThrows(NullPointerException.class, () -> PostAccessPolicy.requireAuthor(user));
    }

    @Test
    void requireValidPostId_whenZeroOrNegative_throwsPostIdInvalid() {
        AppException zero = assertThrows(AppException.class, () -> PostAccessPolicy.requireValidPostId(0L));
        assertEquals(ErrorCode.POST_ID_INVALID, zero.getErrorCode());

        AppException negative = assertThrows(AppException.class, () -> PostAccessPolicy.requireValidPostId(-1L));
        assertEquals(ErrorCode.POST_ID_INVALID, negative.getErrorCode());
    }

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

