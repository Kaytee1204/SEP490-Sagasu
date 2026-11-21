package com.example.base.integration.auth;

import com.example.base.entity.Account;
import com.example.base.entity.AccountRole;
import com.example.base.entity.AccountStatus;
import com.example.base.entity.OtpChallenge;
import com.example.base.entity.OtpPurpose;
import com.example.base.entity.RefreshToken;
import com.example.base.integration.ScenarioTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Report-5.2 (scenario style) sheet "Auth": TC-AUTH-01 .. TC-AUTH-09, per SRS 2.0 v2 UC-02, UC-04, GBR-03..GBR-06.
 * Only flows that need the real stack (DB, security filter, mail, several requests in a row).
 * Pure input validation (blank / bad e-mail, password format boundaries) is covered by Report-5.1 (Auth/Account UTC).
 */
class AuthScenarioIntegrationTest extends ScenarioTestBase {

    static final String MSG_LOCK_5 = "Tài khoản đã bị khóa do nhập sai mật khẩu 5 lần liên tiếp.";
    static final String MSG_SUSPENDED = "Tài khoản đang bị đình chỉ. Liên hệ văn phòng để được hỗ trợ.";
    static final String MSG_OTP_NONE = "Không tìm thấy mã OTP đang hoạt động, vui lòng yêu cầu mã mới";
    static final String MSG_OTP_WRONG = "Mã OTP không chính xác";
    static final String MSG_OTP_SENT = "Đã gửi mã OTP khôi phục mật khẩu";
    static final String MSG_RESET_OK = "Đặt lại mật khẩu thành công";
    static final String MSG_SAME_PW = "Mật khẩu mới phải khác mật khẩu hiện tại";

    Account user01, user02;

    @BeforeEach
    void accounts() {
        user01 = account(USER01, AccountRole.USER);
        user02 = account(USER02, AccountRole.USER);
    }

    static String remaining(int n) {
        return "Mật khẩu không chính xác. Bạn còn " + n + " lần thử trước khi tài khoản bị khóa.";
    }

    Res forgot(String email) throws Exception { return send(post("/api/auth/forgot-password"), null, body("email", email)); }

    Res verifyReset(String email, String otp) throws Exception {
        return send(post("/api/auth/verify-reset-otp"), null, body("email", email, "otp", otp, "purpose", "FORGOT_PASSWORD"));
    }

    Res reset(String email, String otp, String newPassword) throws Exception {
        return send(post("/api/auth/reset-password"), null, body("email", email, "otp", otp, "newPassword", newPassword));
    }

    String forgotOtp(String email) throws Exception {
        Res r = forgot(email);
        if (r.status() != 200) throw new AssertionError("forgot-password failed: " + r.status() + " " + r.message());
        return lastMailedOtp();
    }

    OtpChallenge latestOtp(Account a) {
        return otps.findAll().stream()
                .filter(o -> o.getAccount().getId().equals(a.getId()) && o.getPurpose() == OtpPurpose.FORGOT_PASSWORD)
                .max((x, y) -> x.getCreatedAt().compareTo(y.getCreatedAt())).orElseThrow();
    }

    // ---------- Log In ----------

    @Test
    @DisplayName("TC-AUTH-01 User logs in with a valid e-mail and password and is redirected by role")
    void tcAuth01_loginValidByRole() throws Exception {
        account(MANAGER01, AccountRole.MANAGER);
        account(ADMIN01, AccountRole.ADMIN);
        Res r = login(USER01, PW);
        expect("Step 2 user01", r, 200, null, "Đăng nhập thành công");
        soft.assertThat(r.text("/data/accessToken")).as("accessToken").isNotBlank();
        soft.assertThat(r.text("/data/refreshToken")).as("refreshToken").isNotBlank();
        soft.assertThat(r.text("/data/tokenType")).as("tokenType").isEqualTo("Bearer");
        soft.assertThat(r.text("/data/account/role")).as("user01 role (redirect to /)").isEqualTo("USER");
        soft.assertThat(r.body().at("/data/account/passwordHash").isMissingNode()).as("no password in response").isTrue();
        Res upper = login("SAGASUUSERTEST01@Gmail.com", PW);
        expect("Step 2 upper-case e-mail", upper, 200, null, null);
        soft.assertThat(upper.text("/data/account/email")).as("same account").isEqualTo(USER01);
        soft.assertThat(login(MANAGER01, PW).text("/data/account/role")).as("manager01 role (redirect to /admin)").isEqualTo("MANAGER");
        soft.assertThat(login(ADMIN01, PW).text("/data/account/role")).as("admin01 role (redirect to /admin)").isEqualTo("ADMIN");
        List<RefreshToken> tokens = activeTokens(user01);
        soft.assertThat(tokens).as("Step 4 refresh tokens saved").isNotEmpty();
        soft.assertThat(tokens).as("Step 4 refresh token stored as hash, not raw")
                .noneMatch(t -> r.text("/data/refreshToken").equals(t.getTokenHash()));
        soft.assertThat(hasAudit(user01, "LOGIN_PASSWORD", true)).as("Step 4 audit LOGIN_PASSWORD success").isTrue();
    }

