package com.example.base.integration.category;

import com.example.base.entity.Account;
import com.example.base.entity.AccountRole;
import com.example.base.entity.AccountStatus;
import com.example.base.entity.Category;
import com.example.base.entity.Post;
import com.example.base.repository.AccountRepository;
import com.example.base.repository.CategoryRepository;
import com.example.base.repository.PostRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Report-5.2 sheet "Category Management": 4 APIs of Swagger group "03. Category Management".
 * Expected results follow SRS v2.0 (UC-27, UC-29, UC-30, Category entity rules, GBR-53) for behaviour;
 * messages follow the code (as in the Auth and Account sheets).
 * Real Spring context + DB sagasu_test; every test is rolled back.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CategoryManagementIntegrationTest {

    static final String PW = "Test@1234";
    static final String U = "sagasuusertest01@gmail.com";
    static final String S = "sagasuapprovertest01@gmail.com";
    static final String M = "sagasumanagertest01@gmail.com";
    static final String A = "sagasuadmintest01@gmail.com";
    static final String BASE = "/api/categories";
    static final long MISSING_ID = 999_999_999L;

    static final String MSG_401 = "Chưa xác thực hoặc phiên đăng nhập không hợp lệ";
    static final String MSG_403 = "Không có quyền truy cập";
    static final String MSG_BAD_BODY = "Dữ liệu gửi lên không hợp lệ hoặc thiếu phần data";
    static final String MSG_PAGE = "Trang phải từ 1; kích thước trang từ 1 đến 100";
    static final String MSG_BLANK = "Tên danh mục không được để trống";
    static final String MSG_MAX = "Tên danh mục tối đa 100 ký tự";
    static final String MSG_DUP = "Tên danh mục đã tồn tại, kể cả danh mục ngừng hoạt động. Hãy dùng hoặc kích hoạt lại danh mục đó.";
    static final String MSG_404 = "Không tìm thấy danh mục";
    static final String MSG_STATUS_REQUIRED = "Trạng thái hoạt động là bắt buộc";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AccountRepository accounts;
    @Autowired CategoryRepository categories;
    @Autowired PostRepository posts;
    @Autowired PasswordEncoder encoder;
    @Autowired EntityManager em;
    @MockBean JavaMailSender mailSender;

    Account user, staff, manager, admin;
    String tag;
    Category active1, active2, inactive;

    @BeforeEach
    void setup() {
        user = account(U, AccountRole.USER);
        staff = account(S, AccountRole.APPROVER);
        manager = account(M, AccountRole.MANAGER);
        admin = account(A, AccountRole.ADMIN);
        tag = "ZZIT" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        active1 = category(tag + " Ví tiền", true);
        active2 = category(tag + " Điện thoại", true);
        inactive = category(tag + " Đồ cũ", false);
    }

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

    Category category(String name, boolean active) {
        Category c = new Category();
        c.setName(name);
        c.setActive(active);
        return categories.saveAndFlush(c);
    }

    // ---------- helpers ----------

    static Map<String, Object> body(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    String token(String email) throws Exception {
        String res = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body("email", email, "password", PW))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(res).at("/data/accessToken").asText();
    }

    ResultActions call(MockHttpServletRequestBuilder req, String email, Object body) throws Exception {
        if (email != null) req.header("Authorization", "Bearer " + token(email));
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        return mvc.perform(req);
    }

    ResultActions raw(MockHttpServletRequestBuilder req, String email, String rawJson) throws Exception {
        return mvc.perform(req.header("Authorization", "Bearer " + token(email))
                .contentType(MediaType.APPLICATION_JSON).content(rawJson));
    }

    static void expectError(ResultActions r, int http, String code, String message) throws Exception {
        r.andExpect(status().is(http)).andExpect(jsonPath("$.code").value(code)).andExpect(jsonPath("$.message").value(message));
    }

    JsonNode read(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    JsonNode list(String... kv) throws Exception {
        MockHttpServletRequestBuilder req = get(BASE);
        for (int i = 0; i < kv.length; i += 2) req.param(kv[i], kv[i + 1]);
        return read(call(req, M, null).andExpect(status().isOk())).at("/data");
    }

    static List<String> names(JsonNode page) {
        List<String> out = new ArrayList<>();
        page.at("/content").forEach(n -> out.add(n.get("name").asText()));
        return out;
    }

    List<String> postCategoryNames() throws Exception {
        List<String> out = new ArrayList<>();
        read(call(get("/api/posts/categories"), U, null).andExpect(status().isOk())).at("/data")
                .forEach(n -> out.add(n.get("name").asText()));
        return out;
    }

    Category reload(Category c) {
        em.flush();
        em.clear();
        return categories.findById(c.getCategoryId()).orElseThrow();
    }

    long countByName(String name) {
        return categories.findAll().stream().filter(c -> c.getName().equalsIgnoreCase(name)).count();
    }

    // ---------- [3.1] GET /api/categories ----------

    @Test
    @DisplayName("ITC001 Manager xem danh sách gồm cả danh mục đang và ngừng hoạt động")
    void itc001_listActiveAndInactive() throws Exception {
        JsonNode page = list("keyword", tag, "size", "100");
        assertThat(names(page)).containsExactlyInAnyOrder(active1.getName(), active2.getName(), inactive.getName());
        assertThat(page.get("totalElements").asLong()).isEqualTo(3);
        for (JsonNode n : page.get("content")) {
            assertThat(n.hasNonNull("categoryId")).isTrue();
            assertThat(n.hasNonNull("updatedAt")).isTrue();
            assertThat(n.get("active").asBoolean()).isEqualTo(!n.get("name").asText().equals(inactive.getName()));
        }
    }

    @Test
    @DisplayName("ITC002 Danh sách mặc định trang 1, 10 dòng, mới tạo trước")
    void itc002_defaultPagingAndOrder() throws Exception {
        JsonNode page = list();
        assertThat(page.get("page").asInt()).isEqualTo(1);
        assertThat(page.get("size").asInt()).isEqualTo(10);
        assertThat(page.get("content").size()).isLessThanOrEqualTo(10);
        assertThat(page.at("/content/0/categoryId").asLong()).isEqualTo(inactive.getCategoryId());
        long prev = Long.MAX_VALUE;
        for (JsonNode n : page.get("content")) {
            assertThat(n.get("categoryId").asLong()).isLessThan(prev);
            prev = n.get("categoryId").asLong();
        }
    }

    @Test
    @DisplayName("ITC003 Tìm theo từ khóa không phân biệt hoa thường")
    void itc003_keywordCaseInsensitive() throws Exception {
        JsonNode page = list("keyword", (tag + " ví TIỀN").toLowerCase());
        assertThat(names(page)).containsExactly(active1.getName());
    }

    @Test
    @DisplayName("ITC004 Lọc theo trạng thái hoạt động")
    void itc004_filterByActive() throws Exception {
        assertThat(names(list("keyword", tag, "active", "false"))).containsExactly(inactive.getName());
        assertThat(names(list("keyword", tag, "active", "true"))).containsExactlyInAnyOrder(active1.getName(), active2.getName());
    }

    @Test
    @DisplayName("ITC005 Phân trang danh sách")
    void itc005_pagination() throws Exception {
        JsonNode p2 = list("keyword", tag, "page", "2", "size", "2");
        assertThat(p2.get("totalElements").asLong()).isEqualTo(3);
        assertThat(p2.get("totalPages").asInt()).isEqualTo(2);
        assertThat(p2.get("last").asBoolean()).isTrue();
        assertThat(names(p2)).containsExactly(active1.getName());
    }

    @Test
    @DisplayName("ITC006 Từ khóa không khớp trả danh sách rỗng")
    void itc006_noResult() throws Exception {
        JsonNode page = list("keyword", tag + "khongtontai");
        assertThat(page.get("content").size()).isZero();
        assertThat(page.get("totalElements").asLong()).isZero();
    }

    @Test
    @DisplayName("ITC007 Ký tự % và _ trong từ khóa được tìm như chữ thường")
    void itc007_wildcardKeyword() throws Exception {
        Category pct = category(tag + " 100%_Cotton", true);
        assertThat(names(list("keyword", "%", "size", "100"))).contains(pct.getName()).doesNotContain(active1.getName());
        assertThat(names(list("keyword", tag + "_"))).isEmpty();
    }

    @Test
    @DisplayName("ITC008 Tham số phân trang không hợp lệ")
    void itc008_invalidPaging() throws Exception {
        expectError(call(get(BASE + "?page=0"), M, null), 400, "ERR_400", MSG_PAGE);
        expectError(call(get(BASE + "?size=0"), M, null), 400, "ERR_400", MSG_PAGE);
        expectError(call(get(BASE + "?size=101"), M, null), 400, "ERR_400", MSG_PAGE);
        expectError(call(get(BASE + "?page=abc"), M, null), 400, "ERR_400", MSG_BAD_BODY);
    }

    @Test
    @DisplayName("ITC009 Danh sách có phiên bản bộ câu hỏi mẫu liên kết")
    void itc009_listShowsTemplateVersion() throws Exception {
        JsonNode first = list("keyword", tag).at("/content/0");
        assertThat(first.has("templateVersion") || first.has("questionTemplateVersion") || first.has("questionTemplate"))
                .as("UC-27 bước 3: mỗi dòng có Linked Question Template version").isTrue();
    }

    @Test
    @DisplayName("ITC010 Xem danh sách khi chưa đăng nhập")
    void itc010_listWithoutToken() throws Exception {
        expectError(mvc.perform(get(BASE)), 401, "ERR_401", MSG_401);
    }

    @Test
    @DisplayName("ITC011 USER, Office Staff và ADMIN không xem được danh sách quản lý danh mục")
    void itc011_listForbiddenForOtherRoles() throws Exception {
        for (String e : new String[]{U, S, A}) expectError(call(get(BASE), e, null), 403, "ERR_403", MSG_403);
    }

    // ---------- [3.2] POST /api/categories ----------

    @Test
    @DisplayName("ITC012 Tạo danh mục mới thành công")
    void itc012_create() throws Exception {
        String name = tag + " Máy tính xách tay & Phụ kiện";
        JsonNode res = read(call(post(BASE), M, body("name", name))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Tạo danh mục thành công"))
                .andExpect(jsonPath("$.data.name").value(name))
                .andExpect(jsonPath("$.data.active").value(true)));
        Category saved = categories.findById(res.at("/data/categoryId").asLong()).orElseThrow();
        assertThat(saved.getName()).isEqualTo(name);
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("ITC013 Danh mục mới hiện ngay trong danh sách chọn khi đăng bài")
    void itc013_newCategoryInPostSelection() throws Exception {
        String name = tag + " Chìa khóa";
        call(post(BASE), M, body("name", name)).andExpect(status().isOk());
        assertThat(postCategoryNames()).contains(name);
        assertThat(names(list("keyword", tag))).contains(name);
    }

    @Test
    @DisplayName("ITC014 Tên danh mục được bỏ khoảng trắng thừa")
    void itc014_nameNormalized() throws Exception {
        call(post(BASE), M, body("name", "   " + tag + "   Ba   lô  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value(tag + " Ba lô"));
    }

    @Test
    @DisplayName("ITC015 Tạo danh mục trùng tên (không phân biệt hoa thường)")
    void itc015_duplicateName() throws Exception {
        expectError(call(post(BASE), M, body("name", active1.getName().toUpperCase())), 409, "CAT_409", MSG_DUP);
        assertThat(countByName(active1.getName())).isEqualTo(1);
    }

    @Test
    @DisplayName("ITC016 Tạo danh mục trùng tên danh mục đã ngừng hoạt động")
    void itc016_duplicateInactiveName() throws Exception {
        expectError(call(post(BASE), M, body("name", "  " + inactive.getName() + " ")), 409, "CAT_409", MSG_DUP);
        assertThat(countByName(inactive.getName())).isEqualTo(1);
    }

    @Test
    @DisplayName("ITC017 Tạo danh mục khi bỏ trống tên")
    void itc017_blankName() throws Exception {
        long before = categories.count();
        expectError(call(post(BASE), M, body("name", "")), 400, "ERR_400", MSG_BLANK);
        expectError(call(post(BASE), M, body("name", "     ")), 400, "ERR_400", MSG_BLANK);
        expectError(call(post(BASE), M, body()), 400, "ERR_400", MSG_BLANK);
        assertThat(categories.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("ITC018 Giới hạn độ dài tên danh mục 100 ký tự")
    void itc018_nameLength() throws Exception {
        String n100 = tag + "a".repeat(100 - tag.length());
        call(post(BASE), M, body("name", n100)).andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value(n100));
        expectError(call(post(BASE), M, body("name", n100 + "b")), 400, "ERR_400", MSG_MAX);
    }

    @Test
    @DisplayName("ITC019 Tạo danh mục với body JSON sai cú pháp")
    void itc019_malformedJson() throws Exception {
        expectError(raw(post(BASE), M, "{\"name\": "), 400, "ERR_400", MSG_BAD_BODY);
    }

    @Test
    @DisplayName("ITC020 Tạo danh mục khi chưa đăng nhập hoặc không phải MANAGER")
    void itc020_createForbidden() throws Exception {
        String name = tag + " Không được tạo";
        expectError(call(post(BASE), null, body("name", name)), 401, "ERR_401", MSG_401);
        for (String e : new String[]{U, S, A}) expectError(call(post(BASE), e, body("name", name)), 403, "ERR_403", MSG_403);
        assertThat(countByName(name)).isZero();
    }

    // ---------- [3.3] PUT /api/categories/{id} ----------

    @Test
    @DisplayName("ITC021 Đổi tên danh mục thành công, giữ nguyên ID")
    void itc021_rename() throws Exception {
        String name = tag + " Ví & Bóp";
        call(put(BASE + "/" + active1.getCategoryId()), M, body("name", name))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đổi tên danh mục thành công"))
                .andExpect(jsonPath("$.data.categoryId").value(active1.getCategoryId()))
                .andExpect(jsonPath("$.data.name").value(name));
        Category c = reload(active1);
        assertThat(c.getName()).isEqualTo(name);
        assertThat(c.isActive()).isTrue();
    }

    @Test
    @DisplayName("ITC022 Đổi tên không làm mất liên kết với bài đăng cũ")
    void itc022_renameKeepsPosts() throws Exception {
        Post p = posts.saveAndFlush(Post.lost(user, active1, tag + " Mất ví", "Ví nâu", "Thư viện",
                OffsetDateTime.now().minusDays(1).withNano(0)));
        call(put(BASE + "/" + active1.getCategoryId()), M, body("name", tag + " Ví mới")).andExpect(status().isOk());
        em.flush();
        em.clear();
        Post after = posts.findById(p.getId()).orElseThrow();
        assertThat(after.getCategory().getCategoryId()).isEqualTo(active1.getCategoryId());
        assertThat(after.getCategory().getName()).isEqualTo(tag + " Ví mới");
    }

    @Test
    @DisplayName("ITC023 Đổi tên chỉ khác hoa thường so với tên hiện tại")
    void itc023_renameSameNameDifferentCase() throws Exception {
        String name = active1.getName().toUpperCase();
        call(put(BASE + "/" + active1.getCategoryId()), M, body("name", name))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value(name));
    }

    @Test
    @DisplayName("ITC024 Đổi tên trùng với danh mục khác")
    void itc024_renameDuplicate() throws Exception {
        expectError(call(put(BASE + "/" + active1.getCategoryId()), M, body("name", active2.getName().toLowerCase())), 409, "CAT_409", MSG_DUP);
        expectError(call(put(BASE + "/" + active1.getCategoryId()), M, body("name", inactive.getName())), 409, "CAT_409", MSG_DUP);
        assertThat(reload(active1).getName()).isEqualTo(tag + " Ví tiền");
    }

    @Test
    @DisplayName("ITC025 Đổi tên với tên trống hoặc quá 100 ký tự")
    void itc025_renameInvalidName() throws Exception {
        String url = BASE + "/" + active1.getCategoryId();
        expectError(call(put(url), M, body("name", "  ")), 400, "ERR_400", MSG_BLANK);
        expectError(call(put(url), M, body("name", "x".repeat(101))), 400, "ERR_400", MSG_MAX);
        assertThat(reload(active1).getName()).isEqualTo(tag + " Ví tiền");
    }

    @Test
    @DisplayName("ITC026 Đổi tên danh mục không tồn tại")
    void itc026_renameNotFound() throws Exception {
        expectError(call(put(BASE + "/" + MISSING_ID), M, body("name", tag + " X")), 404, "CAT_404", MSG_404);
        expectError(call(put(BASE + "/abc"), M, body("name", tag + " X")), 400, "ERR_400", MSG_BAD_BODY);
    }

    @Test
    @DisplayName("ITC027 Đổi tên khi chưa đăng nhập hoặc không phải MANAGER")
    void itc027_renameForbidden() throws Exception {
        String url = BASE + "/" + active1.getCategoryId();
        expectError(call(put(url), null, body("name", tag + " Hack")), 401, "ERR_401", MSG_401);
        for (String e : new String[]{U, S, A}) expectError(call(put(url), e, body("name", tag + " Hack")), 403, "ERR_403", MSG_403);
        assertThat(reload(active1).getName()).isEqualTo(tag + " Ví tiền");
    }

    // ---------- [3.4] PATCH /api/categories/{id}/status ----------

    @Test
    @DisplayName("ITC028 Ngừng kích hoạt danh mục")
    void itc028_deactivate() throws Exception {
        call(patch(BASE + "/" + active1.getCategoryId() + "/status"), M, body("active", false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Cập nhật trạng thái thành công"))
                .andExpect(jsonPath("$.data.active").value(false));
        assertThat(reload(active1).isActive()).isFalse();
        assertThat(postCategoryNames()).doesNotContain(active1.getName());
        assertThat(names(list("keyword", tag))).contains(active1.getName());
    }

    @Test
    @DisplayName("ITC029 Kích hoạt lại danh mục")
    void itc029_reactivate() throws Exception {
        assertThat(postCategoryNames()).doesNotContain(inactive.getName());
        call(patch(BASE + "/" + inactive.getCategoryId() + "/status"), M, body("active", true))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.active").value(true));
        assertThat(reload(inactive).isActive()).isTrue();
        assertThat(postCategoryNames()).contains(inactive.getName());
    }

    @Test
    @DisplayName("ITC030 Ngừng kích hoạt không làm ảnh hưởng bài đăng cũ")
    void itc030_deactivateKeepsPosts() throws Exception {
        Post p = posts.saveAndFlush(Post.lost(user, active1, tag + " Mất ví", "Ví nâu", "Thư viện",
                OffsetDateTime.now().minusDays(1).withNano(0)));
        call(patch(BASE + "/" + active1.getCategoryId() + "/status"), M, body("active", false)).andExpect(status().isOk());
        em.flush();
        em.clear();
        Post after = posts.findById(p.getId()).orElseThrow();
        assertThat(after.getCategory().getCategoryId()).isEqualTo(active1.getCategoryId());
        assertThat(categories.findById(active1.getCategoryId())).isPresent();
    }

    @Test
    @DisplayName("ITC031 Đổi trạng thái khi thiếu hoặc sai kiểu giá trị active")
    void itc031_statusInvalidBody() throws Exception {
        String url = BASE + "/" + active1.getCategoryId() + "/status";
        expectError(call(patch(url), M, body()), 400, "ERR_400", MSG_STATUS_REQUIRED);
        expectError(raw(patch(url), M, "{\"active\": \"abc\"}"), 400, "ERR_400", MSG_BAD_BODY);
        assertThat(reload(active1).isActive()).isTrue();
    }

    @Test
    @DisplayName("ITC032 Đổi trạng thái danh mục không tồn tại")
    void itc032_statusNotFound() throws Exception {
        expectError(call(patch(BASE + "/" + MISSING_ID + "/status"), M, body("active", false)), 404, "CAT_404", MSG_404);
    }

    @Test
    @DisplayName("ITC033 Đổi trạng thái khi chưa đăng nhập hoặc không phải MANAGER")
    void itc033_statusForbidden() throws Exception {
        String url = BASE + "/" + active1.getCategoryId() + "/status";
        expectError(call(patch(url), null, body("active", false)), 401, "ERR_401", MSG_401);
        for (String e : new String[]{U, S, A}) expectError(call(patch(url), e, body("active", false)), 403, "ERR_403", MSG_403);
        assertThat(reload(active1).isActive()).isTrue();
    }
}
