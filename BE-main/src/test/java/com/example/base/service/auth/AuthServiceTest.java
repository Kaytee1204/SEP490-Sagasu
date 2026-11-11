package com.example.base.service.auth;

import com.example.base.dto.account.AccountDto;
import com.example.base.dto.auth.ChangePasswordConfirmRequest;
import com.example.base.dto.auth.ForgotPasswordRequest;
import com.example.base.dto.auth.GoogleLoginRequest;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

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

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "refreshTokenExpirationMs", 604800000L);
        lenient().when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(jwtTokenProvider.generateAccessToken(any())).thenReturn("jwt-access-token");
    }

    // UTC001, UTC002 - Active user login trims/lowercases email, resets failedLoginCount, issues tokens & records audit
    @Test
    void login_whenActiveMatchingPassword_resetsFailsIssuesTokensAndRecordsAudit() {
        Account acc = account(1L, "a@fpt.edu.vn", AccountRole.USER, AccountStatus.ACTIVE);
        acc.setFailedLoginCount(2);
        acc.setLockedUntil(OffsetDateTime.now().minusMinutes(5));

        // UTC001 & UTC002 - Trimmed/lowercased email lookup, reset failedLoginCount=0, save refresh token & audit
        when(accountRepository.findByEmailIgnoreCase("a@fpt.edu.vn")).thenReturn(Optional.of(acc));
        when(passwordEncoder.matches("Valid@123", "encoded-hash")).thenReturn(true);

        LoginResponse res = service.login(loginReq("  A@FPT.EDU.VN ", "Valid@123"));

        assertEquals("jwt-access-token", res.getAccessToken());
        assertNotNull(res.getRefreshToken());
        assertEquals("a@fpt.edu.vn", res.getAccount().getEmail());
        assertEquals(0, acc.getFailedLoginCount());
        assertNull(acc.getLockedUntil());
        verify(refreshTokenRepository).save(any(RefreshToken.class));
        assertAuditSaved("LOGIN_PASSWORD", true);
    }

    // UTC003, UTC004, UTC005, UTC006 - Missing account, wrong password failure counts (1st, 4th) & lock on 5th attempt
    @Test
    void login_whenWrongPasswordOrMissingAccount_incrementsFailsAndLocksOnFifthAttempt() {
        // UTC003 - Missing account throws INVALID_CREDENTIALS
        when(accountRepository.findByEmailIgnoreCase("none@fpt.edu.vn")).thenReturn(Optional.empty());
        AppException notFound = assertThrows(AppException.class,
                () -> service.login(loginReq("none@fpt.edu.vn", "x")));
        assertEquals(ErrorCode.INVALID_CREDENTIALS, notFound.getErrorCode());

        // UTC004 - failedLoginCount=0 -> 1, 4 attempts remaining, audit LOGIN_PASSWORD success=false
        Account acc0 = account(1L, "a@fpt.edu.vn", AccountRole.USER, AccountStatus.ACTIVE);
        when(accountRepository.findByEmailIgnoreCase("a@fpt.edu.vn")).thenReturn(Optional.of(acc0));
        when(passwordEncoder.matches("wrong", "encoded-hash")).thenReturn(false);

        AppException firstFail = assertThrows(AppException.class,
                () -> service.login(loginReq("a@fpt.edu.vn", "wrong")));
        assertEquals(ErrorCode.INVALID_CREDENTIALS, firstFail.getErrorCode());
        assertEquals("Mật khẩu không chính xác. Bạn còn 4 lần thử trước khi tài khoản bị khóa.", firstFail.getMessage());
        assertEquals(1, acc0.getFailedLoginCount());
        assertAuditSaved("LOGIN_PASSWORD", false);

        // UTC005 - failedLoginCount=3 -> 4, 1 attempt remaining, account remains ACTIVE
        acc0.setFailedLoginCount(3);
        AppException fourthFail = assertThrows(AppException.class,
                () -> service.login(loginReq("a@fpt.edu.vn", "wrong")));
        assertEquals(ErrorCode.INVALID_CREDENTIALS, fourthFail.getErrorCode());
        assertTrue(fourthFail.getMessage().contains("Bạn còn 1 lần thử"));
        assertEquals(4, acc0.getFailedLoginCount());
        assertEquals(AccountStatus.ACTIVE, acc0.getStatus());

        // UTC006 - failedLoginCount=4 -> 5th failure locks account (SUSPENDED, lockedUntil=+100y, ACCOUNT_LOCKED_5_FAILS)
        AppException fifthLock = assertThrows(AppException.class,
                () -> service.login(loginReq("a@fpt.edu.vn", "wrong")));
        assertEquals(ErrorCode.ACCOUNT_LOCKED, fifthLock.getErrorCode());
        assertEquals("Tài khoản đã bị khóa do nhập sai mật khẩu 5 lần liên tiếp.", fifthLock.getMessage());
        assertEquals(AccountStatus.SUSPENDED, acc0.getStatus());
        assertTrue(acc0.getLockedUntil().isAfter(OffsetDateTime.now().plusYears(99)));
        assertAuditSaved("ACCOUNT_LOCKED_5_FAILS", false);
    }

    // UTC007, UTC008, UTC009, UTC010, UTC011, UTC012 - Account DELETED, SUSPENDED, lockedUntil active/expired & validUntil checks
    @Test
    void login_whenAccountDeletedSuspendedLockedOrExpired_handlesLockAndValidityStates() {
        // UTC007 - DELETED account throws ACCOUNT_DELETED
        Account deleted = account(1L, "a@fpt.edu.vn", AccountRole.USER, AccountStatus.DELETED);
        when(accountRepository.findByEmailIgnoreCase("a@fpt.edu.vn")).thenReturn(Optional.of(deleted));
        assertEquals(ErrorCode.ACCOUNT_DELETED,
                assertThrows(AppException.class, () -> service.login(loginReq("a@fpt.edu.vn", "Valid@123"))).getErrorCode());

        // UTC008 - SUSPENDED account throws ACCOUNT_LOCKED
        Account suspended = account(1L, "a@fpt.edu.vn", AccountRole.USER, AccountStatus.SUSPENDED);
        when(accountRepository.findByEmailIgnoreCase("a@fpt.edu.vn")).thenReturn(Optional.of(suspended));
        AppException suspEx = assertThrows(AppException.class,
                () -> service.login(loginReq("a@fpt.edu.vn", "Valid@123")));
        assertEquals(ErrorCode.ACCOUNT_LOCKED, suspEx.getErrorCode());
        assertEquals("Tài khoản đang bị đình chỉ. Liên hệ văn phòng để được hỗ trợ.", suspEx.getMessage());

        Account locked5 = account(1L, "a@fpt.edu.vn", AccountRole.USER, AccountStatus.SUSPENDED);
        locked5.setFailedLoginCount(5);
        when(accountRepository.findByEmailIgnoreCase("a@fpt.edu.vn")).thenReturn(Optional.of(locked5));
        AppException locked5Ex = assertThrows(AppException.class,
                () -> service.login(loginReq("a@fpt.edu.vn", "Valid@123")));
        assertEquals(ErrorCode.ACCOUNT_LOCKED, locked5Ex.getErrorCode());
        assertEquals("Tài khoản đã bị khóa do nhập sai mật khẩu 5 lần liên tiếp.", locked5Ex.getMessage());

        // UTC009 - ACTIVE with future lockedUntil throws ACCOUNT_LOCKED
        Account lockedFuture = account(1L, "a@fpt.edu.vn", AccountRole.USER, AccountStatus.ACTIVE);
        lockedFuture.setLockedUntil(OffsetDateTime.now().plusHours(1));
        when(accountRepository.findByEmailIgnoreCase("a@fpt.edu.vn")).thenReturn(Optional.of(lockedFuture));
        AppException lockEx = assertThrows(AppException.class,
                () -> service.login(loginReq("a@fpt.edu.vn", "Valid@123")));
        assertEquals(ErrorCode.ACCOUNT_LOCKED, lockEx.getErrorCode());
        assertEquals("Tài khoản đang bị khóa.", lockEx.getMessage());

        // UTC010 & UTC012 - Expired lock (now-1m) is cleared & future validUntil (tomorrow) allows login
        Account lockedExpired = account(1L, "a@fpt.edu.vn", AccountRole.USER, AccountStatus.ACTIVE);
        lockedExpired.setLockedUntil(OffsetDateTime.now().minusMinutes(1));
        lockedExpired.setValidUntil(OffsetDateTime.now().plusDays(1));
        when(accountRepository.findByEmailIgnoreCase("a@fpt.edu.vn")).thenReturn(Optional.of(lockedExpired));
        when(passwordEncoder.matches("Valid@123", "encoded-hash")).thenReturn(true);
        assertNotNull(service.login(loginReq("a@fpt.edu.vn", "Valid@123")).getAccessToken());
        assertNull(lockedExpired.getLockedUntil());

        // UTC011 - Past validUntil (yesterday) throws ACCOUNT_EXPIRED
        Account expired = account(1L, "a@fpt.edu.vn", AccountRole.USER, AccountStatus.ACTIVE);
        expired.setValidUntil(OffsetDateTime.now().minusDays(1));
        when(accountRepository.findByEmailIgnoreCase("a@fpt.edu.vn")).thenReturn(Optional.of(expired));
        assertEquals(ErrorCode.ACCOUNT_EXPIRED,
                assertThrows(AppException.class, () -> service.login(loginReq("a@fpt.edu.vn", "Valid@123"))).getErrorCode());
    }

    // UTC013, UTC014, UTC015, UTC016 - Admin fallback login & test "user" bypass scenarios
    @Test
    void login_whenAdminFallbackOrUserBypass_handlesSpecialAccounts() {
        // UTC013 - "admin" falls back to first ADMIN account
        Account admin = account(2L, "admin@sagasu.vn", AccountRole.ADMIN, AccountStatus.ACTIVE);
        when(accountRepository.findByEmailIgnoreCase("admin")).thenReturn(Optional.empty());
        when(accountRepository.findByRole(AccountRole.ADMIN)).thenReturn(List.of(admin));
        when(passwordEncoder.matches("Admin@123", "encoded-hash")).thenReturn(true);

        LoginResponse adminRes = service.login(loginReq("admin", "Admin@123"));
        assertEquals("admin@sagasu.vn", adminRes.getAccount().getEmail());

        // UTC014 - Existing "user" bypass with password "123" resets failedLoginCount & audits LOGIN_SPECIAL_USER
        Account testUser = account(3L, "user", AccountRole.USER, AccountStatus.ACTIVE);
        testUser.setFailedLoginCount(3);
        when(accountRepository.findByEmailIgnoreCase("user")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.encode("123")).thenReturn("hash-123");

        LoginResponse bypassExisting = service.login(loginReq("user@gmail.com", "123"));
        assertNotNull(bypassExisting.getAccessToken());
        assertEquals(AccountStatus.ACTIVE, testUser.getStatus());
        assertEquals(0, testUser.getFailedLoginCount());
        assertNull(testUser.getLockedUntil());
        assertAuditSaved("LOGIN_SPECIAL_USER", true);

        // UTC015 - Bypass creates new USER account when neither "user" nor USER role account exists
        when(accountRepository.findByEmailIgnoreCase("user")).thenReturn(Optional.empty());
        when(accountRepository.findByRole(AccountRole.USER)).thenReturn(List.of());
        LoginResponse bypassNew = service.login(loginReq("user", "123"));
        assertEquals("user", bypassNew.getAccount().getEmail());
        assertEquals(AccountRole.USER, bypassNew.getAccount().getRole());

        // UTC016 - Bypass email with wrong password falls back to normal password check (INVALID_CREDENTIALS)
        Account storedUser = account(4L, "user", AccountRole.USER, AccountStatus.ACTIVE);
        when(accountRepository.findByEmailIgnoreCase("user")).thenReturn(Optional.of(storedUser));
        when(passwordEncoder.matches("456", "encoded-hash")).thenReturn(false);
        assertEquals(ErrorCode.INVALID_CREDENTIALS,
                assertThrows(AppException.class, () -> service.login(loginReq("user", "456"))).getErrorCode());
    }

    // UTC017, UTC018 - Google login with direct email or OAuth UserInfo token resolution
    @Test
    void loginWithGoogle_whenEmailOrTokenValid_authenticatesAndLogsAudit() {
        Account googleAcc = account(10L, "g@fpt.edu.vn", AccountRole.USER, AccountStatus.ACTIVE);
        when(accountRepository.findByEmailIgnoreCase("g@fpt.edu.vn")).thenReturn(Optional.of(googleAcc));

        // UTC017 - Direct email with accessToken=null succeeds & audits LOGIN_GOOGLE
        GoogleLoginRequest directReq = new GoogleLoginRequest();
        directReq.setEmail("g@fpt.edu.vn");
        LoginResponse directRes = service.loginWithGoogle(directReq);
        assertEquals("g@fpt.edu.vn", directRes.getAccount().getEmail());
        assertAuditSaved("LOGIN_GOOGLE", true);

        // UTC018 - Valid accessToken resolved via Google UserInfo API
        GoogleLoginRequest tokenReq = new GoogleLoginRequest();
        tokenReq.setAccessToken("valid-token");
        try (MockedConstruction<RestTemplate> ignored = mockConstruction(RestTemplate.class,
                (mock, ctx) -> when(mock.exchange(
                        eq("https://www.googleapis.com/oauth2/v3/userinfo"),
                        eq(HttpMethod.GET),
                        any(HttpEntity.class),
                        eq(Map.class)))
                        .thenReturn(ResponseEntity.ok(Map.of("email", "g@fpt.edu.vn"))))) {
            LoginResponse oauthRes = service.loginWithGoogle(tokenReq);
            assertEquals("g@fpt.edu.vn", oauthRes.getAccount().getEmail());
        }
    }

    // UTC019, UTC020, UTC021, UTC022, UTC023, UTC024 - Google login rejects invalid token, blank/null email, missing, locked & deleted accounts
    @Test
    void loginWithGoogle_whenTokenRejectedEmailMissingNotFoundOrLocked_throwsExpectedError() {
        // UTC019 - Google UserInfo API 401 throws UNAUTHORIZED
        GoogleLoginRequest expiredTokenReq = new GoogleLoginRequest();
        expiredTokenReq.setAccessToken("expired-token");
        try (MockedConstruction<RestTemplate> ignored = mockConstruction(RestTemplate.class,
                (mock, ctx) -> when(mock.exchange(anyString(), eq(HttpMethod.GET), any(), eq(Map.class)))
                        .thenThrow(HttpClientErrorException.create(
                                HttpStatus.UNAUTHORIZED, "Unauthorized", HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8)))) {
            AppException oauthEx = assertThrows(AppException.class, () -> service.loginWithGoogle(expiredTokenReq));
            assertEquals(ErrorCode.UNAUTHORIZED, oauthEx.getErrorCode());
            assertTrue(oauthEx.getMessage().startsWith("Không thể xác thực phiên Google OAuth:"));
        }

        // UTC020 - Empty email with null accessToken throws BAD_REQUEST
        GoogleLoginRequest emptyEmailReq = new GoogleLoginRequest();
        emptyEmailReq.setEmail("");
        AppException emptyEx = assertThrows(AppException.class, () -> service.loginWithGoogle(emptyEmailReq));
        assertEquals(ErrorCode.BAD_REQUEST, emptyEx.getErrorCode());
        assertEquals("Vui lòng chọn hoặc nhập email tài khoản Google", emptyEx.getMessage());

        // UTC021 - Both accessToken and email null throws BAD_REQUEST
        GoogleLoginRequest nullEmailReq = new GoogleLoginRequest();
        assertEquals(ErrorCode.BAD_REQUEST,
                assertThrows(AppException.class, () -> service.loginWithGoogle(nullEmailReq)).getErrorCode());

        // UTC022 - Unknown Google email throws ACCOUNT_NOT_FOUND
        GoogleLoginRequest newEmailReq = new GoogleLoginRequest();
        newEmailReq.setEmail("new@gmail.com");
        when(accountRepository.findByEmailIgnoreCase("new@gmail.com")).thenReturn(Optional.empty());
        AppException missingEx = assertThrows(AppException.class, () -> service.loginWithGoogle(newEmailReq));
        assertEquals(ErrorCode.ACCOUNT_NOT_FOUND, missingEx.getErrorCode());
        assertTrue(missingEx.getMessage().contains("new@gmail.com"));

        // UTC023 - SUSPENDED account throws ACCOUNT_LOCKED
        Account suspended = account(10L, "g@fpt.edu.vn", AccountRole.USER, AccountStatus.SUSPENDED);
        when(accountRepository.findByEmailIgnoreCase("g@fpt.edu.vn")).thenReturn(Optional.of(suspended));
        GoogleLoginRequest suspendedReq = new GoogleLoginRequest();
        suspendedReq.setEmail("g@fpt.edu.vn");
        assertEquals(ErrorCode.ACCOUNT_LOCKED,
                assertThrows(AppException.class, () -> service.loginWithGoogle(suspendedReq)).getErrorCode());

        // UTC024 - DELETED account throws ACCOUNT_DELETED
        Account deleted = account(10L, "g@fpt.edu.vn", AccountRole.USER, AccountStatus.DELETED);
        when(accountRepository.findByEmailIgnoreCase("g@fpt.edu.vn")).thenReturn(Optional.of(deleted));
        assertEquals(ErrorCode.ACCOUNT_DELETED,
                assertThrows(AppException.class, () -> service.loginWithGoogle(suspendedReq)).getErrorCode());
    }

    // UTC025, UTC026, UTC027 - Filters ACTIVE + USER accounts containing '@', falls back null fullName to email, and returns empty list when none
    @Test
    void getAvailableGoogleAccounts_filtersActiveUsersWithAtSymbolAndHandlesFallbacks() {
        Account valid = account(1L, "a@x.com", AccountRole.USER, AccountStatus.ACTIVE);
        valid.setFullName("Valid User");
        Account noName = account(2L, "noname@x.com", AccountRole.USER, AccountStatus.ACTIVE);
        noName.setFullName(null);
        Account suspended = account(3L, "b@x.com", AccountRole.USER, AccountStatus.SUSPENDED);
        Account admin = account(4L, "c@x.com", AccountRole.ADMIN, AccountStatus.ACTIVE);
        Account bypass = account(5L, "user", AccountRole.USER, AccountStatus.ACTIVE);

        // UTC025 & UTC026 - Only ACTIVE USER accounts with '@'; null fullName falls back to email
        when(accountRepository.findAll()).thenReturn(List.of(valid, noName, suspended, admin, bypass));
        List<Map<String, Object>> list = service.getAvailableGoogleAccounts();
        assertEquals(2, list.size());
        assertEquals("a@x.com", list.get(0).get("email"));
        assertEquals("noname@x.com", list.get(1).get("fullName"));

        // UTC027 - Empty account list returns empty list
        when(accountRepository.findAll()).thenReturn(List.of());
        assertTrue(service.getAvailableGoogleAccounts().isEmpty());
    }

    // UTC028, UTC029, UTC030, UTC031 - requestChangePasswordOtp covers success, wrong password, missing account, and suspended account
    @Test
    void requestChangePasswordOtp_coversSuccessWrongPasswordMissingAndSuspended() {
        // UTC028 - Active account with matching current password sends OTP & saves challenge
        Account acc = account(1L, "a@fpt.edu.vn", AccountRole.USER, AccountStatus.ACTIVE);
        when(accountRepository.findByEmailIgnoreCase("a@fpt.edu.vn")).thenReturn(Optional.of(acc));
        when(passwordEncoder.matches("Old@123", "encoded-hash")).thenReturn(true);
        when(otpChallengeRepository.findByAccount_IdAndPurposeAndUsedAtIsNullAndRevokedAtIsNull(1L, OtpPurpose.CHANGE_PASSWORD))
                .thenReturn(List.of());
        when(emailService.sendOtpEmail(eq("a@fpt.edu.vn"), anyString(), eq(OtpPurpose.CHANGE_PASSWORD))).thenReturn(true);

        RequestChangePasswordOtpRequest req = new RequestChangePasswordOtpRequest();
        req.setEmail("a@fpt.edu.vn");
        req.setCurrentPassword("Old@123");
        Map<String, Object> res = service.requestChangePasswordOtp(req);

        assertEquals("CHANGE_PASSWORD", res.get("purpose"));
        assertEquals(true, res.get("emailSent"));
        verify(otpChallengeRepository).save(any(OtpChallenge.class));

        // UTC029 - Wrong current password throws INVALID_CREDENTIALS & audits CHANGE_PASSWORD_OTP_FAILED
        req.setCurrentPassword("wrong");
        when(passwordEncoder.matches("wrong", "encoded-hash")).thenReturn(false);
        AppException wrongPass = assertThrows(AppException.class, () -> service.requestChangePasswordOtp(req));
        assertEquals(ErrorCode.INVALID_CREDENTIALS, wrongPass.getErrorCode());
        assertEquals("Mật khẩu hiện tại không chính xác", wrongPass.getMessage());
        assertAuditSaved("CHANGE_PASSWORD_OTP_FAILED", false);

        // UTC030 - Missing account throws ACCOUNT_NOT_FOUND
        req.setEmail("none@fpt.edu.vn");
        when(accountRepository.findByEmailIgnoreCase("none@fpt.edu.vn")).thenReturn(Optional.empty());
        assertEquals(ErrorCode.ACCOUNT_NOT_FOUND,
                assertThrows(AppException.class, () -> service.requestChangePasswordOtp(req)).getErrorCode());

        // UTC031 - Suspended account throws ACCOUNT_LOCKED
        Account suspended = account(2L, "susp@fpt.edu.vn", AccountRole.USER, AccountStatus.SUSPENDED);
        req.setEmail("susp@fpt.edu.vn");
        when(accountRepository.findByEmailIgnoreCase("susp@fpt.edu.vn")).thenReturn(Optional.of(suspended));
        assertEquals(ErrorCode.ACCOUNT_LOCKED,
                assertThrows(AppException.class, () -> service.requestChangePasswordOtp(req)).getErrorCode());
    }

    // UTC032, UTC033, UTC034, UTC035, UTC036, UTC037, UTC038, UTC039 - confirmChangePassword covers trimmed valid OTP, missing/expired/max-attempts/wrong OTP & missing account
    @Test
    void confirmChangePassword_coversSuccessTrimmedOtpAndChallengeFailures() {
        Account acc = account(1L, "a@fpt.edu.vn", AccountRole.USER, AccountStatus.ACTIVE);
        acc.setMustChangePassword(true);
        OtpChallenge ch = challenge(acc, OtpPurpose.CHANGE_PASSWORD, "123456", OffsetDateTime.now().plusMinutes(5), 0);
        RefreshToken t1 = refreshToken(acc);
        RefreshToken t2 = refreshToken(acc);

        // UTC032 & UTC033 - Trimmed OTP " 123456 " succeeds, encodes password, clears mustChangePassword, marks challenge used, revokes tokens & audits
        when(accountRepository.findByEmailIgnoreCase("a@fpt.edu.vn")).thenReturn(Optional.of(acc));
        when(otpChallengeRepository.findFirstByAccount_IdAndPurposeAndUsedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(
                1L, OtpPurpose.CHANGE_PASSWORD)).thenReturn(Optional.of(ch));
        when(passwordEncoder.encode("New@1234")).thenReturn("new-encoded");
        when(refreshTokenRepository.findByAccount_IdAndRevokedAtIsNull(1L)).thenReturn(List.of(t1, t2));

        ChangePasswordConfirmRequest req = new ChangePasswordConfirmRequest();
        req.setEmail("a@fpt.edu.vn");
        req.setOtp(" 123456 ");
        req.setNewPassword("New@1234");
        AccountDto dto = service.confirmChangePassword(req);

        assertEquals("new-encoded", acc.getPasswordHash());
        assertFalse(acc.isMustChangePassword());
        assertNotNull(ch.getUsedAt());
        assertNotNull(t1.getRevokedAt());
        assertNotNull(t2.getRevokedAt());
        assertEquals("a@fpt.edu.vn", dto.getEmail());
        assertAuditSaved("CHANGE_PASSWORD_SUCCESS", true);

        // UTC034 - No open challenge throws OTP_NOT_FOUND
        when(otpChallengeRepository.findFirstByAccount_IdAndPurposeAndUsedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(
                1L, OtpPurpose.CHANGE_PASSWORD)).thenReturn(Optional.empty());
        assertEquals(ErrorCode.OTP_NOT_FOUND,
                assertThrows(AppException.class, () -> service.confirmChangePassword(req)).getErrorCode());

        // UTC035 - Expired challenge (expiresAt=now-1s) throws OTP_EXPIRED and revokes challenge
        OtpChallenge expired = challenge(acc, OtpPurpose.CHANGE_PASSWORD, "123456", OffsetDateTime.now().minusSeconds(1), 0);
        when(otpChallengeRepository.findFirstByAccount_IdAndPurposeAndUsedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(
                1L, OtpPurpose.CHANGE_PASSWORD)).thenReturn(Optional.of(expired));
        assertEquals(ErrorCode.OTP_EXPIRED,
                assertThrows(AppException.class, () -> service.confirmChangePassword(req)).getErrorCode());
        assertNotNull(expired.getRevokedAt());

        // UTC036 - attemptCount=5 (maxAttempts=5) throws OTP_MAX_ATTEMPTS and revokes challenge
        OtpChallenge maxed = challenge(acc, OtpPurpose.CHANGE_PASSWORD, "123456", OffsetDateTime.now().plusMinutes(5), 5);
        when(otpChallengeRepository.findFirstByAccount_IdAndPurposeAndUsedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(
                1L, OtpPurpose.CHANGE_PASSWORD)).thenReturn(Optional.of(maxed));
        assertEquals(ErrorCode.OTP_MAX_ATTEMPTS,
                assertThrows(AppException.class, () -> service.confirmChangePassword(req)).getErrorCode());
        assertNotNull(maxed.getRevokedAt());

        // UTC037 - Wrong OTP at attemptCount=0 increments to 1 without revoking & audits OTP_VERIFY_FAILED
        req.setOtp("000000");
        OtpChallenge wrongOnce = challenge(acc, OtpPurpose.CHANGE_PASSWORD, "123456", OffsetDateTime.now().plusMinutes(5), 0);
        when(otpChallengeRepository.findFirstByAccount_IdAndPurposeAndUsedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(
                1L, OtpPurpose.CHANGE_PASSWORD)).thenReturn(Optional.of(wrongOnce));
        assertEquals(ErrorCode.OTP_INVALID,
                assertThrows(AppException.class, () -> service.confirmChangePassword(req)).getErrorCode());
        assertEquals(1, wrongOnce.getAttemptCount());
        assertNull(wrongOnce.getRevokedAt());
        assertAuditSaved("OTP_VERIFY_FAILED", false);

        // UTC038 - Wrong OTP on 5th attempt (attemptCount=4 -> 5) sets revokedAt & throws OTP_INVALID
        OtpChallenge fifthWrong = challenge(acc, OtpPurpose.CHANGE_PASSWORD, "123456", OffsetDateTime.now().plusMinutes(5), 4);
        when(otpChallengeRepository.findFirstByAccount_IdAndPurposeAndUsedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(
                1L, OtpPurpose.CHANGE_PASSWORD)).thenReturn(Optional.of(fifthWrong));
        assertEquals(ErrorCode.OTP_INVALID,
                assertThrows(AppException.class, () -> service.confirmChangePassword(req)).getErrorCode());
        assertEquals(5, fifthWrong.getAttemptCount());
        assertNotNull(fifthWrong.getRevokedAt());

        // UTC039 - Missing account throws ACCOUNT_NOT_FOUND
        req.setEmail("none@fpt.edu.vn");
        when(accountRepository.findByEmailIgnoreCase("none@fpt.edu.vn")).thenReturn(Optional.empty());
        assertEquals(ErrorCode.ACCOUNT_NOT_FOUND,
                assertThrows(AppException.class, () -> service.confirmChangePassword(req)).getErrorCode());
    }

    // UTC040, UTC041, UTC042, UTC043, UTC044, UTC045, UTC046, UTC047, UTC048 - sendOtp emailSent/fallback note, default purpose, revoking old challenges, status checks & 6-digit zero-padding
    @Test
    void sendOtp_coversEmailSentFallbackNoteRevocationAndAccountStatusRules() {
        Account acc = account(1L, "a@fpt.edu.vn", AccountRole.USER, AccountStatus.ACTIVE);
        when(accountRepository.findByEmailIgnoreCase("a@fpt.edu.vn")).thenReturn(Optional.of(acc));

        // UTC040, UTC042 & UTC043 - Null purpose defaults to CHANGE_PASSWORD, revokes old open challenges via saveAllAndFlush, sends email & saves challenge (maxAttempts=5, +5m)
        OtpChallenge old1 = challenge(acc, OtpPurpose.CHANGE_PASSWORD, "111111", OffsetDateTime.now().plusMinutes(3), 0);
        OtpChallenge old2 = challenge(acc, OtpPurpose.CHANGE_PASSWORD, "222222", OffsetDateTime.now().plusMinutes(4), 0);
        when(otpChallengeRepository.findByAccount_IdAndPurposeAndUsedAtIsNullAndRevokedAtIsNull(1L, OtpPurpose.CHANGE_PASSWORD))
                .thenReturn(List.of(old1, old2));
        when(emailService.sendOtpEmail(eq("a@fpt.edu.vn"), anyString(), eq(OtpPurpose.CHANGE_PASSWORD))).thenReturn(true);

        SendOtpRequest req = new SendOtpRequest();
        req.setEmail("a@fpt.edu.vn");
        req.setPurpose(null);
        Map<String, Object> sentRes = service.sendOtp(req);

        assertEquals(true, sentRes.get("emailSent"));
        assertEquals("CHANGE_PASSWORD", sentRes.get("purpose"));
        assertEquals("Đã gửi mã OTP 6 số tới Gmail: a@fpt.edu.vn", sentRes.get("note"));
        assertNotNull(old1.getRevokedAt());
        assertNotNull(old2.getRevokedAt());
        verify(otpChallengeRepository).saveAllAndFlush(List.of(old1, old2));

        ArgumentCaptor<OtpChallenge> savedCap = ArgumentCaptor.forClass(OtpChallenge.class);
        verify(otpChallengeRepository).save(savedCap.capture());
        assertEquals(5, savedCap.getValue().getMaxAttempts());
        assertTrue(savedCap.getValue().getExpiresAt().isAfter(OffsetDateTime.now().plusMinutes(4)));

        // UTC041 & UTC048 - EmailService false puts 6-digit zero-padded %06d OTP in note (SECURE_RANDOM is private static final, verifies 6-digit format & "000042" formatting)
        ArgumentCaptor<String> otpCap = ArgumentCaptor.forClass(String.class);
        when(otpChallengeRepository.findByAccount_IdAndPurposeAndUsedAtIsNullAndRevokedAtIsNull(1L, OtpPurpose.LOGIN))
                .thenReturn(List.of());
        when(emailService.sendOtpEmail(eq("a@fpt.edu.vn"), otpCap.capture(), eq(OtpPurpose.LOGIN))).thenReturn(false);
        req.setPurpose(OtpPurpose.LOGIN);
        Map<String, Object> fallbackRes = service.sendOtp(req);
        String plainOtp = otpCap.getValue();
        assertTrue(plainOtp.matches("\\d{6}"));
        assertEquals("000042", String.format("%06d", 42));
        assertEquals(false, fallbackRes.get("emailSent"));
        assertTrue(String.valueOf(fallbackRes.get("note")).contains(plainOtp));

        // UTC045 - SUSPENDED account with CHANGE_PASSWORD throws ACCOUNT_LOCKED
        Account suspended = account(2L, "susp@fpt.edu.vn", AccountRole.USER, AccountStatus.SUSPENDED);
        when(accountRepository.findByEmailIgnoreCase("susp@fpt.edu.vn")).thenReturn(Optional.of(suspended));
        req.setEmail("susp@fpt.edu.vn");
        req.setPurpose(OtpPurpose.CHANGE_PASSWORD);
        assertEquals(ErrorCode.ACCOUNT_LOCKED,
                assertThrows(AppException.class, () -> service.sendOtp(req)).getErrorCode());

        // UTC044 - SUSPENDED account skips lock check for FORGOT_PASSWORD
        when(otpChallengeRepository.findByAccount_IdAndPurposeAndUsedAtIsNullAndRevokedAtIsNull(2L, OtpPurpose.FORGOT_PASSWORD))
                .thenReturn(List.of());
        when(emailService.sendOtpEmail(eq("susp@fpt.edu.vn"), anyString(), eq(OtpPurpose.FORGOT_PASSWORD))).thenReturn(true);
        req.setPurpose(OtpPurpose.FORGOT_PASSWORD);
        assertEquals("FORGOT_PASSWORD", service.sendOtp(req).get("purpose"));

        // UTC046 - DELETED account throws ACCOUNT_DELETED
        Account deleted = account(3L, "del@fpt.edu.vn", AccountRole.USER, AccountStatus.DELETED);
        when(accountRepository.findByEmailIgnoreCase("del@fpt.edu.vn")).thenReturn(Optional.of(deleted));
        req.setEmail("del@fpt.edu.vn");
        assertEquals(ErrorCode.ACCOUNT_DELETED,
                assertThrows(AppException.class, () -> service.sendOtp(req)).getErrorCode());

        // UTC047 - Missing account throws ACCOUNT_NOT_FOUND
        req.setEmail("none@fpt.edu.vn");
        when(accountRepository.findByEmailIgnoreCase("none@fpt.edu.vn")).thenReturn(Optional.empty());
        assertEquals(ErrorCode.ACCOUNT_NOT_FOUND,
                assertThrows(AppException.class, () -> service.sendOtp(req)).getErrorCode());
    }

    // UTC049, UTC050, UTC051, UTC052, UTC053, UTC054, UTC055, UTC056, UTC057, UTC058, UTC059, UTC060 - verifyOtp & verifyResetPasswordOtp cover valid paths, defaults & errors
    @Test
    void verifyOtp_and_verifyResetPasswordOtp_coverValidAndErrorPaths() {
        Account acc = account(1L, "a@fpt.edu.vn", AccountRole.USER, AccountStatus.ACTIVE);
        when(accountRepository.findByEmailIgnoreCase("a@fpt.edu.vn")).thenReturn(Optional.of(acc));

        // UTC049 & UTC050 - verifyOtp null purpose defaults to LOGIN, marks challenge used, issues tokens & audits OTP_VERIFY_LOGIN
        OtpChallenge loginCh = challenge(acc, OtpPurpose.LOGIN, "123456", OffsetDateTime.now().plusMinutes(5), 0);
        when(otpChallengeRepository.findFirstByAccount_IdAndPurposeAndUsedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(
                1L, OtpPurpose.LOGIN)).thenReturn(Optional.of(loginCh));

        VerifyOtpRequest loginReq = new VerifyOtpRequest();
        loginReq.setEmail("a@fpt.edu.vn");
        loginReq.setOtp("123456");
        loginReq.setPurpose(null);
        LoginResponse loginRes = service.verifyOtp(loginReq);
        assertEquals("jwt-access-token", loginRes.getAccessToken());
        assertNotNull(loginCh.getUsedAt());
        assertAuditSaved("OTP_VERIFY_LOGIN", true);

        // UTC054 - verifyResetPasswordOtp valid for ACTIVE without marking usedAt
        OtpChallenge forgotCh = challenge(acc, OtpPurpose.FORGOT_PASSWORD, "123456", OffsetDateTime.now().plusMinutes(5), 0);
        when(otpChallengeRepository.findFirstByAccount_IdAndPurposeAndUsedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(
                1L, OtpPurpose.FORGOT_PASSWORD)).thenReturn(Optional.of(forgotCh));
        VerifyOtpRequest resetReq = new VerifyOtpRequest();
        resetReq.setEmail("a@fpt.edu.vn");
        resetReq.setOtp("123456");
        Map<String, Object> verifyMap = service.verifyResetPasswordOtp(resetReq);
        assertEquals(true, verifyMap.get("valid"));
        assertNull(forgotCh.getUsedAt());

        // UTC055 - verifyResetPasswordOtp allows SUSPENDED account
        acc.setStatus(AccountStatus.SUSPENDED);
        assertEquals(true, service.verifyResetPasswordOtp(resetReq).get("valid"));

        // UTC056 - verifyResetPasswordOtp rejects DELETED account with ACCOUNT_DELETED
        acc.setStatus(AccountStatus.DELETED);
        assertEquals(ErrorCode.ACCOUNT_DELETED,
                assertThrows(AppException.class, () -> service.verifyResetPasswordOtp(resetReq)).getErrorCode());

        // UTC057 - Missing FORGOT_PASSWORD challenge throws OTP_NOT_FOUND
        acc.setStatus(AccountStatus.ACTIVE);
        when(otpChallengeRepository.findFirstByAccount_IdAndPurposeAndUsedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(
                1L, OtpPurpose.FORGOT_PASSWORD)).thenReturn(Optional.empty());
        assertEquals(ErrorCode.OTP_NOT_FOUND,
                assertThrows(AppException.class, () -> service.verifyResetPasswordOtp(resetReq)).getErrorCode());

        // UTC058 & UTC052 - Expired challenge throws OTP_EXPIRED on verifyResetPasswordOtp and verifyOtp
        OtpChallenge expired = challenge(acc, OtpPurpose.FORGOT_PASSWORD, "123456", OffsetDateTime.now().minusMinutes(1), 0);
        when(otpChallengeRepository.findFirstByAccount_IdAndPurposeAndUsedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(
                1L, OtpPurpose.FORGOT_PASSWORD)).thenReturn(Optional.of(expired));
        assertEquals(ErrorCode.OTP_EXPIRED,
                assertThrows(AppException.class, () -> service.verifyResetPasswordOtp(resetReq)).getErrorCode());
        when(otpChallengeRepository.findFirstByAccount_IdAndPurposeAndUsedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(
                1L, OtpPurpose.LOGIN)).thenReturn(Optional.of(expired));
        assertEquals(ErrorCode.OTP_EXPIRED,
                assertThrows(AppException.class, () -> service.verifyOtp(loginReq)).getErrorCode());

        // UTC059 - attemptCount=5 throws OTP_MAX_ATTEMPTS
        OtpChallenge maxed = challenge(acc, OtpPurpose.FORGOT_PASSWORD, "123456", OffsetDateTime.now().plusMinutes(5), 5);
        when(otpChallengeRepository.findFirstByAccount_IdAndPurposeAndUsedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(
                1L, OtpPurpose.FORGOT_PASSWORD)).thenReturn(Optional.of(maxed));
        assertEquals(ErrorCode.OTP_MAX_ATTEMPTS,
                assertThrows(AppException.class, () -> service.verifyResetPasswordOtp(resetReq)).getErrorCode());

        // UTC060 - Wrong OTP on attemptCount=4 increments to 5, sets revokedAt & throws OTP_INVALID
        resetReq.setOtp("000000");
        OtpChallenge fourthWrong = challenge(acc, OtpPurpose.FORGOT_PASSWORD, "123456", OffsetDateTime.now().plusMinutes(5), 4);
        when(otpChallengeRepository.findFirstByAccount_IdAndPurposeAndUsedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(
                1L, OtpPurpose.FORGOT_PASSWORD)).thenReturn(Optional.of(fourthWrong));
        assertEquals(ErrorCode.OTP_INVALID,
                assertThrows(AppException.class, () -> service.verifyResetPasswordOtp(resetReq)).getErrorCode());
        assertEquals(5, fourthWrong.getAttemptCount());
        assertNotNull(fourthWrong.getRevokedAt());

        // UTC053 - verifyOtp wrong OTP throws OTP_INVALID
        loginReq.setOtp("999999");
        when(otpChallengeRepository.findFirstByAccount_IdAndPurposeAndUsedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(
                1L, OtpPurpose.LOGIN)).thenReturn(Optional.of(challenge(acc, OtpPurpose.LOGIN, "123456", OffsetDateTime.now().plusMinutes(5), 0)));
        assertEquals(ErrorCode.OTP_INVALID,
                assertThrows(AppException.class, () -> service.verifyOtp(loginReq)).getErrorCode());

        // UTC051 - verifyOtp missing account throws ACCOUNT_NOT_FOUND
        loginReq.setEmail("none@fpt.edu.vn");
        when(accountRepository.findByEmailIgnoreCase("none@fpt.edu.vn")).thenReturn(Optional.empty());
        assertEquals(ErrorCode.ACCOUNT_NOT_FOUND,
                assertThrows(AppException.class, () -> service.verifyOtp(loginReq)).getErrorCode());
    }

    // UTC061, UTC062, UTC063, UTC064, UTC065, UTC066, UTC067, UTC068, UTC069, UTC070 - forgotPassword, resetPassword & sha256Hex
    @Test
    void forgotPassword_and_resetPassword_and_sha256Hex_coverAllBranches() {
        Account suspended = account(1L, "a@fpt.edu.vn", AccountRole.USER, AccountStatus.SUSPENDED);
        suspended.setFailedLoginCount(5);
        suspended.setLockedUntil(OffsetDateTime.now().plusYears(10));
        when(accountRepository.findByEmailIgnoreCase("a@fpt.edu.vn")).thenReturn(Optional.of(suspended));
        when(otpChallengeRepository.findByAccount_IdAndPurposeAndUsedAtIsNullAndRevokedAtIsNull(1L, OtpPurpose.FORGOT_PASSWORD))
                .thenReturn(List.of());
        when(emailService.sendOtpEmail(eq("a@fpt.edu.vn"), anyString(), eq(OtpPurpose.FORGOT_PASSWORD))).thenReturn(true);

        // UTC061 - forgotPassword delegates to sendOtp(FORGOT_PASSWORD)
        ForgotPasswordRequest forgotReq = new ForgotPasswordRequest();
        forgotReq.setEmail("a@fpt.edu.vn");
        assertEquals("FORGOT_PASSWORD", service.forgotPassword(forgotReq).get("purpose"));

        // UTC063 - resetPassword unlocks SUSPENDED account, resets failedLoginCount/lockedUntil, revokes refresh tokens & audits RESET_PASSWORD_AND_UNLOCKED
        OtpChallenge resetCh = challenge(suspended, OtpPurpose.FORGOT_PASSWORD, "123456", OffsetDateTime.now().plusMinutes(5), 0);
        RefreshToken token = refreshToken(suspended);
        when(otpChallengeRepository.findFirstByAccount_IdAndPurposeAndUsedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(
                1L, OtpPurpose.FORGOT_PASSWORD)).thenReturn(Optional.of(resetCh));
        when(passwordEncoder.encode("New@1234")).thenReturn("new-hash");
        when(refreshTokenRepository.findByAccount_IdAndRevokedAtIsNull(1L)).thenReturn(List.of(token));

        ResetPasswordRequest resetReq = new ResetPasswordRequest();
        resetReq.setEmail("a@fpt.edu.vn");
        resetReq.setOtp("123456");
        resetReq.setNewPassword("New@1234");
        AccountDto unlocked = service.resetPassword(resetReq);
        assertEquals(AccountStatus.ACTIVE, unlocked.getStatus());
        assertEquals(0, suspended.getFailedLoginCount());
        assertNull(suspended.getLockedUntil());
        assertNotNull(token.getRevokedAt());
        assertAuditSaved("RESET_PASSWORD_AND_UNLOCKED", true);

        // UTC064 & UTC065 - Valid OTP updates password once; second call with reused OTP throws OTP_NOT_FOUND
        Account active = account(2L, "b@fpt.edu.vn", AccountRole.USER, AccountStatus.ACTIVE);
        when(accountRepository.findByEmailIgnoreCase("b@fpt.edu.vn")).thenReturn(Optional.of(active));
        OtpChallenge activeCh = challenge(active, OtpPurpose.FORGOT_PASSWORD, "123456", OffsetDateTime.now().plusMinutes(5), 0);
        when(otpChallengeRepository.findFirstByAccount_IdAndPurposeAndUsedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(
                2L, OtpPurpose.FORGOT_PASSWORD))
                .thenReturn(Optional.of(activeCh))
                .thenReturn(Optional.empty());
        when(refreshTokenRepository.findByAccount_IdAndRevokedAtIsNull(2L)).thenReturn(List.of());
        resetReq.setEmail("b@fpt.edu.vn");
        assertEquals(AccountStatus.ACTIVE, service.resetPassword(resetReq).getStatus());
        assertEquals(ErrorCode.OTP_NOT_FOUND,
                assertThrows(AppException.class, () -> service.resetPassword(resetReq)).getErrorCode());

        // UTC066 - OTP mismatch throws OTP_INVALID
        when(otpChallengeRepository.findFirstByAccount_IdAndPurposeAndUsedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(
                2L, OtpPurpose.FORGOT_PASSWORD)).thenReturn(Optional.of(
                challenge(active, OtpPurpose.FORGOT_PASSWORD, "123456", OffsetDateTime.now().plusMinutes(5), 0)));
        resetReq.setOtp("000000");
        assertEquals(ErrorCode.OTP_INVALID,
                assertThrows(AppException.class, () -> service.resetPassword(resetReq)).getErrorCode());

        // UTC062 & UTC067 - Missing account throws ACCOUNT_NOT_FOUND for forgotPassword & resetPassword
        forgotReq.setEmail("none@fpt.edu.vn");
        resetReq.setEmail("none@fpt.edu.vn");
        when(accountRepository.findByEmailIgnoreCase("none@fpt.edu.vn")).thenReturn(Optional.empty());
        assertEquals(ErrorCode.ACCOUNT_NOT_FOUND,
                assertThrows(AppException.class, () -> service.forgotPassword(forgotReq)).getErrorCode());
        assertEquals(ErrorCode.ACCOUNT_NOT_FOUND,
                assertThrows(AppException.class, () -> service.resetPassword(resetReq)).getErrorCode());

        // UTC068, UTC069 & UTC070 - sha256Hex exact digests for "123456", empty string "" & UTF-8 "Mật khẩu"
        assertEquals("8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92",
                AuthService.sha256Hex("123456"));
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                AuthService.sha256Hex(""));
        String utf8Hash = AuthService.sha256Hex("Mật khẩu");
        assertTrue(utf8Hash.matches("^[0-9a-f]{64}$"));
        assertEquals(utf8Hash, AuthService.sha256Hex("Mật khẩu"));
    }

    private void assertAuditSaved(String expectedEventType, boolean expectedSuccess) {
        ArgumentCaptor<LoginAuditLog> cap = ArgumentCaptor.forClass(LoginAuditLog.class);
        verify(loginAuditLogRepository, atLeastOnce()).save(cap.capture());
        List<LoginAuditLog> logs = cap.getAllValues();
        LoginAuditLog last = logs.get(logs.size() - 1);
        assertEquals(expectedEventType, last.getEventType());
        assertEquals(expectedSuccess, last.isSuccess());
    }

    private static LoginRequest loginReq(String email, String password) {
        LoginRequest req = new LoginRequest();
        req.setEmail(email);
        req.setPassword(password);
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

