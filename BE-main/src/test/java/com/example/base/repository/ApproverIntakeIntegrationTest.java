package com.example.base.repository;

import com.example.base.dto.receipt.*;
import com.example.base.entity.*;
import com.example.base.exception.*;
import com.example.base.mapper.*;
import com.example.base.security.CustomUserDetails;
import com.example.base.service.approver.ApproverIntakeService;
import com.example.base.service.post.*;
import com.example.base.service.receipt.ReceiptLocationService;
import com.example.base.storage.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Isolated in-memory DB: exercises real transactions, row locks, repositories and DTOs. */
@DataJpaTest(showSql = false, properties = {
        "spring.datasource.url=jdbc:h2:mem:intake;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.sql.init.mode=never", "app.storage.r2.enabled=false"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ApproverIntakeService.class, ReceiptLocationService.class, ReceiptPostPublisher.class,
        ReceiptMapper.class, PostMapper.class, PostService.class, PostManagementService.class, ImageValidator.class})
@ImportAutoConfiguration(JdbcTemplateAutoConfiguration.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ApproverIntakeIntegrationTest {
    @Autowired ApproverIntakeService service;
    @Autowired PostService publicPosts;
    @Autowired PostManagementService management;
    @Autowired AccountRepository accounts;
    @Autowired PostRepository posts;
    @Autowired ItemReceiptRepository receipts;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @MockBean ImageStorage storage;
    CustomUserDetails officer;
    long categoryId, locationId;

    @BeforeEach void fixture() {
        Account account = new Account();
        account.setEmail(UUID.randomUUID() + "@example.test");
        account.setPasswordHash("test-only"); account.setFullName("Nguyễn Văn An");
        account.setRole(AccountRole.APPROVER);
        officer = new CustomUserDetails(accounts.saveAndFlush(account));
        jdbc.update("insert into category(name,is_active,created_at,updated_at) values ('Wallet',true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        categoryId = jdbc.queryForObject("select max(category_id) from category", Long.class);
        jdbc.update("insert into storage_location(code,name,is_active) values (?, 'Office A', true)", UUID.randomUUID().toString());
        locationId = jdbc.queryForObject("select max(storage_location_id) from storage_location", Long.class);
        when(storage.readUrl(anyString(), any())).thenAnswer(call -> "https://images.example.test/" + call.getArgument(0));
    }

    MockMultipartFile image() throws Exception {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(3, 3, BufferedImage.TYPE_INT_RGB), "png", out);
        return new MockMultipartFile("files", "wallet.png", "image/png", out.toByteArray());
    }

    SaveReceiptRequest request(Long location) {
        return new SaveReceiptRequest(categoryId, "Brown wallet", "Brown leather wallet", "Scratched",
                "Hall A", OffsetDateTime.now().minusDays(1), "SECRET-SERIAL", "INTERNAL-NOTE", List.of(), null, location);
    }

    ReceiptDto draft(Long location) throws Exception {
        return service.create(request(location), List.of(image()), officer);
    }

    void expectCode(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(code));
    }

    @Test void draftThenReceivePublishesOnceWithPublicMetadataAndLocksEditing() throws Exception {
        var draft = draft(locationId);
        assertThat(draft.source()).isEqualTo("WALK_IN");
        assertThat(draft.postId()).isNull();
        assertThat(service.list(1, 100, "", "DRAFT", officer).getContent()).anyMatch(r -> r.id().equals(draft.id()));
        var published = service.submit(draft.id(), new SubmitReceiptRequest(draft.updatedAt()), officer);
        assertThat(published.status()).isEqualTo("IN_STORAGE");
        assertThat(published.reviewStatus()).isEqualTo("ACCEPTED");
        assertThat(published.receivedBy()).isEqualTo("Nguyễn Văn An");
        assertThat(published.receivedAt()).isNotNull();
        var repeat = service.submit(draft.id(), new SubmitReceiptRequest(draft.updatedAt()), officer);
        assertThat(repeat.postId()).isEqualTo(published.postId());
        assertThat(jdbc.queryForObject("select count(*) from post where created_by=?", Long.class, officer.getId())).isEqualTo(1);
        var detail = publicPosts.getDetailForViewer(published.postId(), officer.getId());
        assertThat(detail.postType()).isEqualTo("FOUND");
        assertThat(detail.custodyMode()).isEqualTo("OFFICE");
        assertThat(detail.moderationStatus()).isEqualTo("VISIBLE");
        assertThat(detail.authorName()).isEqualTo("Nguyễn Văn An");
        assertThat(detail.officeReceipt().storageLocationName()).isEqualTo("Office A");
        assertThat(detail.canEditOwnPost()).isFalse();
        assertThat(detail.images()).hasSize(1);
        assertThat(new ObjectMapper().findAndRegisterModules().writeValueAsString(detail))
                .doesNotContain("SECRET-SERIAL", "INTERNAL-NOTE", "finderContact");
        var summary = publicPosts.listVisible(1, 100).content().stream().filter(p -> p.id().equals(published.postId())).findFirst().orElseThrow();
        assertThat(summary.authorName()).isEqualTo(detail.authorName());
        assertThat(summary.officeReceipt()).isEqualTo(detail.officeReceipt());
        var update = new SaveReceiptRequest(categoryId, "Changed", "Description", "Good", "Hall A", draft.foundAt(),
                null, null, List.of(draft.images().getFirst().id()), published.updatedAt(), locationId);
        expectCode(() -> service.update(draft.id(), update, List.of(), officer), ErrorCode.RECEIPT_NOT_EDITABLE);
    }

    @Test void missingInactiveLocationAndStaleVersionDoNotPublish() throws Exception {
        var missing = draft(null);
        expectCode(() -> service.submit(missing.id(), new SubmitReceiptRequest(missing.updatedAt()), officer), ErrorCode.BAD_REQUEST);
        var valid = draft(locationId);
        expectCode(() -> service.submit(valid.id(), new SubmitReceiptRequest(valid.updatedAt().minusSeconds(1)), officer), ErrorCode.RECEIPT_STALE);
        jdbc.update("update storage_location set is_active=false where storage_location_id=?", locationId);
        expectCode(() -> service.submit(valid.id(), new SubmitReceiptRequest(valid.updatedAt()), officer), ErrorCode.BAD_REQUEST);
        assertThat(service.detail(valid.id(), officer).postId()).isNull();
        assertThat(jdbc.queryForObject("select count(*) from post where created_by=?", Long.class, officer.getId())).isZero();
    }

    @Test void wrongRoleOtherOfficerAndRevokedRoleCannotConfirm() throws Exception {
        var draft = draft(locationId);
        Account user = new Account(); user.setId(officer.getId()); user.setRole(AccountRole.USER);
        expectCode(() -> service.submit(draft.id(), new SubmitReceiptRequest(draft.updatedAt()), new CustomUserDetails(user)), ErrorCode.FORBIDDEN);
        var original = officer;
        fixture();
        assertThat(service.detail(draft.id(), officer).id()).isEqualTo(draft.id());
        jdbc.update("update accounts set role='USER' where id=?", original.getId());
        expectCode(() -> service.submit(draft.id(), new SubmitReceiptRequest(draft.updatedAt()), original), ErrorCode.FORBIDDEN);
    }

    @Test void publicationFailureRollsBackReceiptAndCanBeRetried() throws Exception {
        var draft = draft(locationId);
        // Fail during the real post INSERT, then remove the temporary test-only constraint.
        jdbc.execute("alter table post add constraint intake_test_failure check (created_by <> " + officer.getId() + ")");
        try {
            assertThatThrownBy(() -> service.submit(draft.id(), new SubmitReceiptRequest(draft.updatedAt()), officer)).isInstanceOf(RuntimeException.class);
            var after = service.detail(draft.id(), officer);
            assertThat(after.status()).isEqualTo("DRAFT");
            assertThat(after.receivedAt()).isNull(); assertThat(after.postId()).isNull();
        } finally { jdbc.execute("alter table post drop constraint intake_test_failure"); }
        assertThat(service.submit(draft.id(), new SubmitReceiptRequest(draft.updatedAt()), officer).postId()).isNotNull();
    }

    @Test void failureAfterPostInsertRollsBackBothRecords() throws Exception {
        var draft = draft(locationId);
        jdbc.execute("alter table item_receipt add constraint intake_receipt_failure check (status <> 'IN_STORAGE' or item_receipt_id <> " + draft.id() + ")");
        try {
            assertThatThrownBy(() -> service.submit(draft.id(), new SubmitReceiptRequest(draft.updatedAt()), officer)).isInstanceOf(RuntimeException.class);
            assertThat(jdbc.queryForObject("select count(*) from post where created_by=?", Long.class, officer.getId())).isZero();
            assertThat(service.detail(draft.id(), officer).status()).isEqualTo("DRAFT");
        } finally { jdbc.execute("alter table item_receipt drop constraint intake_receipt_failure"); }
    }

    @Test void concurrentConfirmationsReturnTheSamePost() throws Exception {
        var draft = draft(locationId);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            Callable<Long> submit = () -> { start.await(); return service.submit(draft.id(), new SubmitReceiptRequest(draft.updatedAt()), officer).postId(); };
            var first = executor.submit(submit); var second = executor.submit(submit); start.countDown();
            assertThat(first.get(15, TimeUnit.SECONDS)).isEqualTo(second.get(15, TimeUnit.SECONDS));
        }
        assertThat(jdbc.queryForObject("select count(*) from post where created_by=?", Long.class, officer.getId())).isEqualTo(1);
    }

    @Test void internalImagesStayPrivateAndOfficialPostCannotBeEditedAfterRoleChange() throws Exception {
        var draft = service.create(request(locationId), List.of(image(), image()), officer);
        jdbc.update("update item_receipt_image set is_sensitive=true where item_receipt_image_id=?", draft.images().getFirst().id());
        var receipt = service.submit(draft.id(), new SubmitReceiptRequest(draft.updatedAt()), officer);
        assertThat(publicPosts.getDetailForViewer(receipt.postId(), null).images()).hasSize(1);
        jdbc.update("update accounts set role='USER' where id=?", officer.getId());
        var changed = new CustomUserDetails(accounts.findById(officer.getId()).orElseThrow());
        expectCode(() -> management.getForEdit(receipt.postId(), changed), ErrorCode.POST_NOT_EDITABLE);
        expectCode(() -> management.delete(receipt.postId(), new com.example.base.dto.post.DeletePostRequest("Delete"), changed), ErrorCode.POST_NOT_EDITABLE);
    }

    @Test void normalOfficeRequestHasNoVerificationAndAuthorIsCreatedBy() {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            var owner = accounts.findById(officer.getId()).orElseThrow();
            owner.setRole(AccountRole.USER);
            var category = new Category();
            org.springframework.test.util.ReflectionTestUtils.setField(category, "categoryId", categoryId);
            Post post = Post.found(owner, category, "User request", "Description", "Hall", OffsetDateTime.now(), "OFFICE", OffsetDateTime.now().plusDays(1));
            posts.saveAndFlush(post);
            var detail = publicPosts.getDetailForViewer(post.getId(), owner.getId());
            assertThat(detail.officeReceipt()).isNull();
            assertThat(detail.moderationStatus()).isEqualTo("PENDING");
            assertThat(detail.authorName()).isEqualTo("Nguyễn Văn An");
        });
    }
    @Test void officeCanSeeAndCompleteSubmittedReceiptsWithoutChangingOriginalAuthor() throws Exception {
        var draft = draft(locationId);
        Account legacy = new Account(); legacy.setEmail(UUID.randomUUID()+"@example.test");
        legacy.setFullName("Legacy recorder"); legacy.setPasswordHash("test-only"); legacy.setRole(AccountRole.APPROVER);
        accounts.saveAndFlush(legacy);
        jdbc.update("update item_receipt set recorded_by=?, source='WALK_IN',status='SUBMITTED',review_status='ACCEPTED' where item_receipt_id=?",legacy.getId(),draft.id());
        var detail = service.detail(draft.id(),officer);
        assertThat(detail.canProcess()).isTrue(); assertThat(detail.recordedBy()).isEqualTo("Legacy recorder");
        assertThat(service.list(1,100,"", "SUBMITTED",officer).getContent()).anyMatch(r -> r.id().equals(draft.id()));
        var request = new SaveReceiptRequest(categoryId,"Updated legacy wallet","Public description","Good","Hall B",draft.foundAt(),null,null,
            List.of(draft.images().getFirst().id()),detail.updatedAt(),locationId);
        var updated = service.update(draft.id(),request,List.of(),officer);
        var received = service.submit(draft.id(),new SubmitReceiptRequest(updated.updatedAt()),officer);
        assertThat(received.recordedBy()).isEqualTo("Legacy recorder");
        assertThat(received.receivedBy()).isEqualTo("Nguyễn Văn An");
        assertThat(received.source()).isEqualTo("WALK_IN");
        assertThat(received.canProcess()).isFalse();
        assertThat(publicPosts.getDetailForViewer(received.postId(),null).authorName()).isEqualTo("Nguyễn Văn An");
    }

}
