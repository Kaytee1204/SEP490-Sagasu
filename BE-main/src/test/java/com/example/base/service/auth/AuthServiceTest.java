package com.example.base.service.auth;

import com.example.base.dto.account.AccountDto;
import com.example.base.dto.auth.ChangePasswordConfirmRequest;
import com.example.base.dto.auth.ForgotPasswordRequest;
import com.example.base.dto.auth.LoginRequest;
import com.example.base.dto.auth.LoginResponse;
import com.example.base.dto.auth.RequestChangePasswordOtpRequest;
import com.example.base.dto.auth.ResetPasswordRequest;
import com.example.base.dto.auth.SendOtpRequest;
import com.example.base.dto.auth.VerifyOtpRequest;
import com.example.base.entity.Account;
import com.example.base.entity.AccountRole;
import com.example.base.entity.AccountStatus;
import com.example.base.entity.LoginAuditLog;
import com.example.base.entity.OtpChallenge;
import com.example.base.entity.OtpPurpose;
import com.example.base.entity.RefreshToken;
import com.example.base.exception.AppException;
import com.example.base.exception.ErrorCode;
import com.example.base.mapper.AccountMapper;
import com.example.base.repository.AccountRepository;
import com.example.base.repository.LoginAuditLogRepository;
import com.example.base.repository.OtpChallengeRepository;
import com.example.base.repository.RefreshTokenRepository;
import com.example.base.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** One test per UTC of sheet "Auth" (UTC001-UTC055; EmailServiceTest holds UTC056-UTC060). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    private static final String EMAIL = "usertest@gmail.com";

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private OtpChallengeRepository otpChallengeRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private LoginAuditLogRepository loginAuditLogRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Spy
    private AccountMapper accountMapper = new AccountMapper();
    @Mock
    private EmailService emailService;

    @InjectMocks
    private AuthService service;

    /** Account behind {@link #EMAIL}; tests adjust its status/counters as needed. */
    private Account acc;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "refreshTokenExpirationMs", 604800000L);
        acc = account(1L, EMAIL, AccountRole.USER, AccountStatus.ACTIVE);
        when(accountRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(acc));
        when(accountRepository.findByEmailIgnoreCase("none@gmail.com")).thenReturn(Optional.empty());
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtTokenProvider.generateAccessToken(any())).thenReturn("jwt-access-token");
        when(passwordEncoder.matches("Valid@123", "encoded-hash")).thenReturn(true);
        when(otpChallengeRepository.findByAccount_IdAndPurposeAndUsedAtIsNullAndRevokedAtIsNull(any(), any()))
                .thenReturn(List.of());
        when(emailService.sendOtpEmail(anyString(), anyString(), any())).thenReturn(true);
    }

    // ---------------------------------------------------------------- login

    @Test
    void utc001_login_trimsAndLowercasesEmailAndIssuesTokens() {
        LoginResponse res = service.login(loginReq("  USERTEST@GMAIL.COM ", "Valid@123"));

        assertEquals("jwt-access-token", res.getAccessToken());
        assertNotNull(res.getRefreshToken());
        assertEquals(EMAIL, res.getAccount().getEmail());
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void utc002_login_resetsFailedCountAndRecordsAudit() {
        acc.setFailedLoginCount(2);
        acc.setLockedUntil(OffsetDateTime.now().minusMinutes(5));

        service.login(loginReq(EMAIL, "Valid@123"));

        assertEquals(0, acc.getFailedLoginCount());
        assertNull(acc.getLockedUntil());
        assertAuditSaved("LOGIN_PASSWORD", true);
    }

    @Test
    void utc003_login_missingAccountThrowsInvalidCredentials() {
        assertEquals(ErrorCode.INVALID_CREDENTIALS, code(() -> service.login(loginReq("none@gmail.com", "x"))));
    }

    @Test
    void utc004_login_firstWrongPasswordIncrementsCount() {
        AppException ex = assertThrows(AppException.class, () -> service.login(loginReq(EMAIL, "wrong")));
        assertEquals(ErrorCode.INVALID_CREDENTIALS, ex.getErrorCode());
        assertEquals("Mật khẩu không chính xác. Bạn còn 4 lần thử trước khi tài khoản bị khóa.", ex.getMessage());
        assertEquals(1, acc.getFailedLoginCount());
        assertAuditSaved("LOGIN_PASSWORD", false);
    }

    @Test
    void utc005_login_fourthWrongPasswordLeavesOneAttempt() {
        acc.setFailedLoginCount(3);
        AppException ex = assertThrows(AppException.class, () -> service.login(loginReq(EMAIL, "wrong")));
        assertEquals(ErrorCode.INVALID_CREDENTIALS, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Bạn còn 1 lần thử"));
        assertEquals(4, acc.getFailedLoginCount());
        assertEquals(AccountStatus.ACTIVE, acc.getStatus());
    }

    @Test
    void utc006_login_fifthWrongPasswordLocksAccount() {
        acc.setFailedLoginCount(4);
        AppException ex = assertThrows(AppException.class, () -> service.login(loginReq(EMAIL, "wrong")));
        assertEquals(ErrorCode.ACCOUNT_LOCKED, ex.getErrorCode());
        assertEquals("Tài khoản đã bị khóa do nhập sai mật khẩu 5 lần liên tiếp.", ex.getMessage());
        assertEquals(AccountStatus.SUSPENDED, acc.getStatus());
        assertTrue(acc.getLockedUntil().isAfter(OffsetDateTime.now().plusYears(99)));
        assertAuditSaved("ACCOUNT_LOCKED_5_FAILS", false);
    }

    @Test
    void utc007_login_deletedAccountThrowsAccountDeleted() {
        acc.setStatus(AccountStatus.DELETED);
        assertEquals(ErrorCode.ACCOUNT_DELETED, code(() -> service.login(loginReq(EMAIL, "Valid@123"))));
    }

    @Test
    void utc008_login_suspendedAccountThrowsAccountLocked() {
        acc.setStatus(AccountStatus.SUSPENDED);
        AppException ex = assertThrows(AppException.class, () -> service.login(loginReq(EMAIL, "Valid@123")));
        assertEquals(ErrorCode.ACCOUNT_LOCKED, ex.getErrorCode());
        assertEquals("Tài khoản đang bị đình chỉ. Liên hệ văn phòng để được hỗ trợ.", ex.getMessage());

        acc.setFailedLoginCount(5);
        AppException locked5 = assertThrows(AppException.class, () -> service.login(loginReq(EMAIL, "Valid@123")));
        assertEquals("Tài khoản đã bị khóa do nhập sai mật khẩu 5 lần liên tiếp.", locked5.getMessage());
    }

    @Test
    void utc009_login_activeLockThrowsAccountLocked() {
        acc.setLockedUntil(OffsetDateTime.now().plusHours(1));
        AppException ex = assertThrows(AppException.class, () -> service.login(loginReq(EMAIL, "Valid@123")));
        assertEquals(ErrorCode.ACCOUNT_LOCKED, ex.getErrorCode());
        assertEquals("Tài khoản đang bị khóa.", ex.getMessage());
    }

    @Test
    void utc010_login_expiredLockIsCleared() {
        acc.setLockedUntil(OffsetDateTime.now().minusMinutes(1));
        assertNotNull(service.login(loginReq(EMAIL, "Valid@123")).getAccessToken());
        assertNull(acc.getLockedUntil());
    }

    @Test
    void utc011_login_pastValidUntilThrowsAccountExpired() {
        acc.setValidUntil(OffsetDateTime.now().minusDays(1));
        assertEquals(ErrorCode.ACCOUNT_EXPIRED, code(() -> service.login(loginReq(EMAIL, "Valid@123"))));
    }

    @Test
    void utc012_login_futureValidUntilAllowsLogin() {
        acc.setValidUntil(OffsetDateTime.now().plusDays(1));
        assertNotNull(service.login(loginReq(EMAIL, "Valid@123")).getAccessToken());
    }

    // ---------------------------------------------------------------- requestChangePasswordOtp

    @Test
    void utc013_requestChangePasswordOtp_matchingPasswordSendsOtp() {
        when(passwordEncoder.matches("Old@123", "encoded-hash")).thenReturn(true);
        Map<String, Object> res = service.requestChangePasswordOtp(changeOtpReq(EMAIL, "Old@123"));
        assertEquals("CHANGE_PASSWORD", res.get("purpose"));
        assertEquals(true, res.get("emailSent"));
        verify(otpChallengeRepository).save(any(OtpChallenge.class));
    }

    @Test
    void utc014_requestChangePasswordOtp_wrongPasswordThrowsAndAudits() {
        when(passwordEncoder.matches("wrong", "encoded-hash")).thenReturn(false);
        AppException ex = assertThrows(AppException.class,
                () -> service.requestChangePasswordOtp(changeOtpReq(EMAIL, "wrong")));
        assertEquals(ErrorCode.INVALID_CREDENTIALS, ex.getErrorCode());
        assertEquals("Mật khẩu hiện tại không chính xác", ex.getMessage());
        assertAuditSaved("CHANGE_PASSWORD_OTP_FAILED", false);
    }

    @Test
    void utc015_requestChangePasswordOtp_missingAccountThrowsAccountNotFound() {
        assertEquals(ErrorCode.ACCOUNT_NOT_FOUND,
                code(() -> service.requestChangePasswordOtp(changeOtpReq("none@gmail.com", "Old@123"))));
    }

    @Test
    void utc016_requestChangePasswordOtp_suspendedAccountThrowsAccountLocked() {
        acc.setStatus(AccountStatus.SUSPENDED);
        assertEquals(ErrorCode.ACCOUNT_LOCKED, code(() -> service.requestChangePasswordOtp(changeOtpReq(EMAIL, "Old@123"))));
    }

    // ---------------------------------------------------------------- confirmChangePassword

    @Test
    void utc017_confirmChangePassword_validOtpUpdatesPasswordAndRevokesTokens() {
        acc.setMustChangePassword(true);
        OtpChallenge ch = stubChallenge(OtpPurpose.CHANGE_PASSWORD, futureChallenge(OtpPurpose.CHANGE_PASSWORD, 0));
        RefreshToken t1 = refreshToken(acc);
        RefreshToken t2 = refreshToken(acc);
        when(refreshTokenRepository.findByAccount_IdAndRevokedAtIsNull(1L)).thenReturn(List.of(t1, t2));
        when(passwordEncoder.encode("New@1234")).thenReturn("new-encoded");

        AccountDto dto = service.confirmChangePassword(confirmReq(EMAIL, "123456"));

        assertEquals("new-encoded", acc.getPasswordHash());
        assertFalse(acc.isMustChangePassword());
        assertNotNull(ch.getUsedAt());
        assertNotNull(t1.getRevokedAt());
        assertNotNull(t2.getRevokedAt());
        assertEquals(EMAIL, dto.getEmail());
        assertAuditSaved("CHANGE_PASSWORD_SUCCESS", true);
    }

    @Test
    void utc018_confirmChangePassword_otpWithSpacesIsTrimmed() {
        OtpChallenge ch = stubChallenge(OtpPurpose.CHANGE_PASSWORD, futureChallenge(OtpPurpose.CHANGE_PASSWORD, 0));
        assertEquals(EMAIL, service.confirmChangePassword(confirmReq(EMAIL, " 123456 ")).getEmail());
        assertNotNull(ch.getUsedAt());
    }

    @Test
    void utc019_confirmChangePassword_noOpenChallengeThrowsOtpNotFound() {
        assertEquals(ErrorCode.OTP_NOT_FOUND, code(() -> service.confirmChangePassword(confirmReq(EMAIL, "123456"))));
    }

    @Test
    void utc020_confirmChangePassword_expiredChallengeThrowsAndRevokes() {
        OtpChallenge expired = stubChallenge(OtpPurpose.CHANGE_PASSWORD,
                challenge(acc, OtpPurpose.CHANGE_PASSWORD, "123456", OffsetDateTime.now().minusSeconds(1), 0));
        assertEquals(ErrorCode.OTP_EXPIRED, code(() -> service.confirmChangePassword(confirmReq(EMAIL, "123456"))));
        assertNotNull(expired.getRevokedAt());
    }

    @Test
    void utc021_confirmChangePassword_maxAttemptsThrowsAndRevokes() {
        OtpChallenge maxed = stubChallenge(OtpPurpose.CHANGE_PASSWORD, futureChallenge(OtpPurpose.CHANGE_PASSWORD, 5));
        assertEquals(ErrorCode.OTP_MAX_ATTEMPTS, code(() -> service.confirmChangePassword(confirmReq(EMAIL, "123456"))));
        assertNotNull(maxed.getRevokedAt());
    }

    @Test
    void utc022_confirmChangePassword_firstWrongOtpIncrementsAttempts() {
        OtpChallenge ch = stubChallenge(OtpPurpose.CHANGE_PASSWORD, futureChallenge(OtpPurpose.CHANGE_PASSWORD, 0));
        assertEquals(ErrorCode.OTP_INVALID, code(() -> service.confirmChangePassword(confirmReq(EMAIL, "000000"))));
        assertEquals(1, ch.getAttemptCount());
        assertNull(ch.getRevokedAt());
        assertAuditSaved("OTP_VERIFY_FAILED", false);
    }

    @Test
    void utc023_confirmChangePassword_fifthWrongOtpRevokesChallenge() {
        OtpChallenge ch = stubChallenge(OtpPurpose.CHANGE_PASSWORD, futureChallenge(OtpPurpose.CHANGE_PASSWORD, 4));
        assertEquals(ErrorCode.OTP_INVALID, code(() -> service.confirmChangePassword(confirmReq(EMAIL, "000000"))));
        assertEquals(5, ch.getAttemptCount());
        assertNotNull(ch.getRevokedAt());
    }

    @Test
    void utc024_confirmChangePassword_missingAccountThrowsAccountNotFound() {
        assertEquals(ErrorCode.ACCOUNT_NOT_FOUND,
                code(() -> service.confirmChangePassword(confirmReq("none@gmail.com", "123456"))));
    }

    // ---------------------------------------------------------------- sendOtp

    @Test
    void utc025_sendOtp_emailSentReturnsConfirmationNote() {
        Map<String, Object> res = service.sendOtp(sendOtpReq(EMAIL, OtpPurpose.CHANGE_PASSWORD));
        assertEquals(true, res.get("emailSent"));
        assertEquals("Đã gửi mã OTP 6 số tới Gmail: usertest@gmail.com", res.get("note"));

        ArgumentCaptor<OtpChallenge> saved = ArgumentCaptor.forClass(OtpChallenge.class);
        verify(otpChallengeRepository).save(saved.capture());
        assertEquals(5, saved.getValue().getMaxAttempts());
        assertTrue(saved.getValue().getExpiresAt().isAfter(OffsetDateTime.now().plusMinutes(4)));
    }

    @Test
    void utc026_sendOtp_emailFailurePutsOtpInFallbackNote() {
        ArgumentCaptor<String> otp = ArgumentCaptor.forClass(String.class);
        when(emailService.sendOtpEmail(eq(EMAIL), otp.capture(), eq(OtpPurpose.LOGIN))).thenReturn(false);

        Map<String, Object> res = service.sendOtp(sendOtpReq(EMAIL, OtpPurpose.LOGIN));

        assertEquals(false, res.get("emailSent"));
        assertTrue(String.valueOf(res.get("note")).contains(otp.getValue()));
    }

    @Test
    void utc027_sendOtp_nullPurposeDefaultsToChangePassword() {
        assertEquals("CHANGE_PASSWORD", service.sendOtp(sendOtpReq(EMAIL, null)).get("purpose"));
        verify(emailService).sendOtpEmail(eq(EMAIL), anyString(), eq(OtpPurpose.CHANGE_PASSWORD));
    }

    @Test
    void utc028_sendOtp_revokesOldOpenChallenges() {
        OtpChallenge old1 = futureChallenge(OtpPurpose.CHANGE_PASSWORD, 0);
        OtpChallenge old2 = futureChallenge(OtpPurpose.CHANGE_PASSWORD, 0);
        when(otpChallengeRepository.findByAccount_IdAndPurposeAndUsedAtIsNullAndRevokedAtIsNull(1L, OtpPurpose.CHANGE_PASSWORD))
                .thenReturn(List.of(old1, old2));

        service.sendOtp(sendOtpReq(EMAIL, OtpPurpose.CHANGE_PASSWORD));

        assertNotNull(old1.getRevokedAt());
        assertNotNull(old2.getRevokedAt());
        verify(otpChallengeRepository).saveAllAndFlush(List.of(old1, old2));
    }

    @Test
    void utc029_sendOtp_suspendedAccountAllowedForForgotPassword() {
        acc.setStatus(AccountStatus.SUSPENDED);
        assertEquals("FORGOT_PASSWORD", service.sendOtp(sendOtpReq(EMAIL, OtpPurpose.FORGOT_PASSWORD)).get("purpose"));
    }

    @Test
    void utc030_sendOtp_suspendedAccountThrowsAccountLockedForChangePassword() {
        acc.setStatus(AccountStatus.SUSPENDED);
        assertEquals(ErrorCode.ACCOUNT_LOCKED, code(() -> service.sendOtp(sendOtpReq(EMAIL, OtpPurpose.CHANGE_PASSWORD))));
    }

    @Test
    void utc031_sendOtp_deletedAccountThrowsAccountDeleted() {
        acc.setStatus(AccountStatus.DELETED);
        assertEquals(ErrorCode.ACCOUNT_DELETED, code(() -> service.sendOtp(sendOtpReq(EMAIL, OtpPurpose.FORGOT_PASSWORD))));
    }

    @Test
    void utc032_sendOtp_missingAccountThrowsAccountNotFound() {
        assertEquals(ErrorCode.ACCOUNT_NOT_FOUND,
                code(() -> service.sendOtp(sendOtpReq("none@gmail.com", OtpPurpose.LOGIN))));
    }

    @Test
    void utc033_sendOtp_otpIsSixZeroPaddedDigits() {
        ArgumentCaptor<String> otp = ArgumentCaptor.forClass(String.class);
        service.sendOtp(sendOtpReq(EMAIL, OtpPurpose.LOGIN));
        verify(emailService).sendOtpEmail(eq(EMAIL), otp.capture(), eq(OtpPurpose.LOGIN));
        assertTrue(otp.getValue().matches("\\d{6}"));
        assertEquals("000042", String.format("%06d", 42));
    }

    // ---------------------------------------------------------------- verifyOtp / verifyResetPasswordOtp

    @Test
    void utc034_verifyOtp_validOtpMarksUsedIssuesTokensAndAudits() {
        OtpChallenge ch = stubChallenge(OtpPurpose.LOGIN, futureChallenge(OtpPurpose.LOGIN, 0));
        LoginResponse res = service.verifyOtp(verifyReq(EMAIL, "123456", OtpPurpose.LOGIN));
        assertEquals("jwt-access-token", res.getAccessToken());
        assertNotNull(ch.getUsedAt());
        assertAuditSaved("OTP_VERIFY_LOGIN", true);
    }

    @Test
    void utc035_verifyOtp_nullPurposeDefaultsToLogin() {
        OtpChallenge ch = stubChallenge(OtpPurpose.LOGIN, futureChallenge(OtpPurpose.LOGIN, 0));
        assertNotNull(service.verifyOtp(verifyReq(EMAIL, "123456", null)).getAccessToken());
        assertNotNull(ch.getUsedAt());
    }

    @Test
    void utc036_verifyOtp_missingAccountThrowsAccountNotFound() {
        assertEquals(ErrorCode.ACCOUNT_NOT_FOUND,
                code(() -> service.verifyOtp(verifyReq("none@gmail.com", "123456", OtpPurpose.LOGIN))));
    }

    @Test
    void utc037_verifyOtp_expiredChallengeThrowsOtpExpired() {
        stubChallenge(OtpPurpose.LOGIN, challenge(acc, OtpPurpose.LOGIN, "123456", OffsetDateTime.now().minusMinutes(1), 0));
        assertEquals(ErrorCode.OTP_EXPIRED, code(() -> service.verifyOtp(verifyReq(EMAIL, "123456", OtpPurpose.LOGIN))));
    }

    @Test
    void utc038_verifyOtp_wrongOtpThrowsOtpInvalid() {
        stubChallenge(OtpPurpose.LOGIN, futureChallenge(OtpPurpose.LOGIN, 0));
        assertEquals(ErrorCode.OTP_INVALID, code(() -> service.verifyOtp(verifyReq(EMAIL, "999999", OtpPurpose.LOGIN))));
    }

    @Test
    void utc039_verifyResetPasswordOtp_validForActiveWithoutMarkingUsed() {
        OtpChallenge ch = stubChallenge(OtpPurpose.FORGOT_PASSWORD, futureChallenge(OtpPurpose.FORGOT_PASSWORD, 0));
        assertEquals(true, service.verifyResetPasswordOtp(verifyReq(EMAIL, "123456", null)).get("valid"));
        assertNull(ch.getUsedAt());
    }

    @Test
    void utc040_verifyResetPasswordOtp_allowsSuspendedAccount() {
        acc.setStatus(AccountStatus.SUSPENDED);
        stubChallenge(OtpPurpose.FORGOT_PASSWORD, futureChallenge(OtpPurpose.FORGOT_PASSWORD, 0));
        assertEquals(true, service.verifyResetPasswordOtp(verifyReq(EMAIL, "123456", null)).get("valid"));
    }

    @Test
    void utc041_verifyResetPasswordOtp_deletedAccountThrowsAccountDeleted() {
        acc.setStatus(AccountStatus.DELETED);
        stubChallenge(OtpPurpose.FORGOT_PASSWORD, futureChallenge(OtpPurpose.FORGOT_PASSWORD, 0));
        assertEquals(ErrorCode.ACCOUNT_DELETED, code(() -> service.verifyResetPasswordOtp(verifyReq(EMAIL, "123456", null))));
    }

    @Test
    void utc042_verifyResetPasswordOtp_missingChallengeThrowsOtpNotFound() {
        assertEquals(ErrorCode.OTP_NOT_FOUND, code(() -> service.verifyResetPasswordOtp(verifyReq(EMAIL, "123456", null))));
    }

    @Test
    void utc043_verifyResetPasswordOtp_expiredChallengeThrowsOtpExpired() {
        stubChallenge(OtpPurpose.FORGOT_PASSWORD,
                challenge(acc, OtpPurpose.FORGOT_PASSWORD, "123456", OffsetDateTime.now().minusMinutes(1), 0));
        assertEquals(ErrorCode.OTP_EXPIRED, code(() -> service.verifyResetPasswordOtp(verifyReq(EMAIL, "123456", null))));
    }

    @Test
    void utc044_verifyResetPasswordOtp_maxAttemptsThrowsOtpMaxAttempts() {
        stubChallenge(OtpPurpose.FORGOT_PASSWORD, futureChallenge(OtpPurpose.FORGOT_PASSWORD, 5));
        assertEquals(ErrorCode.OTP_MAX_ATTEMPTS, code(() -> service.verifyResetPasswordOtp(verifyReq(EMAIL, "123456", null))));
    }

    @Test
    void utc045_verifyResetPasswordOtp_fifthWrongOtpRevokesChallenge() {
        OtpChallenge ch = stubChallenge(OtpPurpose.FORGOT_PASSWORD, futureChallenge(OtpPurpose.FORGOT_PASSWORD, 4));
        assertEquals(ErrorCode.OTP_INVALID, code(() -> service.verifyResetPasswordOtp(verifyReq(EMAIL, "000000", null))));
        assertEquals(5, ch.getAttemptCount());
        assertNotNull(ch.getRevokedAt());
    }

    // ---------------------------------------------------------------- forgotPassword / resetPassword / sha256Hex

    @Test
    void utc046_forgotPassword_delegatesToSendOtpForgotPassword() {
        assertEquals("FORGOT_PASSWORD", service.forgotPassword(forgotReq(EMAIL)).get("purpose"));
    }

    @Test
    void utc047_forgotPassword_missingAccountThrowsAccountNotFound() {
        assertEquals(ErrorCode.ACCOUNT_NOT_FOUND, code(() -> service.forgotPassword(forgotReq("none@gmail.com"))));
    }

    @Test
    void utc048_resetPassword_unlocksSuspendedAccountAndRevokesTokens() {
        acc.setStatus(AccountStatus.SUSPENDED);
        acc.setFailedLoginCount(5);
        acc.setLockedUntil(OffsetDateTime.now().plusYears(10));
        stubChallenge(OtpPurpose.FORGOT_PASSWORD, futureChallenge(OtpPurpose.FORGOT_PASSWORD, 0));
        RefreshToken token = refreshToken(acc);
        when(refreshTokenRepository.findByAccount_IdAndRevokedAtIsNull(1L)).thenReturn(List.of(token));

        assertEquals(AccountStatus.ACTIVE, service.resetPassword(resetReq(EMAIL, "123456")).getStatus());
        assertEquals(0, acc.getFailedLoginCount());
        assertNull(acc.getLockedUntil());
        assertNotNull(token.getRevokedAt());
        assertAuditSaved("RESET_PASSWORD_AND_UNLOCKED", true);
    }

    @Test
    void utc049_resetPassword_validOtpUpdatesPassword() {
        OtpChallenge ch = stubChallenge(OtpPurpose.FORGOT_PASSWORD, futureChallenge(OtpPurpose.FORGOT_PASSWORD, 0));
        when(passwordEncoder.encode("New@1234")).thenReturn("new-hash");

        assertEquals(AccountStatus.ACTIVE, service.resetPassword(resetReq(EMAIL, "123456")).getStatus());
        assertEquals("new-hash", acc.getPasswordHash());
        assertNotNull(ch.getUsedAt());
    }

    @Test
    void utc050_resetPassword_reusedOtpThrowsOtpNotFound() {
        when(otpChallengeRepository.findFirstByAccount_IdAndPurposeAndUsedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(
                1L, OtpPurpose.FORGOT_PASSWORD))
                .thenReturn(Optional.of(futureChallenge(OtpPurpose.FORGOT_PASSWORD, 0)))
                .thenReturn(Optional.empty());
        service.resetPassword(resetReq(EMAIL, "123456"));
        assertEquals(ErrorCode.OTP_NOT_FOUND, code(() -> service.resetPassword(resetReq(EMAIL, "123456"))));
    }

    @Test
    void utc051_resetPassword_otpMismatchThrowsOtpInvalid() {
        stubChallenge(OtpPurpose.FORGOT_PASSWORD, futureChallenge(OtpPurpose.FORGOT_PASSWORD, 0));
        assertEquals(ErrorCode.OTP_INVALID, code(() -> service.resetPassword(resetReq(EMAIL, "000000"))));
    }

    @Test
    void utc052_resetPassword_missingAccountThrowsAccountNotFound() {
        assertEquals(ErrorCode.ACCOUNT_NOT_FOUND, code(() -> service.resetPassword(resetReq("none@gmail.com", "123456"))));
    }

    @Test
    void utc053_sha256Hex_digestOf123456() {
        assertEquals("8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92", AuthService.sha256Hex("123456"));
    }

    @Test
    void utc054_sha256Hex_digestOfEmptyString() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", AuthService.sha256Hex(""));
    }

    @Test
    void utc055_sha256Hex_utf8InputIsStableHex() {
        String hash = AuthService.sha256Hex("Mật khẩu");
        assertTrue(hash.matches("^[0-9a-f]{64}$"));
        assertEquals(hash, AuthService.sha256Hex("Mật khẩu"));
    }

    // ---------------------------------------------------------------- helpers

    private static ErrorCode code(Executable call) {
        return assertThrows(AppException.class, call).getErrorCode();
    }

    private void assertAuditSaved(String expectedEventType, boolean expectedSuccess) {
        ArgumentCaptor<LoginAuditLog> cap = ArgumentCaptor.forClass(LoginAuditLog.class);
        verify(loginAuditLogRepository, atLeastOnce()).save(cap.capture());
        LoginAuditLog last = cap.getAllValues().get(cap.getAllValues().size() - 1);
        assertEquals(expectedEventType, last.getEventType());
        assertEquals(expectedSuccess, last.isSuccess());
    }

    private OtpChallenge stubChallenge(OtpPurpose purpose, OtpChallenge ch) {
        when(otpChallengeRepository.findFirstByAccount_IdAndPurposeAndUsedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(
                1L, purpose)).thenReturn(Optional.of(ch));
        return ch;
    }

    private OtpChallenge futureChallenge(OtpPurpose purpose, int attempts) {
        return challenge(acc, purpose, "123456", OffsetDateTime.now().plusMinutes(5), attempts);
    }

    private static LoginRequest loginReq(String email, String password) {
        LoginRequest req = new LoginRequest();
        req.setEmail(email);
        req.setPassword(password);
        return req;
    }

    private static RequestChangePasswordOtpRequest changeOtpReq(String email, String currentPassword) {
        RequestChangePasswordOtpRequest req = new RequestChangePasswordOtpRequest();
        req.setEmail(email);
        req.setCurrentPassword(currentPassword);
        return req;
    }

    private static ChangePasswordConfirmRequest confirmReq(String email, String otp) {
        ChangePasswordConfirmRequest req = new ChangePasswordConfirmRequest();
        req.setEmail(email);
        req.setOtp(otp);
        req.setNewPassword("New@1234");
        return req;
    }

    private static SendOtpRequest sendOtpReq(String email, OtpPurpose purpose) {
        SendOtpRequest req = new SendOtpRequest();
        req.setEmail(email);
        req.setPurpose(purpose);
        return req;
    }

    private static VerifyOtpRequest verifyReq(String email, String otp, OtpPurpose purpose) {
        VerifyOtpRequest req = new VerifyOtpRequest();
        req.setEmail(email);
        req.setOtp(otp);
        req.setPurpose(purpose);
        return req;
    }

    private static ForgotPasswordRequest forgotReq(String email) {
        ForgotPasswordRequest req = new ForgotPasswordRequest();
        req.setEmail(email);
        return req;
    }

    private static ResetPasswordRequest resetReq(String email, String otp) {
        ResetPasswordRequest req = new ResetPasswordRequest();
        req.setEmail(email);
        req.setOtp(otp);
        req.setNewPassword("New@1234");
        return req;
    }

    private static Account account(long id, String email, AccountRole role, AccountStatus status) {
        Account acc = new Account();
        acc.setId(id);
        acc.setEmail(email);
        acc.setFullName("User " + id);
        acc.setPasswordHash("encoded-hash");
        acc.setRole(role);
        acc.setStatus(status);
        return acc;
    }

    private static OtpChallenge challenge(Account acc, OtpPurpose purpose, String otp, OffsetDateTime expiresAt, int attempts) {
        OtpChallenge c = new OtpChallenge();
        c.setAccount(acc);
        c.setPurpose(purpose);
        c.setOtpHash(AuthService.sha256Hex(otp));
        c.setExpiresAt(expiresAt);
        c.setAttemptCount(attempts);
        c.setMaxAttempts(5);
        return c;
    }

    private static RefreshToken refreshToken(Account acc) {
        RefreshToken rt = new RefreshToken();
        rt.setAccount(acc);
        return rt;
    }
}
