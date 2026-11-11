package com.example.base.service.office;

import com.example.base.dto.office.OfficeDtos.PageData;
import com.example.base.dto.office.OfficeWorkflowDtos.*;
import com.example.base.entity.*;
import com.example.base.exception.AppException;
import com.example.base.exception.ErrorCode;
import com.example.base.repository.*;
import com.example.base.security.CustomUserDetails;
import com.example.base.service.chat.ChatConversationService;
import com.example.base.service.notification.NotificationService;
import com.example.base.service.report.ReportHoldGuard;
import com.example.base.storage.ImageStorage;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OfficeTransferServiceTest {

    @Mock ReportHoldGuard reportHolds;
    @Mock OfficeAccess access;
    @Mock PostRepository posts;
    @Mock ClaimRepository claims;
    @Mock ClaimActivityRepository activities;
    @Mock ItemReceiptRepository receipts;
    @Mock StorageLocationRepository locations;
    @Mock AppointmentRepository appointments;
    @Mock ChatConversationService conversations;
    @Mock ImageStorage images;
    @Mock JdbcTemplate jdbc;
    @Mock EntityManager em;
    @Mock NotificationService notifications;

    @InjectMocks
    OfficeTransferService service;

    Account approver;
    Account manager;
    Account owner;
    Account otherUser;
    Category category;
    StorageLocation activeLoc;

    @BeforeEach
    void setUp() {
        approver = account(2L, AccountRole.APPROVER, "Approver 2");
        manager = account(1L, AccountRole.MANAGER, "Manager 1");
        owner = account(5L, AccountRole.USER, "Owner 5");
        otherUser = account(6L, AccountRole.USER, "Other 6");

        category = new Category();
        ReflectionTestUtils.setField(category, "categoryId", 10L);
        category.setName("Wallet");

        activeLoc = StorageLocation.create("A-01", "Shelf A1", null);
        ReflectionTestUtils.setField(activeLoc, "id", 3L);

        when(images.readUrl(any(), any())).thenAnswer(inv -> "url:" + inv.getArgument(0));
        when(receipts.saveAndFlush(any(ItemReceipt.class))).thenAnswer(inv -> {
            ItemReceipt r = inv.getArgument(0);
            ReflectionTestUtils.setField(r, "id", 100L);
            return r;
        });
        when(jdbc.query(contains("office_intake_appointment"), any(RowMapper.class), anyLong())).thenReturn(List.of());
    }

    private Account account(long id, AccountRole role, String name) {
        Account a = new Account();
        a.setId(id);
        a.setRole(role);
        a.setStatus(AccountStatus.ACTIVE);
        a.setFullName(name);
        a.setEmail("user" + id + "@example.com");
        a.setPasswordHash("hash");
        return a;
    }

    private CustomUserDetails ud(Account a) {
        return new CustomUserDetails(a);
    }

    private void grant(Account actor) {
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

    private Post eligiblePost(long id, Account postOwner) {
        Post p = Post.found(postOwner, category, "Black Wallet", "Leather wallet", "Hall A",
                OffsetDateTime.now().minusDays(1), "P2P", null);
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    private Map<String, Object> transferRow(long id, long postId, long requestedBy, String status, long version) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("transfer_id", id);
        m.put("post_id", postId);
        m.put("requested_by", requestedBy);
        m.put("status", status);
        m.put("version", version);
        m.put("requested_at", Timestamp.from(Instant.now().minusSeconds(3600)));
        m.put("processed_at", null);
        m.put("reason", null);
        m.put("reconciliation_note", null);
        m.put("item_receipt_id", null);
        return m;
    }

    private void stubViewQueries(long transferId, Map<String, Object> row, Post p, long activeClaims, long handovers) {
        when(jdbc.queryForList(contains("from office_transfer where transfer_id=?"), eq(transferId)))
                .thenReturn(List.of(row));
        when(jdbc.queryForMap(contains("from office_transfer where transfer_id=? for update"), eq(transferId)))
                .thenReturn(row);
        when(posts.findById(p.getId())).thenReturn(Optional.of(p));
        when(jdbc.queryForObject(contains("from claim where post_id=?"), eq(Long.class), eq(p.getId())))
                .thenReturn(activeClaims);
        when(jdbc.queryForObject(contains("from p2p_handover"), eq(Long.class), eq(p.getId())))
                .thenReturn(handovers);
    }

    @Test
    void utc001_request_validOwner_insertsTransferRequestsHandoverAndNotifiesRoles() {
        grant(owner);
        Post p = eligiblePost(10L, owner);
        when(posts.findForClaimUpdate(10L)).thenReturn(Optional.of(p));
        when(jdbc.queryForObject(contains("exists(select 1 from office_transfer"), eq(Boolean.class), eq(10L)))
                .thenReturn(false);
        when(receipts.existsByPostId(10L)).thenReturn(false);

        Post result = service.request(10L, ud(owner));

        assertThat(result).isSameAs(p);
        assertThat(p.getHandoverRequestedAt()).isNotNull();
        verify(jdbc).update("insert into office_transfer(post_id,requested_by) values(?,?)", 10L, 5L);
        verify(notifications).notifyRoles(eq(List.of(AccountRole.APPROVER)), eq(5L), eq("OFFICE_TRANSFER_REQUESTED"),
                anyString(), anyString(), eq("/approver/transfers"), eq(false));
        verify(notifications).notifyRoles(eq(List.of(AccountRole.MANAGER)), eq(5L), eq("OFFICE_TRANSFER_REQUESTED"),
                anyString(), anyString(), eq("/admin/transfers"), eq(false));
    }

    @Test
    void utc002_request_pendingAlreadyExists_idempotentNoInsert() {
        grant(owner);
        Post p = eligiblePost(10L, owner);
        when(posts.findForClaimUpdate(10L)).thenReturn(Optional.of(p));
        when(jdbc.queryForObject(contains("exists(select 1 from office_transfer"), eq(Boolean.class), eq(10L)))
                .thenReturn(true);

        Post result = service.request(10L, ud(owner));

        assertThat(result).isSameAs(p);
        verify(jdbc, never()).update(contains("insert into office_transfer"), any(), any());
        verifyNoInteractions(notifications);
    }

    @Test
    void utc003_request_notOwner_throwsBadRequest() {
        grant(otherUser);
        Post p = eligiblePost(10L, owner);
        when(posts.findForClaimUpdate(10L)).thenReturn(Optional.of(p));

        AppException ex = catchThrowableOfType(() -> service.request(10L, ud(otherUser)), AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ex.getMessage()).isEqualTo("Chỉ người đăng được yêu cầu chuyển giao.");
    }

    @Test
    void utc004_request_receiptExists_throwsBadRequest() {
        grant(owner);
        Post p = eligiblePost(10L, owner);
        when(posts.findForClaimUpdate(10L)).thenReturn(Optional.of(p));
        when(jdbc.queryForObject(contains("exists(select 1 from office_transfer"), eq(Boolean.class), eq(10L)))
                .thenReturn(false);
        when(receipts.existsByPostId(10L)).thenReturn(true);

        AppException ex = catchThrowableOfType(() -> service.request(10L, ud(owner)), AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ex.getMessage()).isEqualTo("Bài đăng không còn đủ điều kiện chuyển giao.");
    }

    @Test
    void utc005_request_postMissing_throwsBadRequest() {
        grant(owner);
        when(posts.findForClaimUpdate(999L)).thenReturn(Optional.empty());

        AppException ex = catchThrowableOfType(() -> service.request(999L, ud(owner)), AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ex.getMessage()).isEqualTo("Không tìm thấy bài đăng.");
    }

    @Test
    void utc006_counterPeople_approverValidQuery_queriesExcludingActor() {
        grant(approver);
        CounterPerson person = new CounterPerson(5L, "An Nguyen", "an@example.com");
        when(jdbc.query(contains("from accounts a"), any(RowMapper.class), eq(2L), eq("%an%"), eq("%an%")))
                .thenReturn(List.of(person));

        List<CounterPerson> res = service.counterPeople("an", ud(approver));

        assertThat(res).containsExactly(person);
    }

    @Test
    void utc007_counterPeople_shortQuery_returnsEmptyWithoutQuery() {
        grant(approver);

        List<CounterPerson> res = service.counterPeople("a", ud(approver));

        assertThat(res).isEmpty();
        verifyNoInteractions(jdbc);
    }

    @Test
    void utc008_counterPeople_nullQuery_returnsEmpty() {
        grant(approver);

        List<CounterPerson> res = service.counterPeople(null, ud(approver));

        assertThat(res).isEmpty();
        verifyNoInteractions(jdbc);
    }

    @Test
    void utc009_counterPeople_escapesSqlWildcards() {
        grant(approver);
        when(jdbc.query(contains("from accounts a"), any(RowMapper.class), eq(2L), eq("%50\\%\\_%"), eq("%50\\%\\_%")))
                .thenReturn(List.of());

        service.counterPeople("50%_", ud(approver));

        verify(jdbc).query(contains("from accounts a"), any(RowMapper.class), eq(2L), eq("%50\\%\\_%"), eq("%50\\%\\_%"));
    }

    @Test
    void utc010_counterPeople_regularUser_throwsForbidden() {
        grant(owner);

        AppException ex = catchThrowableOfType(() -> service.counterPeople("an", ud(owner)), AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void utc011_counterPosts_returnsEligiblePostsWithImagesAndHandoverCount() {
        grant(approver);
        Post p1 = eligiblePost(10L, owner);
        p1.addImage(new PostImage("k1", 0, "image/jpeg", 10L, 100, 100, "h1"));
        Post p2 = eligiblePost(11L, owner);
        when(jdbc.queryForList(contains("from post p where p.account_id=?"), eq(Long.class), eq(5L)))
                .thenReturn(List.of(10L, 11L));
        when(posts.findById(10L)).thenReturn(Optional.of(p1));
        when(posts.findById(11L)).thenReturn(Optional.of(p2));
        when(jdbc.queryForObject(contains("from p2p_handover"), eq(Long.class), eq(10L))).thenReturn(1L);
        when(jdbc.queryForObject(contains("from p2p_handover"), eq(Long.class), eq(11L))).thenReturn(0L);

        List<CounterPost> res = service.counterPosts(5L, ud(approver));

        assertThat(res).hasSize(2);
        assertThat(res.get(0).images()).containsExactly("url:k1");
        assertThat(res.get(0).previousHandovers()).isEqualTo(1L);
        assertThat(res.get(1).previousHandovers()).isEqualTo(0L);
    }

    @Test
    void utc012_receiveAtCounter_valid_createsReceiptClosesPendingTransferAndAppointments() {
        grant(approver);
        Post p = eligiblePost(10L, owner);
        when(posts.findForClaimUpdate(10L)).thenReturn(Optional.of(p));
        when(jdbc.queryForObject(contains("select exists(select 1 from post p where p.post_id=?"), eq(Boolean.class), eq(10L)))
                .thenReturn(true);
        when(receipts.existsByPostId(10L)).thenReturn(false);
        when(claims.findAllForOfficeTransfer(10L)).thenReturn(List.of());
        when(jdbc.queryForObject(contains("from p2p_handover"), eq(Long.class), eq(10L))).thenReturn(0L);
        when(locations.findById(3L)).thenReturn(Optional.of(activeLoc));
        when(jdbc.queryForList(contains("select transfer_id from office_transfer"), eq(Long.class), eq(10L)))
                .thenReturn(List.of(4L));

        Long receiptId = service.receiveAtCounter(new CounterIntake(5L, 10L, 3L, "Good", null, true), ud(approver));

        assertThat(receiptId).isEqualTo(100L);
        ArgumentCaptor<ItemReceipt> cap = ArgumentCaptor.forClass(ItemReceipt.class);
        verify(receipts).saveAndFlush(cap.capture());
        assertThat(cap.getValue().getReceiptCode()).startsWith("VP-");
        assertThat(p.getCustodyMode()).isEqualTo("OFFICE");
        verify(jdbc).update(contains("update office_transfer set status='RECEIVED'"), eq(2L), eq(100L), isNull(), eq(4L));
        verify(jdbc).update(contains("update office_intake_appointment set status='COMPLETED'"), eq(4L));
    }

    @Test
    void utc013_receiveAtCounter_wrongOwner_throwsBadRequest() {
        grant(approver);
        Post p = eligiblePost(10L, otherUser);
        when(posts.findForClaimUpdate(10L)).thenReturn(Optional.of(p));

        AppException ex = catchThrowableOfType(
                () -> service.receiveAtCounter(new CounterIntake(5L, 10L, 3L, "Good", null, true), ud(approver)),
                AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ex.getMessage()).isEqualTo("Bài đăng không thuộc người giao đã chọn.");
    }

    @Test
    void utc014_receiveAtCounter_notPhysicallyReceived_throwsBadRequest() {
        grant(approver);
        Post p = eligiblePost(10L, owner);
        when(posts.findForClaimUpdate(10L)).thenReturn(Optional.of(p));

        AppException ex = catchThrowableOfType(
                () -> service.receiveAtCounter(new CounterIntake(5L, 10L, 3L, "Good", null, false), ud(approver)),
                AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ex.getMessage()).isEqualTo("Cần xác nhận đã trực tiếp nhận và kiểm tra đồ.");
    }

    @Test
    void utc015_receiveAtCounter_ineligible_throwsBadRequest() {
        grant(approver);
        Post p = eligiblePost(10L, owner);
        when(posts.findForClaimUpdate(10L)).thenReturn(Optional.of(p));
        when(jdbc.queryForObject(contains("select exists(select 1 from post p where p.post_id=?"), eq(Boolean.class), eq(10L)))
                .thenReturn(false);

        AppException ex = catchThrowableOfType(
                () -> service.receiveAtCounter(new CounterIntake(5L, 10L, 3L, "Good", null, true), ud(approver)),
                AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ex.getMessage()).contains("Bài đăng không còn đủ điều kiện tiếp nhận.");
    }

    @Test
    void utc016_receiveAtCounter_blankCondition_throwsBadRequest() {
        grant(approver);
        Post p = eligiblePost(10L, owner);
        when(posts.findForClaimUpdate(10L)).thenReturn(Optional.of(p));
        when(jdbc.queryForObject(contains("select exists(select 1 from post p where p.post_id=?"), eq(Boolean.class), eq(10L)))
                .thenReturn(true);
        when(receipts.existsByPostId(10L)).thenReturn(false);

        AppException ex = catchThrowableOfType(
                () -> service.receiveAtCounter(new CounterIntake(5L, 10L, 3L, "  ", null, true), ud(approver)),
                AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ex.getMessage()).isEqualTo("Ghi tình trạng đồ khi nhận.");
    }

    @Test
    void utc017_receiveAtCounter_approverIsOwner_throwsBadRequest() {
        grant(approver);
        Post p = eligiblePost(10L, approver);
        when(posts.findForClaimUpdate(10L)).thenReturn(Optional.of(p));
        when(jdbc.queryForObject(contains("select exists(select 1 from post p where p.post_id=?"), eq(Boolean.class), eq(10L)))
                .thenReturn(true);

        AppException ex = catchThrowableOfType(
                () -> service.receiveAtCounter(new CounterIntake(2L, 10L, 3L, "Good", null, true), ud(approver)),
                AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ex.getMessage()).isEqualTo("Người giao không được tự xác nhận tiếp nhận đồ của mình.");
    }

    @Test
    void utc018_receiveAtCounter_nullLocation_throwsBadRequest() {
        grant(approver);
        Post p = eligiblePost(10L, owner);
        when(posts.findForClaimUpdate(10L)).thenReturn(Optional.of(p));
        when(jdbc.queryForObject(contains("select exists(select 1 from post p where p.post_id=?"), eq(Boolean.class), eq(10L)))
                .thenReturn(true);
        when(claims.findAllForOfficeTransfer(10L)).thenReturn(List.of());
        when(jdbc.queryForObject(contains("from p2p_handover"), eq(Long.class), eq(10L))).thenReturn(0L);
        when(locations.findById(-1L)).thenReturn(Optional.empty());

        AppException ex = catchThrowableOfType(
                () -> service.receiveAtCounter(new CounterIntake(5L, 10L, null, "Good", null, true), ud(approver)),
                AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ex.getMessage()).isEqualTo("Chọn vị trí lưu kho.");
    }

    @Test
    void utc019_receiveAtCounter_inactiveLocation_throwsBadRequest() {
        grant(approver);
        Post p = eligiblePost(10L, owner);
        StorageLocation inactive = StorageLocation.create("A-01", "Shelf A1", null);
        inactive.update("A-01", "Shelf A1", null, false);
        ReflectionTestUtils.setField(inactive, "id", 3L);
        when(posts.findForClaimUpdate(10L)).thenReturn(Optional.of(p));
        when(jdbc.queryForObject(contains("select exists(select 1 from post p where p.post_id=?"), eq(Boolean.class), eq(10L)))
                .thenReturn(true);
        when(claims.findAllForOfficeTransfer(10L)).thenReturn(List.of());
        when(jdbc.queryForObject(contains("from p2p_handover"), eq(Long.class), eq(10L))).thenReturn(0L);
        when(locations.findById(3L)).thenReturn(Optional.of(inactive));

        AppException ex = catchThrowableOfType(
                () -> service.receiveAtCounter(new CounterIntake(5L, 10L, 3L, "Good", null, true), ud(approver)),
                AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ex.getMessage()).isEqualTo("Vị trí lưu kho đã ngừng sử dụng.");
    }

    @Test
    void utc020_receiveAtCounter_previousHandoverWithoutReconciliationNote_throwsBadRequest() {
        grant(approver);
        Post p = eligiblePost(10L, owner);
        when(posts.findForClaimUpdate(10L)).thenReturn(Optional.of(p));
        when(jdbc.queryForObject(contains("select exists(select 1 from post p where p.post_id=?"), eq(Boolean.class), eq(10L)))
                .thenReturn(true);
        when(claims.findAllForOfficeTransfer(10L)).thenReturn(List.of());
        when(jdbc.queryForObject(contains("from p2p_handover"), eq(Long.class), eq(10L))).thenReturn(1L);

        AppException ex = catchThrowableOfType(
                () -> service.receiveAtCounter(new CounterIntake(5L, 10L, 3L, "Good", null, true), ud(approver)),
                AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ex.getMessage()).contains("Đã có biên bản trao trả trước đó.");
    }

    @Test
    void utc021_receiveAtCounter_completedClaimExists_throwsBadRequest() {
        grant(approver);
        Post p = eligiblePost(10L, owner);
        Claim c = Claim.submit(p, otherUser, "Mine", null);
        c.review("COMPLETED");
        when(posts.findForClaimUpdate(10L)).thenReturn(Optional.of(p));
        when(jdbc.queryForObject(contains("select exists(select 1 from post p where p.post_id=?"), eq(Boolean.class), eq(10L)))
                .thenReturn(true);
        when(claims.findAllForOfficeTransfer(10L)).thenReturn(List.of(c));

        AppException ex = catchThrowableOfType(
                () -> service.receiveAtCounter(new CounterIntake(5L, 10L, 3L, "Good", null, true), ud(approver)),
                AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ex.getMessage()).isEqualTo("Đồ đã hoàn tất trao trả, không thể chuyển giao.");
    }

    @Test
    void utc022_receiveAtCounter_transfersOpenP2pClaimsCancelsAppointmentsRecordsActivityAndNotifiesChat() {
        grant(approver);
        Post p = eligiblePost(10L, owner);
        Claim c1 = Claim.submit(p, otherUser, "C1", null);
        ReflectionTestUtils.setField(c1, "id", 31L);
        c1.review("APPROVED");
        Claim c2 = Claim.submit(p, manager, "C2", null);
        ReflectionTestUtils.setField(c2, "id", 32L);
        Appointment appt1 = Appointment.schedule(c1, owner, OffsetDateTime.now().plusDays(1), "Gate", null);

        when(posts.findForClaimUpdate(10L)).thenReturn(Optional.of(p));
        when(jdbc.queryForObject(contains("select exists(select 1 from post p where p.post_id=?"), eq(Boolean.class), eq(10L)))
                .thenReturn(true);
        when(claims.findAllForOfficeTransfer(10L)).thenReturn(List.of(c1, c2));
        when(jdbc.queryForObject(contains("from p2p_handover"), eq(Long.class), eq(10L))).thenReturn(0L);
        when(locations.findById(3L)).thenReturn(Optional.of(activeLoc));
        when(appointments.findFirstByClaim_IdAndStatusOrderByIdDesc(31L, "SCHEDULED")).thenReturn(Optional.of(appt1));
        when(appointments.findFirstByClaim_IdAndStatusOrderByIdDesc(32L, "SCHEDULED")).thenReturn(Optional.empty());

        service.receiveAtCounter(new CounterIntake(5L, 10L, 3L, "Good", null, true), ud(approver));

        assertThat(c1.isOffice()).isTrue();
        assertThat(c1.getStatus()).isEqualTo("PENDING");
        assertThat(c2.isOffice()).isTrue();
        assertThat(appt1.getStatus()).isEqualTo("CANCELLED");
        verify(activities, times(2)).save(any(ClaimActivity.class));
        verify(conversations).notifyChanged(c1);
        verify(conversations).notifyChanged(c2);
    }

    @Test
    void utc023_list_approverAll_returns3TransfersTotalPages1() {
        grant(approver);
        Post p = eligiblePost(10L, owner);
        when(jdbc.queryForObject(startsWith("select count(*)"), eq(Long.class),
                eq("ALL"), eq("ALL"), eq("ALL"), eq("ALL"), eq("ALL"), eq(false), eq(2L), eq("%%")))
                .thenReturn(3L);
        when(jdbc.query(startsWith("select t.transfer_id"), any(RowMapper.class),
                eq("ALL"), eq("ALL"), eq("ALL"), eq("ALL"), eq("ALL"), eq(false), eq(2L), eq("%%"), eq(0L)))
                .thenReturn(List.of(1L, 2L, 3L));
        stubViewQueries(1L, transferRow(1L, 10L, 5L, "PENDING", 0), p, 0L, 0L);
        stubViewQueries(2L, transferRow(2L, 10L, 5L, "PENDING", 0), p, 0L, 0L);
        stubViewQueries(3L, transferRow(3L, 10L, 5L, "PENDING", 0), p, 0L, 0L);

        PageData<TransferView> res = service.list(1, "ALL", null, false, ud(approver));

        assertThat(res.content()).hasSize(3);
        assertThat(res.totalPages()).isEqualTo(1);
    }

    @Test
    void utc024_list_userMineTrue_filtersByActor() {
        grant(owner);
        when(jdbc.queryForObject(startsWith("select count(*)"), eq(Long.class),
                eq("ALL"), eq("ALL"), eq("ALL"), eq("ALL"), eq("ALL"), eq(true), eq(5L), eq("%%")))
                .thenReturn(0L);
        when(jdbc.query(startsWith("select t.transfer_id"), any(RowMapper.class),
                eq("ALL"), eq("ALL"), eq("ALL"), eq("ALL"), eq("ALL"), eq(true), eq(5L), eq("%%"), eq(0L)))
                .thenReturn(List.of());

        PageData<TransferView> res = service.list(1, "ALL", null, true, ud(owner));

        assertThat(res.content()).isEmpty();
        verify(jdbc).query(startsWith("select t.transfer_id"), any(RowMapper.class),
                eq("ALL"), eq("ALL"), eq("ALL"), eq("ALL"), eq("ALL"), eq(true), eq(5L), eq("%%"), eq(0L));
    }

    @Test
    void utc025_list_userMineFalse_throwsForbidden() {
        grant(owner);

        AppException ex = catchThrowableOfType(
                () -> service.list(1, "ALL", null, false, ud(owner)),
                AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void utc026_list_pageZero_throwsBadRequest() {
        grant(approver);

        AppException ex = catchThrowableOfType(
                () -> service.list(0, "ALL", null, false, ud(approver)),
                AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ex.getMessage()).isEqualTo("Bộ lọc không hợp lệ.");
    }

    @Test
    void utc027_list_invalidStatus_throwsBadRequest() {
        grant(approver);

        AppException ex = catchThrowableOfType(
                () -> service.list(1, "DONE", null, false, ud(approver)),
                AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void utc028_list_zeroCount_totalPagesMinOne() {
        grant(approver);
        when(jdbc.queryForObject(startsWith("select count(*)"), eq(Long.class),
                eq("PENDING"), eq("PENDING"), eq("PENDING"), eq("PENDING"), eq("PENDING"), eq(false), eq(2L), eq("%%")))
                .thenReturn(0L);
        when(jdbc.query(startsWith("select t.transfer_id"), any(RowMapper.class),
                eq("PENDING"), eq("PENDING"), eq("PENDING"), eq("PENDING"), eq("PENDING"), eq(false), eq(2L), eq("%%"), eq(0L)))
                .thenReturn(List.of());

        PageData<TransferView> res = service.list(1, "PENDING", null, false, ud(approver));

        assertThat(res.totalElements()).isZero();
        assertThat(res.totalPages()).isEqualTo(1);
    }

    @Test
    void utc029_detail_requester_returnsTransferView() {
        grant(owner);
        Post p = eligiblePost(10L, owner);
        stubViewQueries(4L, transferRow(4L, 10L, 5L, "PENDING", 0), p, 1L, 0L);

        TransferView view = service.detail(4L, ud(owner));

        assertThat(view.id()).isEqualTo(4L);
        assertThat(view.postId()).isEqualTo(10L);
    }

    @Test
    void utc030_detail_nonRequesterUser_throwsBadRequest() {
        grant(otherUser);
        Post p = eligiblePost(10L, owner);
        stubViewQueries(4L, transferRow(4L, 10L, 5L, "PENDING", 0), p, 0L, 0L);

        AppException ex = catchThrowableOfType(() -> service.detail(4L, ud(otherUser)), AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ex.getMessage()).isEqualTo("Không có quyền xem yêu cầu này.");
    }

    @Test
    void utc031_detail_missingTransfer_throwsBadRequest() {
        grant(approver);
        when(jdbc.queryForList(contains("from office_transfer where transfer_id=?"), eq(999L))).thenReturn(List.of());

        AppException ex = catchThrowableOfType(() -> service.detail(999L, ud(approver)), AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ex.getMessage()).isEqualTo("Không tìm thấy yêu cầu chuyển giao.");
    }

    @Test
    void utc032_decide_received_createsReceiptCompletesAppointmentAndNotifies() {
        grant(approver);
        Post p = eligiblePost(10L, owner);
        Map<String, Object> pendingRow = transferRow(4L, 10L, 5L, "PENDING", 1L);
        Map<String, Object> receivedRow = transferRow(4L, 10L, 5L, "RECEIVED", 2L);
        receivedRow.put("item_receipt_id", 100L);

        when(jdbc.queryForList(contains("from office_transfer where transfer_id=?"), eq(4L)))
                .thenReturn(List.of(pendingRow), List.of(receivedRow));
        when(jdbc.queryForMap(contains("from office_transfer where transfer_id=? for update"), eq(4L)))
                .thenReturn(pendingRow);
        when(posts.findForClaimUpdate(10L)).thenReturn(Optional.of(p));
        when(posts.findById(10L)).thenReturn(Optional.of(p));
        when(receipts.existsByPostId(10L)).thenReturn(false);
        when(claims.findAllForOfficeTransfer(10L)).thenReturn(List.of());
        when(jdbc.queryForObject(contains("from p2p_handover"), eq(Long.class), eq(10L))).thenReturn(0L);
        when(jdbc.queryForObject(contains("from claim where post_id=?"), eq(Long.class), eq(10L))).thenReturn(0L);
        when(locations.findById(3L)).thenReturn(Optional.of(activeLoc));

        TransferView res = service.decide(4L, new TransferDecision(1L, "RECEIVED", 3L, "Good", null, null), ud(approver));

        assertThat(res.status()).isEqualTo("RECEIVED");
        assertThat(res.receiptId()).isEqualTo(100L);
        verify(jdbc).update(contains("update office_intake_appointment set status=?"), eq("COMPLETED"), isNull(), eq(4L));
        verify(notifications).notifyUser(eq(5L), eq("OFFICE_TRANSFER_RECEIVED"), anyString(), anyString(), eq("/van-phong/chuyen-giao"), eq(true));
    }

    @Test
    void utc033_decide_rejected_cancelsRequestAppointmentAndNotifies() {
        grant(approver);
        Post p = eligiblePost(10L, owner);
        p.requestOfficeHandover();
        Map<String, Object> pendingRow = transferRow(4L, 10L, 5L, "PENDING", 1L);
        Map<String, Object> rejectedRow = transferRow(4L, 10L, 5L, "REJECTED", 2L);
        when(jdbc.queryForList(contains("from office_transfer where transfer_id=?"), eq(4L)))
                .thenReturn(List.of(pendingRow), List.of(rejectedRow));
        when(jdbc.queryForMap(contains("from office_transfer where transfer_id=? for update"), eq(4L)))
                .thenReturn(pendingRow);
        when(posts.findForClaimUpdate(10L)).thenReturn(Optional.of(p));
        when(posts.findById(10L)).thenReturn(Optional.of(p));
        when(jdbc.queryForObject(contains("from p2p_handover"), eq(Long.class), eq(10L))).thenReturn(0L);
        when(jdbc.queryForObject(contains("from claim where post_id=?"), eq(Long.class), eq(10L))).thenReturn(0L);

        TransferView res = service.decide(4L, new TransferDecision(1L, "REJECTED", null, null, "Item not brought", null), ud(approver));

        assertThat(res.status()).isEqualTo("REJECTED");
        assertThat(p.getHandoverRequestedAt()).isNull();
        verify(jdbc).update(contains("update office_intake_appointment set status=?"), eq("CANCELLED"), eq("Item not brought"), eq(4L));
        verify(notifications).notifyUser(eq(5L), eq("OFFICE_TRANSFER_REJECTED"), anyString(), anyString(), eq("/van-phong/chuyen-giao"), eq(true));
    }

    @Test
    void utc034_decide_cancelledByRequester_noNotification() {
        grant(owner);
        Post p = eligiblePost(10L, owner);
        p.requestOfficeHandover();
        Map<String, Object> pendingRow = transferRow(4L, 10L, 5L, "PENDING", 1L);
        Map<String, Object> cancelledRow = transferRow(4L, 10L, 5L, "CANCELLED", 2L);
        when(jdbc.queryForList(contains("from office_transfer where transfer_id=?"), eq(4L)))
                .thenReturn(List.of(pendingRow), List.of(cancelledRow));
        when(jdbc.queryForMap(contains("from office_transfer where transfer_id=? for update"), eq(4L)))
                .thenReturn(pendingRow);
        when(posts.findForClaimUpdate(10L)).thenReturn(Optional.of(p));
        when(posts.findById(10L)).thenReturn(Optional.of(p));
        when(jdbc.queryForObject(contains("from p2p_handover"), eq(Long.class), eq(10L))).thenReturn(0L);
        when(jdbc.queryForObject(contains("from claim where post_id=?"), eq(Long.class), eq(10L))).thenReturn(0L);

        TransferView res = service.decide(4L, new TransferDecision(1L, "CANCELLED", null, null, "I returned it myself", null), ud(owner));

        assertThat(res.status()).isEqualTo("CANCELLED");
        verifyNoInteractions(notifications);
    }

    @Test
    void utc035_decide_cancelByNonRequester_throwsBadRequest() {
        grant(otherUser);
        Post p = eligiblePost(10L, owner);
        Map<String, Object> row = transferRow(4L, 10L, 5L, "PENDING", 1L);
        when(jdbc.queryForList(contains("from office_transfer where transfer_id=?"), eq(4L))).thenReturn(List.of(row));
        when(jdbc.queryForMap(contains("from office_transfer where transfer_id=? for update"), eq(4L))).thenReturn(row);
        when(posts.findForClaimUpdate(10L)).thenReturn(Optional.of(p));

        AppException ex = catchThrowableOfType(
                () -> service.decide(4L, new TransferDecision(1L, "CANCELLED", null, null, "Reason", null), ud(otherUser)),
                AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ex.getMessage()).isEqualTo("Chỉ người gửi được rút yêu cầu.");
    }

    @Test
    void utc036_decide_receivedByManager_throwsBadRequest() {
        grant(manager);
        Post p = eligiblePost(10L, owner);
        Map<String, Object> row = transferRow(4L, 10L, 5L, "PENDING", 1L);
        when(jdbc.queryForList(contains("from office_transfer where transfer_id=?"), eq(4L))).thenReturn(List.of(row));
        when(jdbc.queryForMap(contains("from office_transfer where transfer_id=? for update"), eq(4L))).thenReturn(row);
        when(posts.findForClaimUpdate(10L)).thenReturn(Optional.of(p));

        AppException ex = catchThrowableOfType(
                () -> service.decide(4L, new TransferDecision(1L, "RECEIVED", 3L, "Good", null, null), ud(manager)),
                AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ex.getMessage()).isEqualTo("Chỉ nhân viên tiếp nhận được xử lý việc nhận đồ.");
    }

    @Test
    void utc037_decide_rejectedEmptyReason_throwsBadRequest() {
        grant(approver);
        Post p = eligiblePost(10L, owner);
        Map<String, Object> row = transferRow(4L, 10L, 5L, "PENDING", 1L);
        when(jdbc.queryForList(contains("from office_transfer where transfer_id=?"), eq(4L))).thenReturn(List.of(row));
        when(jdbc.queryForMap(contains("from office_transfer where transfer_id=? for update"), eq(4L))).thenReturn(row);
        when(posts.findForClaimUpdate(10L)).thenReturn(Optional.of(p));

        AppException ex = catchThrowableOfType(
                () -> service.decide(4L, new TransferDecision(1L, "REJECTED", null, null, "", null), ud(approver)),
                AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ex.getMessage()).isEqualTo("Nhập lý do kết thúc yêu cầu chuyển giao.");
    }

    @Test
    void utc038_decide_versionMismatch_throwsBadRequest() {
        grant(approver);
        Post p = eligiblePost(10L, owner);
        Map<String, Object> row = transferRow(4L, 10L, 5L, "PENDING", 2L);
        when(jdbc.queryForList(contains("from office_transfer where transfer_id=?"), eq(4L))).thenReturn(List.of(row));
        when(jdbc.queryForMap(contains("from office_transfer where transfer_id=? for update"), eq(4L))).thenReturn(row);
        when(posts.findForClaimUpdate(10L)).thenReturn(Optional.of(p));

        AppException ex = catchThrowableOfType(
                () -> service.decide(4L, new TransferDecision(1L, "REJECTED", null, null, "No show", null), ud(approver)),
                AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ex.getMessage()).isEqualTo("Yêu cầu đã thay đổi. Hãy tải lại.");
    }

    @Test
    void utc039_decide_alreadyReceived_throwsBadRequest() {
        grant(approver);
        Post p = eligiblePost(10L, owner);
        Map<String, Object> row = transferRow(4L, 10L, 5L, "RECEIVED", 1L);
        when(jdbc.queryForList(contains("from office_transfer where transfer_id=?"), eq(4L))).thenReturn(List.of(row));
        when(jdbc.queryForMap(contains("from office_transfer where transfer_id=? for update"), eq(4L))).thenReturn(row);
        when(posts.findForClaimUpdate(10L)).thenReturn(Optional.of(p));

        AppException ex = catchThrowableOfType(
                () -> service.decide(4L, new TransferDecision(1L, "REJECTED", null, null, "No show", null), ud(approver)),
                AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
    }
}