    @Test
    @DisplayName("TC-AUTH-02 Wrong passwords are counted across requests and the 5th locks the account")
    void tcAuth02_wrongPasswordCountAndLock() throws Exception {
        expect("Step 1 unregistered e-mail", login(UNKNOWN, PW), 401, "AUTH_401", "Tài khoản hoặc mật khẩu không chính xác");
        expect("Step 2 wrong password", login(USER01, WRONG), 401, "AUTH_401", remaining(4));
        soft.assertThat(reload(user01).getFailedLoginCount()).as("Step 2 failed_login_count").isEqualTo(1);
        expect("Step 2 correct password", login(USER01, PW), 200, null, "Đăng nhập thành công");
        soft.assertThat(reload(user01).getFailedLoginCount()).as("Step 2 counter reset by success (GBR-04)").isZero();
        for (int i = 1; i <= 4; i++) expect("Step 3 attempt " + i, login(USER01, WRONG), 401, "AUTH_401", remaining(5 - i));
        expect("Step 3 attempt 5", login(USER01, WRONG), 403, "ACC_403_LOCKED", MSG_LOCK_5);
        expect("Step 4 correct password while locked", login(USER01, PW), 403, "ACC_403_LOCKED", MSG_LOCK_5);
        Account a = reload(user01);
        soft.assertThat(a.getStatus()).as("Step 5 status").isEqualTo(AccountStatus.SUSPENDED);
        soft.assertThat(a.getFailedLoginCount()).as("Step 5 failed_login_count").isEqualTo(5);
        soft.assertThat(hasAudit(user01, "ACCOUNT_LOCKED_5_FAILS", false)).as("Step 5 audit ACCOUNT_LOCKED_5_FAILS").isTrue();
    }

    @Test
    @DisplayName("TC-AUTH-03 Suspended, locked, deleted, expired or not-activated accounts cannot log in")
    void tcAuth03_blockedStatuses() throws Exception {
        user02.setStatus(AccountStatus.SUSPENDED);
        save(user02);
        expect("Step 1 SUSPENDED", login(USER02, PW), 403, "ACC_403_LOCKED", MSG_SUSPENDED);

        user02.setStatus(AccountStatus.ACTIVE);
        user02.setLockedUntil(OffsetDateTime.now().plusMinutes(30));
        save(user02);
        expect("Step 2 locked_until in future", login(USER02, PW), 403, "ACC_403_LOCKED", "Tài khoản đang bị khóa.");

        user02.setLockedUntil(null);
        user02.setStatus(AccountStatus.DELETED);
        save(user02);
        expect("Step 3 DELETED", login(USER02, PW), 403, "ACC_403_DELETED", "Tài khoản đã bị xóa");

        user02.setStatus(AccountStatus.ACTIVE);
        user02.setValidUntil(OffsetDateTime.now().minusDays(1));
        save(user02);
        expect("Step 3 expired", login(USER02, PW), 403, "ACC_403_EXPIRED", "Tài khoản đã hết thời hạn hiệu lực");

        user02.setValidUntil(null);
        user02.setStatus(AccountStatus.PENDING_ACTIVATION);
        save(user02);
        Res pending = login(USER02, PW);
        soft.assertThat(pending.status()).as("Step 3 PENDING_ACTIVATION: HTTP status (GBR-06)").isEqualTo(403);
        soft.assertThat(pending.text("/data/accessToken")).as("Step 3 PENDING_ACTIVATION: no token").isNull();
    }

