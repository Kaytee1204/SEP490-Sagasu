package com.example.base.integration.account;

import com.example.base.entity.Account;
import com.example.base.entity.AccountRole;
import com.example.base.entity.AccountStatus;
import com.example.base.entity.RefreshToken;
import com.example.base.integration.ScenarioTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * Report-5.2 (scenario style) sheet "Account Management": TC-ACC-01 .. TC-ACC-12, per SRS 2.0 v2 UC-06..UC-12, GBR-01..GBR-07.
 * Main flows that need the real stack (DB, security config, refresh tokens, mail, Excel upload).
 * Field validation (phone / password / e-mail format, missing current password, Manager role scope, create with a past date)
 * is covered by Report-5.1 Account UTC039-UTC085.
 */
class AccountScenarioIntegrationTest extends ScenarioTestBase {

    static final String NEW1 = "sagasunewtest01@gmail.com";
    static final String NEW2 = "sagasunewtest02@gmail.com";
    static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    static final String MSG_DUP = "Email/Tài khoản đã tồn tại trong hệ thống";
    static final String MSG_SCOPE = "Tài khoản MANAGER chỉ được phép quản lý các tài khoản có role: USER, APPROVER";
    static final String MSG_SUSPENDED = "Tài khoản đang bị đình chỉ. Liên hệ văn phòng để được hỗ trợ.";
    static final String MSG_PROFILE_OK = "Cập nhật hồ sơ cá nhân thành công";
    static final String MSG_SAME_PW = "Mật khẩu mới phải khác mật khẩu hiện tại";

    Account user01, user02, staff01, manager01, admin01;

    @BeforeEach
    void accounts() {
        user01 = account(USER01, AccountRole.USER);
        user02 = account(USER02, AccountRole.USER);
        staff01 = account(STAFF01, AccountRole.APPROVER);
        manager01 = account(MANAGER01, AccountRole.MANAGER);
        admin01 = account(ADMIN01, AccountRole.ADMIN);
    }

