package com.example.base.service.post;

import com.example.base.dto.post.CreatedPostDto;
import com.example.base.dto.post.DeletePostRequest;
import com.example.base.dto.post.EditPostDto;
import com.example.base.dto.post.MyPostPageDto;
import com.example.base.dto.post.UpdatePostRequest;
import com.example.base.entity.Account;
import com.example.base.entity.AccountRole;
import com.example.base.entity.AccountStatus;
import com.example.base.entity.Category;
import com.example.base.entity.Post;
import com.example.base.entity.PostImage;
import com.example.base.exception.AppException;
import com.example.base.exception.ErrorCode;
import com.example.base.repository.AccountRepository;
import com.example.base.repository.CategoryRepository;
import com.example.base.repository.ClaimRepository;
import com.example.base.repository.ItemReceiptRepository;
import com.example.base.repository.PostRepository;
import com.example.base.security.CustomUserDetails;
import com.example.base.service.office.OfficeTransferService;
import com.example.base.storage.ImageStorage;
import com.example.base.storage.ImageValidator;
import com.example.base.storage.ImageValidator.ValidatedImage;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostManagementServiceTest {

    @Mock
    private PostRepository posts;
    @Mock
    private AccountRepository accounts;
    @Mock
    private CategoryRepository categories;
    @Mock
    private ImageStorage storage;
    @Mock
    private ImageValidator validator;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private EntityManager entityManager;
    @Mock
    private ItemReceiptRepository receipts;
    @Mock
    private ClaimRepository claims;
    @Mock
    private OfficeTransferService officeTransfers;

    @InjectMocks
    private PostManagementService service;

    private Account owner;
    private CustomUserDetails user;
    private Category category;

    @BeforeEach
    void setUp() {
        owner = account(5L);
        user = new CustomUserDetails(owner);
        category = category(3L, "Wallet");
        lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    }

    @Test
    void listMine_whenAuthorHasThreePosts_preservesOrderMapsThumbnailsAndPendingCounts() {
        Post p1 = foundPost(11L, owner, category);
        addImage(p1, 1L, "k1", 0);
        addImage(p1, 2L, "k2", 1);
        Post p2 = foundPost(12L, owner, category);
        ReflectionTestUtils.setField(p2, "status", "CLOSED");
        Post p3 = foundPost(13L, owner, category);
        ReflectionTestUtils.setField(p3, "moderationStatus", "HIDDEN");

        Page<Long> idPage = new PageImpl<>(List.of(13L, 11L, 12L), PageRequest.of(0, 10), 3);
        when(posts.findMyIds(5L, "ALL", PageRequest.of(0, 10))).thenReturn(idPage);
        when(posts.findMyByIds(List.of(13L, 11L, 12L), 5L)).thenReturn(List.of(p1, p2, p3));
        mockPendingCounts(List.of(tuple(11L, 2L)));
        when(storage.readUrl("k1", null)).thenReturn("https://cdn/k1");
        when(claims.existsByPost_Id(anyLong())).thenAnswer(inv -> Long.valueOf(11L).equals(inv.getArgument(0)));
        when(claims.existsByPost_IdAndStatusIn(anyLong(), eq(Set.of("PENDING", "NEEDS_INFO", "APPROVED"))))
                .thenAnswer(inv -> Long.valueOf(11L).equals(inv.getArgument(0)));

        MyPostPageDto result = service.listMine(1, 10, "ALL", user);

        assertEquals(3, result.content().size());
        assertEquals(13L, result.content().get(0).id());
        assertEquals("Bài đã bị ẩn bởi kiểm duyệt", result.content().get(0).editBlockedReason());
        assertEquals(11L, result.content().get(1).id());
        assertEquals("https://cdn/k1", result.content().get(1).thumbnailUrl());
        assertFalse(result.content().get(1).canEdit());
        assertEquals("Bài đã có hồ sơ claim", result.content().get(1).editBlockedReason());
        assertFalse(result.content().get(1).canDelete());
        assertEquals("Bài còn claim đang chờ hoặc đã được chấp thuận", result.content().get(1).deleteBlockedReason());
        assertEquals(2L, result.content().get(1).pendingClaimCount());
        assertEquals(12L, result.content().get(2).id());
        assertEquals("Bài không còn ở trạng thái mở", result.content().get(2).editBlockedReason());
    }

    @Test
    void listMine_whenInvalidPaginationOrFilter_throwsExpectedErrorCode() {
        AppException p0 = assertThrows(AppException.class, () -> service.listMine(0, 10, "ALL", user));
        assertEquals(ErrorCode.POST_PAGINATION_INVALID, p0.getErrorCode());

        AppException s101 = assertThrows(AppException.class, () -> service.listMine(1, 101, "ALL", user));
        assertEquals(ErrorCode.POST_PAGINATION_INVALID, s101.getErrorCode());

        AppException badFilter = assertThrows(AppException.class, () -> service.listMine(1, 10, "DELETED", user));
        assertEquals(ErrorCode.POST_FILTER_INVALID, badFilter.getErrorCode());

        AppException nullFilter = assertThrows(AppException.class, () -> service.listMine(1, 10, null, user));
        assertEquals(ErrorCode.POST_FILTER_INVALID, nullFilter.getErrorCode());
    }

    @Test
    void listMine_whenBoundarySize100AndPendingFilterAndEmptyPage_skipsSecondaryQueries() {
        when(posts.findMyIds(5L, "PENDING", PageRequest.of(0, 100)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));

        MyPostPageDto result = service.listMine(1, 100, "PENDING", user);

        assertTrue(result.content().isEmpty());
        verify(posts, never()).findMyByIds(anyList(), eq(5L));
        verify(entityManager, never()).createQuery(anyString(), eq(Tuple.class));
    }

    @Test
    void getForEdit_whenEditableOwner_returnsEditPostDtoWithImageUrls() {
        Post post = foundPost(10L, owner, category);
        addImage(post, 1L, "k1", 0);
        when(posts.findManagedDetail(10L, 5L)).thenReturn(Optional.of(post));
        when(storage.readUrl("k1", null)).thenReturn("https://cdn/k1");

        EditPostDto dto = service.getForEdit(10L, user);

        assertEquals(10L, dto.id());
        assertEquals(1, dto.images().size());
        assertEquals("https://cdn/k1", dto.images().get(0).url());
    }

    @Test
    void getForEdit_whenNotOwnerOrBlocked_throwsExpectedException() {
        when(posts.findManagedDetail(10L, 5L)).thenReturn(Optional.empty());
        assertEquals(ErrorCode.POST_OWNER_REQUIRED,
                assertThrows(AppException.class, () -> service.getForEdit(10L, user)).getErrorCode());

        Post post = foundPost(10L, owner, category);
        when(posts.findManagedDetail(10L, 5L)).thenReturn(Optional.of(post));
        when(receipts.existsByPostId(10L)).thenReturn(true);
        assertEquals(ErrorCode.POST_NOT_EDITABLE,
                assertThrows(AppException.class, () -> service.getForEdit(10L, user)).getErrorCode());

        when(receipts.existsByPostId(10L)).thenReturn(false);
        when(claims.existsByPost_Id(10L)).thenReturn(true);
        assertEquals(ErrorCode.CLAIM_POST_EDIT_BLOCKED,
                assertThrows(AppException.class, () -> service.getForEdit(10L, user)).getErrorCode());

        when(claims.existsByPost_Id(10L)).thenReturn(false);
        ReflectionTestUtils.setField(post, "status", "RETURNED");
        assertEquals(ErrorCode.POST_NOT_EDITABLE,
                assertThrows(AppException.class, () -> service.getForEdit(10L, user)).getErrorCode());

        assertEquals(ErrorCode.POST_ID_INVALID,
                assertThrows(AppException.class, () -> service.getForEdit(0L, user)).getErrorCode());
    }

    @Test
    void update_whenKeepingImagesAndAddingNew_reordersDeletesRemovedAfterCommit() {
        OffsetDateTime updatedAt = OffsetDateTime.now().minusMinutes(5);
        Post post = foundPost(10L, owner, category);
        ReflectionTestUtils.setField(post, "updatedAt", updatedAt);
        addImage(post, 1L, "k1", 0);
        addImage(post, 2L, "k2", 1);
        addImage(post, 3L, "k3", 2);

        UpdatePostRequest request = new UpdatePostRequest(
                "New", "Desc", 3L, "Loc", OffsetDateTime.now().minusDays(1),
                null, List.of(1L, 3L), updatedAt, null);
        List<MultipartFile> files = List.of();
        List<ValidatedImage> newImages = List.of(
                new ValidatedImage(new byte[]{9}, "image/jpeg", "jpg", 50, 50, "hash9")
        );

        when(posts.findManagedDetail(10L, 5L)).thenReturn(Optional.of(post));
        when(categories.existsByCategoryIdAndActiveTrue(3L)).thenReturn(true);
        when(validator.validate(files)).thenReturn(newImages);
        when(accounts.findActiveAccountForPostCreation(5L)).thenReturn(Optional.of(owner));
        when(posts.findManagedForUpdate(10L, 5L)).thenReturn(Optional.of(post));
        when(categories.findActiveForPostCreation(3L)).thenReturn(Optional.of(category));

        CreatedPostDto result = service.update(10L, request, files, user);

        assertEquals(10L, result.id());
        assertEquals("New", post.getTitle());
        assertEquals(3, post.getImages().size());
        assertEquals(0, post.getImages().get(0).getSortOrder());
        assertEquals(1, post.getImages().get(1).getSortOrder());
        assertEquals(2, post.getImages().get(2).getSortOrder());
        verify(storage).delete("k2");
        verify(storage, never()).delete("k1");
        verify(storage, never()).delete("k3");
    }

    @Test
    void update_whenValidationFails_throwsExpectedErrors() {
        OffsetDateTime updatedAt = OffsetDateTime.now().minusMinutes(5);
        Post post = foundPost(10L, owner, category);
        ReflectionTestUtils.setField(post, "updatedAt", updatedAt);
        addImage(post, 1L, "k1", 0);
        addImage(post, 2L, "k2", 1);
        addImage(post, 3L, "k3", 2);
        when(posts.findManagedDetail(10L, 5L)).thenReturn(Optional.of(post));

        UpdatePostRequest stale = new UpdatePostRequest("T", "D", 3L, "L", OffsetDateTime.now().minusDays(1),
                null, List.of(1L), updatedAt.minusSeconds(1), null);
        assertEquals(ErrorCode.POST_STALE,
                assertThrows(AppException.class, () -> service.update(10L, stale, List.of(), user)).getErrorCode());

        UpdatePostRequest dup = new UpdatePostRequest("T", "D", 3L, "L", OffsetDateTime.now().minusDays(1),
                null, List.of(1L, 1L), updatedAt, null);
        assertEquals(ErrorCode.POST_IMAGES_INVALID,
                assertThrows(AppException.class, () -> service.update(10L, dup, List.of(), user)).getErrorCode());

        UpdatePostRequest foreign = new UpdatePostRequest("T", "D", 3L, "L", OffsetDateTime.now().minusDays(1),
                null, List.of(1L, 99L), updatedAt, null);
        assertEquals(ErrorCode.POST_IMAGES_INVALID,
                assertThrows(AppException.class, () -> service.update(10L, foreign, List.of(), user)).getErrorCode());

        UpdatePostRequest custody = new UpdatePostRequest("T", "D", 3L, "L", OffsetDateTime.now().minusDays(1),
                "OFFICE", List.of(1L), updatedAt, null);
        assertEquals(ErrorCode.POST_CUSTODY_CHANGE_REQUIRES_HANDOVER,
                assertThrows(AppException.class, () -> service.update(10L, custody, List.of(), user)).getErrorCode());

        when(categories.existsByCategoryIdAndActiveTrue(9L)).thenReturn(false);
        UpdatePostRequest badCat = new UpdatePostRequest("T", "D", 9L, "L", OffsetDateTime.now().minusDays(1),
                null, List.of(1L), updatedAt, null);
        assertEquals(ErrorCode.POST_CATEGORY_UNAVAILABLE,
                assertThrows(AppException.class, () -> service.update(10L, badCat, List.of(), user)).getErrorCode());

        when(categories.existsByCategoryIdAndActiveTrue(3L)).thenReturn(true);
        UpdatePostRequest tooMany = new UpdatePostRequest("T", "D", 3L, "L", OffsetDateTime.now().minusDays(1),
                null, List.of(1L, 2L, 3L), updatedAt, null);
        ValidatedImage img = new ValidatedImage(new byte[]{1}, "image/jpeg", "jpg", 10, 10, "h");
        when(validator.validate(any())).thenReturn(List.of(img, img, img));
        assertEquals(ErrorCode.IMAGE_COUNT_EXCEEDED,
                assertThrows(AppException.class, () -> service.update(10L, tooMany, List.of(), user)).getErrorCode());
    }

    @Test
    void update_whenTotalFiveImages_succeeds() {
        OffsetDateTime updatedAt = OffsetDateTime.now().minusMinutes(5);
        Post post = foundPost(10L, owner, category);
        ReflectionTestUtils.setField(post, "updatedAt", updatedAt);
        addImage(post, 1L, "k1", 0);
        addImage(post, 2L, "k2", 1);
        addImage(post, 3L, "k3", 2);
        UpdatePostRequest req = new UpdatePostRequest("T", "D", 3L, "L", OffsetDateTime.now().minusDays(1),
                null, List.of(1L, 2L, 3L), updatedAt, null);
        ValidatedImage img = new ValidatedImage(new byte[]{1}, "image/jpeg", "jpg", 10, 10, "h");

        when(posts.findManagedDetail(10L, 5L)).thenReturn(Optional.of(post));
        when(categories.existsByCategoryIdAndActiveTrue(3L)).thenReturn(true);
        when(validator.validate(any())).thenReturn(List.of(img, img));
        when(accounts.findActiveAccountForPostCreation(5L)).thenReturn(Optional.of(owner));
        when(posts.findManagedForUpdate(10L, 5L)).thenReturn(Optional.of(post));
        when(categories.findActiveForPostCreation(3L)).thenReturn(Optional.of(category));

        CreatedPostDto dto = service.update(10L, req, List.of(), user);

        assertEquals(10L, dto.id());
        assertEquals(5, post.getImages().size());
    }

    @Test
    void update_whenTransactionThrowsAfterUpload_cleansUpOnlyNewKeys() {
        OffsetDateTime updatedAt = OffsetDateTime.now().minusMinutes(5);
        Post post = foundPost(10L, owner, category);
        ReflectionTestUtils.setField(post, "updatedAt", updatedAt);
        addImage(post, 1L, "old-key", 0);
        UpdatePostRequest req = new UpdatePostRequest("T", "D", 3L, "L", OffsetDateTime.now().minusDays(1),
                null, List.of(), updatedAt, null);
        ValidatedImage img = new ValidatedImage(new byte[]{1}, "image/jpeg", "jpg", 10, 10, "h");

        when(posts.findManagedDetail(10L, 5L)).thenReturn(Optional.of(post));
        when(categories.existsByCategoryIdAndActiveTrue(3L)).thenReturn(true);
        when(validator.validate(any())).thenReturn(List.of(img));
        when(accounts.findActiveAccountForPostCreation(5L)).thenReturn(Optional.empty());

        assertThrows(AppException.class, () -> service.update(10L, req, List.of(), user));

        verify(storage, never()).delete("old-key");
        verify(storage).delete(org.mockito.ArgumentMatchers.startsWith("posts/5/"));
    }

    @Test
    void update_whenRemovedImageCleanupThrows_stillReturnsSuccess() {
        OffsetDateTime updatedAt = OffsetDateTime.now().minusMinutes(5);
        Post post = foundPost(10L, owner, category);
        ReflectionTestUtils.setField(post, "updatedAt", updatedAt);
        addImage(post, 1L, "old-key", 0);
        UpdatePostRequest req = new UpdatePostRequest("T", "D", 3L, "L", OffsetDateTime.now().minusDays(1),
                null, List.of(), updatedAt, null);

        when(posts.findManagedDetail(10L, 5L)).thenReturn(Optional.of(post));
        when(categories.existsByCategoryIdAndActiveTrue(3L)).thenReturn(true);
        when(validator.validate(any())).thenReturn(List.of());
        when(accounts.findActiveAccountForPostCreation(5L)).thenReturn(Optional.of(owner));
        when(posts.findManagedForUpdate(10L, 5L)).thenReturn(Optional.of(post));
        when(categories.findActiveForPostCreation(3L)).thenReturn(Optional.of(category));
        doThrow(new RuntimeException("S3 timeout")).when(storage).delete("old-key");

        CreatedPostDto dto = service.update(10L, req, List.of(), user);

        assertEquals(10L, dto.id());
    }

    @Test
    void delete_whenValidOrOnlyRejectedClaims_softDeletesAndFlushes() {
        Post post = foundPost(10L, owner, category);
        when(posts.findManagedForUpdate(10L, 5L)).thenReturn(Optional.of(post));
        when(receipts.existsByPostId(10L)).thenReturn(false);
        when(claims.existsByPost_IdAndStatusIn(10L, Set.of("PENDING", "NEEDS_INFO", "APPROVED"))).thenReturn(false);

        service.delete(10L, new DeletePostRequest("  Found it myself  "), user);

        assertNotNull(post.getDeletedAt());
        assertEquals("Found it myself", post.getDeletionReason());
        verify(entityManager).refresh(post, LockModeType.PESSIMISTIC_WRITE);
        verify(posts).flush();
    }

    @Test
    void delete_whenBlockedOrInvalid_throwsExpectedErrors() {
        when(posts.findManagedForUpdate(10L, 5L)).thenReturn(Optional.empty());
        assertEquals(ErrorCode.POST_OWNER_REQUIRED,
                assertThrows(AppException.class, () -> service.delete(10L, new DeletePostRequest("x"), user)).getErrorCode());

        Post deletedPost = foundPost(10L, owner, category);
        ReflectionTestUtils.setField(deletedPost, "deletedAt", OffsetDateTime.now());
        when(posts.findManagedForUpdate(10L, 5L)).thenReturn(Optional.of(deletedPost));
        assertEquals(ErrorCode.POST_OWNER_REQUIRED,
                assertThrows(AppException.class, () -> service.delete(10L, new DeletePostRequest("x"), user)).getErrorCode());

        Post post = foundPost(10L, owner, category);
        when(posts.findManagedForUpdate(10L, 5L)).thenReturn(Optional.of(post));
        when(receipts.existsByPostId(10L)).thenReturn(true);
        assertEquals(ErrorCode.POST_NOT_EDITABLE,
                assertThrows(AppException.class, () -> service.delete(10L, new DeletePostRequest("x"), user)).getErrorCode());

        when(receipts.existsByPostId(10L)).thenReturn(false);
        when(claims.existsByPost_IdAndStatusIn(10L, Set.of("PENDING", "NEEDS_INFO", "APPROVED"))).thenReturn(true);
        assertEquals(ErrorCode.CLAIM_POST_DELETE_BLOCKED,
                assertThrows(AppException.class, () -> service.delete(10L, new DeletePostRequest("x"), user)).getErrorCode());
    }

    @Test
    void requestOfficeHandover_delegatesToOfficeTransferServiceAndFlushes() {
        Post post = foundPost(10L, owner, category);
        when(officeTransfers.request(10L, user)).thenReturn(post);

        CreatedPostDto result = service.requestOfficeHandover(10L, user);

        assertEquals(10L, result.id());
        verify(posts).flush();
    }

    @Test
    void requestOfficeHandover_whenOfficeTransferThrows_propagatesException() {
        when(officeTransfers.request(10L, user)).thenThrow(new AppException(ErrorCode.BAD_REQUEST, "err"));

        AppException ex = assertThrows(AppException.class, () -> service.requestOfficeHandover(10L, user));

        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
    }

    @SuppressWarnings("unchecked")
    private void mockPendingCounts(List<Tuple> tuples) {
        TypedQuery<Tuple> query = mock(TypedQuery.class);
        when(entityManager.createQuery(anyString(), eq(Tuple.class))).thenReturn(query);
        when(query.setParameter(eq("ids"), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(tuples);
    }

    private static Tuple tuple(Long postId, Long count) {
        Tuple t = mock(Tuple.class);
        when(t.get(0, Long.class)).thenReturn(postId);
        when(t.get(1, Long.class)).thenReturn(count);
        return t;
    }

    private static Account account(Long id) {
        Account a = new Account();
        a.setId(id);
        a.setEmail("owner" + id + "@fpt.edu.vn");
        a.setFullName("Owner " + id);
        a.setRole(AccountRole.USER);
        a.setStatus(AccountStatus.ACTIVE);
        return a;
    }

    private static Category category(Long id, String name) {
        Category c = new Category();
        ReflectionTestUtils.setField(c, "categoryId", id);
        c.setName(name);
        return c;
    }

    private static Post foundPost(Long id, Account owner, Category cat) {
        Post p = Post.found(owner, cat, "Title " + id, "Desc", "Hall A",
                OffsetDateTime.now().minusDays(1), "P2P", null);
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    private static void addImage(Post post, Long imageId, String key, int order) {
        PostImage img = new PostImage(key, order, "image/jpeg", 100L, 10, 10, "hash");
        ReflectionTestUtils.setField(img, "id", imageId);
        post.addImage(img);
    }
}

