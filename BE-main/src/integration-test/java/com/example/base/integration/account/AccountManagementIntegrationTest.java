package com.example.base.integration.account;

import com.example.base.entity.Account;
import com.example.base.entity.AccountRole;
import com.example.base.entity.AccountStatus;
import com.example.base.repository.AccountRepository;
import com.example.base.repository.LoginAuditLogRepository;
import com.example.base.repository.RefreshTokenRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mockingDetails;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Report-5.2 sheet "Account Management": 10 APIs of Swagger group "02. Account Management".
 * Expected results follow SRS v2.0 (UC-06..UC-12, GBR-53/54) for behaviour; error messages follow the code (as in the Auth sheet).
 * Real Spring context + DB sagasu_test; every test is rolled back. JavaMailSender is mocked.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AccountManagementIntegrationTest {

    static final String PW = "Test@1234";
    static final String NEW_PW = "NewPass@2026";
    static final String U = "sagasuusertest01@gmail.com";
    static final String U2 = "sagasuusertest02@gmail.com";
    static final String M = "sagasumanagertest01@gmail.com";
    static final String M2 = "sagasumanagertest02@gmail.com";
    static final String A = "sagasuadmintest01@gmail.com";
    static final String NEW = "sagasunewtest01@gmail.com";
    static final String NEW2 = "sagasunewtest02@gmail.com";
    static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    static final String MSG_401 = "Chưa xác thực hoặc phiên đăng nhập không hợp lệ";
    static final String MSG_403 = "Không có quyền truy cập";
    static final String MSG_PHONE = "Số điện thoại không hợp lệ (phải gồm 10-11 chữ số, bắt đầu bằng 0 hoặc +84)";
    static final String MSG05 = "Mật khẩu phải dài từ 8 đến 16 ký tự, chứa ít nhất 1 ký tự đặc biệt và không có khoảng trắng";
    static final String MSG04 = "Email/Tài khoản đã tồn tại trong hệ thống";
    static final String MSG_NEW_PW = "Mật khẩu mới phải dài từ 8 đến 16 ký tự, chứa ít nhất 1 ký tự đặc biệt và không có khoảng trắng";
    static final String MSG_MANAGER_SCOPE = "Tài khoản MANAGER chỉ được phép quản lý các tài khoản có role: USER, STAFF";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AccountRepository accounts;
    @Autowired PasswordEncoder encoder;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired LoginAuditLogRepository auditLogs;
    @MockBean JavaMailSender mailSender;

    Account user, manager, admin;

    @BeforeEach
    void createAccounts() {
        user = account(U, AccountRole.USER);
        manager = account(M, AccountRole.MANAGER);
        admin = account(A, AccountRole.ADMIN);
    }

    /** Creates (or resets) an ACTIVE account with password PW. */
    Account account(String email, AccountRole role) {
        Account a = accounts.findByEmailIgnoreCase(email).orElseGet(Account::new);
        a.setFullName("Sagasu Test " + email.substring(0, email.indexOf('@')));
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

    // ---------- helpers ----------

    static Map<String, Object> body(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body("email", email, "password", password))));
    }

    String token(String email) throws Exception {
        return read(login(email, PW).andExpect(status().isOk())).at("/data/accessToken").asText();
    }

    ResultActions call(MockHttpServletRequestBuilder req, String email, Object body) throws Exception {
        if (email != null) req.header("Authorization", "Bearer " + token(email));
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        return mvc.perform(req);
    }

    ResultActions upload(String email, byte[] bytes) throws Exception {
        return mvc.perform(multipart("/api/accounts/import")
                .file(new MockMultipartFile("file", "accounts.xlsx", XLSX, bytes))
                .header("Authorization", "Bearer " + token(email)));
    }

    static byte[] xlsx(String[]... rows) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet s = wb.createSheet("Accounts_Import");
            String[] header = {"email", "password", "full_name", "role"};
            Row h = s.createRow(0);
            for (int c = 0; c < header.length; c++) h.createCell(c).setCellValue(header[c]);
            for (int r = 0; r < rows.length; r++) {
                Row row = s.createRow(r + 1);
                for (int c = 0; c < rows[r].length; c++) row.createCell(c).setCellValue(rows[r][c]);
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    static void expectError(ResultActions r, int http, String code, String message) throws Exception {
        r.andExpect(status().is(http)).andExpect(jsonPath("$.code").value(code)).andExpect(jsonPath("$.message").value(message));
    }

    JsonNode read(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    Account reload(Account a) {
        return accounts.findById(a.getId()).orElseThrow();
    }

    boolean hasAudit(Account a, String eventType) {
        return auditLogs.findByAccount_IdOrderByCreatedAtDesc(a.getId()).stream().anyMatch(l -> eventType.equals(l.getEventType()));
    }

    List<String> emailsIn(JsonNode page) {
        List<String> out = new ArrayList<>();
        page.at("/data/content").forEach(n -> out.add(n.get("email").asText()));
        return out;
    }

    // ---------- [2.1] GET /api/accounts/me ----------

    @Test
    @DisplayName("ITC001 Xem hồ sơ cá nhân khi đã đăng nhập")
    void itc001_viewMyProfile() throws Exception {
        call(get("/api/accounts/me"), U, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(U))
                .andExpect(jsonPath("$.data.fullName").value(user.getFullName()))
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test
    @DisplayName("ITC002 Xem hồ sơ khi chưa đăng nhập")
    void itc002_viewProfileWithoutToken() throws Exception {
        expectError(mvc.perform(get("/api/accounts/me")), 401, "ERR_401", MSG_401);
    }

    // ---------- [2.2] GET /api/accounts/me/audit-logs ----------

    @Test
    @DisplayName("ITC003 Xem lịch sử đăng nhập và hoạt động của chính mình")
    void itc003_myAuditLogs() throws Exception {
        JsonNode logs = read(call(get("/api/accounts/me/audit-logs"), U, null).andExpect(status().isOk())).at("/data");
        assertThat(logs.size()).isPositive();
        assertThat(logs.get(0).get("eventType").asText()).isEqualTo("LOGIN_PASSWORD");
        assertThat(logs.get(0).get("success").asBoolean()).isTrue();
        assertThat(logs.get(0).has("createdAt")).isTrue();
        long own = auditLogs.findByAccount_IdOrderByCreatedAtDesc(user.getId()).size();
        assertThat(logs.size()).isEqualTo((int) Math.min(own, 30));
        for (int i = 1; i < logs.size(); i++) {
            assertThat(OffsetDateTime.parse(logs.get(i - 1).get("createdAt").asText()))
                    .isAfterOrEqualTo(OffsetDateTime.parse(logs.get(i).get("createdAt").asText()));
        }
    }

    // ---------- [2.3] PUT /api/accounts/me ----------

    @Test
    @DisplayName("ITC004 Cập nhật số điện thoại hợp lệ")
    void itc004_updatePhone() throws Exception {
        call(put("/api/accounts/me"), U, body("phone", "0912345678"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Cập nhật hồ sơ cá nhân thành công"))
                .andExpect(jsonPath("$.data.phone").value("0912345678"));
        assertThat(reload(user).getPhone()).isEqualTo("0912345678");
        assertThat(hasAudit(user, "PROFILE_UPDATED")).isTrue();
    }

    @Test
    @DisplayName("ITC005 Cập nhật số điện thoại sai định dạng")
    void itc005_phoneBadFormat() throws Exception {
        expectError(call(put("/api/accounts/me"), U, body("phone", "abc123")), 400, "ERR_400", MSG_PHONE);
        assertThat(reload(user).getPhone()).isNull();
    }

    @Test
    @DisplayName("ITC006 Số điện thoại +84 được nhận, số bắt đầu bằng 0 có 11 chữ số bị từ chối")
    void itc006_phoneElevenDigits() throws Exception {
        call(put("/api/accounts/me"), U, body("phone", "+84912345678"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phone").value("+84912345678"));
        call(put("/api/accounts/me"), U, body("phone", "09123456789")).andExpect(status().isBadRequest());
        assertThat(reload(user).getPhone()).isEqualTo("+84912345678");
    }

    @Test
    @DisplayName("ITC007 USER tự đổi role của mình")
    void itc007_userChangesOwnRole() throws Exception {
        expectError(call(put("/api/accounts/me"), U, body("role", "ADMIN")), 403, "ERR_403",
                "Chỉ có ADMIN mới được phép thay đổi Role (Vai trò)");
        assertThat(reload(user).getRole()).isEqualTo(AccountRole.USER);
    }

    @Test
    @DisplayName("ITC008 Đổi mật khẩu thành công")
    void itc008_changePasswordSuccess() throws Exception {
        String t = token(U);
        user.setMustChangePassword(true);
        accounts.saveAndFlush(user);
        mvc.perform(put("/api/accounts/me").header("Authorization", "Bearer " + t).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body("currentPassword", PW, "newPassword", NEW_PW))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mustChangePassword").value(false));
        assertThat(hasAudit(user, "PROFILE_UPDATED")).isTrue();
        login(U, PW).andExpect(status().isUnauthorized());
        login(U, NEW_PW).andExpect(status().isOk());
    }

    @Test
    @DisplayName("ITC009 Đổi mật khẩu với mật khẩu hiện tại sai")
    void itc009_wrongCurrentPassword() throws Exception {
        expectError(call(put("/api/accounts/me"), U, body("currentPassword", "Wrong@123", "newPassword", NEW_PW)),
                401, "AUTH_401", "Mật khẩu hiện tại không chính xác");
        assertThat(encoder.matches(PW, reload(user).getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("ITC010 Đổi mật khẩu khi không nhập mật khẩu hiện tại")
    void itc010_missingCurrentPassword() throws Exception {
        ResultActions r = call(put("/api/accounts/me"), U, body("newPassword", NEW_PW));
        assertThat(encoder.matches(PW, reload(user).getPasswordHash())).as("mật khẩu không được đổi").isTrue();
        r.andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("ITC011 Mật khẩu mới trùng mật khẩu cũ")
    void itc011_newPasswordSameAsOld() throws Exception {
        call(put("/api/accounts/me"), U, body("currentPassword", PW, "newPassword", PW))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Mật khẩu mới phải khác mật khẩu hiện tại"));
    }

    @Test
    @DisplayName("ITC012 Mật khẩu mới sai quy tắc")
    void itc012_weakNewPassword() throws Exception {
        expectError(call(put("/api/accounts/me"), U, body("currentPassword", PW, "newPassword", "Abcd1234")), 400, "ERR_400", MSG_NEW_PW);
        assertThat(encoder.matches(PW, reload(user).getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("ITC013 Đổi mật khẩu thu hồi refresh token cũ")
    void itc013_changePasswordRevokesRefreshTokens() throws Exception {
        call(put("/api/accounts/me"), U, body("currentPassword", PW, "newPassword", NEW_PW)).andExpect(status().isOk());
        assertThat(refreshTokens.findByAccount_IdAndRevokedAtIsNull(user.getId())).as("refresh token còn hiệu lực").isEmpty();
    }

    // ---------- [2.4] GET /api/accounts ----------

    @Test
    @DisplayName("ITC014 Manager xem danh sách tài khoản có phân trang")
    void itc014_listPaged() throws Exception {
        call(get("/api/accounts").param("page", "1").param("size", "5"), M, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(5))
                .andExpect(jsonPath("$.data.content.length()").value(org.hamcrest.Matchers.lessThanOrEqualTo(5)))
                .andExpect(jsonPath("$.data.totalElements").isNumber())
                .andExpect(jsonPath("$.data.totalPages").isNumber())
                .andExpect(jsonPath("$.data.activeCount").isNumber())
                .andExpect(jsonPath("$.data.suspendedCount").isNumber())
                .andExpect(jsonPath("$.data.deletedCount").isNumber());
    }

    @Test
    @DisplayName("ITC015 Tìm kiếm tài khoản theo họ tên hoặc email")
    void itc015_searchByNameOrEmail() throws Exception {
        user.setFullName("Nguyen Tim Kiem Zeta");
        accounts.saveAndFlush(user);
        assertThat(emailsIn(read(call(get("/api/accounts").param("keyword", "tim kiem zeta"), M, null).andExpect(status().isOk()))))
                .containsExactly(U);
        assertThat(emailsIn(read(call(get("/api/accounts").param("keyword", "sagasuusertest01"), M, null).andExpect(status().isOk()))))
                .containsExactly(U);
    }

    @Test
    @DisplayName("ITC016 Tìm kiếm tài khoản theo số điện thoại")
    void itc016_searchByPhone() throws Exception {
        user.setPhone("0938271605");
        accounts.saveAndFlush(user);
        assertThat(emailsIn(read(call(get("/api/accounts").param("keyword", "0938271605"), M, null).andExpect(status().isOk()))))
                .containsExactly(U);
    }

    @Test
    @DisplayName("ITC017 Lọc danh sách theo trạng thái")
    void itc017_filterByStatus() throws Exception {
        Account s = account(U2, AccountRole.USER);
        s.setStatus(AccountStatus.SUSPENDED);
        accounts.saveAndFlush(s);
        JsonNode page = read(call(get("/api/accounts").param("status", "SUSPENDED").param("size", "100"), M, null).andExpect(status().isOk()));
        page.at("/data/content").forEach(n -> assertThat(n.get("status").asText()).isEqualTo("SUSPENDED"));
        assertThat(emailsIn(page)).contains(U2);
    }

    @Test
    @DisplayName("ITC018 Tìm kiếm không có kết quả")
    void itc018_searchNoResult() throws Exception {
        call(get("/api/accounts").param("keyword", "khongtontai_xyz_999"), M, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isEmpty())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    @DisplayName("ITC019 USER không được xem danh sách tài khoản")
    void itc019_userCannotList() throws Exception {
        expectError(call(get("/api/accounts"), U, null), 403, "ERR_403", MSG_403);
    }

    // ---------- [2.5] GET /api/accounts/{id} ----------

    @Test
    @DisplayName("ITC020 Xem chi tiết tài khoản theo ID")
    void itc020_getById() throws Exception {
        call(get("/api/accounts/" + user.getId()), M, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(user.getId()))
                .andExpect(jsonPath("$.data.email").value(U))
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("ITC021 Xem chi tiết với ID không tồn tại")
    void itc021_getByIdNotFound() throws Exception {
        expectError(call(get("/api/accounts/999999999"), M, null), 404, "ACC_404", "Không tìm thấy tài khoản");
    }

    // ---------- [2.6] POST /api/accounts ----------

    Map<String, Object> newAccount(String email) {
        return body("fullName", "Khach Moi Test", "email", email, "role", "USER",
                "validUntil", OffsetDateTime.now().plusMonths(3).toString());
    }

    @Test
    @DisplayName("ITC022 Manager tạo tài khoản USER không nhập mật khẩu")
    void itc022_createUserWithoutPassword() throws Exception {
        JsonNode d = read(call(post("/api/accounts"), M, newAccount(NEW))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Tạo tài khoản thành công"))
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.mustChangePassword").value(true))).at("/data");
        String generated = d.get("generatedPassword").asText();
        assertThat(generated).isNotBlank();
        assertThat(accounts.findByEmailIgnoreCase(NEW)).isPresent();
        login(NEW, generated).andExpect(status().isOk());
    }

    @Test
    @DisplayName("ITC023 Gửi email mật khẩu ban đầu cho tài khoản mới")
    void itc023_createSendsPasswordEmail() throws Exception {
        call(post("/api/accounts"), M, newAccount(NEW)).andExpect(status().isOk());
        assertThat(mockingDetails(mailSender).getInvocations()).as("email gửi mật khẩu").isNotEmpty();
    }

    @Test
    @DisplayName("ITC024 Tạo tài khoản với email đã tồn tại")
    void itc024_createDuplicateEmail() throws Exception {
        long before = accounts.count();
        expectError(call(post("/api/accounts"), M, newAccount(U)), 409, "ACC_409", MSG04);
        assertThat(accounts.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("ITC025 Tạo tài khoản với email sai định dạng")
    void itc025_createBadEmail() throws Exception {
        expectError(call(post("/api/accounts"), M, newAccount("user01@")), 400, "ERR_400",
                "Định dạng Email không hợp lệ (VD: user01@gmail.com)");
    }

    @Test
    @DisplayName("ITC026 Tạo tài khoản với mật khẩu sai quy tắc")
    void itc026_createWeakPassword() throws Exception {
        Map<String, Object> b = newAccount(NEW);
        b.put("password", "Abcd1234");
        expectError(call(post("/api/accounts"), M, b), 400, "ERR_400", MSG05);
    }

    @Test
    @DisplayName("ITC027 Tạo tài khoản bỏ trống họ tên")
    void itc027_createBlankName() throws Exception {
        Map<String, Object> b = newAccount(NEW);
        b.put("fullName", "");
        call(post("/api/accounts"), M, b)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ERR_400"))
                .andExpect(jsonPath("$.message", containsString("Tên người dùng không được để trống")));
    }

    @Test
    @DisplayName("ITC028 Manager tạo tài khoản role khác USER")
    void itc028_managerCreatesApprover() throws Exception {
        Map<String, Object> b = newAccount(NEW);
        b.put("role", "APPROVER");
        expectError(call(post("/api/accounts"), M, b), 403, "ERR_403", "Chỉ có ADMIN mới được quyền thiết lập Role khác USER");
        assertThat(accounts.findByEmailIgnoreCase(NEW)).isEmpty();
    }

    @Test
    @DisplayName("ITC029 Admin tạo tài khoản Office Staff (APPROVER)")
    void itc029_adminCreatesApprover() throws Exception {
        Map<String, Object> b = newAccount(NEW);
        b.put("role", "APPROVER");
        b.put("password", PW);
        call(post("/api/accounts"), A, b)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Tạo tài khoản thành công"))
                .andExpect(jsonPath("$.data.role").value("APPROVER"));
    }

    @Test
    @DisplayName("ITC030 Tạo tài khoản với thời hạn hiệu lực ở quá khứ")
    void itc030_createValidUntilInPast() throws Exception {
        Map<String, Object> b = newAccount(NEW);
        b.put("validUntil", OffsetDateTime.now().minusDays(1).toString());
        ResultActions r = call(post("/api/accounts"), M, b);
        assertThat(accounts.findByEmailIgnoreCase(NEW)).as("không được tạo tài khoản").isEmpty();
        r.andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Thời hạn hiệu lực phải lớn hơn ngày hiện tại"));
    }

    @Test
    @DisplayName("ITC031 Ghi nhật ký khi tạo tài khoản")
    void itc031_createWritesAuditLog() throws Exception {
        String t = token(M);
        long before = auditLogs.count();
        mvc.perform(post("/api/accounts").header("Authorization", "Bearer " + t).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(newAccount(NEW)))).andExpect(status().isOk());
        assertThat(auditLogs.count()).as("số dòng nhật ký").isGreaterThan(before);
    }

    // ---------- [2.7] PUT /api/accounts/{id} ----------

    @Test
    @DisplayName("ITC032 Manager cập nhật và gia hạn tài khoản USER")
    void itc032_updateAndExtend() throws Exception {
        OffsetDateTime until = OffsetDateTime.now().plusMonths(6).withNano(0);
        call(put("/api/accounts/" + user.getId()), M, body("fullName", "Ten Moi Test", "phone", "0901234567", "validUntil", until.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Cập nhật tài khoản thành công"))
                .andExpect(jsonPath("$.data.fullName").value("Ten Moi Test"))
                .andExpect(jsonPath("$.data.phone").value("0901234567"))
                .andExpect(jsonPath("$.data.validUntil").isNotEmpty());
        Account a = reload(user);
        assertThat(a.getFullName()).isEqualTo("Ten Moi Test");
        assertThat(a.getPhone()).isEqualTo("0901234567");
        assertThat(a.getValidUntil().toInstant()).isEqualTo(until.toInstant());
    }

    @Test
    @DisplayName("ITC033 Gia hạn với ngày ở quá khứ")
    void itc033_extendWithPastDate() throws Exception {
        ResultActions r = call(put("/api/accounts/" + user.getId()), M, body("validUntil", OffsetDateTime.now().minusDays(1).toString()));
        assertThat(reload(user).getValidUntil()).as("valid_until không đổi").isNull();
        r.andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Ngày gia hạn phải ở tương lai"));
    }

    @Test
    @DisplayName("ITC034 Manager đổi role của tài khoản")
    void itc034_managerChangesRole() throws Exception {
        expectError(call(put("/api/accounts/" + user.getId()), M, body("role", "APPROVER")), 403, "ERR_403",
                "Chỉ có ADMIN mới được phép thay đổi Role (Vai trò) của tài khoản");
        assertThat(reload(user).getRole()).isEqualTo(AccountRole.USER);
    }

    @Test
    @DisplayName("ITC035 Manager sửa tài khoản MANAGER khác")
    void itc035_managerEditsManager() throws Exception {
        Account other = account(M2, AccountRole.MANAGER);
        expectError(call(put("/api/accounts/" + other.getId()), M, body("fullName", "Doi Ten Test")), 403, "ERR_403", MSG_MANAGER_SCOPE);
    }

    @Test
    @DisplayName("ITC036 Cập nhật email trùng tài khoản khác")
    void itc036_updateDuplicateEmail() throws Exception {
        account(U2, AccountRole.USER);
        expectError(call(put("/api/accounts/" + user.getId()), M, body("email", U2)), 409, "ACC_409", MSG04);
    }

    @Test
    @DisplayName("ITC037 Khóa tài khoản: thu hồi phiên và chặn đăng nhập")
    void itc037_suspendRevokesAndBlocks() throws Exception {
        login(U, PW).andExpect(status().isOk());
        assertThat(refreshTokens.findByAccount_IdAndRevokedAtIsNull(user.getId())).isNotEmpty();
        call(put("/api/accounts/" + user.getId()), M, body("status", "SUSPENDED", "reason", "Vi phạm nội quy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUSPENDED"));
        assertThat(refreshTokens.findByAccount_IdAndRevokedAtIsNull(user.getId())).isEmpty();
        login(U, PW).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACC_403_LOCKED"));
    }

    @Test
    @DisplayName("ITC038 Khóa tài khoản không nhập lý do")
    void itc038_suspendWithoutReason() throws Exception {
        ResultActions r = call(put("/api/accounts/" + user.getId()), M, body("status", "SUSPENDED"));
        assertThat(reload(user).getStatus()).as("status không đổi").isEqualTo(AccountStatus.ACTIVE);
        r.andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Vui lòng nhập lý do khóa tài khoản"));
    }

    @Test
    @DisplayName("ITC039 Manager tự khóa tài khoản của mình")
    void itc039_managerSuspendsSelf() throws Exception {
        expectError(call(put("/api/accounts/" + manager.getId()), M, body("status", "SUSPENDED")), 403, "ERR_403", MSG_MANAGER_SCOPE);
        assertThat(reload(manager).getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    @DisplayName("ITC040 Mở khóa tài khoản đặt lại số lần đăng nhập sai")
    void itc040_reactivateResetsFailedCount() throws Exception {
        user.setStatus(AccountStatus.SUSPENDED);
        user.setFailedLoginCount(5);
        accounts.saveAndFlush(user);
        call(put("/api/accounts/" + user.getId()), M, body("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.failedLoginCount").value(0));
        assertThat(reload(user).getFailedLoginCount()).isZero();
    }

    // ---------- [2.8] DELETE /api/accounts/{id} ----------

    @Test
    @DisplayName("ITC041 Manager xóa mềm tài khoản USER")
    void itc041_softDelete() throws Exception {
        login(U, PW).andExpect(status().isOk());
        call(delete("/api/accounts/" + user.getId()), M, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đã xóa tài khoản thành công"))
                .andExpect(jsonPath("$.data.status").value("DELETED"));
        assertThat(reload(user).getStatus()).isEqualTo(AccountStatus.DELETED);
        assertThat(refreshTokens.findByAccount_IdAndRevokedAtIsNull(user.getId())).isEmpty();
    }

    @Test
    @DisplayName("ITC042 Tự xóa tài khoản đang đăng nhập")
    void itc042_deleteSelf() throws Exception {
        expectError(call(delete("/api/accounts/" + manager.getId()), M, null), 400, "ERR_400",
                "Không thể tự xóa tài khoản đang đăng nhập của chính mình");
    }

    @Test
    @DisplayName("ITC043 Manager xóa tài khoản ADMIN")
    void itc043_managerDeletesAdmin() throws Exception {
        expectError(call(delete("/api/accounts/" + admin.getId()), M, null), 403, "ERR_403", MSG_MANAGER_SCOPE);
        assertThat(reload(admin).getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    // ---------- [2.9] POST /api/accounts/import ----------

    @Test
    @DisplayName("ITC044 Import file Excel hợp lệ")
    void itc044_importValid() throws Exception {
        upload(A, xlsx(new String[]{NEW, PW, "Khach Import 1", "USER"}, new String[]{NEW2, PW, "Khach Import 2", "USER"}))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đã xử lý import file Excel vào database"))
                .andExpect(jsonPath("$.data.totalRows").value(2))
                .andExpect(jsonPath("$.data.successCount").value(2))
                .andExpect(jsonPath("$.data.failedCount").value(0));
        assertThat(accounts.findByEmailIgnoreCase(NEW)).isPresent();
        assertThat(accounts.findByEmailIgnoreCase(NEW2)).isPresent();
    }

    @Test
    @DisplayName("ITC045 Import dòng thiếu email và dòng role sai")
    void itc045_importMissingEmailAndBadRole() throws Exception {
        upload(A, xlsx(new String[]{"", PW, "Khach Thieu Email", "USER"}, new String[]{NEW, PW, "Khach Role Sai", "BOSS"}))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.failedCount").value(2))
                .andExpect(jsonPath("$.data.errors[0]").value("Dòng 2: Cột email/gmail không được để trống"))
                .andExpect(jsonPath("$.data.errors[1]").value("Dòng 3: Role không hợp lệ: BOSS"));
    }

    @Test
    @DisplayName("ITC046 Import dòng email đã tồn tại và dòng mật khẩu sai quy tắc")
    void itc046_importDuplicateEmailAndWeakPassword() throws Exception {
        upload(A, xlsx(new String[]{U, PW, "Khach Trung Email", "USER"}, new String[]{NEW, "Abcd1234", "Khach Mat Khau Sai", "USER"}))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.failedCount").value(2))
                .andExpect(jsonPath("$.data.errors[0]").value("Dòng 2: Email '" + U + "' đã tồn tại trong DB"))
                .andExpect(jsonPath("$.data.errors[1]").value("Dòng 3: Mật khẩu phải dài từ 8 đến 16 ký tự và chứa ít nhất 1 ký tự đặc biệt"));
    }

    @Test
    @DisplayName("ITC047 Manager import tài khoản có role khác USER")
    void itc047_managerImportsApprover() throws Exception {
        upload(M, xlsx(new String[]{NEW, PW, "Khach Approver", "APPROVER"}))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.successCount").value(1));
        assertThat(accounts.findByEmailIgnoreCase(NEW).orElseThrow().getRole()).isEqualTo(AccountRole.USER);
    }

    @Test
    @DisplayName("ITC048 Import file rỗng")
    void itc048_importEmptyFile() throws Exception {
        expectError(upload(A, new byte[0]), 400, "ERR_400", "Vui lòng chọn file Excel (.xlsx hoặc .xls) để tải lên");
    }

    @Test
    @DisplayName("ITC049 USER không được import tài khoản")
    void itc049_userCannotImport() throws Exception {
        expectError(upload(U, xlsx(new String[]{NEW, PW, "Khach Import", "USER"})), 403, "ERR_403", MSG_403);
        assertThat(accounts.findByEmailIgnoreCase(NEW)).isEmpty();
    }

    // ---------- [2.10] GET /api/accounts/import/template ----------

    @Test
    @DisplayName("ITC050 Tải file Excel mẫu")
    void itc050_downloadTemplate() throws Exception {
        byte[] file = call(get("/api/accounts/import/template"), A, null)
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("accounts_import_template.xlsx")))
                .andReturn().getResponse().getContentAsByteArray();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(file))) {
            Row h = wb.getSheetAt(0).getRow(0);
            List<String> cols = new ArrayList<>();
            for (int c = 0; c < 4; c++) cols.add(h.getCell(c).getStringCellValue());
            assertThat(cols).containsExactly("email", "password", "full_name", "role");
        }
    }

    @Test
    @DisplayName("ITC051 Tải file mẫu khi chưa đăng nhập")
    void itc051_templateWithoutLogin() throws Exception {
        expectError(mvc.perform(get("/api/accounts/import/template")), 401, "ERR_401", MSG_401);
    }
}