    Res upload(String token, byte[] bytes) throws Exception {
        return send(multipart("/api/accounts/import").file(new MockMultipartFile("file", "accounts.xlsx", XLSX, bytes)), token, null);
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

    static List<String> emails(Res page) {
        List<String> out = new ArrayList<>();
        page.body().at("/data/content").forEach(n -> out.add(n.get("email").asText()));
        return out;
    }

    Map<String, Object> newAccount(String email) {
        return body("fullName", "Nguyen Van Test", "email", email, "role", "USER",
                "validUntil", OffsetDateTime.now().plusMonths(1).toString());
    }

    // ---------- View & Update My Profile ----------

    @Test
    @DisplayName("TC-ACC-01 User views own profile and activity history")
    void tcAcc01_viewProfileAndHistory() throws Exception {
        String t = token(USER01);
        Res me = send(get("/api/accounts/me"), t, null);
        expect("Step 1 profile", me, 200, null, null);
        soft.assertThat(me.text("/data/email")).as("email").isEqualTo(USER01);
        soft.assertThat(me.text("/data/role")).as("role").isEqualTo("USER");
        soft.assertThat(me.text("/data/status")).as("status").isEqualTo("ACTIVE");
        soft.assertThat(me.body().at("/data/passwordHash").isMissingNode()).as("no password").isTrue();
        Res logs = send(get("/api/accounts/me/audit-logs"), t, null);
        expect("Step 2 activity", logs, 200, null, null);
        JsonNode list = logs.body().at("/data");
        soft.assertThat(list.size()).as("max 30 rows").isBetween(1, 30);
        boolean login = false;
        for (JsonNode n : list) login |= "LOGIN_PASSWORD".equals(n.get("eventType").asText()) && n.get("success").asBoolean();
        soft.assertThat(login).as("has LOGIN_PASSWORD success").isTrue();
        for (int i = 1; i < list.size(); i++)
            soft.assertThat(OffsetDateTime.parse(list.get(i - 1).get("createdAt").asText()))
                    .as("newest first").isAfterOrEqualTo(OffsetDateTime.parse(list.get(i).get("createdAt").asText()));
        expect("Step 3 profile without token", send(get("/api/accounts/me"), null, null), 401, "ERR_401", MSG_401);
        expect("Step 3 audit-logs without token", send(get("/api/accounts/me/audit-logs"), null, null), 401, "ERR_401", MSG_401);
    }

    @Test
    @DisplayName("TC-ACC-02 User updates the phone number and sees it on the profile")
    void tcAcc02_updatePhone() throws Exception {
        String t = token(USER01);
        Res r = send(put("/api/accounts/me"), t, body("phone", "0912345678"));
        expect("Step 1 0912345678", r, 200, null, MSG_PROFILE_OK);
        soft.assertThat(reload(user01).getPhone()).as("Step 1 phone saved").isEqualTo("0912345678");
        soft.assertThat(hasAudit(user01, "PROFILE_UPDATED", true)).as("Step 1 audit PROFILE_UPDATED").isTrue();
        expect("Step 2 +84912345678", send(put("/api/accounts/me"), t, body("phone", "+84912345678")), 200, null, MSG_PROFILE_OK);
        soft.assertThat(send(get("/api/accounts/me"), t, null).text("/data/phone")).as("Step 3 profile shows new phone").isEqualTo("+84912345678");
    }

    @Test
    @DisplayName("TC-ACC-03 User changes the password with the current password; all sessions are signed out")
    void tcAcc03_changePassword() throws Exception {
        String t = token(USER01);
        login(USER01, PW);
        List<Long> old = activeTokens(user01).stream().map(RefreshToken::getId).toList();
        user01.setMustChangePassword(true);
        save(user01);
        expect("Step 1 new = current password (UC-08 E2)", send(put("/api/accounts/me"), t, body("currentPassword", PW, "newPassword", PW)),
                400, "ERR_400", MSG_SAME_PW);
        Res r = send(put("/api/accounts/me"), t, body("currentPassword", PW, "newPassword", NEW_PW));
        expect("Step 2 save", r, 200, null, MSG_PROFILE_OK);
        soft.assertThat(reload(user01).isMustChangePassword()).as("Step 2 mustChangePassword").isFalse();
        soft.assertThat(hasAudit(user01, "PROFILE_UPDATED", true)).as("Step 2 audit PROFILE_UPDATED").isTrue();
        expect("Step 3 old password", login(USER01, PW), 401, "AUTH_401", null);
        expect("Step 3 new password", login(USER01, NEW_PW), 200, null, null);
        soft.assertThat(refreshTokens.findAllById(old)).as("Step 4 old refresh tokens revoked (UC-08 step 7)").allMatch(x -> x.getRevokedAt() != null);
    }

    // ---------- View & Search Accounts ----------

    @Test
    @DisplayName("TC-ACC-04 Manager views, searches and filters the account list")
    void tcAcc04_listSearchFilter() throws Exception {
        String t = token(MANAGER01);
        user02.setFullName("Zeta Unique Testname");
        user02.setPhone("0938271605");
        save(user02);
        Account susp = account(USER03, AccountRole.USER);
        susp.setStatus(AccountStatus.SUSPENDED);
        save(susp);
        Res list = send(get("/api/accounts"), t, null);
        expect("Step 1 list", list, 200, null, null);
        for (String f : new String[]{"activeCount", "suspendedCount", "deletedCount", "totalElements"})
            soft.assertThat(list.body().at("/data/" + f).isNumber()).as("Step 1 counter " + f).isTrue();
        Res p2 = send(get("/api/accounts").param("page", "2").param("size", "5"), t, null);
        expect("Step 2 page 2 size 5", p2, 200, null, null);
        soft.assertThat(p2.body().at("/data/page").asInt()).as("Step 2 page").isEqualTo(2);
        soft.assertThat(p2.body().at("/data/content").size()).as("Step 2 rows").isBetween(0, 5);
        soft.assertThat(emails(send(get("/api/accounts").param("keyword", "Zeta Unique"), t, null))).as("Step 3 by name").containsExactly(USER02);
        soft.assertThat(emails(send(get("/api/accounts").param("keyword", "sagasuusertest02"), t, null))).as("Step 3 by e-mail").containsExactly(USER02);
        soft.assertThat(emails(send(get("/api/accounts").param("keyword", "0938271605"), t, null))).as("Step 3 by phone (UC-09 step 4)").containsExactly(USER02);
        Res s = send(get("/api/accounts").param("status", "SUSPENDED").param("size", "100"), t, null);
        s.body().at("/data/content").forEach(n -> soft.assertThat(n.get("status").asText()).as("Step 4 status filter").isEqualTo("SUSPENDED"));
        soft.assertThat(emails(s)).as("Step 4 suspended account listed").contains(USER03);
        Res none = send(get("/api/accounts").param("keyword", "khongtontai_xyz_999"), t, null);
        soft.assertThat(none.body().at("/data/totalElements").asInt()).as("Step 4 unknown keyword (UC-09 E1)").isZero();
    }

    @Test
    @DisplayName("TC-ACC-05 Manager opens an account detail; unknown ID and other roles are denied")
    void tcAcc05_detailAndAuthorization() throws Exception {
        String t = token(MANAGER01);
        Res d = send(get("/api/accounts/" + user02.getId()), t, null);
        expect("Step 1 detail", d, 200, null, null);
        soft.assertThat(d.text("/data/email")).as("Step 1 email").isEqualTo(USER02);
        soft.assertThat(d.text("/data/role")).as("Step 1 role").isEqualTo("USER");
        soft.assertThat(d.text("/data/status")).as("Step 1 status").isEqualTo("ACTIVE");
        expect("Step 2 unknown id", send(get("/api/accounts/999999999"), t, null), 404, "ACC_404", "Không tìm thấy tài khoản");
        expect("Step 3 user01", send(get("/api/accounts"), token(USER01), null), 403, "ERR_403", MSG_403);
        expect("Step 3 staff01", send(get("/api/accounts"), token(STAFF01), null), 403, "ERR_403", MSG_403);
        expect("Step 3 no token", send(get("/api/accounts"), null, null), 401, "ERR_401", MSG_401);
    }

    // ---------- Create Account ----------

    @Test
    @DisplayName("TC-ACC-06 Manager creates a USER account; the user receives the initial password and logs in")
    void tcAcc06_createWithoutPassword() throws Exception {
        String t = token(MANAGER01);
        long auditBefore = auditLogs.count();
        int mailBefore = mailCount();
        Res r = send(post("/api/accounts"), t, newAccount(NEW1));
        expect("Step 2 create", r, 200, null, "Tạo tài khoản thành công");
        soft.assertThat(r.text("/data/status")).as("Step 2 status").isEqualTo("ACTIVE");
        soft.assertThat(r.text("/data/role")).as("Step 2 role").isEqualTo("USER");
        soft.assertThat(r.text("/data/mustChangePassword")).as("Step 2 mustChangePassword").isEqualTo("true");
        String generated = r.text("/data/generatedPassword");
        soft.assertThat(generated).as("Step 2 generatedPassword").isNotBlank();
        soft.assertThat(mailCount()).as("Step 3 e-mail with initial password sent (UC-10 step 6)").isGreaterThan(mailBefore);
        soft.assertThat(auditLogs.count()).as("Step 3 audit row for account creation (UC-10 Postcondition 3)").isGreaterThan(auditBefore);
        if (generated != null) expect("Step 4 login with generated password", login(NEW1, generated), 200, null, null);
        expect("Step 5 same e-mail again (UC-10 E1)", send(post("/api/accounts"), t, newAccount(NEW1.toUpperCase())), 409, "ACC_409", MSG_DUP);
    }

    // ---------- Update, Extend & Suspend Account ----------

    @Test
    @DisplayName("TC-ACC-07 Manager updates and extends a USER account")
    void tcAcc07_updateAndExtend() throws Exception {
        String t = token(MANAGER01);
        OffsetDateTime until = OffsetDateTime.now().plusYears(1).withNano(0);
        Res r = send(put("/api/accounts/" + user02.getId()), t, body("fullName", "Ten Moi Test", "phone", "0901234567", "validUntil", until.toString()));
        expect("Step 1 update", r, 200, null, "Cập nhật tài khoản thành công");
        Account a = reload(user02);
        soft.assertThat(a.getFullName()).as("Step 1 name").isEqualTo("Ten Moi Test");
        soft.assertThat(a.getPhone()).as("Step 1 phone").isEqualTo("0901234567");
        soft.assertThat(a.getValidUntil()).as("Step 1 valid_until").isNotNull();
        OffsetDateTime saved = a.getValidUntil();
        Res past = send(put("/api/accounts/" + user02.getId()), t, body("validUntil", OffsetDateTime.now().minusDays(1).toString()));
        expect("Step 2 valid-until yesterday (UC-11 E1)", past, 400, null, "Ngày gia hạn phải ở tương lai");
        soft.assertThat(reload(user02).getValidUntil()).as("Step 2 valid_until unchanged").isEqualTo(saved);
        expect("Step 3 duplicate e-mail", send(put("/api/accounts/" + user02.getId()), t, body("email", USER01)), 409, "ACC_409", MSG_DUP);
    }

    @Test
    @DisplayName("TC-ACC-08 Manager suspends an account with a reason; the user is signed out and blocked")
    void tcAcc08_suspendWithReason() throws Exception {
        login(USER02, PW);
        String t = token(MANAGER01);
        Res noReason = send(put("/api/accounts/" + user02.getId()), t, body("status", "SUSPENDED"));
        soft.assertThat(noReason.status()).as("Step 2 suspend without reason (UC-12 E1): HTTP status").isEqualTo(400);
        soft.assertThat(noReason.message()).as("Step 2 suspend without reason: message").isEqualTo("Vui lòng nhập lý do khóa tài khoản");
        soft.assertThat(reload(user02).getStatus()).as("Step 2 status unchanged without reason").isEqualTo(AccountStatus.ACTIVE);
        Account reset = reload(user02);
        reset.setStatus(AccountStatus.ACTIVE);
        save(reset);
        login(USER02, PW);
        Res withReason = send(put("/api/accounts/" + user02.getId()), t, body("status", "SUSPENDED", "reason", "Vi phạm nội quy"));
        expect("Step 2 suspend with reason", withReason, 200, null, "Cập nhật tài khoản thành công");
        soft.assertThat(reload(user02).getStatus()).as("Step 2 status").isEqualTo(AccountStatus.SUSPENDED);
        soft.assertThat(activeTokens(user02)).as("Step 3 refresh tokens revoked (UC-12 Postcondition 2)").isEmpty();
        expect("Step 3 login", login(USER02, PW), 403, "ACC_403_LOCKED", MSG_SUSPENDED);
    }

    @Test
    @DisplayName("TC-ACC-09 Manager reactivates a suspended account and the failed-login counter is reset")
    void tcAcc09_reactivate() throws Exception {
        for (int i = 0; i < 5; i++) login(USER02, PW + "x");
        soft.assertThat(reload(user02).getStatus()).as("Step 1 locked").isEqualTo(AccountStatus.SUSPENDED);
        Res r = send(put("/api/accounts/" + user02.getId()), token(MANAGER01), body("status", "ACTIVE"));
        expect("Step 2 reactivate", r, 200, null, "Cập nhật tài khoản thành công");
        Account a = reload(user02);
        soft.assertThat(a.getStatus()).as("Step 2 status").isEqualTo(AccountStatus.ACTIVE);
        soft.assertThat(a.getFailedLoginCount()).as("Step 2 failed_login_count reset (UC-12 Alt 2.1)").isZero();
        soft.assertThat(a.getLockedUntil()).as("Step 2 locked_until cleared").isNull();
        expect("Step 3 login", login(USER02, PW), 200, null, "Đăng nhập thành công");
    }

    // ---------- Soft Delete Account ----------

    @Test
    @DisplayName("TC-ACC-10 Manager soft-deletes a USER account; self-delete and deleting Admin are blocked")
    void tcAcc10_softDelete() throws Exception {
        login(USER02, PW);
        String t = token(MANAGER01);
        Res d = send(delete("/api/accounts/" + user02.getId()), t, null);
        expect("Step 2 delete", d, 200, null, "Đã xóa mềm tài khoản thành công");
        soft.assertThat(accounts.findById(user02.getId())).as("Step 2 row kept").isPresent();
        soft.assertThat(reload(user02).getStatus()).as("Step 2 status").isEqualTo(AccountStatus.DELETED);
        soft.assertThat(activeTokens(user02)).as("Step 2 refresh tokens revoked").isEmpty();
        expect("Step 3 login deleted", login(USER02, PW), 403, "ACC_403_DELETED", "Tài khoản đã bị xóa");
        expect("Step 4 self", send(delete("/api/accounts/" + manager01.getId()), t, null), 400, "ERR_400",
                "Không thể tự xóa tài khoản đang đăng nhập của chính mình");
        expect("Step 4 admin", send(delete("/api/accounts/" + admin01.getId()), t, null), 403, "ERR_403", MSG_SCOPE);
        soft.assertThat(reload(admin01).getStatus()).as("Step 4 admin unchanged").isEqualTo(AccountStatus.ACTIVE);
    }

    // ---------- Import Accounts From Excel ----------

    @Test
    @DisplayName("TC-ACC-11 Admin downloads the template and imports an Excel file with good and bad rows")
    void tcAcc11_templateAndImport() throws Exception {
        String t = token(ADMIN01);
        Res tpl = send(get("/api/accounts/import/template"), t, null);
        expect("Step 1 template", tpl, 200, null, null);
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(tpl.raw()))) {
            Sheet s = wb.getSheetAt(0);
            List<String> cols = new ArrayList<>();
            for (int c = 0; c < 4; c++) cols.add(s.getRow(0).getCell(c).getStringCellValue());
            soft.assertThat(cols).as("Step 1 header").containsExactly("email", "password", "full_name", "role");
        }
        Res r = upload(t, xlsx(
                new String[]{NEW1, PW, "Khach Hop Le 1", "USER"},
                new String[]{"", PW, "Khach Thieu Email", "USER"},
                new String[]{USER01, PW, "Khach Trung Email", "USER"},
                new String[]{NEW2, PW, "Khach Hop Le 2", "USER"}));
        expect("Step 2 import", r, 200, null, "Đã xử lý import file Excel vào database");
        soft.assertThat(r.text("/data/totalRows")).as("Step 2 totalRows").isEqualTo("4");
        soft.assertThat(r.text("/data/successCount")).as("Step 2 successCount").isEqualTo("2");
        soft.assertThat(r.text("/data/failedCount")).as("Step 2 failedCount").isEqualTo("2");
        List<String> errors = new ArrayList<>();
        if (r.body() != null) r.body().at("/data/errors").forEach(n -> errors.add(n.asText()));
        soft.assertThat(errors).as("Step 2 row errors").containsExactly(
                "Dòng 3: Cột email/gmail không được để trống",
                "Dòng 4: Email '" + USER01 + "' đã tồn tại trong DB");
        soft.assertThat(accounts.findByEmailIgnoreCase(NEW1)).as("Step 3 account 1").isPresent();
        soft.assertThat(accounts.findByEmailIgnoreCase(NEW2)).as("Step 3 account 2").isPresent();
    }

    @Test
    @DisplayName("TC-ACC-12 Manager imports only USER accounts; User and guests cannot import or download the template")
    void tcAcc12_importRoles() throws Exception {
        Res m = upload(token(MANAGER01), xlsx(new String[]{NEW1, PW, "Khach Approver", "APPROVER"}));
        expect("Step 1 manager import", m, 200, null, null);
        soft.assertThat(m.text("/data/successCount")).as("Step 1 successCount").isEqualTo("1");
        accounts.findByEmailIgnoreCase(NEW1).ifPresent(a -> soft.assertThat(a.getRole()).as("Step 1 role forced to USER").isEqualTo(AccountRole.USER));
        expect("Step 2 user import", upload(token(USER01), xlsx(new String[]{NEW2, PW, "Khach", "USER"})), 403, "ERR_403", MSG_403);
        soft.assertThat(accounts.findByEmailIgnoreCase(NEW2)).as("Step 2 no account").isEmpty();
        expect("Step 3 template without login", send(get("/api/accounts/import/template"), null, null), 401, "ERR_401", MSG_401);
    }
}
