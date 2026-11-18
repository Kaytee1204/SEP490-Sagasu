package com.example.base.integration.auth;

import com.example.base.entity.Account;
import com.example.base.entity.AccountRole;
import com.example.base.entity.AccountStatus;
import com.example.base.entity.OtpChallenge;
import com.example.base.entity.OtpPurpose;
import com.example.base.repository.AccountRepository;
import com.example.base.repository.LoginAuditLogRepository;
import com.example.base.repository.OtpChallengeRepository;
import com.example.base.repository.RefreshTokenRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared setup for the "1. Auth & OTP" integration tests (Report-5.2).
 * Real Spring context + local DB sagasu_test; every test runs in a transaction that is rolled back.
 * JavaMailSender is mocked, so OTP e-mails are captured (and the OTP read from them) instead of sent.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
abstract class AuthIntegrationTestBase {

    static final String U = "sagasuusertest01@gmail.com";
    static final String U_MIXED_CASE = "SagasuUserTest01@GMAIL.com";
    static final String PW = "Test@1234";
    static final String WRONG = "Wrong@123";
    static final String NEW_PW = "NewPass@2026";
    static final String UNKNOWN = "notexist999@gmail.com";

    static final String MSG_BAD_BODY = "Dữ liệu gửi lên không hợp lệ hoặc thiếu phần data";
    static final String MSG_EMAIL_BLANK = "Email không được để trống";
    static final String MSG_EMAIL_FORMAT = "Định dạng Email không hợp lệ (VD: example@gmail.com)";
    static final String MSG_OTP_BLANK = "Mã OTP không được để trống";
    static final String MSG_OTP_FORMAT = "Mã OTP phải gồm đúng 6 chữ số";
    static final String MSG_PW_RULE = "Mật khẩu mới phải dài từ 8 đến 16 ký tự, chứa ít nhất 1 ký tự đặc biệt và không có khoảng trắng";
    static final String MSG_LOCKED = "Tài khoản đang bị khóa do nhập sai mật khẩu 5 lần liên tiếp. Vui lòng kiểm tra email để lấy mã OTP đặt lại mật khẩu và mở khóa tài khoản.";
    static final String MSG_TEMP_LOCKED = "Tài khoản đang bị tạm khóa. Vui lòng kiểm tra email để lấy mã OTP đặt lại mật khẩu và mở khóa tài khoản.";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AccountRepository accounts;
    @Autowired PasswordEncoder encoder;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired LoginAuditLogRepository auditLogs;
    @Autowired OtpChallengeRepository otps;
    @MockBean JavaMailSender mailSender;

    Account user;

    @BeforeEach
    void createTestAccount() {
        user = activeAccount(U);
    }

    /** Creates (or resets) an ACTIVE USER account with password PW. */
    Account activeAccount(String email) {
        Account a = accounts.findByEmailIgnoreCase(email).orElseGet(Account::new);
        a.setFullName("Sagasu User Test 01");
        a.setEmail(email);
        a.setPasswordHash(encoder.encode(PW));
        a.setRole(AccountRole.USER);
        a.setStatus(AccountStatus.ACTIVE);
        a.setMustChangePassword(false);
        a.setFailedLoginCount(0);
        a.setLockedUntil(null);
        a.setValidUntil(null);
        return accounts.saveAndFlush(a);
    }

    // ---------- HTTP helpers ----------

