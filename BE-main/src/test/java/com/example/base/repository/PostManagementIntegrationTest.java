package com.example.base.repository;

import com.example.base.dto.post.*;
import com.example.base.entity.*;
import com.example.base.exception.*;
import com.example.base.security.CustomUserDetails;
import com.example.base.service.post.*;
import com.example.base.storage.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import java.time.OffsetDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(showSql=false, properties={"spring.jpa.hibernate.ddl-auto=none","app.storage.r2.enabled=false"})
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Import({PostManagementService.class,ImageValidator.class})
@ImportAutoConfiguration(JdbcTemplateAutoConfiguration.class)
@EnabledIfEnvironmentVariable(named="SAGASU_DB_TESTS",matches="true")
class PostManagementIntegrationTest {
    @Autowired PostManagementService service;
    @Autowired PostRepository posts;
    @Autowired AccountRepository accounts;
    @Autowired CategoryRepository categories;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @MockBean ImageStorage storage;
    CustomUserDetails user, outsider;
    long postId, ownerId, otherId, categoryId;

    @BeforeEach void fixture() {
        ownerId = newAccount(); otherId=newAccount();
        var owner=accounts.findById(ownerId).orElseThrow();
        user=new CustomUserDetails(owner); outsider=new CustomUserDetails(accounts.findById(otherId).orElseThrow());
        categoryId=jdbc.queryForObject("SELECT category_id FROM category WHERE is_active ORDER BY category_id LIMIT 1",Long.class);
        var post=Post.lost(owner,categories.findById(categoryId).orElseThrow(),"Original","Public description","Hall A",OffsetDateTime.now().minusDays(1));
        post.updatePrivateIdentifiers("ORIGINAL-PRIVATE");
        postId=posts.saveAndFlush(post).getId(); em.clear();
    }
    long newAccount() {
        return jdbc.queryForObject("INSERT INTO accounts(email,password_hash) VALUES (?, 'not-a-login') RETURNING id",
                Long.class,"management-"+UUID.randomUUID()+"@example.test");
    }
    UpdatePostRequest request(List<Long> kept) {
        var edit=service.getForEdit(postId,user);
        return new UpdatePostRequest("Updated","Changed public description",categoryId,"Hall B",edit.eventAt(),"P2P",kept,edit.updatedAt(),null);
    }

    void expectCode(Runnable action,ErrorCode expected) {
        assertThatThrownBy(action::run).isInstanceOf(AppException.class)
                .satisfies(ex->assertThat(((AppException)ex).getErrorCode()).isEqualTo(expected));
    }

