package com.example.base.service.office;

import com.example.base.entity.Account;
import com.example.base.entity.AccountRole;
import com.example.base.entity.AccountStatus;
import com.example.base.exception.AppException;
import com.example.base.exception.ErrorCode;
import com.example.base.repository.AccountRepository;
import com.example.base.security.CustomUserDetails;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OfficeAccessTest {

    @Mock
    AccountRepository accounts;

    @InjectMocks
    OfficeAccess access;

    private Account account(long id, AccountRole role, AccountStatus status) {
        Account a = new Account();
        a.setId(id);
        a.setRole(role);
        a.setStatus(status);
        a.setEmail("user" + id + "@example.com");
        a.setFullName("User " + id);
        a.setPasswordHash("hash");
        return a;
    }

    @Test
    void utc001_require_activeApproverWithMatchingRole_returnsAccount() {
        Account acc = account(2L, AccountRole.APPROVER, AccountStatus.ACTIVE);
        when(accounts.findById(2L)).thenReturn(Optional.of(acc));

        Account result = access.require(new CustomUserDetails(acc), AccountRole.APPROVER);

        assertThat(result).isSameAs(acc);
        assertThat(result.getId()).isEqualTo(2L);
    }

    @Test
    void utc002_require_activeApproverWithNoRoleRestriction_returnsAccount() {
        Account acc = account(2L, AccountRole.APPROVER, AccountStatus.ACTIVE);
        when(accounts.findById(2L)).thenReturn(Optional.of(acc));

        Account result = access.require(new CustomUserDetails(acc));

        assertThat(result).isSameAs(acc);
    }

    @Test
    void utc003_require_roleMismatch_throwsForbidden() {
        Account acc = account(2L, AccountRole.USER, AccountStatus.ACTIVE);
        when(accounts.findById(2L)).thenReturn(Optional.of(acc));

        AppException ex = catchThrowableOfType(
                () -> access.require(new CustomUserDetails(acc), AccountRole.APPROVER, AccountRole.MANAGER),
                AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void utc004_require_nullUser_throwsUnauthorized() {
        AppException ex = catchThrowableOfType(
                () -> access.require(null),
                AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    void utc005_require_accountDeletedFromDb_throwsForbidden() {
        Account acc = account(2L, AccountRole.APPROVER, AccountStatus.ACTIVE);
        when(accounts.findById(2L)).thenReturn(Optional.empty());

        AppException ex = catchThrowableOfType(
                () -> access.require(new CustomUserDetails(acc)),
                AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void utc006_require_accountSuspended_throwsForbidden() {
        Account acc = account(2L, AccountRole.APPROVER, AccountStatus.SUSPENDED);
        when(accounts.findById(2L)).thenReturn(Optional.of(acc));

        AppException ex = catchThrowableOfType(
                () -> access.require(new CustomUserDetails(acc)),
                AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void utc007_require_accountValidUntilInPast_throwsForbidden() {
        Account acc = account(2L, AccountRole.APPROVER, AccountStatus.ACTIVE);
        acc.setValidUntil(OffsetDateTime.now().minusMinutes(5));
        when(accounts.findById(2L)).thenReturn(Optional.of(acc));

        AppException ex = catchThrowableOfType(
                () -> access.require(new CustomUserDetails(acc)),
                AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
    }
}

