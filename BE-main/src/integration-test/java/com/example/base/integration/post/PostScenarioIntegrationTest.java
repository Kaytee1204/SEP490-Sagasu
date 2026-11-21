package com.example.base.integration.post;

import com.example.base.entity.Account;
import com.example.base.entity.AccountRole;
import com.example.base.entity.AccountStatus;
import com.example.base.entity.Category;
import com.example.base.entity.Post;
import com.example.base.entity.PostImage;
import com.example.base.exception.AppException;
import com.example.base.exception.ErrorCode;
import com.example.base.integration.ScenarioTestBase;
import com.example.base.repository.CategoryRepository;
import com.example.base.repository.PostRepository;
import com.example.base.storage.ImageStorage;
import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpServer;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * Report-5.2 (scenario style) sheet "Posts": TC-POST-01 .. TC-POST-12, Swagger group 04, per SRS 2.0 v2
 * UC-13..UC-17, UC-21, UC-30..UC-32 and the data business rules (active category, private identifiers, post images, opposite-type matching).
 * ImageStorage (R2) is mocked; the Python AI service is a local stub HTTP server (up or down per case).
 * Field checks of the create form are unit-tested (Report-5.1 Post UTC098-UTC117); the Smart Match scoring formula has no unit test.
 */
class PostScenarioIntegrationTest extends ScenarioTestBase {

    static final String MSG_CREATED = "Đăng bài thành công";
    static final String MSG_NOT_VISIBLE = "Bài đăng không tồn tại hoặc không còn hiển thị";
    static final String MSG_OWNER = "Không tìm thấy bài đăng thuộc tài khoản của bạn";
    static final String MSG_CATEGORY = "Danh mục không tồn tại hoặc đã ngừng sử dụng";
    static final String MSG_NO_IMAGE = "Vui lòng tải lên ít nhất 1 hình ảnh cho bài đăng.";
    static final String MSG_CUSTODY = "Bài mới mặc định là P2P; giao bên tiếp nhận sau khi đăng thành công";
    static final String MSG_PI_SAVED = "Đã lưu dấu hiệu nhận dạng riêng tư";
    static final String MSG_MATCHED = "Đã đối sánh thành công";
    static final String MSG_AI_FORBIDDEN = "Chỉ chủ bài đăng, Quản trị viên (Admin) hoặc Quản lý (Manager) mới có thể sử dụng tính năng đối sánh AI cho bài đăng này.";
    static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    // ---------- Python AI service stub ----------
    static final HttpServer AI;
    static volatile int aiStatus = 503;
    static volatile String aiBody = "{}";
    static volatile String aiLastRequest;

