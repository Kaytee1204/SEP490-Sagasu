package com.example.base.integration.auth;

import com.example.base.entity.Account;
import com.example.base.entity.AccountStatus;
import com.example.base.entity.OtpChallenge;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.OffsetDateTime;

import static com.example.base.entity.OtpPurpose.FORGOT_PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Report-5.2_Integration Test.xlsx, sheet "Auth - Login & Forgot Password" (ITC001 - ITC034).
 * Mỗi method itcNNN_... ứng với một dòng ITCNNN; kết quả chạy được ghi lại vào cột Round 1.
 */
class AuthLoginForgotPasswordIntegrationTest extends AuthIntegrationTestBase {

    static final String LOGIN = "/api/auth/login";
    static final String FORGOT = "/api/auth/forgot-password";
    static final String VERIFY = "/api/auth/verify-reset-otp";
    static final String RESET = "/api/auth/reset-password";

    static final String MSG_LOCKED_5 = "Tài khoản đã bị khóa do nhập sai mật khẩu 5 lần liên tiếp.";
    static final String MSG_SUSPENDED = "Tài khoản đang bị đình chỉ. Liên hệ văn phòng để được hỗ trợ.";
    static final String MSG_OTP_NOT_FOUND = "Không tìm thấy mã OTP đang hoạt động, vui lòng yêu cầu mã mới";
    static final String MSG_OTP_INVALID = "Mã OTP không chính xác";

    static String triesLeft(int n) {
        return "Mật khẩu không chính xác. Bạn còn " + n + " lần thử trước khi tài khoản bị khóa.";
    }

    /** Requests a forgot-password OTP for the given e-mail and returns it (read from the captured mail). */
    String forgotOtp(String email) throws Exception {
        postJson(FORGOT, body("email", email)).andExpect(status().isOk());
        return lastMailedOtp();
    }

    // ========== [1.1] POST /api/auth/login ==========

