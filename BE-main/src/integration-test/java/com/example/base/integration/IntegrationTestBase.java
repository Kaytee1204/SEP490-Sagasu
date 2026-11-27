package com.example.base.integration;

import com.example.base.entity.Account;
import com.example.base.entity.AccountRole;
import com.example.base.entity.AccountStatus;
import com.example.base.entity.RefreshToken;
import com.example.base.repository.AccountRepository;
import com.example.base.repository.LoginAuditLogRepository;
import com.example.base.repository.OtpChallengeRepository;
import com.example.base.repository.RefreshTokenRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.mockito.Mockito.mockingDetails;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Shared setup for the scenario-style sheets of Report-5.2 (TC-AUTH-xx, TC-ACC-xx).
 * One test method = one TC row; every step is checked with SoftAssertions so a failure lists every broken step.
 * Real Spring context + DB sagasu_test, rolled back after each test; JavaMailSender is mocked (OTP read from the captured mail).
 * Test accounts (password Test@1234): user01 = sagasuusertest01@gmail.com, user02 = sagasuusertest02@gmail.com,
 * staff01 = sagasustafftest01@gmail.com (APPROVER), manager01/02 = sagasumanagertest01/02@gmail.com, admin01 = sagasuadmintest01@gmail.com.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public abstract class IntegrationTestBase {

    protected static final String PW = "Test@1234";
    protected static final String NEW_PW = "NewPass@123";
    protected static final String WRONG = "Wrong@123";
    protected static final String USER01 = "sagasuusertest01@gmail.com";
    protected static final String USER02 = "sagasuusertest02@gmail.com";
    protected static final String USER03 = "sagasuusertest03@gmail.com";
    protected static final String STAFF01 = "sagasustafftest01@gmail.com";
    protected static final String MANAGER01 = "sagasumanagertest01@gmail.com";
    protected static final String MANAGER02 = "sagasumanagertest02@gmail.com";
    protected static final String ADMIN01 = "sagasuadmintest01@gmail.com";
    protected static final String UNKNOWN = "notexist999@gmail.com";

    protected static final String MSG_401 = "Chưa xác thực hoặc phiên đăng nhập không hợp lệ";
    protected static final String MSG_403 = "Không có quyền truy cập";

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    @Autowired protected AccountRepository accounts;
    @Autowired protected PasswordEncoder encoder;
    @Autowired protected RefreshTokenRepository refreshTokens;
    @Autowired protected LoginAuditLogRepository auditLogs;
    @Autowired protected OtpChallengeRepository otps;
    @MockBean protected JavaMailSender mailSender;

    protected SoftAssertions soft;

    @BeforeEach
    void openSoft() { soft = new SoftAssertions(); }

    @AfterEach
    void closeSoft() { soft.assertAll(); }

    /** HTTP result: status + parsed body (null when the body is not JSON). */
    public record Res(int status, JsonNode body, byte[] raw) {
        public String text(String ptr) { return body == null ? null : body.at(ptr).isMissingNode() ? null : body.at(ptr).asText(); }
        public String code() { return text("/code"); }
        public String message() { return text("/message"); }
    }

    /** Creates (or resets) an ACTIVE account with password PW. */
    protected Account account(String email, AccountRole role) {
        Account a = accounts.findByEmailIgnoreCase(email).orElseGet(Account::new);
        a.setFullName("Sagasu Test " + email.substring(0, email.indexOf('@')).replaceAll("[^A-Za-z0-9]", ""));
        a.setEmail(email);
        a.setPhone(null);
        a.setPasswordHash(encoder.encode(PW));
        a.setRole(role);
        a.setStatus(AccountStatus.ACTIVE);
        a.setMustChangePassword(false);
        a.setFailedLoginCount(0);
        a.setLockedUntil(null);
        a.setValidUntil(null);
        return accounts.saveAndFlush(a);
    }

    protected Account reload(Account a) { return accounts.findById(a.getId()).orElseThrow(); }

    protected Account save(Account a) { return accounts.saveAndFlush(a); }

    protected static Map<String, Object> body(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    protected Res send(MockHttpServletRequestBuilder req, String token, Object body) throws Exception {
        if (token != null) req.header("Authorization", "Bearer " + token);
        if (body != null) req.contentType(MediaType.APPLICATION_JSON)
                .content(body instanceof String s ? s : json.writeValueAsString(body));
        var resp = mvc.perform(req).andReturn().getResponse();
        byte[] raw = resp.getContentAsByteArray();
        JsonNode node = null;
        try { if (raw.length > 0) node = json.readTree(new String(raw, StandardCharsets.UTF_8)); } catch (Exception ignored) { }
        return new Res(resp.getStatus(), node, raw);
    }

    protected Res login(String email, String password) throws Exception {
        return send(post("/api/auth/login"), null, body("email", email, "password", password));
    }

    protected String token(String email) throws Exception {
        Res r = login(email, PW);
        if (r.status() != 200) throw new AssertionError("login " + email + " failed: " + r.status() + " " + r.message());
        return r.text("/data/accessToken");
    }

    /** Soft check of HTTP status, error code and message (code/message skipped when null). */
    protected void expect(String step, Res r, int http, String code, String message) {
        soft.assertThat(r.status()).as(step + ": HTTP status (message: %s)", r.message()).isEqualTo(http);
        if (code != null) soft.assertThat(r.code()).as(step + ": code").isEqualTo(code);
        if (message != null) soft.assertThat(r.message()).as(step + ": message").isEqualTo(message);
    }

    /** Like expect(), for bean-validation errors: several messages are joined with "; " in no fixed order. */
    protected void expectHas(String step, Res r, int http, String code, String message) {
        soft.assertThat(r.status()).as(step + ": HTTP status (message: %s)", r.message()).isEqualTo(http);
        if (code != null) soft.assertThat(r.code()).as(step + ": code").isEqualTo(code);
        soft.assertThat(r.message()).as(step + ": message").contains(message);
    }

    protected boolean passwordIs(Account a, String raw) { return encoder.matches(raw, reload(a).getPasswordHash()); }

    protected boolean hasAudit(Account a, String eventType, boolean success) {
        return auditLogs.findByAccount_IdOrderByCreatedAtDesc(a.getId()).stream()
                .anyMatch(l -> eventType.equals(l.getEventType()) && l.isSuccess() == success);
    }

    protected List<RefreshToken> activeTokens(Account a) { return refreshTokens.findByAccount_IdAndRevokedAtIsNull(a.getId()); }

    protected int mailCount() { return mockingDetails(mailSender).getInvocations().size(); }

    protected String lastMailedOtp() {
        ArgumentCaptor<SimpleMailMessage> mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
        org.mockito.Mockito.verify(mailSender, org.mockito.Mockito.atLeastOnce()).send(mail.capture());
        List<SimpleMailMessage> all = mail.getAllValues();
        Matcher m = Pattern.compile("là: (\\d{6})").matcher(all.get(all.size() - 1).getText());
        if (!m.find()) throw new AssertionError("no 6-digit OTP in the e-mail body");
        return m.group(1);
    }

    protected static String wrongOtp(String otp) { return "000000".equals(otp) ? "111111" : "000000"; }
}