    static {
        try {
            AI = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            AI.createContext("/api/match", ex -> {
                aiLastRequest = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                byte[] out = aiBody.getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().add("Content-Type", "application/json");
                ex.sendResponseHeaders(aiStatus, out.length);
                try (OutputStream os = ex.getResponseBody()) { os.write(out); }
            });
            AI.start();
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void aiUrl(DynamicPropertyRegistry r) {
        r.add("app.ai.service-url", () -> "http://127.0.0.1:" + AI.getAddress().getPort() + "/");
    }

    @Autowired CategoryRepository categories;
    @Autowired PostRepository posts;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @MockBean ImageStorage storage;

    Account user01, user02, manager01, admin01;
    Category cat, cat2;

    @BeforeEach
    void setUp() {
        aiStatus = 503;
        aiBody = "{}";
        aiLastRequest = null;
        when(storage.readUrl(any(), any())).thenAnswer(inv -> "https://img.test/" + inv.getArgument(0));
        user01 = account(USER01, AccountRole.USER);
        user02 = account(USER02, AccountRole.USER);
        manager01 = account(MANAGER01, AccountRole.MANAGER);
        admin01 = account(ADMIN01, AccountRole.ADMIN);
        cat = category("ZZ Test Ví " + uniq(), true);
        cat2 = category("ZZ Test Điện thoại " + uniq(), true);
    }

    // ---------- fixtures ----------

    static String uniq() { return UUID.randomUUID().toString().replace("-", "").substring(0, 10); }

    /** Unique search token (letters + digits) that the simple tsvector keeps as one word. */
    static String token() { return "zq" + uniq(); }

    static OffsetDateTime ago(long hours) { return OffsetDateTime.now(VN).minusHours(hours).withNano(0); }

    Category category(String name, boolean active) {
        Category c = new Category();
        c.setName(name);
        c.setActive(active);
        return categories.saveAndFlush(c);
    }

    Post lost(Account owner, Category c, String title, String location, OffsetDateTime at) {
        return posts.saveAndFlush(Post.lost(owner, c, title, "Mô tả " + title, location, at));
    }

    Post found(Account owner, Category c, String title, String location, OffsetDateTime at) {
        return posts.saveAndFlush(Post.found(owner, c, title, "Mô tả " + title, location, at, "P2P", null));
    }

    Post withImages(Post p, int n) {
        for (int i = 0; i < n; i++) {
            PostImage img = new PostImage("posts/test/" + uniq() + ".jpg", i, "image/jpeg", 100, 10, 10, "h" + uniq());
            p.addImage(img);
            em.persist(img);
        }
        em.flush();
        return p;
    }

    Post secret(Post p, String text) {
        p.updatePrivateIdentifiers(text);
        return posts.saveAndFlush(p);
    }

    void hide(Post p) { p.moderate("HIDDEN", "Vi phạm nội quy", manager01); posts.saveAndFlush(p); }

    void softDelete(Post p) { p.softDelete("Người dùng xóa"); posts.saveAndFlush(p); }

    void sync() { em.flush(); em.clear(); }

    Post reloadPost(Post p) { em.clear(); return posts.findById(p.getId()).orElseThrow(); }

    long countTitle(String part) {
        em.flush();
        return jdbc.queryForObject("select count(*) from post where title like ?", Long.class, "%" + part + "%");
    }

    Map<String, Object> postBody(String title, Category c) {
        return body("title", "  " + title + "  ", "description", "Ví da màu nâu, có thẻ sinh viên",
                "categoryId", c.getCategoryId(), "locationText", "Thư viện tầng 2",
                "eventAt", ago(24).toString(), "privateIdentifiers", "Có ảnh gia đình bên trong");
    }

    static byte[] picture(String format) throws IOException {
        BufferedImage img = new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, 0xFF0000);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, format, out);
        return out.toByteArray();
    }

    static MockMultipartFile jpg() throws IOException { return new MockMultipartFile("images", "a.jpg", "image/jpeg", picture("jpg")); }

    static MockMultipartFile png() throws IOException { return new MockMultipartFile("images", "b.png", "image/png", picture("png")); }

    Res create(String token, String type, Object data, MockMultipartFile... images) throws Exception {
        MockMultipartHttpServletRequestBuilder req = multipart("/api/posts/" + type);
        req.file(new MockMultipartFile("data", "", MediaType.APPLICATION_JSON_VALUE,
                json.writeValueAsString(data).getBytes(StandardCharsets.UTF_8)));
        for (MockMultipartFile f : images) req.file(f);
        return send(req, token, null);
    }

    Res list(String token, String query) throws Exception { return send(get("/api/posts" + query), token, null); }

    static List<Long> ids(Res r) {
        List<Long> out = new ArrayList<>();
        if (r.body() != null) r.body().at("/data/content").forEach(n -> out.add(n.get("id").asLong()));
        return out;
    }

    static List<Long> dataIds(Res r) {
        List<Long> out = new ArrayList<>();
        if (r.body() != null) r.body().at("/data").forEach(n -> out.add(n.get("id").asLong()));
        return out;
    }

    Res patchCategory(String token, Category c, boolean active) throws Exception {
        return send(patch("/api/categories/" + c.getCategoryId() + "/status"), token, body("active", active));
    }

    // ---------- View & Search Posts ----------

