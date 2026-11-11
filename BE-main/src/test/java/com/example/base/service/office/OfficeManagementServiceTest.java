package com.example.base.service.office;

import com.example.base.dto.office.OfficeDtos.*;
import com.example.base.entity.*;
import com.example.base.exception.AppException;
import com.example.base.exception.ErrorCode;
import com.example.base.repository.ItemReceiptRepository;
import com.example.base.repository.PostRepository;
import com.example.base.repository.StorageLocationRepository;
import com.example.base.security.CustomUserDetails;
import com.example.base.service.notification.NotificationService;
import com.example.base.storage.ImageStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OfficeManagementServiceTest {

    @Mock OfficeAccess access;
    @Mock PostRepository posts;
    @Mock ItemReceiptRepository receipts;
    @Mock StorageLocationRepository locations;
    @Mock ImageStorage storage;
    @Mock NotificationService notifications;

    @InjectMocks
    OfficeManagementService service;

    Account manager;
    Account approver;
    Account regularUser;
    Account owner;
    Category category;

    @BeforeEach
    void setUp() {
        manager = account(1L, AccountRole.MANAGER, "Manager 1");
        approver = account(2L, AccountRole.APPROVER, "Approver 2");
        regularUser = account(3L, AccountRole.USER, "User 3");
        owner = account(5L, AccountRole.USER, "Owner 5");

        category = new Category();
        ReflectionTestUtils.setField(category, "categoryId", 10L);
        category.setName("Electronics");

        when(storage.readUrl(any(), any())).thenAnswer(inv -> "url:" + inv.getArgument(0));
    }

    private Account account(long id, AccountRole role, String fullName) {
        Account a = new Account();
        a.setId(id);
        a.setRole(role);
        a.setStatus(AccountStatus.ACTIVE);
        a.setFullName(fullName);
        a.setEmail("user" + id + "@example.com");
        a.setPasswordHash("hash");
        return a;
    }

    private CustomUserDetails ud(Account a) {
        return new CustomUserDetails(a);
    }

    private void grant(Account actor, AccountRole... roles) {
        when(access.require(any(), any(AccountRole[].class))).thenAnswer(inv -> {
            CustomUserDetails u = inv.getArgument(0);
            AccountRole[] req = (AccountRole[]) inv.getRawArguments()[1];
            if (u == null) throw new AppException(ErrorCode.UNAUTHORIZED, "Unauthorized");
            if (req != null && req.length > 0 && Arrays.stream(req).noneMatch(r -> r == actor.getRole())) {
                throw new AppException(ErrorCode.FORBIDDEN, "Forbidden");
            }
            return actor;
        });
    }

    private StorageLocation loc(long id, String code, String name, Long parentId, boolean active) {
        StorageLocation l = StorageLocation.create(code, name, parentId);
        ReflectionTestUtils.setField(l, "id", id);
        l.update(code, name, parentId, active);
        return l;
    }

    private Post foundPost(long id, Account postOwner, String moderation, OffsetDateTime updatedAt) {
        Post p = postOwner != null
                ? Post.found(postOwner, category, "Black Wallet", "Leather wallet", "Hall A", OffsetDateTime.now().minusDays(1), "P2P", null)
                : Post.receivedByOffice(manager, category, "Black Wallet", "Leather wallet", "Hall A", OffsetDateTime.now().minusDays(1));
        if (postOwner == null) {
            ReflectionTestUtils.setField(p, "account", null);
        }
        ReflectionTestUtils.setField(p, "id", id);
        ReflectionTestUtils.setField(p, "moderationStatus", moderation);
        ReflectionTestUtils.setField(p, "updatedAt", updatedAt);
        ReflectionTestUtils.setField(p, "createdAt", updatedAt.minusHours(2));
        return p;
    }

    private ItemReceipt receipt(long id, long postId, StorageLocation sl) {
        ItemReceipt r = ItemReceipt.walkInDraft("VP-" + id, approver);
        ReflectionTestUtils.setField(r, "id", id);
        r.edit(category, "Wallet", "Desc", "Good", "Lobby", OffsetDateTime.now().minusHours(5), null, null);
        r.assignLocation(sl);
        r.receiveAndPublish(approver, postId);
        return r;
    }

    @Test
    void utc008_page_validOne_returnsFirstPageSize12Desc() {
        PageRequest req = OfficeManagementService.page(1);
        assertThat(req.getPageNumber()).isEqualTo(0);
        assertThat(req.getPageSize()).isEqualTo(12);
        assertThat(req.getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "id"));
    }

    @Test
    void utc009_page_zero_throwsBadRequest() {
        AppException ex = catchThrowableOfType(() -> OfficeManagementService.page(0), AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ex.getMessage()).isEqualTo("Trang không hợp lệ.");
    }

    @Test
    void utc010_clean_trimsWhitespace() {
        assertThat(OfficeManagementService.clean("  abc  ")).isEqualTo("abc");
    }

    @Test
    void utc011_clean_blankOrNull_returnsNull() {
        assertThat(OfficeManagementService.clean("   ")).isNull();
        assertThat(OfficeManagementService.clean(null)).isNull();
    }

    @Test
    void utc012_person_returnsFullName() {
        Account a = account(10L, AccountRole.USER, "Nguyen A");
        assertThat(OfficeManagementService.person(a)).isEqualTo("Nguyen A");
    }

    @Test
    void utc013_person_blankFullName_fallsBackToEmail() {
        Account a = account(10L, AccountRole.USER, " ");
        a.setEmail("a@x.com");
        assertThat(OfficeManagementService.person(a)).isEqualTo("a@x.com");
    }

    @Test
    void utc014_person_nullAccount_returnsDash() {
        assertThat(OfficeManagementService.person(null)).isEqualTo("—");
    }

    @Test
    void utc015_descendants_parentWithChain_returnsSubtree() {
        List<StorageLocation> all = List.of(
                loc(1L, "A", "A", null, true),
                loc(2L, "A1", "A1", 1L, true),
                loc(3L, "A1A", "A1A", 2L, true),
                loc(4L, "B", "B", null, true)
        );
        assertThat(OfficeManagementService.descendants(1L, all)).containsExactlyInAnyOrder(1L, 2L, 3L);
    }

    @Test
    void utc016_descendants_leafNode_returnsOnlySelf() {
        List<StorageLocation> all = List.of(
                loc(1L, "A", "A", null, true),
                loc(2L, "A1", "A1", 1L, true),
                loc(3L, "A1A", "A1A", 2L, true)
        );
        assertThat(OfficeManagementService.descendants(3L, all)).containsExactly(3L);
    }

    @Test
    void utc017_posts_managerValidQuery_returnsSummaryPostViews() {
        grant(manager, AccountRole.MANAGER);
        OffsetDateTime now = OffsetDateTime.now();
        Post p1 = foundPost(11L, owner, "VISIBLE", now);
        p1.addImage(new PostImage("k1", 0, "image/jpeg", 10L, 100, 100, "h1"));
        p1.addImage(new PostImage("k2", 1, "image/jpeg", 10L, 100, 100, "h2"));
        Post p2 = foundPost(12L, owner, "VISIBLE", now);
        p2.addImage(new PostImage("k3", 0, "image/jpeg", 10L, 100, 100, "h3"));

        Page<Post> page = new PageImpl<>(List.of(p1, p2), PageRequest.of(0, 12), 2);
        when(posts.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        PageData<PostView> res = service.posts(1, "Wallet", "ALL", "ALL", "ALL", "ALL", null, ud(manager));

        assertThat(res.content()).hasSize(2);
        assertThat(res.content().get(0).images()).hasSize(1);
        assertThat(res.content().get(0).description()).isNull();
        assertThat(res.content().get(1).images()).hasSize(1);
        assertThat(res.content().get(1).description()).isNull();
    }

    @Test
    void utc018_posts_approverForbidden_throwsForbidden() {
        grant(approver, AccountRole.MANAGER);

        AppException ex = catchThrowableOfType(
                () -> service.posts(1, null, "ALL", "ALL", "ALL", "ALL", null, ud(approver)),
                AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void utc019_posts_keywordOver200Chars_throwsBadRequest() {
        grant(manager, AccountRole.MANAGER);
        String longQ = "a".repeat(201);

        AppException ex = catchThrowableOfType(
                () -> service.posts(1, longQ, "ALL", "ALL", "ALL", "ALL", null, ud(manager)),
                AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ex.getMessage()).isEqualTo("Từ khóa tối đa 200 ký tự.");
    }

    @Test
    void utc020_posts_pageZero_throwsBadRequest() {
        grant(manager, AccountRole.MANAGER);

        AppException ex = catchThrowableOfType(
                () -> service.posts(0, null, "ALL", "ALL", "ALL", "ALL", null, ud(manager)),
                AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void utc021_detail_existingPostWithReceipt_returnsFullDetail() {
        grant(manager, AccountRole.MANAGER);
        OffsetDateTime now = OffsetDateTime.now();
        Post p = foundPost(10L, owner, "VISIBLE", now);
        for (int i = 0; i < 6; i++) {
            p.addImage(new PostImage("k" + i, i, "image/jpeg", 10L, 100, 100, "h" + i));
        }
        StorageLocation sl = loc(1L, "A-01", "Shelf A1", null, true);
        ItemReceipt r = receipt(20L, 10L, sl);

        when(posts.findById(10L)).thenReturn(Optional.of(p));
        when(receipts.findByPostId(10L)).thenReturn(Optional.of(r));

        PostView view = service.detail(10L, ud(manager));

        assertThat(view.description()).isEqualTo("Leather wallet");
        assertThat(view.authorEmail()).isEqualTo("user5@example.com");
        assertThat(view.images()).hasSize(5);
        assertThat(view.receipt()).isNotNull();
        assertThat(view.receipt().code()).isEqualTo("VP-20");
    }

    @Test
    void utc022_detail_softDeletedPost_throwsBadRequest() {
        grant(manager, AccountRole.MANAGER);
        Post p = foundPost(10L, owner, "VISIBLE", OffsetDateTime.now());
        p.softDelete("Deleted by user");
        when(posts.findById(10L)).thenReturn(Optional.of(p));

        AppException ex = catchThrowableOfType(() -> service.detail(10L, ud(manager)), AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ex.getMessage()).isEqualTo("Không tìm thấy bài đăng.");
    }

    @Test
    void utc023_moderate_visibleToHidden_notifiesOwner() {
        grant(manager, AccountRole.MANAGER);
        OffsetDateTime t = OffsetDateTime.now().minusMinutes(5);
        Post p = foundPost(10L, owner, "VISIBLE", t);
        when(posts.findForClaimUpdate(10L)).thenReturn(Optional.of(p));
        when(posts.saveAndFlush(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));
        when(receipts.findByPostId(10L)).thenReturn(Optional.empty());

        PostView res = service.moderate(10L, new Moderation("HIDDEN", " Spam ", t), ud(manager));

        assertThat(res.moderationStatus()).isEqualTo("HIDDEN");
        assertThat(res.moderationNote()).isEqualTo("Spam");
        verify(notifications).notifyUser(eq(5L), eq("POST_HIDDEN"), anyString(), anyString(), anyString(), eq(true));
    }

    @Test
    void utc024_moderate_hiddenToVisible_notifiesPostVisible() {
        grant(manager, AccountRole.MANAGER);
        OffsetDateTime t = OffsetDateTime.now().minusMinutes(5);
        Post p = foundPost(10L, owner, "HIDDEN", t);
        when(posts.findForClaimUpdate(10L)).thenReturn(Optional.of(p));
        when(posts.saveAndFlush(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));
        when(receipts.findByPostId(10L)).thenReturn(Optional.empty());

        PostView res = service.moderate(10L, new Moderation("VISIBLE", "Reviewed", t), ud(manager));

        assertThat(res.moderationStatus()).isEqualTo("VISIBLE");
        verify(notifications).notifyUser(eq(5L), eq("POST_VISIBLE"), anyString(), anyString(), anyString(), eq(false));
    }

    @Test
    void utc025_moderate_visibleToVisible_throwsBadRequest() {
        grant(manager, AccountRole.MANAGER);
        OffsetDateTime t = OffsetDateTime.now().minusMinutes(5);
        Post p = foundPost(10L, owner, "VISIBLE", t);
        when(posts.findForClaimUpdate(10L)).thenReturn(Optional.of(p));

        AppException ex = catchThrowableOfType(
                () -> service.moderate(10L, new Moderation("VISIBLE", "Note", t), ud(manager)),
                AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ex.getMessage()).isEqualTo("Chỉ ẩn bài đang hiển thị hoặc gỡ ẩn bài đang bị ẩn.");
    }

    @Test
    void utc026_moderate_pendingPost_throwsBadRequest() {
        grant(manager, AccountRole.MANAGER);
        OffsetDateTime t = OffsetDateTime.now().minusMinutes(5);
        Post p = foundPost(10L, owner, "PENDING", t);
        when(posts.findForClaimUpdate(10L)).thenReturn(Optional.of(p));

        AppException ex = catchThrowableOfType(
                () -> service.moderate(10L, new Moderation("HIDDEN", "Note", t), ud(manager)),
                AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void utc027_moderate_staleUpdatedAt_throwsClaimStale() {
        grant(manager, AccountRole.MANAGER);
        OffsetDateTime t = OffsetDateTime.now().minusMinutes(5);
        Post p = foundPost(10L, owner, "VISIBLE", t);
        when(posts.findForClaimUpdate(10L)).thenReturn(Optional.of(p));

        AppException ex = catchThrowableOfType(
                () -> service.moderate(10L, new Moderation("HIDDEN", "Spam", t.minusSeconds(1)), ud(manager)),
                AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CLAIM_STALE);
        assertThat(ex.getMessage()).isEqualTo("Bài đăng đã thay đổi. Tải lại trước khi thao tác.");
    }

    @Test
    void utc028_moderate_sameInstantDifferentOffset_accepted() {
        grant(manager, AccountRole.MANAGER);
        OffsetDateTime tPlus7 = OffsetDateTime.of(2026, 11, 11, 12, 0, 0, 0, ZoneOffset.ofHours(7));
        OffsetDateTime tUtc = tPlus7.withOffsetSameInstant(ZoneOffset.UTC);
        Post p = foundPost(10L, owner, "VISIBLE", tPlus7);
        when(posts.findForClaimUpdate(10L)).thenReturn(Optional.of(p));
        when(posts.saveAndFlush(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));
        when(receipts.findByPostId(10L)).thenReturn(Optional.empty());

        PostView res = service.moderate(10L, new Moderation("HIDDEN", "Check", tUtc), ud(manager));

        assertThat(res.moderationStatus()).isEqualTo("HIDDEN");
    }

    @Test
    void utc029_moderate_deletedPost_throwsBadRequest() {
        grant(manager, AccountRole.MANAGER);
        OffsetDateTime t = OffsetDateTime.now().minusMinutes(5);
        Post p = foundPost(10L, owner, "VISIBLE", t);
        p.softDelete("Removed");
        when(posts.findForClaimUpdate(10L)).thenReturn(Optional.of(p));

        AppException ex = catchThrowableOfType(
                () -> service.moderate(10L, new Moderation("HIDDEN", "Spam", t), ud(manager)),
                AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ex.getMessage()).isEqualTo("Bài đăng đã bị xóa.");
    }

    @Test
    void utc030_moderate_officePostNullAccount_noNotification() {
        grant(manager, AccountRole.MANAGER);
        OffsetDateTime t = OffsetDateTime.now().minusMinutes(5);
        Post p = foundPost(10L, null, "VISIBLE", t);
        when(posts.findForClaimUpdate(10L)).thenReturn(Optional.of(p));
        when(posts.saveAndFlush(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));
        when(receipts.findByPostId(10L)).thenReturn(Optional.empty());

        PostView res = service.moderate(10L, new Moderation("HIDDEN", "Internal", t), ud(manager));

        assertThat(res.moderationStatus()).isEqualTo("HIDDEN");
        verifyNoInteractions(notifications);
    }

    @Test
    void utc031_stored_approverValid_returnsReceiptViews() {
        grant(approver, AccountRole.APPROVER, AccountRole.MANAGER);
        StorageLocation sl = loc(1L, "A-01", "Shelf A1", null, true);
        List<ItemReceipt> list = List.of(
                receipt(1L, 101L, sl),
                receipt(2L, 102L, sl),
                receipt(3L, 103L, sl)
        );
        Page<ItemReceipt> page = new PageImpl<>(list, PageRequest.of(0, 12), 3);
        when(receipts.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        PageData<ReceiptView> res = service.stored(1, null, null, ud(approver));

        assertThat(res.content()).hasSize(3);
        verify(locations, never()).findAll();
    }

    @Test
    void utc032_stored_withLocationFilter_loadsDescendantsAndQueriesReceipts() {
        grant(manager, AccountRole.APPROVER, AccountRole.MANAGER);
        StorageLocation root = loc(1L, "A", "A", null, true);
        StorageLocation child = loc(2L, "A1", "A1", 1L, true);
        when(locations.findAll()).thenReturn(List.of(root, child));
        Page<ItemReceipt> empty = new PageImpl<>(List.of(), PageRequest.of(0, 12), 0);
        when(receipts.findAll(any(Specification.class), any(Pageable.class))).thenReturn(empty);

        PageData<ReceiptView> res = service.stored(1, "", 1L, ud(manager));

        assertThat(res.content()).isEmpty();
        verify(locations).findAll();
        verify(receipts).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void utc033_stored_regularUser_throwsForbidden() {
        grant(regularUser, AccountRole.APPROVER, AccountRole.MANAGER);

        AppException ex = catchThrowableOfType(
                () -> service.stored(1, null, null, ud(regularUser)),
                AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void utc034_locations_approver_returnsSortedLocations() {
        grant(approver, AccountRole.APPROVER, AccountRole.MANAGER);
        List<StorageLocation> all = List.of(
                loc(1L, "A-01", "Shelf A1", null, true),
                loc(2L, "B-01", "Shelf B1", null, true)
        );
        when(locations.findAll(Sort.by("code"))).thenReturn(all);

        List<LocationView> res = service.locations(ud(approver));

        assertThat(res).hasSize(2);
        assertThat(res).extracting(LocationView::code).containsExactly("A-01", "B-01");
    }

    @Test
    void utc035_saveLocation_createNewUppercasesCode() {
        grant(manager, AccountRole.MANAGER);
        when(locations.lockAll()).thenReturn(List.of());
        when(locations.existsByCodeIgnoreCaseAndIdNot("A-01", -1L)).thenReturn(false);
        when(locations.saveAndFlush(any(StorageLocation.class))).thenAnswer(inv -> {
            StorageLocation s = inv.getArgument(0);
            ReflectionTestUtils.setField(s, "id", 10L);
            return s;
        });

        LocationView res = service.saveLocation(null, new LocationInput(" a-01 ", "Shelf A1", null, true), ud(manager));

        assertThat(res.code()).isEqualTo("A-01");
        assertThat(res.name()).isEqualTo("Shelf A1");
        assertThat(res.active()).isTrue();
    }

    @Test
    void utc036_saveLocation_duplicateCode_throwsBadRequest() {
        grant(manager, AccountRole.MANAGER);
        when(locations.lockAll()).thenReturn(List.of(loc(5L, "A-01", "Existing", null, true)));
        when(locations.existsByCodeIgnoreCaseAndIdNot("A-01", -1L)).thenReturn(true);

        AppException ex = catchThrowableOfType(
                () -> service.saveLocation(null, new LocationInput("a-01", "Shelf A1", null, true), ud(manager)),
                AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ex.getMessage()).isEqualTo("Mã vị trí đã được sử dụng.");
    }

    @Test
    void utc037_saveLocation_updateOwnCode_accepted() {
        grant(manager, AccountRole.MANAGER);
        StorageLocation existing = loc(1L, "A-01", "Old", null, true);
        when(locations.lockAll()).thenReturn(List.of(existing));
        when(locations.existsByCodeIgnoreCaseAndIdNot("A-01", 1L)).thenReturn(false);
        when(locations.saveAndFlush(any(StorageLocation.class))).thenAnswer(inv -> inv.getArgument(0));

        LocationView res = service.saveLocation(1L, new LocationInput("A-01", "Updated", null, true), ud(manager));

        assertThat(res.id()).isEqualTo(1L);
        assertThat(res.name()).isEqualTo("Updated");
    }

    @Test
    void utc038_saveLocation_missingId_throwsBadRequest() {
        grant(manager, AccountRole.MANAGER);
        when(locations.lockAll()).thenReturn(List.of());

        AppException ex = catchThrowableOfType(
                () -> service.saveLocation(999L, new LocationInput("A-01", "Name", null, true), ud(manager)),
                AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ex.getMessage()).isEqualTo("Không tìm thấy vị trí kho.");
    }

    @Test
    void utc039_saveLocation_parentIsDescendant_throwsBadRequest() {
        grant(manager, AccountRole.MANAGER);
        StorageLocation l1 = loc(1L, "A", "A", null, true);
        StorageLocation l2 = loc(2L, "A1", "A1", 1L, true);
        when(locations.lockAll()).thenReturn(List.of(l1, l2));
        when(locations.existsByCodeIgnoreCaseAndIdNot("A", 1L)).thenReturn(false);

        AppException ex = catchThrowableOfType(
                () -> service.saveLocation(1L, new LocationInput("A", "A", 2L, true), ud(manager)),
                AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ex.getMessage()).isEqualTo("Vị trí cha không được là chính nó hoặc vị trí con.");
    }

    @Test
    void utc040_saveLocation_parentIsSelf_throwsBadRequest() {
        grant(manager, AccountRole.MANAGER);
        StorageLocation l1 = loc(1L, "A", "A", null, true);
        when(locations.lockAll()).thenReturn(List.of(l1));
        when(locations.existsByCodeIgnoreCaseAndIdNot("A", 1L)).thenReturn(false);

        AppException ex = catchThrowableOfType(
                () -> service.saveLocation(1L, new LocationInput("A", "A", 1L, true), ud(manager)),
                AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void utc041_saveLocation_missingParent_throwsBadRequest() {
        grant(manager, AccountRole.MANAGER);
        when(locations.lockAll()).thenReturn(List.of());
        when(locations.existsByCodeIgnoreCaseAndIdNot("A", -1L)).thenReturn(false);

        AppException ex = catchThrowableOfType(
                () -> service.saveLocation(null, new LocationInput("A", "A", 999L, true), ud(manager)),
                AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ex.getMessage()).isEqualTo("Vị trí cha không tồn tại.");
    }

    @Test
    void utc042_saveLocation_inactiveParent_throwsBadRequest() {
        grant(manager, AccountRole.MANAGER);
        StorageLocation p3 = loc(3L, "C", "C", null, false);
        when(locations.lockAll()).thenReturn(List.of(p3));
        when(locations.existsByCodeIgnoreCaseAndIdNot("C1", -1L)).thenReturn(false);

        AppException ex = catchThrowableOfType(
                () -> service.saveLocation(null, new LocationInput("C1", "C1", 3L, true), ud(manager)),
                AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ex.getMessage()).isEqualTo("Vị trí cha đã ngừng sử dụng.");
    }

    @Test
    void utc043_saveLocation_deactivateWithStoredItem_throwsBadRequest() {
        grant(manager, AccountRole.MANAGER);
        StorageLocation l1 = loc(1L, "A", "A", null, true);
        when(locations.lockAll()).thenReturn(List.of(l1));
        when(locations.existsByCodeIgnoreCaseAndIdNot("A", 1L)).thenReturn(false);
        when(receipts.existsByStorageLocation_IdInAndStatusIn(anySet(), eq(List.of("IN_STORAGE")))).thenReturn(true);

        AppException ex = catchThrowableOfType(
                () -> service.saveLocation(1L, new LocationInput("A", "A", null, false), ud(manager)),
                AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ex.getMessage()).isEqualTo("Vị trí hoặc vị trí con vẫn còn đồ trong kho.");
    }

    @Test
    void utc044_saveLocation_deactivateWithActiveChild_throwsBadRequest() {
        grant(manager, AccountRole.MANAGER);
        StorageLocation l1 = loc(1L, "A", "A", null, true);
        StorageLocation l2 = loc(2L, "A1", "A1", 1L, true);
        when(locations.lockAll()).thenReturn(List.of(l1, l2));
        when(locations.existsByCodeIgnoreCaseAndIdNot("A", 1L)).thenReturn(false);
        when(receipts.existsByStorageLocation_IdInAndStatusIn(anySet(), eq(List.of("IN_STORAGE")))).thenReturn(false);

        AppException ex = catchThrowableOfType(
                () -> service.saveLocation(1L, new LocationInput("A", "A", null, false), ud(manager)),
                AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ex.getMessage()).isEqualTo("Hãy ngừng sử dụng các vị trí con trước.");
    }

    @Test
    void utc045_saveLocation_approverForbidden_throwsForbidden() {
        grant(approver, AccountRole.MANAGER);

        AppException ex = catchThrowableOfType(
                () -> service.saveLocation(null, new LocationInput("A", "A", null, true), ud(approver)),
                AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
    }
}
