package com.example.base.service.auth;

import com.example.base.dto.account.AccountDto;
import com.example.base.dto.auth.*;
import com.example.base.entity.*;
import com.example.base.exception.AppException;
import com.example.base.exception.ErrorCode;
import com.example.base.mapper.AccountMapper;
import com.example.base.repository.AccountRepository;
import com.example.base.repository.LoginAuditLogRepository;
import com.example.base.repository.OtpChallengeRepository;
import com.example.base.repository.RefreshTokenRepository;
import com.example.base.security.CustomUserDetails;
import com.example.base.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AuthService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AccountRepository accountRepository;
    private final OtpChallengeRepository otpChallengeRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final LoginAuditLogRepository loginAuditLogRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AccountMapper accountMapper;
    private final EmailService emailService;

    @Value("${app.jwt.refresh-token-expiration-ms:604800000}")
    private long refreshTokenExpirationMs;

    public AuthService(
            AccountRepository accountRepository,
            OtpChallengeRepository otpChallengeRepository,
            RefreshTokenRepository refreshTokenRepository,
            LoginAuditLogRepository loginAuditLogRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider,
            AccountMapper accountMapper,
            EmailService emailService
    ) {
        this.accountRepository = accountRepository;
        this.otpChallengeRepository = otpChallengeRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.loginAuditLogRepository = loginAuditLogRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.accountMapper = accountMapper;
        this.emailService = emailService;
    }

    @Transactional(noRollbackFor = AppException.class)
    public LoginResponse login(LoginRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();
        Account account = accountRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_CREDENTIALS));

        validateAccountCanAuthenticate(account);

        if (!passwordEncoder.matches(request.getPassword(), account.getPasswordHash())) {
            int fails = account.getFailedLoginCount() + 1;
            account.setFailedLoginCount(fails);
            if (fails >= 5) {
                account.setLockedUntil(OffsetDateTime.now().plusMinutes(15));
            }
            accountRepository.save(account);
            recordAuditLog(account, "LOGIN_PASSWORD", false, Map.of("reason", "Wrong password", "failedCount", fails));
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        }

        account.setFailedLoginCount(0);
        account.setLockedUntil(null);
        accountRepository.save(account);

        // Đối với các role người dùng (USER, CHECKER, APPROVER) -> bắt buộc xác thực mã OTP qua email khi đăng nhập
        if (account.getRole() != AccountRole.ADMIN && account.getRole() != AccountRole.MANAGER) {
            SendOtpRequest otpRequest = new SendOtpRequest();
            otpRequest.setEmail(account.getEmail());
            otpRequest.setPurpose(OtpPurpose.LOGIN);
            Map<String, Object> otpResult = sendOtp(otpRequest);

            LoginResponse otpResponse = new LoginResponse();
            otpResponse.setRequiresOtp(true);
            otpResponse.setEmail(account.getEmail());
            otpResponse.setMessage(String.valueOf(
                    otpResult.getOrDefault("note", "Mã OTP 6 số đã được gửi tới email " + account.getEmail())
            ));
            recordAuditLog(account, "LOGIN_OTP_REQUIRED", true, Map.of("role", account.getRole().name()));
            return otpResponse;
        }

        LoginResponse response = issueTokensForAccount(account);
        recordAuditLog(account, "LOGIN_PASSWORD", true, Map.of("role", account.getRole().name()));
        return response;
    }

    @Transactional(noRollbackFor = AppException.class)
    public Map<String, Object> requestChangePasswordOtp(RequestChangePasswordOtpRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();
        Account account = accountRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));

        validateAccountCanAuthenticate(account);

        if (!passwordEncoder.matches(request.getCurrentPassword(), account.getPasswordHash())) {
            recordAuditLog(account, "CHANGE_PASSWORD_OTP_FAILED", false, Map.of("reason", "Wrong current password"));
            throw new AppException(ErrorCode.INVALID_CREDENTIALS, "Mật khẩu hiện tại không chính xác");
        }

        SendOtpRequest otpRequest = new SendOtpRequest();
        otpRequest.setEmail(normalizedEmail);
        otpRequest.setPurpose(OtpPurpose.CHANGE_PASSWORD);
        return sendOtp(otpRequest);
    }

    @Transactional(noRollbackFor = AppException.class)
    public AccountDto confirmChangePassword(ChangePasswordConfirmRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();
        Account account = accountRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));

        validateAccountObjectAndVerifyOtp(account, request.getOtp().trim(), OtpPurpose.CHANGE_PASSWORD);

        account.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        account.setMustChangePassword(false);
        account.setFailedLoginCount(0);
        account.setLockedUntil(null);
        accountRepository.save(account);

        List<RefreshToken> activeTokens = refreshTokenRepository.findByAccount_IdAndRevokedAtIsNull(account.getId());
        OffsetDateTime now = OffsetDateTime.now();
        for (RefreshToken token : activeTokens) {
            token.setRevokedAt(now);
        }
        refreshTokenRepository.saveAll(activeTokens);

        recordAuditLog(account, "CHANGE_PASSWORD_SUCCESS", true, Map.of("email", account.getEmail()));
        return accountMapper.toDto(account);
    }

    @Transactional(noRollbackFor = AppException.class)
    public Map<String, Object> sendOtp(SendOtpRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();
        OtpPurpose purpose = request.getPurpose() != null ? request.getPurpose() : OtpPurpose.CHANGE_PASSWORD;

        Account account = accountRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));

        validateAccountCanAuthenticate(account);

        List<OtpChallenge> openChallenges = otpChallengeRepository
                .findByAccount_IdAndPurposeAndUsedAtIsNullAndRevokedAtIsNull(account.getId(), purpose);
        OffsetDateTime now = OffsetDateTime.now();
        for (OtpChallenge existing : openChallenges) {
            existing.setRevokedAt(now);
        }
        if (!openChallenges.isEmpty()) {
            otpChallengeRepository.saveAllAndFlush(openChallenges);
        }

        String otpCode = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
        String otpHash = sha256Hex(otpCode);

        OtpChallenge challenge = new OtpChallenge();
        challenge.setAccount(account);
        challenge.setPurpose(purpose);
        challenge.setOtpHash(otpHash);
        challenge.setCreatedAt(now);
        challenge.setExpiresAt(now.plusMinutes(5));
        challenge.setAttemptCount(0);
        challenge.setMaxAttempts(5);
        otpChallengeRepository.save(challenge);

        boolean emailSent = emailService.sendOtpEmail(account.getEmail(), otpCode, purpose);
        recordAuditLog(account, "OTP_REQUEST_" + purpose.name(), true, Map.of("emailSent", emailSent));

        return Map.of(
                "email", account.getEmail(),
                "purpose", purpose.name(),
                "expiresAt", challenge.getExpiresAt(),
                "emailSent", emailSent,
                "note", emailSent
                        ? "Đã gửi mã OTP 6 số tới Gmail: " + account.getEmail()
                        : "Chưa điền Gmail App Password trong application.yml nên in OTP tại đây để bạn test: " + otpCode
        );
    }

    @Transactional(noRollbackFor = AppException.class)
    public LoginResponse verifyOtp(VerifyOtpRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();
        OtpPurpose purpose = request.getPurpose() != null ? request.getPurpose() : OtpPurpose.LOGIN;

        Account account = accountRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));

        validateAccountObjectAndVerifyOtp(account, request.getOtp().trim(), purpose);

        recordAuditLog(account, "OTP_VERIFY_" + purpose.name(), true, Map.of("purpose", purpose.name()));
        return issueTokensForAccount(account);
    }

    @Transactional(noRollbackFor = AppException.class)
    public Map<String, Object> forgotPassword(ForgotPasswordRequest request) {
        SendOtpRequest otpRequest = new SendOtpRequest();
        otpRequest.setEmail(request.getEmail());
        otpRequest.setPurpose(OtpPurpose.FORGOT_PASSWORD);
        return sendOtp(otpRequest);
    }

    @Transactional(noRollbackFor = AppException.class)
    public AccountDto resetPassword(ResetPasswordRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();
        Account account = accountRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));

        validateAccountObjectAndVerifyOtp(account, request.getOtp().trim(), OtpPurpose.FORGOT_PASSWORD);

        account.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        account.setMustChangePassword(false);
        account.setFailedLoginCount(0);
        account.setLockedUntil(null);
        accountRepository.save(account);

        List<RefreshToken> activeTokens = refreshTokenRepository.findByAccount_IdAndRevokedAtIsNull(account.getId());
        OffsetDateTime now = OffsetDateTime.now();
        for (RefreshToken token : activeTokens) {
            token.setRevokedAt(now);
        }
        refreshTokenRepository.saveAll(activeTokens);

        recordAuditLog(account, "RESET_PASSWORD", true, Map.of("email", account.getEmail()));
        return accountMapper.toDto(account);
    }

    private void validateAccountObjectAndVerifyOtp(Account account, String rawOtp, OtpPurpose purpose) {
        OtpChallenge challenge = otpChallengeRepository
                .findFirstByAccount_IdAndPurposeAndUsedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(account.getId(), purpose)
                .orElseThrow(() -> new AppException(ErrorCode.OTP_NOT_FOUND));

        OffsetDateTime now = OffsetDateTime.now();
        if (challenge.getExpiresAt().isBefore(now)) {
            challenge.setRevokedAt(now);
            otpChallengeRepository.save(challenge);
            throw new AppException(ErrorCode.OTP_EXPIRED);
        }

        if (challenge.getAttemptCount() >= challenge.getMaxAttempts()) {
            challenge.setRevokedAt(now);
            otpChallengeRepository.save(challenge);
            throw new AppException(ErrorCode.OTP_MAX_ATTEMPTS);
        }

        String inputHash = sha256Hex(rawOtp);
        if (!challenge.getOtpHash().equalsIgnoreCase(inputHash)) {
            int newAttempts = challenge.getAttemptCount() + 1;
            challenge.setAttemptCount(newAttempts);
            if (newAttempts >= challenge.getMaxAttempts()) {
                challenge.setRevokedAt(now);
            }
            otpChallengeRepository.save(challenge);
            recordAuditLog(account, "OTP_VERIFY_FAILED", false, Map.of("purpose", purpose.name(), "attempts", newAttempts));
            throw new AppException(ErrorCode.OTP_INVALID);
        }

        challenge.setUsedAt(now);
        otpChallengeRepository.save(challenge);
    }

    private void validateAccountCanAuthenticate(Account account) {
        if (account.getStatus() == AccountStatus.DELETED) {
            throw new AppException(ErrorCode.ACCOUNT_DELETED);
        }
        if (account.getStatus() == AccountStatus.SUSPENDED) {
            throw new AppException(ErrorCode.ACCOUNT_LOCKED);
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (account.getLockedUntil() != null && account.getLockedUntil().isAfter(now)) {
            throw new AppException(ErrorCode.ACCOUNT_LOCKED, "Tài khoản đang bị tạm khóa đến " + account.getLockedUntil());
        }
        if (account.getValidUntil() != null && account.getValidUntil().isBefore(now)) {
            throw new AppException(ErrorCode.ACCOUNT_EXPIRED);
        }
    }

    private LoginResponse issueTokensForAccount(Account account) {
        CustomUserDetails userDetails = new CustomUserDetails(account);
        String accessToken = jwtTokenProvider.generateAccessToken(userDetails);

        String rawRefreshToken = UUID.randomUUID() + "-" + UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setAccount(account);
        refreshToken.setTokenHash(sha256Hex(rawRefreshToken));
        refreshToken.setFamilyId(UUID.randomUUID());
        refreshToken.setCreatedAt(now);
        refreshToken.setExpiresAt(now.plusSeconds(refreshTokenExpirationMs / 1000));
        refreshTokenRepository.save(refreshToken);

        return new LoginResponse(accessToken, rawRefreshToken, accountMapper.toDto(account));
    }

    private void recordAuditLog(Account account, String eventType, boolean success, Map<String, Object> details) {
        LoginAuditLog auditLog = new LoginAuditLog();
        auditLog.setAccount(account);
        auditLog.setEventType(eventType);
        auditLog.setSuccess(success);
        auditLog.setDetails(details);
        loginAuditLogRepository.save(auditLog);
    }

    public static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