    @Test
    @DisplayName("TC-POST-01 User views the public post list: visible posts only, newest first, paged")
    void tcPost01_viewList() throws Exception {
        String key = token();
        Post a = lost(user02, cat, "Mất ví " + key, "Thư viện tầng 2", ago(30));
        Post b = found(user02, cat, "Nhặt được ví " + key, "Nhà ăn A", ago(20));
        Post c = secret(lost(user02, cat2, "Mất điện thoại " + key, "Sân bóng", ago(10)), "BIMAT-" + key);
        hide(lost(user02, cat, "Bài bị ẩn " + key, "Thư viện", ago(5)));
        softDelete(found(user02, cat, "Bài đã xóa " + key, "Thư viện", ago(5)));
        sync();
        String t = token(USER01);
        Res all = list(t, "");
        expect("Step 1 default list", all, 200, null, null);
        soft.assertThat(all.body().at("/data/page").asInt()).as("Step 1 page").isEqualTo(1);
        soft.assertThat(all.body().at("/data/content").size()).as("Step 1 default page size 12").isBetween(1, 12);
        Res p1 = list(t, "?keyword=" + key + "&page=1&size=2");
        soft.assertThat(ids(p1)).as("Step 2 page 1: newest first, hidden / deleted excluded").containsExactly(c.getId(), b.getId());
        soft.assertThat(p1.body().at("/data/totalElements").asInt()).as("Step 2 totalElements").isEqualTo(3);
        soft.assertThat(p1.body().at("/data/totalPages").asInt()).as("Step 2 totalPages").isEqualTo(2);
        soft.assertThat(ids(list(t, "?keyword=" + key + "&page=2&size=2"))).as("Step 3 page 2").containsExactly(a.getId());
        JsonNode card = p1.body().at("/data/content/0");
        for (String f : new String[]{"postType", "title", "categoryName", "locationText", "eventAt", "status"})
            soft.assertThat(card.hasNonNull(f)).as("Step 2 card field " + f).isTrue();
        soft.assertThat(new String(p1.raw(), StandardCharsets.UTF_8)).as("Step 4 no private identifiers in the list")
                .doesNotContain("BIMAT-").doesNotContain("privateIdentifiers");
        expect("Step 5 without token", list(null, ""), 401, "ERR_401", MSG_401);
    }

    @Test
    @DisplayName("TC-POST-02 User searches posts by keyword and filters by type, category, location and time")
    void tcPost02_searchAndFilter() throws Exception {
        String key = token(), hidden = token();
        Post l1 = lost(user02, cat, "Mất ví da " + key, "Thư viện tầng 2", ago(2));
        Post f1 = found(user02, cat2, "Nhặt được điện thoại " + key, "Nhà ăn A", ago(240));
        Post l2 = secret(lost(user02, cat, "Mất chìa khóa " + key, "Thư viện tầng 3", ago(72)), "Móc khóa " + hidden);
        sync();
        String t = token(USER01);
        soft.assertThat(ids(list(t, "?keyword=" + key.toUpperCase()))).as("Step 1 keyword (case-insensitive)")
                .containsExactlyInAnyOrder(l1.getId(), f1.getId(), l2.getId());
        soft.assertThat(ids(list(t, "?keyword=" + key + "&postType=FOUND"))).as("Step 2 post type FOUND").containsExactly(f1.getId());
        soft.assertThat(ids(list(t, "?keyword=" + key + "&categoryId=" + cat2.getCategoryId()))).as("Step 2 category").containsExactly(f1.getId());
        soft.assertThat(ids(send(get("/api/posts").param("keyword", key).param("location", "thư viện"), t, null)))
                .as("Step 2 location").containsExactlyInAnyOrder(l1.getId(), l2.getId());
        soft.assertThat(ids(list(t, "?keyword=" + key + "&timeRange=24H"))).as("Step 3 last 24 hours").containsExactly(l1.getId());
        LocalDate today = LocalDate.now(VN);
        soft.assertThat(ids(list(t, "?keyword=" + key + "&timeRange=CUSTOM&fromDate=" + today.minusDays(5) + "&toDate=" + today.minusDays(2))))
                .as("Step 3 custom dates").containsExactly(l2.getId());
        soft.assertThat(list(t, "?keyword=" + hidden).body().at("/data/totalElements").asInt())
                .as("Step 4 private identifiers are not searchable").isZero();
        soft.assertThat(list(t, "?keyword=" + key + "x9").body().at("/data/totalElements").asInt())
                .as("Step 5 no match (UC-21 Alt 6.1)").isZero();
        expect("Step 6 post type ABC", list(t, "?postType=ABC"), 400, "POST_400_SEARCH_FILTER", null);
        expect("Step 6 from after to", list(t, "?fromDate=" + today + "&toDate=" + today.minusDays(3)), 400, "POST_400_SEARCH_DATE", null);
    }