    @Test void mineIsAuthorScopedAndSoftDeleteKeepsDataButHidesThePost() {
        var mine = service.listMine(1, 1, "ALL", user);
        assertThat(mine.totalElements()).isEqualTo(1);
        assertThat(mine.content().getFirst().id()).isEqualTo(postId);
        assertThat(mine.content().getFirst().moderationStatus()).isEqualTo("VISIBLE");
        assertThat(service.listMine(1, 12, "ALL", outsider).content()).isEmpty();
        jdbc.update("INSERT INTO post_image(post_id,storage_key,sort_order) VALUES (?,'test-soft-delete-image',0)", postId);
        em.clear();

        service.delete(postId, new DeletePostRequest("No longer needed"), user);

        assertThat(service.listMine(1, 12, "ALL", user).content()).isEmpty();
        assertThat(posts.findVisibleDetail(postId)).isEmpty();
        assertThat(posts.findOwnDetail(postId, ownerId)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM post WHERE post_id=?", Boolean.class, postId)).isTrue();
        assertThat(jdbc.queryForObject("SELECT deletion_reason FROM post WHERE post_id=?", String.class, postId)).isEqualTo("No longer needed");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM post_image WHERE post_id=?", Long.class, postId)).isEqualTo(1);
        verifyNoInteractions(storage);
    }

    @Test void listUsesCreatedByRatherThanAnotherOwnerId() {
        jdbc.update("UPDATE post SET created_by=? WHERE post_id=?",otherId,postId); em.clear();
        assertThat(service.listMine(1,12,"ALL",user).content()).isEmpty();
        var postedOnBehalf=service.listMine(1,12,"ALL",outsider).content().getFirst();
        assertThat(postedOnBehalf.canEdit()).isFalse(); assertThat(postedOnBehalf.canDelete()).isFalse();
    }

    @Test void cannotEditOrDeleteSomeoneElsesPost() {
        var edit=request(List.of());
        expectCode(()->service.update(postId,edit,List.of(),outsider),ErrorCode.POST_OWNER_REQUIRED);
        expectCode(()->service.delete(postId,new DeletePostRequest("bad"),outsider),ErrorCode.POST_OWNER_REQUIRED);
        verifyNoInteractions(storage);
    }

    @ParameterizedTest @ValueSource(strings={"USER","CHECKER","EXTERNAL_USER"})
    void editingReappliesCurrentRolePolicyAndPreservesSecrets(String role) {
        jdbc.update("UPDATE accounts SET role=? WHERE id=?",role,ownerId); em.clear();
        user=new CustomUserDetails(accounts.findById(ownerId).orElseThrow());
        var result=service.update(postId,request(List.of()),List.of(),user); em.clear();
        var saved=posts.findById(postId).orElseThrow();
        assertThat(saved.getTitle()).isEqualTo("Updated");
        assertThat(saved.getPrivateIdentifiers()).isEqualTo("ORIGINAL-PRIVATE");
        assertThat(result.moderationStatus()).isEqualTo(role.equals("USER")?"VISIBLE":"PENDING");
        assertThat(saved.getModeratedBy()).isNull();
    }

    @Test void staleFormAndForeignImageIdsAreRejected() {
        var edit=request(List.of());
        service.update(postId,edit,List.of(),user); em.clear();
        expectCode(()->service.update(postId,edit,List.of(),user),ErrorCode.POST_STALE);
        expectCode(()->service.update(postId,request(List.of(-123L)),List.of(),user),ErrorCode.POST_IMAGES_INVALID);
    }

    @Test void switchingFoundToOfficeStoresProposedTimeAndAlwaysReturnsPending() {
        jdbc.update("UPDATE post SET post_type='FOUND' WHERE post_id=?", postId); em.clear();
        var proposed = OffsetDateTime.now().plusDays(1).withNano(0);
        var edit = service.getForEdit(postId, user);
        var office = new UpdatePostRequest(edit.title(), edit.description(), categoryId, edit.locationText(),
                edit.eventAt(), "OFFICE", List.of(), edit.updatedAt(), proposed);
        assertThat(service.update(postId, office, List.of(), user).moderationStatus()).isEqualTo("PENDING");
        em.clear();
        assertThat(posts.findVisibleDetail(postId)).isEmpty();
        assertThat(service.getForEdit(postId, user).handoverRequestedAt().isEqual(proposed)).isTrue();
        var latest = service.getForEdit(postId, user);
        var changed = new UpdatePostRequest("Office updated", latest.description(), categoryId, latest.locationText(),
                latest.eventAt(), "OFFICE", List.of(), latest.updatedAt(), proposed.plusHours(1));
        assertThat(service.update(postId, changed, List.of(), user).moderationStatus()).isEqualTo("PENDING");
    }

    @Test void refreshUnderLockRejectsAConcurrentHide() {
        var edit = request(List.of());
        jdbc.update("UPDATE post SET moderation_status='HIDDEN',moderated_by=?,moderated_at=now(),moderation_note='Hidden concurrently' WHERE post_id=?", ownerId, postId);
        expectCode(() -> service.update(postId, edit, List.of(), user), ErrorCode.POST_NOT_EDITABLE);
        assertThat(jdbc.queryForObject("SELECT title FROM post WHERE post_id=?", String.class, postId)).isEqualTo("Original");
    }

    @Test void refreshUnderLockDetectsAConcurrentContentUpdate() {
        var edit = request(List.of());
        jdbc.update("UPDATE post SET title='Changed by another request' WHERE post_id=?", postId);
        expectCode(() -> service.update(postId, edit, List.of(), user), ErrorCode.POST_STALE);
        assertThat(jdbc.queryForObject("SELECT title FROM post WHERE post_id=?", String.class, postId))
                .isEqualTo("Changed by another request");
    }

    @Test void hiddenPostsCannotBypassModerationByEditing() {
        var edit=request(List.of());
        jdbc.update("UPDATE post SET moderation_status='HIDDEN',moderated_by=?,moderated_at=now(),moderation_note='hidden' WHERE post_id=?",ownerId,postId); em.clear();
        expectCode(()->service.update(postId,edit,List.of(),user),ErrorCode.POST_NOT_EDITABLE);
    }

    MockMultipartFile png() throws Exception {
        var bytes=new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",bytes);
        return new MockMultipartFile("images","test.png","image/png",bytes.toByteArray());
    }

    @Test void editsImagesAtomicallyWithoutUniquePositionConflicts() throws Exception {
        jdbc.update("INSERT INTO post_image(post_id,storage_key,sort_order) VALUES (?,'old-1',0),(?,'old-2',1)",postId,postId); em.clear();
        var original=service.getForEdit(postId,user).images();
        var result=service.update(postId,request(List.of(original.getLast().id())),List.of(png()),user);
        jdbc.execute("SET CONSTRAINTS uq_post_image_order IMMEDIATE"); em.clear();
        var saved=posts.findManagedDetail(result.id(),ownerId).orElseThrow();
        assertThat(saved.getImages()).hasSize(2);
        assertThat(saved.getImages().getFirst().getStorageKey()).isEqualTo("old-2");
        assertThat(saved.getImages().getLast().getSortOrder()).isEqualTo(1);
        verify(storage).delete("old-1");
        verify(storage,never()).delete("old-2");
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"1,1,1", "1,1,4", "2,2,1", "2,1,1", "1,0,1", "2,0,0", "2,1,4"})
    void addingReplacingAndRemovingImagesUsesDistinctPositions(int oldCount, int keptCount, int newCount) throws Exception {
        for (int index = 0; index < oldCount; index++) {
            jdbc.update("INSERT INTO post_image(post_id,storage_key,sort_order) VALUES (?,?,?)", postId, "fixture-old-" + index, index);
        }
        em.clear();
        var images = service.getForEdit(postId, user).images();
        var keptIds = images.stream().skip(oldCount - keptCount).map(PostDetailDto.ImageDto::id).toList();
        var update = request(keptIds);
        var uploadedFiles = new ArrayList<org.springframework.web.multipart.MultipartFile>();
        for (int index = 0; index < newCount; index++) uploadedFiles.add(png());

        service.update(postId, update, uploadedFiles, user);
        em.clear();

        var saved = posts.findManagedDetail(postId, ownerId).orElseThrow().getImages();
        assertThat(saved).hasSize(keptCount + newCount);
        assertThat(saved).extracting(PostImage::getSortOrder)
                .containsExactlyElementsOf(java.util.stream.IntStream.range(0, keptCount + newCount).boxed().toList());
        assertThat(saved.stream().limit(keptCount).map(PostImage::getId).toList()).containsExactlyElementsOf(keptIds);
        verify(storage, times(newCount)).put(anyString(), any(), eq("image/png"));
        verify(storage, times(oldCount - keptCount)).delete(startsWith("fixture-old-"));
    }


    @Test void softDeleteDuringUploadBlocksSaveAndCleansNewUpload() throws Exception {
        var edit = request(List.of());
        var image = png();
        doAnswer(invocation -> {
            jdbc.update("UPDATE post SET deleted_at=now(),deletion_reason='Deleted during upload' WHERE post_id=?", postId);
            return null;
        }).when(storage).put(anyString(), any(), anyString());
        expectCode(() -> service.update(postId, edit, List.of(image), user), ErrorCode.POST_OWNER_REQUIRED);
        verify(storage).delete(startsWith("posts/" + ownerId + "/"));
        assertThat(jdbc.queryForObject("SELECT title FROM post WHERE post_id=?", String.class, postId)).isEqualTo("Original");
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM post WHERE post_id=?", Boolean.class, postId)).isTrue();
    }
}