    @Test
    @DisplayName("TC-AUTH-04 Access token works on protected APIs")
    void tcAuth04_accessTokenOnProtectedApi() throws Exception {
        String t = token(USER01);
        Res me = send(get("/api/accounts/me"), t, null);
        expect("Step 2 with token", me, 200, null, null);
        soft.assertThat(me.text("/data/email")).as("profile of user01").isEqualTo(USER01);
        expect("Step 2 without token", send(get("/api/accounts/me"), null, null), 401, "ERR_401", MSG_401);
        expect("Step 2 invalid token", send(get("/api/accounts/me"), "abc.def.ghi", null), 401, "ERR_401", MSG_401);
    }

    // ---------- Forgot / Reset Password ----------

    @Test
    @DisplayName("TC-AUTH-05 User requests a reset OTP and receives it by e-mail")
    void tcAuth05_forgotSendsOtp() throws Exception {
        Res r = forgot(USER01);
        expect("Step 2", r, 200, null, MSG_OTP_SENT);
        soft.assertThat(r.text("/data/purpose")).as("purpose").isEqualTo("FORGOT_PASSWORD");
        String otp = lastMailedOtp();
        soft.assertThat(otp).as("Step 3 6-digit OTP in mail").matches("\\d{6}");
        OtpChallenge c = latestOtp(user01);
        soft.assertThat(c.getOtpHash()).as("Step 3 OTP stored as hash").isNotEqualTo(otp).hasSize(64);
        soft.assertThat(java.time.Duration.between(c.getCreatedAt(), c.getExpiresAt()).toSeconds())
                .as("Step 3 expires_at = created + 5 minutes").isBetween(299L, 300L);
        soft.assertThat(c.getAttemptCount()).as("Step 3 attempt_count").isZero();
        soft.assertThat(c.getMaxAttempts()).as("Step 3 max_attempts").isEqualTo(5);
    }

    @Test
    @DisplayName("TC-AUTH-06 Forgot password with an unregistered, deleted or locked e-mail")
    void tcAuth06_forgotSpecialEmails() throws Exception {
        long before = otps.count();
        int mailsBefore = mailCount();
        Res unknown = forgot(UNKNOWN);
        soft.assertThat(unknown.status()).as("Step 1 unregistered: generic answer, no enumeration (UC-04 E1) - HTTP status (message: %s)",
                unknown.message()).isEqualTo(200);
        soft.assertThat(otps.count()).as("Step 1 no OTP created").isEqualTo(before);
        soft.assertThat(mailCount()).as("Step 1 no e-mail sent").isEqualTo(mailsBefore);

        Account deleted = account(USER03, AccountRole.USER);
        deleted.setStatus(AccountStatus.DELETED);
        save(deleted);
        expect("Step 2 DELETED", forgot(USER03), 403, "ACC_403_DELETED", "Tài khoản đã bị xóa");
        soft.assertThat(otps.count()).as("Step 2 no OTP for deleted account").isEqualTo(before);

        for (int i = 0; i < 5; i++) login(USER01, WRONG);
        expect("Step 3 locked account", forgot(USER01), 200, null, MSG_OTP_SENT);
    }