    @Test
    @DisplayName("TC-POST-03 Posting form lists only active categories; a category deactivated by the Manager disappears")
    void tcPost03_activeCategories() throws Exception {
        Category off = category("ZZ Test Ngừng " + uniq(), false);
        String t = token(USER01);
        Res r = send(get("/api/posts/categories"), t, null);
        expect("Step 1 categories", r, 200, null, null);
        List<Long> got = dataIds(r);
        soft.assertThat(got).as("Step 1 active categories listed").contains(cat.getCategoryId(), cat2.getCategoryId());
        soft.assertThat(got).as("Step 1 inactive category hidden").doesNotContain(off.getCategoryId());
        expect("Step 2 manager deactivates", patchCategory(token(MANAGER01), cat2, false), 200, null, null);
        sync();
        soft.assertThat(dataIds(send(get("/api/posts/categories"), t, null))).as("Step 3 deactivated category gone")
                .doesNotContain(cat2.getCategoryId()).contains(cat.getCategoryId());
        expect("Step 4 without token", send(get("/api/posts/categories"), null, null), 401, "ERR_401", MSG_401);
    }

    // ---------- View Post Detail ----------

    @Test
    @DisplayName("TC-POST-04 User views a post detail; private identifiers stay hidden; hidden or deleted posts are blocked")
    void tcPost04_detail() throws Exception {
        Post p = withImages(secret(lost(user01, cat, "Mất ví da " + token(), "Thư viện tầng 2", ago(24)), "BIMAT-SN123"), 2);
        sync();
        String other = token(USER02), owner = token(USER01);
        Res d = send(get("/api/posts/" + p.getId()), other, null);
        expect("Step 1 user02 opens", d, 200, null, null);
        soft.assertThat(d.text("/data/title")).as("Step 1 title").isEqualTo(p.getTitle());
        soft.assertThat(d.text("/data/categoryName")).as("Step 1 category").isEqualTo(cat.getName());
        soft.assertThat(d.text("/data/custodyMode")).as("Step 1 custody").isEqualTo("P2P");
        soft.assertThat(d.body().at("/data/images").size()).as("Step 1 gallery").isEqualTo(2);
        soft.assertThat(d.text("/data/images/0/url")).as("Step 1 image URL from storage").startsWith("https://img.test/posts/test/");
        soft.assertThat(d.text("/data/isOwner")).as("Step 1 isOwner").isEqualTo("false");
        soft.assertThat(d.text("/data/canEditOwnPost")).as("Step 1 no edit button").isEqualTo("false");
        soft.assertThat(new String(d.raw(), StandardCharsets.UTF_8)).as("Step 1 private identifiers hidden").doesNotContain("BIMAT-SN123");
        Res mine = send(get("/api/posts/" + p.getId()), owner, null);
        soft.assertThat(mine.text("/data/isOwner")).as("Step 2 owner isOwner").isEqualTo("true");
        soft.assertThat(mine.text("/data/canEditOwnPost")).as("Step 2 owner can edit").isEqualTo("true");
        hide(reloadPost(p));
        sync();
        expect("Step 3 hidden, user02", send(get("/api/posts/" + p.getId()), other, null), 404, "POST_404", MSG_NOT_VISIBLE);
        expect("Step 3 hidden, owner", send(get("/api/posts/" + p.getId()), owner, null), 200, null, null);
        softDelete(reloadPost(p));
        sync();
        expect("Step 4 deleted, owner", send(get("/api/posts/" + p.getId()), owner, null), 404, "POST_404", MSG_NOT_VISIBLE);
        expect("Step 5 id abc", send(get("/api/posts/abc"), other, null), 400, "POST_400_ID_FORMAT", null);
        expect("Step 5 unknown id", send(get("/api/posts/999999999"), other, null), 404, "POST_404", MSG_NOT_VISIBLE);
        expect("Step 5 without token", send(get("/api/posts/" + p.getId()), null, null), 401, "ERR_401", MSG_401);
    }

