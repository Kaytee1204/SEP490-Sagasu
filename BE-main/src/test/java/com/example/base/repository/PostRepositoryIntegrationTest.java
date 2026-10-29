package com.example.base.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.base.dto.post.CreateLostPostRequest;
import com.example.base.storage.ImageValidator.ValidatedImage;
import com.example.base.entity.Post;
import com.example.base.entity.PostImage;
import com.example.base.entity.Account;
import com.example.base.entity.Category;
import com.example.base.mapper.PostMapper;
import com.example.base.storage.DisabledImageStorage;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.domain.PageRequest;

import java.util.UUID;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs against the existing PostgreSQL schema. Each test rolls back its fixtures. */
@DataJpaTest(showSql = false, properties = {"spring.jpa.hibernate.ddl-auto=none", "app.storage.r2.enabled=false"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostMapper.class, DisabledImageStorage.class, com.example.base.service.post.PostPrivateIdentifiersService.class,
        com.example.base.service.post.PostService.class})
@ImportAutoConfiguration(JdbcTemplateAutoConfiguration.class)
@EnabledIfEnvironmentVariable(named = "SAGASU_DB_TESTS", matches = "true")
class PostRepositoryIntegrationTest {
    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"24H,23,25", "7D,167,169"})
    void recentTimeRangesFilterByElapsedHoursAndStillExcludePending(String range, int insideHours, int outsideHours) {
        String token = "recent" + UUID.randomUUID().toString().replace("-", "");
        var now = OffsetDateTime.now();
        long included = searchFixture(token, "LOST", "VISIBLE", "Sảnh", now.minusHours(insideHours).toString());
        searchFixture(token, "FOUND", "VISIBLE", "Sảnh", now.minusHours(outsideHours).toString());
        searchFixture(token, "FOUND", "PENDING", "Sảnh", now.minusHours(insideHours).toString());
        searchFixture(token, "FOUND", "VISIBLE", "Sảnh", now.plusHours(1).toString());
        var filter = com.example.base.dto.post.PostSearchFilter.parse(token, "", "", "", "", "", range);
        assertThat(postService.listVisible(1, 12, filter).content())
                .extracting(com.example.base.dto.post.PostSummaryDto::id).containsExactly(included);
    }

    @Test
    void publicSearchIncludesBothTypesButNeverPendingHiddenRejectedOrDeleted() {
        String keyword = "search" + UUID.randomUUID().toString().replace("-", "");
        long lost = searchFixture(keyword, "LOST", "VISIBLE", "Hall A", "2026-10-10T10:00:00+07:00");
        long found = searchFixture(keyword, "FOUND", "VISIBLE", "Hall A", "2026-10-10T10:00:00+07:00");
        for (String type : List.of("LOST", "FOUND")) {
            for (String moderation : List.of("PENDING", "HIDDEN", "REJECTED")) {
                searchFixture(keyword, type, moderation, "Hall A", "2026-10-10T10:00:00+07:00");
            }
        }
        long deleted = searchFixture(keyword, "FOUND", "VISIBLE", "Hall A", "2026-10-10T10:00:00+07:00");
        jdbc.update("update post set deleted_at=now() where post_id=?", deleted);
        var result = search(keyword, "", "", "", "", "", 1, 12);
        assertThat(result.content()).extracting(com.example.base.dto.post.PostSummaryDto::id).containsExactly(found, lost);
        assertThat(result.content()).extracting(com.example.base.dto.post.PostSummaryDto::postType).containsExactly("FOUND", "LOST");
        assertThat(result.totalElements()).isEqualTo(2);
        // UC13 without a filter also returns both types and excludes unpublished rows.
        jdbc.update("update post set created_at='2101-01-01T00:00:00Z' where title=?", keyword);
        entityManager.clear();
        assertThat(postService.listVisible(1, 2).content())
                .extracting(com.example.base.dto.post.PostSummaryDto::id).containsExactly(found, lost);
    }

    @Test
    void combinesAllSearchFiltersAndIncludesTheWholeVietnameseDay() {
        String keyword = "search" + UUID.randomUUID().toString().replace("-", "");
        long start = searchFixture(keyword, "FOUND", "VISIBLE", "Thư viện 100% A", "2026-10-10T00:00:00+07:00");
        long end = searchFixture(keyword, "FOUND", "VISIBLE", "Thư viện 100% A", "2026-10-10T23:59:59.999999+07:00");
        searchFixture(keyword, "FOUND", "VISIBLE", "Thư viện 100% A", "2026-10-09T23:59:59+07:00");
        searchFixture(keyword, "FOUND", "VISIBLE", "Thư viện 100% A", "2026-10-11T00:00:00+07:00");
        searchFixture(keyword, "LOST", "VISIBLE", "Thư viện 100% A", "2026-10-10T12:00:00+07:00");
        searchFixture(keyword, "FOUND", "VISIBLE", "Sảnh B", "2026-10-10T12:00:00+07:00");
        String category = jdbc.queryForObject("select category_id::text from post where post_id=?", String.class, postId);
        var result = search(keyword.toUpperCase(), category, "FOUND", "THƯ VIỆN 100%", "2026-10-10", "2026-10-10", 1, 12);
        assertThat(result.content()).extracting(com.example.base.dto.post.PostSummaryDto::id).containsExactly(end, start);
        assertThat(result.totalElements()).isEqualTo(2);
        assertThat(search(keyword, Long.toString(Long.MAX_VALUE), "", "", "", "", 1, 12).content()).isEmpty();
    }

    @Test
    void keywordUsesPublicDescriptionAndLocationButNotPrivateIdentifiers() {
        String token = "search" + UUID.randomUUID().toString().replace("-", "");
        long description = searchFixture("Title", "LOST", "VISIBLE", "Sảnh", "2026-10-10T10:00:00+07:00");
        jdbc.update("update post set description=? where post_id=?", token, description);
        long location = searchFixture("Title", "FOUND", "VISIBLE", token, "2026-10-10T10:00:00+07:00");
        long privateOnly = searchFixture("Title", "FOUND", "VISIBLE", "Sảnh", "2026-10-10T10:00:00+07:00");
        jdbc.update("update post set private_identifiers=? where post_id=?", token, privateOnly);
        assertThat(search(token, "", "", "", "", "", 1, 12).content())
                .extracting(com.example.base.dto.post.PostSummaryDto::id).containsExactly(location, description);
        assertThat(search("' OR 1=1 --", "", "", "", "", "", 1, 12).content()).isEmpty();
    }

    @Test
    void filteredPaginationCountsPostsOnceAndKeepsNewestOrder() {
        String token = "search" + UUID.randomUUID().toString().replace("-", "");
        long first = searchFixture(token, "LOST", "VISIBLE", "Sảnh", "2026-10-10T10:00:00+07:00");
        long second = searchFixture(token, "FOUND", "VISIBLE", "Sảnh", "2026-10-10T10:00:00+07:00");
        var page1 = search(token, "", "", "", "", "", 1, 1);
        var page2 = search(token, "", "", "", "", "", 2, 1);
        assertThat(page1.content()).extracting(com.example.base.dto.post.PostSummaryDto::id).containsExactly(second);
        assertThat(page2.content()).extracting(com.example.base.dto.post.PostSummaryDto::id).containsExactly(first);
        assertThat(page1.totalElements()).isEqualTo(2);
        assertThat(page1.totalPages()).isEqualTo(2);
        assertThat(search(token, "", "", "", "", "", 3, 1).content()).isEmpty();
    }

    private com.example.base.dto.post.PostPageDto search(String keyword, String category, String type,
            String location, String from, String to, int page, int size) {
        return postService.listVisible(page, size, com.example.base.dto.post.PostSearchFilter.parse(
                keyword, category, type, location, from, to));
    }

    private long searchFixture(String title, String type, String moderation, String location, String eventAt) {
        return jdbc.queryForObject("""
                insert into post(account_id, created_by, category_id, post_type, title, description,
                    location_text, event_at, moderation_status, moderated_by, moderated_at, moderation_note, created_at)
                select account_id, created_by, category_id, ?, ?, 'Public description', ?, cast(? as timestamptz),
                    ?, account_id, now(), 'Test moderation reason', '2026-10-01T00:00:00Z'
                from post where post_id=? returning post_id
                """, Long.class, type, title, location, eventAt, moderation, postId);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired PostRepository posts;
    @Autowired EntityManager entityManager;
    @Autowired PostMapper mapper;
    @Autowired CategoryRepository categories;
    @Autowired AccountRepository accounts;
    @Autowired com.example.base.service.post.PostPrivateIdentifiersService privateIdentifiers;
    @Autowired com.example.base.service.post.PostService postService;
    long postId;

    @BeforeEach
    void fixture() {
        long account = jdbc.queryForObject("""
                INSERT INTO accounts(email, password_hash) VALUES (?, 'not-a-login-hash') RETURNING id
                """, Long.class, "uc14-" + UUID.randomUUID() + "@test.invalid");
        long category = jdbc.queryForObject("SELECT category_id FROM category WHERE is_active ORDER BY category_id LIMIT 1", Long.class);
        postId = jdbc.queryForObject("""
                INSERT INTO post(account_id, created_by, category_id, post_type, title, description,
                    location_text, event_at, private_identifiers, moderation_status, moderated_by, moderated_at)
                VALUES (?, ?, ?, 'LOST', 'Ví', 'Public description', 'Sảnh A', now(),
                    'SECRET-UC14', 'VISIBLE', ?, now()) RETURNING post_id
                """, Long.class, account, account, category, account);
    }

    @Test
    void privateIdentifiersAreOwnerOnlyAndNeverEnterPublicDataOrSearch() throws Exception {
        var post = posts.findById(postId).orElseThrow();
        var owner = new com.example.base.security.CustomUserDetails(post.getAccount());
        privateIdentifiers.update(postId, new com.example.base.dto.post.PrivateIdentifiersDto("  SERIAL-OWNER-ONLY  "), owner);
        entityManager.clear();
        assertThat(privateIdentifiers.read(postId, owner).privateIdentifiers()).isEqualTo("SERIAL-OWNER-ONLY");
        var updated = posts.findVisibleDetail(postId).orElseThrow();
        assertThat(mapper.toDetail(updated, owner.getId()).canEditOwnPost()).isTrue();
        assertThat(mapper.toDetail(updated, -1L).canEditOwnPost()).isFalse();
        var json = new ObjectMapper().findAndRegisterModules();
        assertThat(json.writeValueAsString(mapper.toDetail(updated, owner.getId())))
                .doesNotContain("SERIAL-OWNER-ONLY", "privateIdentifiers");
        assertThat(json.writeValueAsString(mapper.toSummary(updated))).doesNotContain("SERIAL-OWNER-ONLY");
        assertThat(jdbc.queryForObject("SELECT search_document::text FROM post WHERE post_id=?", String.class, postId))
                .doesNotContain("SERIAL-OWNER-ONLY");

        Account other = new Account(); other.setId(-1L); other.setRole(com.example.base.entity.AccountRole.ADMIN);
        other.setStatus(com.example.base.entity.AccountStatus.ACTIVE);
        var outsider = new com.example.base.security.CustomUserDetails(other);
        org.junit.jupiter.api.Assertions.assertThrows(com.example.base.exception.AppException.class,
                () -> privateIdentifiers.read(postId, outsider));
        org.junit.jupiter.api.Assertions.assertThrows(com.example.base.exception.AppException.class,
                () -> privateIdentifiers.update(postId, new com.example.base.dto.post.PrivateIdentifiersDto("stolen"), outsider));
    }

    @Test
    void ownerCanClearIdentifiers() {
        var owner = new com.example.base.security.CustomUserDetails(posts.findById(postId).orElseThrow().getAccount());
        privateIdentifiers.update(postId, new com.example.base.dto.post.PrivateIdentifiersDto("   "), owner);
        entityManager.clear();
        assertThat(privateIdentifiers.read(postId, owner).privateIdentifiers()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT private_identifiers FROM post WHERE post_id=?", String.class, postId)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"P2P", "OFFICE"})
    void persistsFoundPostAndCustodyChoice(String custody) {
        var existing = posts.findById(postId).orElseThrow();
        var found = Post.found(existing.getAccount(), existing.getCategory(), "Found wallet", "Public",
                "Hall A", OffsetDateTime.now().minusDays(1), custody, custody.equals("OFFICE") ? OffsetDateTime.now().plusDays(1) : null);
        found.updatePrivateIdentifiers("FOUND-SECRET");
        var saved = posts.saveAndFlush(found);
        entityManager.clear();
        var loaded = posts.findManagedDetail(saved.getId(), existing.getAccount().getId()).orElseThrow();
        assertThat(loaded.getPostType()).isEqualTo("FOUND");
        assertThat(loaded.getCustodyMode()).isEqualTo(custody);
        assertThat(loaded.getModerationStatus()).isEqualTo(custody.equals("OFFICE") ? "PENDING" : "VISIBLE");
        assertThat(posts.findVisibleDetail(saved.getId()).isPresent()).isEqualTo(custody.equals("P2P"));
        assertThat(mapper.toDetail(loaded, -1L).handoverRequestedAt()).isNull();
        if (custody.equals("OFFICE")) assertThat(mapper.toDetail(loaded, existing.getAccount().getId()).handoverRequestedAt()).isNotNull();
        assertThat(loaded.getPrivateIdentifiers()).isEqualTo("FOUND-SECRET");
    }

    @Test
    void pendingCheckerPostIsPersistedAndOnlyItsOwnerCanViewIt() {
        var existing = posts.findById(postId).orElseThrow();
        long ownerId = existing.getAccount().getId();
        jdbc.update("UPDATE accounts SET role='CHECKER' WHERE id=?", ownerId);
        entityManager.clear();
        var owner = accounts.findActiveAccountForPostCreation(ownerId).orElseThrow();
        var category = categories.findById(existing.getCategory().getCategoryId()).orElseThrow();
        for (String type : List.of("LOST", "FOUND")) {
            var post = type.equals("LOST") ? Post.lost(owner, category, "Lost", "Public", "Hall A", OffsetDateTime.now())
                    : Post.found(owner, category, "Found", "Public", "Hall A", OffsetDateTime.now(), "OFFICE", OffsetDateTime.now().plusDays(1));
            long id = posts.saveAndFlush(post).getId();
            assertThat(post.getModerationStatus()).isEqualTo("PENDING");
            assertThat(posts.findVisibleDetail(id)).isEmpty();
            assertThat(posts.findVisibleByIds(List.of(id))).isEmpty();
            assertThat(postService.getDetailForViewer(id, ownerId).moderationStatus()).isEqualTo("PENDING");
            org.junit.jupiter.api.Assertions.assertThrows(com.example.base.exception.AppException.class,
                    () -> postService.getDetailForViewer(id, -1L));
        }
    }

    @Test
    void categoryManagementAndPostsShareTheSameCatalog() {
        var service = new com.example.base.service.category.CategoryService(categories);
        var created = service.createCategory("Merge test " + UUID.randomUUID());
        long categoryId = created.categoryId();
        assertThat(categories.findActiveForPostCreation(categoryId)).isPresent();
        assertThat(categories.findByActiveTrueOrderByNameAscCategoryIdAsc()).extracting(Category::getCategoryId)
                .contains(categoryId);
        jdbc.update("UPDATE post SET category_id=? WHERE post_id=?", categoryId, postId);
        entityManager.clear();
        assertThat(mapper.toDetail(posts.findVisibleDetail(postId).orElseThrow()).categoryId())
                .isEqualTo(categoryId);

        service.updateCategory(categoryId, "Renamed " + UUID.randomUUID());
        service.updateCategoryStatus(categoryId, false);
        entityManager.clear();
        assertThat(categories.existsByCategoryIdAndActiveTrue(categoryId)).isFalse();
        assertThat(categories.findActiveForPostCreation(categoryId)).isEmpty();
        assertThat(categories.findByActiveTrueOrderByNameAscCategoryIdAsc()).extracting(Category::getCategoryId)
                .doesNotContain(categoryId);
        assertThat(posts.findVisibleDetail(postId)).isPresent();
    }

    @Test
    void listPaginatesNewestFirstWithIdTieBreaker() {
        jdbc.update("UPDATE post SET created_at='2100-01-01T00:00:00Z' WHERE post_id=?", postId);
        long newest = jdbc.queryForObject("""
                INSERT INTO post(account_id,created_by,category_id,post_type,title,description,location_text,
                  event_at,moderation_status,moderated_by,moderated_at,created_at)
                SELECT account_id,created_by,category_id,'FOUND','New found','Description','Hall B',now(),
                  'VISIBLE',moderated_by,now(),'2100-01-01T00:00:00Z' FROM post WHERE post_id=? RETURNING post_id
                """, Long.class, postId);
        var firstPage = posts.findVisibleIds(PageRequest.of(0, 1));
        var secondPage = posts.findVisibleIds(PageRequest.of(1, 1));
        assertThat(firstPage.getContent()).containsExactly(newest);
        assertThat(secondPage.getContent()).containsExactly(postId);
        assertThat(firstPage.getTotalElements()).isEqualTo(jdbc.queryForObject("""
                SELECT count(*) FROM post WHERE moderation_status='VISIBLE' AND deleted_at IS NULL
                """, Long.class));
        assertThat(posts.findVisibleByIds(List.of(newest))).hasSize(1);
    }

    @Test
    void insertsUserLostPostWithOwnerAndImmediateVisibilityWithoutApprovalActor() {
        var existing = jdbc.queryForMap("SELECT account_id,category_id FROM post WHERE post_id=?", postId);
        long owner = ((Number) existing.get("account_id")).longValue();
        long category = ((Number) existing.get("category_id")).longValue();
        assertThat(categories.existsByCategoryIdAndActiveTrue(category)).isTrue();
        assertThat(categories.findActiveForPostCreation(category)).isPresent();
        assertThat(accounts.findActiveAccountForPostCreation(owner)).isPresent();
        var request = new CreateLostPostRequest("  Lost wallet  ", " Public description ", category,
                " Hall A ", OffsetDateTime.now().minusDays(1), null);
        var post = Post.lost(entityManager.getReference(Account.class, owner), entityManager.getReference(Category.class, category),
                request.title().trim(), request.description().trim(), request.locationText().trim(), request.eventAt());
        var image = new ValidatedImage(new byte[]{1}, "image/png", "png", 2, 3, "a".repeat(64));
        post.addImage(new PostImage("posts/test/image.png", 0, image.mime(), image.bytes().length,
                image.width(), image.height(), image.hash()));
        long createdId = posts.saveAndFlush(post).getId();
        var row = jdbc.queryForMap("SELECT * FROM post WHERE post_id=?", createdId);
        assertThat(row).containsEntry("account_id", owner).containsEntry("created_by", owner)
                .containsEntry("title", "Lost wallet").containsEntry("moderation_status", "VISIBLE")
                .containsEntry("post_type", "LOST").containsEntry("status", "OPEN").containsEntry("custody_mode", "P2P");
        assertThat(row.get("private_identifiers")).isNull();
        assertThat(row.get("moderated_by")).isNull();
        assertThat(row.get("moderated_at")).isNull();
        assertThat(posts.findVisibleDetail(createdId)).isPresent();
        assertThat(posts.findVisibleByIds(List.of(createdId))).hasSize(1);
        var saved = jdbc.queryForMap("SELECT * FROM post_image WHERE post_id=?", createdId);
        assertThat(saved).containsEntry("storage_key", "posts/test/image.png").containsEntry("mime_type", "image/png");
        assertThat(saved.get("image_url")).isNull();
    }

    @Test
    void returnsOrderedImagesWithoutSecretsOrStorageKeys() throws Exception {
        jdbc.update("INSERT INTO post_image(post_id,storage_key,image_url,sort_order) VALUES (?, ?, ?, ?)",
                postId, "private-key-2", "https://example.test/2.jpg", 2);
        jdbc.update("INSERT INTO post_image(post_id,storage_key,image_url,sort_order) VALUES (?, ?, ?, ?)",
                postId, "private-key-1", "https://example.test/1.jpg", 1);
        var detail = posts.findVisibleDetail(postId).orElseThrow();
        assertThat(detail.getImages()).extracting(image -> image.getSortOrder()).containsExactly(1, 2);
        assertThat(mapper.toSummary(detail).thumbnailUrl()).isEqualTo("https://example.test/1.jpg");
        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(mapper.toDetail(detail));
        assertThat(json).doesNotContain("SECRET-UC14", "privateIdentifiers", "private_identifiers", "private-key");
    }

    @ParameterizedTest
    @ValueSource(strings = {"REJECTED", "HIDDEN"})
    void rejectAndHideStillRequireAReviewActor(String moderation) {
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE post SET moderation_status=?, moderated_by=NULL, moderated_at=NULL, moderation_note='reason' WHERE post_id=?",
                        moderation, postId));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "REJECTED", "HIDDEN"})
    void hidesUnpublishedPosts(String moderation) {
        jdbc.update("UPDATE post SET moderation_status=?, moderation_note='test reason' WHERE post_id=?", moderation, postId);
        assertThat(posts.findVisibleDetail(postId)).isEmpty();
        assertThat(posts.findVisibleIds(PageRequest.of(0, 100)).getContent()).doesNotContain(postId);
        assertThat(posts.findVisibleByIds(java.util.List.of(postId))).isEmpty();
    }

    @Test
    void hidesSoftDeletedPosts() {
        jdbc.update("UPDATE post SET deleted_at=now() WHERE post_id=?", postId);
        assertThat(posts.findVisibleDetail(postId)).isEmpty();
        assertThat(posts.findVisibleIds(PageRequest.of(0, 100)).getContent()).doesNotContain(postId);
    }

    @ParameterizedTest
    @ValueSource(strings = {"OPEN", "RESERVED", "RETURNED", "CLOSED"})
    void visiblePostsRemainReadableAcrossHandlingStatuses(String status) {
        jdbc.update("UPDATE post SET status=? WHERE post_id=?", status, postId);
        assertThat(posts.findVisibleDetail(postId)).isPresent();
        assertThat(posts.findVisibleByIds(java.util.List.of(postId))).hasSize(1);
    }
}