    @Test
    @DisplayName("TC-AUTH-07 Wrong, replaced, over-tried or expired OTP cannot be verified")
    void tcAuth07_otpRules() throws Exception {
        String otp1 = forgotOtp(USER01);
        expect("Step 1 wrong code", verifyReset(USER01, wrongOtp(otp1)), 400, "OTP_400_INVALID", MSG_OTP_WRONG);
        soft.assertThat(latestOtp(user01).getAttemptCount()).as("Step 1 attempt_count").isEqualTo(1);

        String otp2 = forgotOtp(USER01);
        if (!otp2.equals(otp1)) expect("Step 2 old OTP-1", verifyReset(USER01, otp1), 400, "OTP_400_INVALID", MSG_OTP_WRONG);
        expect("Step 2 new OTP-2", verifyReset(USER01, otp2), 200, null, "Mã OTP hợp lệ");

        String otp3 = forgotOtp(USER01);
        for (int i = 1; i <= 5; i++) verifyReset(USER01, wrongOtp(otp3));
        expect("Step 3 correct after 5 wrong", verifyReset(USER01, otp3), 400, "OTP_404", MSG_OTP_NONE);

        String otp4 = forgotOtp(USER01);
        OtpChallenge c = latestOtp(user01);
        c.setExpiresAt(OffsetDateTime.now().minusMinutes(1));
        otps.saveAndFlush(c);
        expect("Step 4 expired", verifyReset(USER01, otp4), 400, "OTP_400_EXPIRED", "Mã OTP đã hết hạn");
    }

    @Test
    @DisplayName("TC-AUTH-08 User sets a new password with a valid OTP and logs in with it")
    void tcAuth08_resetSuccess() throws Exception {
        String otp = forgotOtp(USER01);
        Res v = verifyReset(USER01, otp);
        expect("Step 1 verify", v, 200, null, "Mã OTP hợp lệ");
        soft.assertThat(latestOtp(user01).getUsedAt()).as("Step 1 OTP not used up yet").isNull();
        expect("Step 2 same as current password", reset(USER01, otp, PW), 400, "ERR_400", MSG_SAME_PW);
        expect("Step 3 reset", reset(USER01, otp, NEW_PW), 200, null, MSG_RESET_OK);
        expect("Step 4 old password", login(USER01, PW), 401, "AUTH_401", null);
        expect("Step 4 new password", login(USER01, NEW_PW), 200, null, "Đăng nhập thành công");
        expect("Step 5 reuse OTP", reset(USER01, otp, "Other@1234"), 400, "OTP_404", MSG_OTP_NONE);
        Account a = reload(user01);
        soft.assertThat(a.getPasswordHash()).as("BCrypt hash").startsWith("$2");
        soft.assertThat(a.isMustChangePassword()).as("must_change_password = false (UC-04 Postcondition 3)").isFalse();
        soft.assertThat(passwordIs(user01, NEW_PW)).as("password still NewPass@123").isTrue();
    }

    @Test
    @DisplayName("TC-AUTH-09 Reset unlocks a 5-fail lock, keeps a Manager suspension and signs out all sessions")
    void tcAuth09_resetUnlockAndRevoke() throws Exception {
        login(USER01, PW);
        login(USER01, PW);
        List<Long> oldTokens = activeTokens(user01).stream().map(RefreshToken::getId).toList();
        for (int i = 0; i < 5; i++) login(USER01, WRONG);
        String otp = forgotOtp(USER01);
        expect("Step 2 reset user01", reset(USER01, otp, NEW_PW), 200, null, MSG_RESET_OK);
        Account a = reload(user01);
        soft.assertThat(a.getStatus()).as("Step 2 status").isEqualTo(AccountStatus.ACTIVE);
        soft.assertThat(a.getFailedLoginCount()).as("Step 2 failed_login_count").isZero();
        soft.assertThat(hasAudit(user01, "RESET_PASSWORD_AND_UNLOCKED", true)).as("Step 2 audit RESET_PASSWORD_AND_UNLOCKED").isTrue();
        soft.assertThat(refreshTokens.findAllById(oldTokens)).as("Step 3 old refresh tokens revoked").allMatch(t -> t.getRevokedAt() != null);
        expect("Step 2 login new password", login(USER01, NEW_PW), 200, null, null);

        user02.setStatus(AccountStatus.SUSPENDED);
        save(user02);
        String otp2 = forgotOtp(USER02);
        expect("Step 4 reset suspended user02", reset(USER02, otp2, NEW_PW), 200, null, MSG_RESET_OK);
        soft.assertThat(reload(user02).getStatus()).as("Step 4 user02 stays SUSPENDED").isEqualTo(AccountStatus.SUSPENDED);
        expect("Step 4 login user02", login(USER02, NEW_PW), 403, "ACC_403_LOCKED", MSG_SUSPENDED);
    }
}