    // ---------- Create Post ----------

    @Test
    @DisplayName("TC-POST-05 User creates a Lost post with photos; it is saved once and is searchable at once")
    void tcPost05_createLost() throws Exception {
        String key = token(), t = token(USER01);
        Map<String, Object> data = postBody("Mất ví da " + key, cat);
        data.put("requestId", UUID.randomUUID().toString());
        Res r = create(t, "lost", data, jpg(), png());
        expect("Step 1 create", r, 201, null, MSG_CREATED);
        soft.assertThat(r.text("/data/postType")).as("Step 1 postType").isEqualTo("LOST");
        soft.assertThat(r.text("/data/status")).as("Step 1 status").isEqualTo("OPEN");
        soft.assertThat(r.text("/data/moderationStatus")).as("Step 1 moderationStatus").isEqualTo("VISIBLE");
        long id = r.body().at("/data/id").asLong();
        Post saved = posts.findById(id).orElseThrow();
        soft.assertThat(saved.getTitle()).as("Step 2 title trimmed").isEqualTo("Mất ví da " + key);
        soft.assertThat(saved.getImages()).as("Step 2 2 images saved").hasSize(2);
        soft.assertThat(saved.getPrivateIdentifiers()).as("Step 2 private identifiers saved").isEqualTo("Có ảnh gia đình bên trong");
        verify(storage, times(2)).put(startsWith("posts/" + user01.getId() + "/"), any(), anyString());
        Res again = create(t, "lost", data, jpg(), png());
        expect("Step 3 same request sent twice", again, 201, null, null);
        soft.assertThat(again.body().at("/data/id").asLong()).as("Step 3 same post returned").isEqualTo(id);
        soft.assertThat(countTitle(key)).as("Step 3 only one post row").isEqualTo(1);
        sync();
        soft.assertThat(ids(list(token(USER02), "?keyword=" + key))).as("Step 4 searchable at once (UC-15 Postcondition 2)").containsExactly(id);
    }

    @Test
    @DisplayName("TC-POST-06 A Lost post without photos is refused")
    void tcPost06_createLostWithoutPhoto() throws Exception {
        // Follows the code (team decision): at least 1 photo for Lost posts too; SRS UC-15 Alt 4.1 (0 photos) is outdated here.
        String key = token();
        expect("Step 1 Lost post without photo", create(token(USER01), "lost", postBody("Mất thẻ sinh viên " + key, cat)),
                400, "ERR_400", MSG_NO_IMAGE);
        soft.assertThat(countTitle(key)).as("Step 2 no post saved").isZero();
        verify(storage, never()).put(any(), any(), any());
    }