    ResultActions postJson(String url, Object body) throws Exception {
        String content = body instanceof String s ? s : json.writeValueAsString(body);
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(content));
    }

    /** Map.of() does not allow null values; this does. Pairs: key, value, key, value ... */
    static Map<String, Object> body(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    static void expectError(ResultActions r, int http, String code, String message) throws Exception {
        r.andExpect(status().is(http))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").value(message));
    }

    JsonNode read(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    ResultActions login(String email, String password) throws Exception {
        return postJson("/api/auth/login", body("email", email, "password", password));
    }

    /** Logs in and returns the raw refresh token. */
    String loginForRefreshToken() throws Exception {
        return read(login(U, PW).andExpect(status().isOk())).at("/data/refreshToken").asText();
    }

    void lockByFiveWrongPasswords() throws Exception {
        for (int i = 0; i < 5; i++) login(U, WRONG);
        assertThat(reload().getStatus()).isEqualTo(AccountStatus.SUSPENDED);
    }

    // ---------- OTP helpers ----------

    ResultActions sendOtp(String email, OtpPurpose purpose) throws Exception {
        return postJson("/api/auth/send-otp", body("email", email, "purpose", purpose.name()));
    }

    /** Requests an OTP of the given purpose for U and returns the code read from the captured e-mail. */
    String requestOtp(OtpPurpose purpose) throws Exception {
        sendOtp(U, purpose).andExpect(status().isOk());
        return lastMailedOtp();
    }

    String forgotPasswordOtp() throws Exception {
        postJson("/api/auth/forgot-password", body("email", U)).andExpect(status().isOk());
        return lastMailedOtp();
    }

    String changePasswordOtp(String currentPassword) throws Exception {
        postJson("/api/auth/change-password/request-otp", body("email", U, "currentPassword", currentPassword))
                .andExpect(status().isOk());
        return lastMailedOtp();
    }

    List<SimpleMailMessage> sentMails() {
        ArgumentCaptor<SimpleMailMessage> mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, atLeastOnce()).send(mail.capture());
        return mail.getAllValues();
    }

    SimpleMailMessage lastMail() {
        List<SimpleMailMessage> all = sentMails();
        return all.get(all.size() - 1);
    }

    String lastMailedOtp() {
        return otpIn(lastMail().getText());
    }

    static String otpIn(String text) {
        Matcher m = Pattern.compile("là: (\\d{6})").matcher(text);
        assertThat(m.find()).as("6-digit OTP in e-mail body").isTrue();
        return m.group(1);
    }

    /** A 6-digit code that is surely wrong for the given OTP. */
    static String wrongOtp(String otp) {
        return "000000".equals(otp) ? "111111" : "000000";
    }

    OtpChallenge activeOtp(OtpPurpose purpose) {
        return otps.findFirstByAccount_IdAndPurposeAndUsedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(user.getId(), purpose)
                .orElseThrow(() -> new AssertionError("no active " + purpose + " OTP"));
    }

    List<OtpChallenge> otpRows(OtpPurpose purpose) {
        return otps.findAll().stream()
                .filter(o -> o.getAccount().getId().equals(user.getId()) && o.getPurpose() == purpose)
                .sorted((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                .toList();
    }

    long otpCount() {
        return otps.findAll().stream().filter(o -> o.getAccount().getId().equals(user.getId())).count();
    }

    void expireOtp(OtpChallenge c) {
        c.setExpiresAt(OffsetDateTime.now().minusMinutes(1));
        otps.saveAndFlush(c);
    }

    // ---------- DB helpers ----------

    Account reload() {
        return accounts.findById(user.getId()).orElseThrow();
    }

    void setStatus(AccountStatus s) {
        user.setStatus(s);
        accounts.saveAndFlush(user);
    }

    void setLockedUntil(OffsetDateTime t) {
        user.setLockedUntil(t);
        accounts.saveAndFlush(user);
    }

    void setValidUntil(OffsetDateTime t) {
        user.setValidUntil(t);
        accounts.saveAndFlush(user);
    }

    boolean passwordIs(String raw) {
        return encoder.matches(raw, reload().getPasswordHash());
    }

    boolean hasAudit(String eventType, boolean success) {
        return auditLogs.findByAccount_IdOrderByCreatedAtDesc(user.getId()).stream()
                .anyMatch(l -> eventType.equals(l.getEventType()) && l.isSuccess() == success);
    }

    static String sha256(String raw) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
    }
}