    @Test
    @DisplayName("ITC001 Đăng nhập thành công bằng email và mật khẩu đúng")
    void itc001_loginSuccess() throws Exception {
        JsonNode res = read(login(U, PW)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đăng nhập thành công"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.account.email").value(U))
                .andExpect(jsonPath("$.data.account.role").value("USER"))
                .andExpect(jsonPath("$.data.account.passwordHash").doesNotExist()));

        String raw = res.at("/data/refreshToken").asText();
        assertThat(refreshTokens.findByAccount_IdAndRevokedAtIsNull(user.getId()))
                .singleElement()
                .satisfies(t -> assertThat(t.getTokenHash()).isNotEqualTo(raw).isEqualTo(sha256(raw)));
        assertThat(hasAudit("LOGIN_PASSWORD", true)).isTrue();
    }

    @Test
    @DisplayName("ITC002 Đăng nhập bằng tên đăng nhập admin")
    void itc002_loginAdminUsername() throws Exception {
        login("admin", "123")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đăng nhập thành công"))
                .andExpect(jsonPath("$.data.account.role").value("ADMIN"));
    }

    @Test
    @DisplayName("ITC003 Đăng nhập bằng tài khoản test user")
    void itc003_loginTestUser() throws Exception {
        login("user", "123")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đăng nhập thành công"))
                .andExpect(jsonPath("$.data.account.role").value("USER"))
                .andExpect(jsonPath("$.data.requiresOtp").value(false));
    }

    @Test
    @DisplayName("ITC004 Email đăng nhập không phân biệt hoa thường")
    void itc004_loginEmailCaseInsensitive() throws Exception {
        login(U_MIXED_CASE, PW)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.account.email").value(U));
    }

    @Test
    @DisplayName("ITC005 accessToken dùng được để gọi API cần đăng nhập")
    void itc005_accessTokenCallsProtectedApi() throws Exception {
        String token = read(login(U, PW).andExpect(status().isOk())).at("/data/accessToken").asText();

        mvc.perform(get("/api/accounts/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(U));
        mvc.perform(get("/api/accounts/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("ITC006 Đăng nhập sai mật khẩu")
    void itc006_wrongPassword() throws Exception {
        expectError(login(U, WRONG), 401, "AUTH_401", triesLeft(4));
        assertThat(reload().getFailedLoginCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("ITC007 Đăng nhập bằng email chưa đăng ký")
    void itc007_unknownEmail() throws Exception {
        expectError(login(UNKNOWN, PW), 401, "AUTH_401", "Tài khoản hoặc mật khẩu không chính xác");
    }

    @Test
    @DisplayName("ITC008 Khóa tài khoản sau 5 lần sai mật khẩu")
    void itc008_lockAfterFiveFails() throws Exception {
        for (int left = 4; left >= 1; left--) {
            expectError(login(U, WRONG), 401, "AUTH_401", triesLeft(left));
        }
        expectError(login(U, WRONG), 403, "ACC_403_LOCKED", MSG_LOCKED_5);
        expectError(login(U, PW), 403, "ACC_403_LOCKED", MSG_LOCKED_5);

        Account a = reload();
        assertThat(a.getStatus()).isEqualTo(AccountStatus.SUSPENDED);
        assertThat(a.getFailedLoginCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("ITC009 Đăng nhập đúng thì bộ đếm sai về 0")
    void itc009_successResetsFailCounter() throws Exception {
        login(U, WRONG);
        login(U, WRONG);
        login(U, PW).andExpect(status().isOk()).andExpect(jsonPath("$.message").value("Đăng nhập thành công"));
        assertThat(reload().getFailedLoginCount()).isZero();
        expectError(login(U, WRONG), 401, "AUTH_401", triesLeft(4));
    }

    @Test
    @DisplayName("ITC010 Tài khoản bị Manager/Admin đình chỉ không đăng nhập được")
    void itc010_suspendedByStaff() throws Exception {
        setStatus(AccountStatus.SUSPENDED);
        expectError(login(U, PW), 403, "ACC_403_LOCKED", MSG_SUSPENDED);
    }

    @Test
    @DisplayName("ITC011 Tài khoản bị khóa có hạn, đã xóa hoặc hết hạn không đăng nhập được")
    void itc011_lockedDeletedExpired() throws Exception {
        setLockedUntil(OffsetDateTime.now().plusDays(1));
        expectError(login(U, PW), 403, "ACC_403_LOCKED", "Tài khoản đang bị khóa.");

        Account deleted = activeAccount("sagasuusertest02@gmail.com");
        deleted.setStatus(AccountStatus.DELETED);
        accounts.saveAndFlush(deleted);
        expectError(login(deleted.getEmail(), PW), 403, "ACC_403_DELETED", "Tài khoản đã bị xóa");

        Account expired = activeAccount("sagasuusertest03@gmail.com");
        expired.setValidUntil(OffsetDateTime.now().minusDays(1));
        accounts.saveAndFlush(expired);
        expectError(login(expired.getEmail(), PW), 403, "ACC_403_EXPIRED", "Tài khoản đã hết thời hạn hiệu lực");
    }

    @Test
    @DisplayName("ITC012 Dữ liệu đăng nhập không hợp lệ")
    void itc012_invalidLoginInput() throws Exception {
        postJson(LOGIN, body("email", "", "password", PW))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ERR_400"))
                .andExpect(jsonPath("$.message", containsString(MSG_EMAIL_BLANK)));
        postJson(LOGIN, body("email", U, "password", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ERR_400"))
                .andExpect(jsonPath("$.message", containsString("Mật khẩu không được để trống")));
        expectError(postJson(LOGIN, body("email", "user01@", "password", PW)), 400, "ERR_400", MSG_EMAIL_FORMAT);
        expectError(postJson(LOGIN, body("email", U, "password", "12")), 400, "ERR_400", "Mật khẩu phải từ 3 đến 100 ký tự");
        expectError(mvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON)), 400, "ERR_400", MSG_BAD_BODY);
        expectError(postJson(LOGIN, "{\"email\":\"" + U + "\","), 400, "ERR_400", MSG_BAD_BODY);

        assertThat(reload().getFailedLoginCount()).isZero();
    }

    @Test
    @DisplayName("ITC013 Chèn SQL vào ô email không đăng nhập được")
    void itc013_sqlInjection() throws Exception {
        expectError(login("' OR '1'='1", "x' OR '1'='1"), 400, "ERR_400", MSG_EMAIL_FORMAT);
    }

    // ========== [1.2] POST /api/auth/forgot-password ==========

    @Test
    @DisplayName("ITC014 Gửi OTP khôi phục mật khẩu tới email đã đăng ký")
    void itc014_forgotSuccess() throws Exception {
        OffsetDateTime before = OffsetDateTime.now();
        // UC-04 E1: the answer is generic (same as for an unregistered e-mail), so the expiry is read from the stored OTP
        postJson(FORGOT, body("email", U))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đã gửi mã OTP khôi phục mật khẩu"))
                .andExpect(jsonPath("$.data.purpose").value("FORGOT_PASSWORD"))
                .andExpect(jsonPath("$.data.emailSent").doesNotExist());

        OffsetDateTime expiresAt = activeOtp(FORGOT_PASSWORD).getExpiresAt();
        assertThat(expiresAt).isBetween(before.plusMinutes(5).minusSeconds(5), OffsetDateTime.now().plusMinutes(5).plusSeconds(5));

        assertThat(lastMail().getTo()).containsExactly(U);
        String otp = lastMailedOtp();
        assertThat(otp).matches("\\d{6}");

        OtpChallenge c = activeOtp(FORGOT_PASSWORD);
        assertThat(c.getOtpHash()).hasSize(64).isNotEqualTo(otp).isEqualTo(sha256(otp));
        assertThat(c.getAttemptCount()).isZero();
        assertThat(c.getMaxAttempts()).isEqualTo(5);
    }

    @Test
    @DisplayName("ITC015 Gửi lại OTP thì mã cũ hết hiệu lực")
    void itc015_resendInvalidatesOldOtp() throws Exception {
        String otp1 = forgotOtp(U);
        String otp2 = forgotOtp(U);
        org.junit.jupiter.api.Assumptions.assumeFalse(otp1.equals(otp2), "2 OTP ngẫu nhiên trùng nhau");

        expectError(postJson(VERIFY, body("email", U, "otp", otp1)), 400, "OTP_400_INVALID", MSG_OTP_INVALID);
        postJson(VERIFY, body("email", U, "otp", otp2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Mã OTP hợp lệ"));
    }

    @Test
    @DisplayName("ITC016 Email trống hoặc sai định dạng")
    void itc016_forgotInvalidEmail() throws Exception {
        postJson(FORGOT, body("email", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ERR_400"))
                .andExpect(jsonPath("$.message", containsString(MSG_EMAIL_BLANK)));
        expectError(postJson(FORGOT, body("email", "user01@")), 400, "ERR_400", MSG_EMAIL_FORMAT);
    }

    @Test
    @DisplayName("ITC017 Email chưa đăng ký")
    void itc017_forgotUnknownEmail() throws Exception {
        long before = otps.count();
        // SRS UC-04 E1: generic answer, the e-mail is not revealed as unregistered
        postJson(FORGOT, body("email", UNKNOWN)).andExpect(status().isOk());
        assertThat(otps.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("ITC018 Tài khoản bị khóa do sai mật khẩu vẫn nhận được OTP")
    void itc018_lockedAccountGetsOtp() throws Exception {
        lockByFiveWrongPasswords();
        postJson(FORGOT, body("email", U))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đã gửi mã OTP khôi phục mật khẩu"));
        assertThat(lastMail().getTo()).containsExactly(U);
    }

    @Test
    @DisplayName("ITC019 Tài khoản đã xóa không nhận được OTP")
    void itc019_deletedAccountNoOtp() throws Exception {
        setStatus(AccountStatus.DELETED);
        expectError(postJson(FORGOT, body("email", U)), 403, "ACC_403_DELETED", "Tài khoản đã bị xóa");
        assertThat(otpCount()).isZero();
    }

    // ========== [1.3] POST /api/auth/verify-reset-otp ==========

    @Test
    @DisplayName("ITC020 Xác thực OTP đúng")
    void itc020_verifySuccess() throws Exception {
        String otp = forgotOtp(U);
        postJson(VERIFY, body("email", U, "otp", otp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Mã OTP hợp lệ"))
                .andExpect(jsonPath("$.data.valid").value(true));
        assertThat(activeOtp(FORGOT_PASSWORD).getUsedAt()).isNull();
    }

    @Test
    @DisplayName("ITC021 Xác thực OTP sai")
    void itc021_verifyWrongOtp() throws Exception {
        String otp = forgotOtp(U);
        expectError(postJson(VERIFY, body("email", U, "otp", wrongOtp(otp))), 400, "OTP_400_INVALID", MSG_OTP_INVALID);
        assertThat(activeOtp(FORGOT_PASSWORD).getAttemptCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("ITC022 Nhập sai OTP 5 lần thì OTP bị hủy")
    void itc022_fiveWrongOtpRevokes() throws Exception {
        String otp = forgotOtp(U);
        for (int i = 0; i < 5; i++) {
            expectError(postJson(VERIFY, body("email", U, "otp", wrongOtp(otp))), 400, "OTP_400_INVALID", MSG_OTP_INVALID);
        }
        expectError(postJson(VERIFY, body("email", U, "otp", otp)), 400, "OTP_404", MSG_OTP_NOT_FOUND);
    }

    @Test
    @DisplayName("ITC023 OTP quá 5 phút thì hết hạn")
    void itc023_expiredOtp() throws Exception {
        String otp = forgotOtp(U);
        expireOtp(activeOtp(FORGOT_PASSWORD));
        expectError(postJson(VERIFY, body("email", U, "otp", otp)), 400, "OTP_400_EXPIRED", "Mã OTP đã hết hạn");
    }

    @Test
    @DisplayName("ITC024 OTP không đủ 6 chữ số hoặc để trống")
    void itc024_badOtpFormat() throws Exception {
        forgotOtp(U);
        expectError(postJson(VERIFY, body("email", U, "otp", "12345")), 400, "ERR_400", MSG_OTP_FORMAT);
        expectError(postJson(VERIFY, body("email", U, "otp", "12a456")), 400, "ERR_400", MSG_OTP_FORMAT);
        postJson(VERIFY, body("email", U, "otp", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ERR_400"))
                .andExpect(jsonPath("$.message", containsString(MSG_OTP_BLANK)));
        assertThat(activeOtp(FORGOT_PASSWORD).getAttemptCount()).isZero();
    }

    @Test
    @DisplayName("ITC025 OTP chưa yêu cầu hoặc OTP của tài khoản khác")
    void itc025_noOtpOrOtherAccountsOtp() throws Exception {
        expectError(postJson(VERIFY, body("email", U, "otp", "123456")), 400, "OTP_404", MSG_OTP_NOT_FOUND);

        String other = activeAccount("sagasuusertest02@gmail.com").getEmail();
        String otpA = forgotOtp(U);
        String otpB = forgotOtp(other);
        org.junit.jupiter.api.Assumptions.assumeFalse(otpA.equals(otpB), "2 OTP ngẫu nhiên trùng nhau");
        expectError(postJson(VERIFY, body("email", other, "otp", otpA)), 400, "OTP_400_INVALID", MSG_OTP_INVALID);
    }

    // ========== [1.4] POST /api/auth/reset-password ==========

    @Test
    @DisplayName("ITC026 Đặt lại mật khẩu thành công và đăng nhập bằng mật khẩu mới")
    void itc026_resetSuccess() throws Exception {
        String otp = forgotOtp(U);
        postJson(RESET, body("email", U, "otp", otp, "newPassword", NEW_PW))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đặt lại mật khẩu thành công"))
                .andExpect(jsonPath("$.data.failedLoginCount").value(0))
                .andExpect(jsonPath("$.data.mustChangePassword").value(false));

        login(U, PW).andExpect(status().isUnauthorized());
        login(U, NEW_PW).andExpect(status().isOk());

        assertThat(reload().getPasswordHash()).startsWith("$2").doesNotContain(NEW_PW);
        assertThat(otpRows(FORGOT_PASSWORD)).last().satisfies(c -> assertThat(c.getUsedAt()).isNotNull());
    }

    @Test
    @DisplayName("ITC027 Đặt lại mật khẩu mở khóa tài khoản bị khóa do sai 5 lần")
    void itc027_resetUnlocks() throws Exception {
        lockByFiveWrongPasswords();
        postJson(RESET, body("email", U, "otp", forgotOtp(U), "newPassword", NEW_PW))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đặt lại mật khẩu thành công"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.failedLoginCount").value(0));
        login(U, NEW_PW).andExpect(status().isOk()).andExpect(jsonPath("$.message").value("Đăng nhập thành công"));
    }

    @Test
    @DisplayName("ITC028 Đặt lại mật khẩu không gỡ đình chỉ do Manager/Admin đặt")
    void itc028_resetKeepsStaffSuspension() throws Exception {
        setStatus(AccountStatus.SUSPENDED);
        postJson(RESET, body("email", U, "otp", forgotOtp(U), "newPassword", NEW_PW))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đặt lại mật khẩu thành công"))
                .andExpect(jsonPath("$.data.status").value("SUSPENDED"));
        expectError(login(U, NEW_PW), 403, "ACC_403_LOCKED", MSG_SUSPENDED);
    }

    @Test
    @DisplayName("ITC029 Đặt lại mật khẩu thu hồi refresh token cũ")
    void itc029_resetRevokesRefreshTokens() throws Exception {
        loginForRefreshToken();
        loginForRefreshToken();
        assertThat(refreshTokens.findByAccount_IdAndRevokedAtIsNull(user.getId())).hasSize(2);

        postJson(RESET, body("email", U, "otp", forgotOtp(U), "newPassword", NEW_PW))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đặt lại mật khẩu thành công"));
        assertThat(refreshTokens.findByAccount_IdAndRevokedAtIsNull(user.getId())).isEmpty();
    }

    @Test
    @DisplayName("ITC030 OTP chỉ dùng được một lần")
    void itc030_otpSingleUse() throws Exception {
        String otp = forgotOtp(U);
        postJson(RESET, body("email", U, "otp", otp, "newPassword", NEW_PW)).andExpect(status().isOk());
        expectError(postJson(RESET, body("email", U, "otp", otp, "newPassword", "Again@1234")),
                400, "OTP_404", MSG_OTP_NOT_FOUND);
        assertThat(passwordIs(NEW_PW)).isTrue();
    }

    @Test
    @DisplayName("ITC031 OTP sai hoặc hết hạn thì không đổi mật khẩu")
    void itc031_wrongOrExpiredOtp() throws Exception {
        String otp = forgotOtp(U);
        expectError(postJson(RESET, body("email", U, "otp", wrongOtp(otp), "newPassword", NEW_PW)),
                400, "OTP_400_INVALID", MSG_OTP_INVALID);

        expireOtp(activeOtp(FORGOT_PASSWORD));
        expectError(postJson(RESET, body("email", U, "otp", otp, "newPassword", NEW_PW)),
                400, "OTP_400_EXPIRED", "Mã OTP đã hết hạn");
        assertThat(passwordIs(PW)).isTrue();
    }

    @Test
    @DisplayName("ITC032 Mật khẩu mới sai quy tắc")
    void itc032_newPasswordBreaksRule() throws Exception {
        String otp = forgotOtp(U);
        for (String bad : new String[]{"Abc@123", "Abcdefgh@12345678", "Abcd1234", "Abc @1234"}) {
            expectError(postJson(RESET, body("email", U, "otp", otp, "newPassword", bad)), 400, "ERR_400", MSG_PW_RULE);
        }
        assertThat(passwordIs(PW)).isTrue();
        OtpChallenge c = activeOtp(FORGOT_PASSWORD);
        assertThat(c.getAttemptCount()).isZero();
        assertThat(c.getUsedAt()).isNull();
    }

    @Test
    @DisplayName("ITC033 Mật khẩu mới dài 8 và 16 ký tự được chấp nhận")
    void itc033_newPasswordBoundaries() throws Exception {
        for (String ok : new String[]{"Abcd@123", "Abcdefgh@1234567"}) {
            postJson(RESET, body("email", U, "otp", forgotOtp(U), "newPassword", ok))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Đặt lại mật khẩu thành công"));
            login(U, ok).andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("ITC034 Bỏ trống trường hoặc email chưa đăng ký")
    void itc034_resetBlankOrUnknown() throws Exception {
        postJson(RESET, body("email", "", "otp", "", "newPassword", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ERR_400"))
                .andExpect(jsonPath("$.message", containsString(MSG_EMAIL_BLANK)))
                .andExpect(jsonPath("$.message", containsString(MSG_OTP_BLANK)))
                .andExpect(jsonPath("$.message", containsString("Mật khẩu mới không được để trống")))
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.otp").exists())
                .andExpect(jsonPath("$.errors.newPassword").exists());
        expectError(postJson(RESET, body("email", UNKNOWN, "otp", "123456", "newPassword", NEW_PW)),
                404, "ACC_404", "Không tìm thấy tài khoản");
    }
}