    @Test
    @DisplayName("TC-POST-07 User creates a Found post (P2P); Office custody at creation and Found posts without photos are refused")
    void tcPost07_createFound() throws Exception {
        String key = token(), t = token(USER01);
        Res r = create(t, "found", postBody("Nhặt được ví " + key, cat), jpg());
        expect("Step 1 create", r, 201, null, MSG_CREATED);
        soft.assertThat(r.text("/data/postType")).as("Step 1 postType").isEqualTo("FOUND");
        soft.assertThat(r.text("/data/moderationStatus")).as("Step 1 visible at once (UC-16 Postcondition 1)").isEqualTo("VISIBLE");
        long id = r.body().at("/data/id").asLong();
        sync();
        Res d = send(get("/api/posts/" + id), token(USER02), null);
        soft.assertThat(d.text("/data/custodyMode")).as("Step 2 custody P2P").isEqualTo("P2P");
        Map<String, Object> office = postBody("Giao văn phòng " + key, cat);
        office.put("custodyMode", "OFFICE");
        expectHas("Step 3 custody OFFICE", create(t, "found", office, jpg()), 400, "ERR_400", MSG_CUSTODY);
        expect("Step 4 no photo", create(t, "found", postBody("Không ảnh " + key, cat)), 400, "ERR_400", MSG_NO_IMAGE);
        soft.assertThat(countTitle(key)).as("Step 3-4 no extra post").isEqualTo(1);
    }

    @Test
    @DisplayName("TC-POST-08 Post creation is refused for an inactive category, a future time, a storage failure or a restricted account")
    void tcPost08_createRefused() throws Exception {
        String key = token(), t = token(USER01);
        expect("Step 1 manager deactivates category", patchCategory(token(MANAGER01), cat2, false), 200, null, null);
        sync();
        expect("Step 1 post with inactive category (UC-15 E3)", create(t, "lost", postBody("Danh mục tắt " + key, cat2), jpg()), 400, "POST_400_CATEGORY", MSG_CATEGORY);
        verify(storage, never()).put(any(), any(), any());
        Map<String, Object> future = postBody("Tương lai " + key, cat);
        future.put("eventAt", OffsetDateTime.now(VN).plusDays(1).toString());
        Res f = create(t, "lost", future, jpg());
        expect("Step 2 event time in the future (UC-15 E1)", f, 400, "ERR_400", null);
        soft.assertThat(f.body() == null ? null : f.body().at("/errors/eventAt").isMissingNode() ? null : "x").as("Step 2 error on eventAt").isNotNull();
        doNothing().doThrow(new AppException(ErrorCode.IMAGE_UPLOAD_FAILED)).when(storage).put(any(), any(), any());
        expect("Step 3 storage fails on 2nd photo (UC-15 E4)", create(t, "lost", postBody("Lỗi R2 " + key, cat), jpg(), png()),
                503, "IMAGE_503_UPLOAD", "Không tải được ảnh lên R2. Vui lòng thử lại.");
        verify(storage, times(2)).delete(startsWith("posts/" + user01.getId() + "/"));
        reset(storage);
        expect("Step 4 without token", create(null, "lost", postBody("Khách " + key, cat), jpg()), 401, "ERR_401", MSG_401);
        String t2 = token(USER02);
        user02.setStatus(AccountStatus.SUSPENDED);
        save(user02);
        Res s = create(t2, "found", postBody("Bị khóa " + key, cat), jpg());
        soft.assertThat(s.status()).as("Step 5 suspended account with an old token is refused: HTTP status (message: %s)", s.message()).isIn(401, 403);
        soft.assertThat(countTitle(key)).as("No post created in any step").isZero();
    }

    // ---------- Private Identifiers ----------

    @Test
    @DisplayName("TC-POST-09 Owner reads, updates and clears the private identifiers of a post")
    void tcPost09_privateIdentifiersOwner() throws Exception {
        Post p = secret(found(user01, cat, "Nhặt được ví " + token(), "Thư viện", ago(5)), "Serial SN-123");
        sync();
        String t = token(USER01), url = "/api/posts/" + p.getId() + "/private-identifiers";
        Res r = send(get(url), t, null);
        expect("Step 1 read", r, 200, null, null);
        soft.assertThat(r.text("/data/privateIdentifiers")).as("Step 1 value").isEqualTo("Serial SN-123");
        expect("Step 2 update", send(put(url), t, body("privateIdentifiers", "  Khắc tên AN ở mặt sau  ")), 200, null, MSG_PI_SAVED);
        soft.assertThat(send(get(url), t, null).text("/data/privateIdentifiers")).as("Step 2 saved (trimmed)").isEqualTo("Khắc tên AN ở mặt sau");
        soft.assertThat(new String(send(get("/api/posts/" + p.getId()), token(USER02), null).raw(), StandardCharsets.UTF_8))
                .as("Step 3 not shown on the public detail").doesNotContain("Khắc tên AN");
        expect("Step 4 clear", send(put(url), t, body("privateIdentifiers", "")), 200, null, MSG_PI_SAVED);
        soft.assertThat(send(get(url), t, null).text("/data/privateIdentifiers")).as("Step 4 cleared (UC-17 Alt 3.1)").isEmpty();
        Res tooLong = send(put(url), t, body("privateIdentifiers", "a".repeat(5001)));
        expect("Step 5 5001 characters (UC-17 E1)", tooLong, 400, "ERR_400", null);
        soft.assertThat(reloadPost(p).getPrivateIdentifiers()).as("Step 5 unchanged").isNull();
    }

    @Test
    @DisplayName("TC-POST-10 Other users and guests cannot read or change private identifiers")
    void tcPost10_privateIdentifiersDenied() throws Exception {
        Post p = secret(found(user01, cat, "Nhặt được ví " + token(), "Thư viện", ago(5)), "Serial SN-123");
        sync();
        String url = "/api/posts/" + p.getId() + "/private-identifiers", other = token(USER02);
        expect("Step 1 user02 reads", send(get(url), other, null), 404, "POST_404_OWNER", MSG_OWNER);
        expect("Step 1 user02 updates", send(put(url), other, body("privateIdentifiers", "Gian lận")), 404, "POST_404_OWNER", MSG_OWNER);
        soft.assertThat(reloadPost(p).getPrivateIdentifiers()).as("Step 1 unchanged").isEqualTo("Serial SN-123");
        expect("Step 2 without token", send(get(url), null, null), 401, "ERR_401", MSG_401);
        expect("Step 3 unknown post", send(get("/api/posts/999999999/private-identifiers"), token(USER01), null), 404, "POST_404_OWNER", MSG_OWNER);
    }

    // ---------- AI Smart Match ----------

    @Test
    @DisplayName("TC-POST-11 Owner, Manager and Admin run AI Smart Match; only visible opposite-type posts are sent to the AI service")
    void tcPost11_smartMatchAi() throws Exception {
        String key = token();
        Post source = withImages(secret(lost(user01, cat, "Mất ví da nâu " + key, "Thư viện tầng 2", ago(24)), "BIMAT-" + key), 1);
        Post good = withImages(found(user02, cat, "Nhặt được ví da nâu " + key, "Thư viện tầng 2", ago(20)), 1);
        Post hiddenFound = found(user02, cat, "Ví bị ẩn " + key, "Thư viện", ago(20));
        hide(hiddenFound);
        Post deletedFound = found(user02, cat, "Ví đã xóa " + key, "Thư viện", ago(20));
        softDelete(deletedFound);
        Post sameType = lost(user02, cat, "Mất ví khác " + key, "Thư viện", ago(20));
        Post otherCat = found(user02, cat2, "Nhặt được điện thoại " + key, "Thư viện", ago(20));
        sync();
        aiStatus = 200;
        aiBody = "{\"matches\":[{\"id\":" + good.getId() + ",\"title\":\"" + good.getTitle() + "\",\"postType\":\"FOUND\",\"categoryName\":\""
                + cat.getName() + "\",\"matchScore\":88.5,\"matchBreakdown\":{\"visual\":90,\"text\":80},\"matchReason\":\"Hình ảnh rất tương đồng\"}]}";
        String url = "/api/posts/" + source.getId() + "/smart-matches";
        Res r = send(get(url), token(USER01), null);
        expect("Step 1 owner runs Smart Match", r, 200, null, MSG_MATCHED);
        soft.assertThat(dataIds(r)).as("Step 1 AI result").containsExactly(good.getId());
        soft.assertThat(r.body().at("/data/0/matchScore").asDouble()).as("Step 1 score").isEqualTo(88.5);
        JsonNode sent = json.readTree(aiLastRequest == null ? "{}" : aiLastRequest);
        List<Long> cands = new ArrayList<>();
        sent.at("/candidates").forEach(n -> cands.add(n.get("id").asLong()));
        soft.assertThat(sent.at("/sourceItem/id").asLong()).as("Step 2 source sent").isEqualTo(source.getId());
        soft.assertThat(cands).as("Step 2 visible FOUND candidate sent").contains(good.getId());
        soft.assertThat(cands).as("Step 2 hidden, deleted, same-type and the post itself are not sent")
                .doesNotContain(hiddenFound.getId(), deletedFound.getId(), sameType.getId(), source.getId());
        soft.assertThat(cands.size()).as("Step 2 at most 20 candidates").isLessThanOrEqualTo(20);
        List<String> cats = new ArrayList<>();
        sent.at("/candidates").forEach(n -> cats.add(n.get("categoryName").asText()));
        soft.assertThat(cats).as("Step 2 candidates in the same category only (UC-31 Precondition 2)").containsOnly(cat.getName());
        soft.assertThat(aiLastRequest).as("Step 2 private identifiers not sent").doesNotContain("BIMAT-").doesNotContain("privateIdentifiers");
        expect("Step 3 Manager", send(get(url), token(MANAGER01), null), 200, null, MSG_MATCHED);
        expect("Step 3 Admin", send(get(url), token(ADMIN01), null), 200, null, MSG_MATCHED);
        expect("Step 4 another user (UC-32 E1)", send(get(url), token(USER02), null), 403, "ERR_403", MSG_AI_FORBIDDEN);
        expect("Step 4 without token", send(get(url), null, null), 401, "ERR_401", MSG_401);
        soft.assertThat(otherCat.getId()).isNotNull();
    }

    @Test
    @DisplayName("TC-POST-12 Smart Match falls back to backend matching when the AI service is down")
    void tcPost12_smartMatchFallback() throws Exception {
        String key = token();
        Post source = withImages(lost(user01, cat, "Mất ví da màu nâu hiệu Gucci " + key, "Thư viện tầng 2", ago(24)), 1);
        Post similar = withImages(found(user02, cat, "Nhặt được ví da màu nâu hiệu Gucci " + key, "Thư viện tầng 2", ago(20)), 1);
        Category lonely = category("ZZ Test Không ứng viên " + uniq(), true);
        Post nothing = lost(user01, lonely, "Qwxzv plmok " + key, "Khu Z", ago(24));
        sync();
        aiStatus = 503;
        Res r = send(get("/api/posts/" + source.getId() + "/smart-matches"), token(USER01), null);
        expect("Step 1 AI down", r, 200, null, MSG_MATCHED);
        soft.assertThat(aiLastRequest).as("Step 1 AI service was called first").isNotNull();
        soft.assertThat(dataIds(r)).as("Step 2 similar FOUND post suggested").contains(similar.getId());
        JsonNode list = r.body().at("/data");
        for (int i = 0; i < list.size(); i++) {
            JsonNode m = list.get(i);
            soft.assertThat(m.get("matchScore").asDouble()).as("Step 2 score >= 45").isBetween(45.0, 100.0);
            soft.assertThat(m.get("postType").asText()).as("Step 2 opposite type").isEqualTo("FOUND");
            if (i > 0) soft.assertThat(m.get("matchScore").asDouble()).as("Step 2 ranked by score").isLessThanOrEqualTo(list.get(i - 1).get("matchScore").asDouble());
        }
        if (list.size() > 0) {
            for (String f : new String[]{"visual", "color", "text", "spatioTemporal"})
                soft.assertThat(list.get(0).at("/matchBreakdown/" + f).isNumber()).as("Step 2 breakdown " + f).isTrue();
        }
        Res empty = send(get("/api/posts/" + nothing.getId() + "/smart-matches"), token(USER01), null);
        expect("Step 3 nothing similar", empty, 200, null, MSG_MATCHED);
        soft.assertThat(empty.body().at("/data").size()).as("Step 3 empty list (UC-31 E1)").isZero();
    }
}
