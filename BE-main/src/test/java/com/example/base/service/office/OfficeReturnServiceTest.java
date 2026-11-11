package com.example.base.service.office;

import com.example.base.dto.office.OfficeDtos.ReviewResult;
import com.example.base.dto.office.OfficeWorkflowDtos.*;
import com.example.base.entity.*;
import com.example.base.exception.AppException;
import com.example.base.exception.ErrorCode;
import com.example.base.repository.*;
import com.example.base.security.CustomUserDetails;
import com.example.base.service.notification.NotificationService;
import com.example.base.service.report.ReportHoldGuard;
import com.example.base.service.report.WorkflowImages;
import com.example.base.storage.ImageValidator;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OfficeReturnServiceTest {

    @Mock ReportHoldGuard reportHolds;
    @Mock OfficeAccess access;
    @Mock ClaimRepository claims;
    @Mock PostRepository posts;
    @Mock AppointmentRepository appointments;
    @Mock ClaimActivityRepository activities;
    @Mock OfficeInvitationService invitations;
    @Mock WorkflowImages images;
    @Mock EntityManager em;
    @Mock JdbcTemplate jdbc;
    @Mock PlatformTransactionManager transactions;
    @Mock NotificationService notifications;

    @InjectMocks
    OfficeReturnService service;

    Account approver;
    Account claimant;
    Account otherUser;
    Category category;
    StorageLocation location;
    Post post;
    ItemReceipt receipt;

    @BeforeEach
    void setUp() {
        approver = account(2L, AccountRole.APPROVER, "Approver 2");
        claimant = account(5L, AccountRole.USER, "Claimant 5");
        otherUser = account(6L, AccountRole.USER, "Other 6");

        category = new Category();
        ReflectionTestUtils.setField(category, "categoryId", 10L);
        category.setName("Wallet");

        location = StorageLocation.create("A-01", "Shelf A1", null);
        ReflectionTestUtils.setField(location, "id", 1L);

        post = Post.receivedByOffice(approver, category, "Black Wallet", "Leather wallet", "Hall A",
                OffsetDateTime.now().minusDays(1));
        ReflectionTestUtils.setField(post, "id", 10L);
        post.reserveForClaim();

        receipt = ItemReceipt.walkInDraft("VP-1", approver);
        ReflectionTestUtils.setField(receipt, "id", 20L);
        receipt.edit(category, "Black Wallet", "Desc", "Good", "Hall A", OffsetDateTime.now().minusDays(1), null, null);
        receipt.assignLocation(location);
        receipt.receiveAndPublish(approver, 10L);

        when(transactions.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        when(jdbc.query(contains("from office_claim_verification"), any(RowMapper.class), anyLong())).thenReturn(List.of());
        when(jdbc.query(contains("from office_handover h"), any(RowMapper.class), anyLong())).thenReturn(List.of());
        when(claims.findAllForOfficeTransfer(10L)).thenReturn(List.of());
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
        when(access.require(any())).thenReturn(actor);
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

    private Claim officeClaim(long id, Account ownerClaimant, String status, long version) {
        Claim c = Claim.office(post, ownerClaimant, approver, "ONLINE",
                ownerClaimant != null ? ownerClaimant.getFullName() : "Guest",
                ownerClaimant != null ? ownerClaimant.getEmail() : null,
                "0900000000", "Mine", null, receipt);
        ReflectionTestUtils.setField(c, "id", id);
        ReflectionTestUtils.setField(c, "version", version);
        c.review(status);
        return c;
    }

    private Appointment appointment(Claim c, long apptId, String responseStatus) {
        Appointment a = Appointment.schedule(c, approver, OffsetDateTime.now().plusDays(1), "Room 1", null);
        ReflectionTestUtils.setField(a, "id", apptId);
        if ("CONFIRMED".equals(responseStatus)) {
            a.invitationSent("h".repeat(64), OffsetDateTime.now().minusHours(1));
            a.confirmInvitation(OffsetDateTime.now());
        } else if ("PENDING".equals(responseStatus)) {
            a.invitationSent("h".repeat(64), OffsetDateTime.now().minusHours(1));
        }
        return a;
    }

    private void stubClaim(Claim c) {
        long cid = c.getId();
        long pid = c.getPost().getId();
        when(claims.findById(cid)).thenReturn(Optional.of(c));
        when(claims.findPostId(cid)).thenReturn(Optional.of(pid));
        when(posts.findForClaimUpdate(pid)).thenReturn(Optional.of(c.getPost()));
        when(claims.findForUpdate(cid)).thenReturn(Optional.of(c));
    }

    @Test
    void utc024_detail_verifiedConfirmed_canRecordTrue() {
        grant(approver);
        Claim c = officeClaim(30L, claimant, "APPROVED", 1L);
        Appointment a = appointment(c, 8L, "CONFIRMED");
        stubClaim(c);
        when(appointments.findFirstByClaim_IdAndStatusOrderByIdDesc(30L, "SCHEDULED")).thenReturn(Optional.of(a));
        Verification passed = new Verification(1L, 8L, "PASSED", "Matched", "Approver 2", OffsetDateTime.now());
        when(jdbc.query(contains("from office_claim_verification"), any(RowMapper.class), eq(30L)))
                .thenReturn(List.of(passed));

        Workflow w = service.detail(30L, ud(approver));

        assertThat(w.canRecord()).isTrue();
        assertThat(w.nextStep()).isEqualTo("Đã xác minh. Có thể ghi nhận giao đồ.");
    }

    @Test
    void utc025_detail_appointmentNotConfirmed_asksToWaitForConfirmation() {
        grant(approver);
        Claim c = officeClaim(30L, claimant, "APPROVED", 1L);
        Appointment a = appointment(c, 8L, "PENDING");
        stubClaim(c);
        when(appointments.findFirstByClaim_IdAndStatusOrderByIdDesc(30L, "SCHEDULED")).thenReturn(Optional.of(a));

        Workflow w = service.detail(30L, ud(approver));

        assertThat(w.canRecord()).isFalse();
        assertThat(w.nextStep()).isEqualTo("Chờ người nhận đồng ý lịch hẹn qua email hoặc trên hệ thống.");
    }

    @Test
    void utc026_detail_claimRejected_showsClosedMessage() {
        grant(approver);
        Claim c = officeClaim(30L, claimant, "REJECTED", 1L);
        stubClaim(c);

        Workflow w = service.detail(30L, ud(approver));

        assertThat(w.nextStep()).isEqualTo("Hồ sơ đã kết thúc. Xem lý do trong lịch sử xử lý.");
    }

    @Test
    void utc027_detail_handoverCompleted_showsCompletedMessage() {
        grant(approver);
        Claim c = officeClaim(30L, claimant, "COMPLETED", 1L);
        stubClaim(c);
        Handover completed = new Handover(50L, "COMPLETED", "Intact", "Approver 2", "Claimant 5",
                OffsetDateTime.now(), OffsetDateTime.now(), null, 1L, List.of());
        when(jdbc.query(contains("from office_handover h"), any(RowMapper.class), eq(30L)))
                .thenReturn(List.of(completed));

        Workflow w = service.detail(30L, ud(approver));

        assertThat(w.nextStep()).isEqualTo("Đã hoàn tất trao trả.");
    }

    @Test
    void utc028_detail_otherUserNotClaimant_throwsBadRequest() {
        grant(otherUser);
        Claim c = officeClaim(30L, claimant, "APPROVED", 1L);
        stubClaim(c);

        AppException ex = catchThrowableOfType(() -> service.detail(30L, ud(otherUser)), AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ex.getMessage()).isEqualTo("Chỉ người khai nhận được thực hiện thao tác này.");
    }

    @Test
    void utc029_detail_p2pClaim_throwsBadRequest() {
        grant(approver);
        Claim p2p = Claim.submit(post, claimant, "Mine", null);
        ReflectionTestUtils.setField(p2p, "id", 30L);
        when(claims.findById(30L)).thenReturn(Optional.of(p2p));

        AppException ex = catchThrowableOfType(() -> service.detail(30L, ud(approver)), AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ex.getMessage()).isEqualTo("Không tìm thấy hồ sơ văn phòng.");
    }

    @Test
    void utc030_reschedule_valid_cancelsOldSavesNewNotifiesAndSendsInvitation() {
        grant(approver);
        Claim c = officeClaim(30L, claimant, "APPROVED", 3L);
        Appointment oldAppt = appointment(c, 8L, "PENDING");
        stubClaim(c);
        when(appointments.findFirstByClaim_IdAndStatusOrderByIdDesc(30L, "SCHEDULED")).thenReturn(Optional.of(oldAppt));
        when(invitations.send(30L)).thenReturn(true);

        OffsetDateTime nextTime = OffsetDateTime.now().plusDays(2);
        ReviewResult res = service.reschedule(30L, new Reschedule(3L, nextTime, "Room 2", "Staff absent", null), ud(approver));

        assertThat(res.emailStatus()).isEqualTo("SENT");
        assertThat(oldAppt.getStatus()).isEqualTo("CANCELLED");
        verify(appointments).save(any(Appointment.class));
        verify(activities).save(argThat(a -> "RESCHEDULE".equals(a.getAction())));
        verify(notifications).notifyUser(eq(5L), eq("APPOINTMENT_RESCHEDULED"), anyString(), anyString(), eq("/van-phong/claims/30"), eq(false));
    }

    @Test
    void utc031_reschedule_at12HoursBoundary_throwsBadRequest() {
        grant(approver);
        Claim c = officeClaim(30L, claimant, "APPROVED", 3L);
        stubClaim(c);

        AppException ex = catchThrowableOfType(
                () -> service.reschedule(30L, new Reschedule(3L, OffsetDateTime.now().plusHours(12), "Room 2", "Late", null), ud(approver)),
                AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ex.getMessage()).contains("Lịch mới cần cách hiện tại hơn 12 giờ");
    }

    @Test
    void utc032_reschedule_versionMismatch_throwsBadRequest() {
        grant(approver);
        Claim c = officeClaim(30L, claimant, "APPROVED", 4L);
        stubClaim(c);

        AppException ex = catchThrowableOfType(
                () -> service.reschedule(30L, new Reschedule(3L, OffsetDateTime.now().plusDays(2), "Room 2", "Staff absent", null), ud(approver)),
                AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ex.getMessage()).isEqualTo("Hồ sơ đã thay đổi. Hãy tải lại trước khi thao tác.");
    }

    @Test
    void utc033_reschedule_approverIsClaimant_throwsBadRequest() {
        grant(approver);
        Claim c = officeClaim(30L, approver, "APPROVED", 3L);
        stubClaim(c);

        AppException ex = catchThrowableOfType(
                () -> service.reschedule(30L, new Reschedule(3L, OffsetDateTime.now().plusDays(2), "Room 2", "Staff absent", null), ud(approver)),
                AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ex.getMessage()).isEqualTo("Không được tự xử lý hồ sơ nhận đồ của mình.");
    }

    @Test
    void utc034_reschedule_handoverAlreadyExists_throwsBadRequest() {
        grant(approver);
        Claim c = officeClaim(30L, claimant, "APPROVED", 3L);
        stubClaim(c);
        Handover h = new Handover(50L, "COMPLETED", "Intact", "Approver 2", "Claimant 5",
                OffsetDateTime.now(), OffsetDateTime.now(), null, 1L, List.of());
        when(jdbc.query(contains("from office_handover h"), any(RowMapper.class), eq(30L)))
                .thenReturn(List.of(h));

        AppException ex = catchThrowableOfType(
                () -> service.reschedule(30L, new Reschedule(3L, OffsetDateTime.now().plusDays(2), "Room 2", "Staff absent", null), ud(approver)),
                AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ex.getMessage()).contains("Hồ sơ không còn ở bước chuẩn bị trao trả.");
    }

    @Test
    void utc035_reschedule_invitationSendThrows_returnsFailedDelivery() {
        grant(approver);
        Claim c = officeClaim(30L, claimant, "APPROVED", 3L);
        Appointment oldAppt = appointment(c, 8L, "PENDING");
        stubClaim(c);
        when(appointments.findFirstByClaim_IdAndStatusOrderByIdDesc(30L, "SCHEDULED")).thenReturn(Optional.of(oldAppt));
        when(invitations.send(30L)).thenThrow(new RuntimeException("Mail error"));

        ReviewResult res = service.reschedule(30L, new Reschedule(3L, OffsetDateTime.now().plusDays(2), "Room 2", "Staff absent", null), ud(approver));

        assertThat(res.status()).isEqualTo("APPROVED");
        assertThat(res.emailStatus()).isEqualTo("FAILED");
    }

    @Test
    void utc036_cancel_valid_cancelsAppointmentRejectsClaimReopensPostAndNotifies() {
        grant(approver);
        Claim c = officeClaim(30L, claimant, "APPROVED", 3L);
        Appointment appt = appointment(c, 8L, "PENDING");
        stubClaim(c);
        when(appointments.findFirstByClaim_IdAndStatusOrderByIdDesc(30L, "SCHEDULED")).thenReturn(Optional.of(appt));

        Workflow w = service.cancel(30L, new VersionedReason(3L, "Claimant unreachable"), ud(approver));

        assertThat(appt.getStatus()).isEqualTo("CANCELLED");
        assertThat(c.getStatus()).isEqualTo("REJECTED");
        assertThat(post.getStatus()).isEqualTo("OPEN");
        verify(activities).save(argThat(a -> "OFFICE_CANCEL".equals(a.getAction())));
        verify(notifications).notifyUser(eq(5L), eq("CLAIM_REJECTED"), anyString(), anyString(), eq("/van-phong/claims/30"), eq(true));
        assertThat(w.nextStep()).isEqualTo("Hồ sơ đã kết thúc. Xem lý do trong lịch sử xử lý.");
    }

    @Test
    void utc037_cancel_noScheduledAppointment_throwsBadRequest() {
        grant(approver);
        Claim c = officeClaim(30L, claimant, "APPROVED", 3L);
        stubClaim(c);
        when(appointments.findFirstByClaim_IdAndStatusOrderByIdDesc(30L, "SCHEDULED")).thenReturn(Optional.empty());

        AppException ex = catchThrowableOfType(
                () -> service.cancel(30L, new VersionedReason(3L, "Claimant unreachable"), ud(approver)),
                AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ex.getMessage()).isEqualTo("Không có lịch hẹn đang hiệu lực.");
    }

    @Test
    void utc038_verify_confirmedAppointmentPassed_insertsTrimmedNote() {
        grant(approver);
        Claim c = officeClaim(30L, claimant, "APPROVED", 3L);
        Appointment appt = appointment(c, 8L, "CONFIRMED");
        stubClaim(c);
        when(appointments.findFirstByClaim_IdAndStatusOrderByIdDesc(30L, "SCHEDULED")).thenReturn(Optional.of(appt));
        Verification saved = new Verification(1L, 8L, "PASSED", "ID card matched", "Approver 2", OffsetDateTime.now());
        when(jdbc.query(contains("from office_claim_verification"), any(RowMapper.class), eq(30L)))
                .thenReturn(List.of(saved));

        Workflow w = service.verify(30L, new Verify(3L, "PASSED", " ID card matched "), ud(approver));

        verify(jdbc).update(contains("insert into office_claim_verification"), eq(30L), eq(8L), eq(2L), eq("PASSED"), eq("ID card matched"));
        assertThat(w.canRecord()).isTrue();
    }

    @Test
    void utc039_verify_failedResult_showsReverifyNextStep() {
        grant(approver);
        Claim c = officeClaim(30L, claimant, "APPROVED", 3L);
        Appointment appt = appointment(c, 8L, "CONFIRMED");
        stubClaim(c);
        when(appointments.findFirstByClaim_IdAndStatusOrderByIdDesc(30L, "SCHEDULED")).thenReturn(Optional.of(appt));
        Verification failed = new Verification(1L, 8L, "FAILED", "Wrong serial", "Approver 2", OffsetDateTime.now());
        when(jdbc.query(contains("from office_claim_verification"), any(RowMapper.class), eq(30L)))
                .thenReturn(List.of(failed));

        Workflow w = service.verify(30L, new Verify(3L, "FAILED", "Wrong serial"), ud(approver));

        assertThat(w.canRecord()).isFalse();
        assertThat(w.nextStep()).startsWith("Đối chiếu danh tính");
    }

    @Test
    void utc040_verify_appointmentPendingReply_throwsBadRequest() {
        grant(approver);
        Claim c = officeClaim(30L, claimant, "APPROVED", 3L);
        Appointment appt = appointment(c, 8L, "PENDING");
        stubClaim(c);
        when(appointments.findFirstByClaim_IdAndStatusOrderByIdDesc(30L, "SCHEDULED")).thenReturn(Optional.of(appt));

        AppException ex = catchThrowableOfType(
                () -> service.verify(30L, new Verify(3L, "PASSED", "Checked"), ud(approver)),
                AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ex.getMessage()).isEqualTo("Người nhận cần đồng ý lịch hẹn trước khi xác minh tại quầy.");
    }

    @Test
    void utc041_record_validWithImageAndVerificationPassed_completesHandoverClosesOthersAndNotifies() {
        grant(approver);
        Claim c = officeClaim(30L, claimant, "APPROVED", 3L);
        Claim otherPending = officeClaim(31L, otherUser, "PENDING", 0L);
        Appointment appt = appointment(c, 8L, "CONFIRMED");
        stubClaim(c);
        when(appointments.findFirstByClaim_IdAndStatusOrderByIdDesc(30L, "SCHEDULED")).thenReturn(Optional.of(appt));
        when(claims.findAllForOfficeTransfer(10L)).thenReturn(List.of(c, otherPending));

        Verification passed = new Verification(70L, 8L, "PASSED", "Matched", "Approver 2", OffsetDateTime.now());
        when(jdbc.query(contains("from office_claim_verification"), any(RowMapper.class), eq(30L)))
                .thenReturn(List.of(passed));

        MultipartFile jpg = mock(MultipartFile.class);
        UUID reqId = UUID.randomUUID();
        var prepared = new WorkflowImages.Prepared(
                List.of(new ImageValidator.ValidatedImage(new byte[]{1, 2}, "image/jpeg", "jpg", 10, 10, "imghash")),
                List.of("proof.jpg"),
                "reqhash");
        var uploaded = List.of(new WorkflowImages.Image("office-handovers/30/1.jpg", "proof.jpg", "image/jpeg", 2L, "imghash", 0));
        when(images.prepare(eq("OFFICE:30:Intact"), anyList())).thenReturn(prepared);
        when(images.upload(eq("office-handovers/30"), eq(prepared))).thenReturn(uploaded);
        when(jdbc.queryForObject(contains("insert into office_handover"), eq(Long.class),
                eq(30L), eq(20L), eq(70L), eq(2L), eq(5L), eq("Intact"), eq(reqId), eq("reqhash")))
                .thenReturn(90L);
        Handover completedHandover = new Handover(90L, "COMPLETED", "Intact", "Approver 2", "Claimant 5",
                OffsetDateTime.now(), OffsetDateTime.now(), null, 1L, List.of());
        when(jdbc.query(contains("from office_handover h"), any(RowMapper.class), eq(30L)))
                .thenReturn(List.of(), List.of(), List.of(), List.of(), List.of(completedHandover));

        Workflow w = service.record(30L, new RecordHandover(3L, reqId, "Intact"), List.of(jpg), ud(approver));

        assertThat(c.getStatus()).isEqualTo("COMPLETED");
        assertThat(post.getStatus()).isEqualTo("RETURNED");
        assertThat(receipt.getStatus()).isEqualTo("RETURNED");
        assertThat(appt.getStatus()).isEqualTo("COMPLETED");
        assertThat(otherPending.getStatus()).isEqualTo("CLOSED");
        verify(jdbc).update(contains("insert into office_handover_evidence"),
                eq(90L), eq("office-handovers/30/1.jpg"), eq("proof.jpg"), eq("image/jpeg"), eq(2L), eq("imghash"), eq(0));
        verify(activities).save(argThat(a -> "AUTO_CLOSE".equals(a.getAction())));
        verify(notifications).notifyUser(eq(5L), eq("HANDOVER_COMPLETED"), anyString(), anyString(), eq("/van-phong/claims/30"), eq(true));
        assertThat(w.nextStep()).isEqualTo("Đã hoàn tất trao trả.");
    }

    @Test
    void utc042_record_emptyFiles_throwsBadRequest() {
        grant(approver);
        when(images.prepare(eq("OFFICE:30:Intact"), eq(List.of())))
                .thenReturn(new WorkflowImages.Prepared(List.of(), List.of(), "h"));

        AppException ex = catchThrowableOfType(
                () -> service.record(30L, new RecordHandover(3L, UUID.randomUUID(), "Intact"), List.of(), ud(approver)),
                AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ex.getMessage()).isEqualTo("Thêm ít nhất một ảnh chứng minh việc giao đồ.");
    }

    @Test
    void utc043_record_duplicateSameClientRequestIdAndHash_idempotentNoUpload() {
        grant(approver);
        Claim c = officeClaim(30L, claimant, "COMPLETED", 3L);
        stubClaim(c);
        UUID reqId = UUID.randomUUID();
        var prepared = new WorkflowImages.Prepared(
                List.of(new ImageValidator.ValidatedImage(new byte[]{1}, "image/jpeg", "jpg", 10, 10, "imghash")),
                List.of("proof.jpg"),
                "reqhash");
        when(images.prepare(eq("OFFICE:30:Intact"), anyList())).thenReturn(prepared);
        Handover existing = new Handover(90L, "COMPLETED", "Intact", "Approver 2", "Claimant 5",
                OffsetDateTime.now(), OffsetDateTime.now(), null, 1L, List.of());
        when(jdbc.query(contains("from office_handover h"), any(RowMapper.class), eq(30L))).thenReturn(List.of(existing));
        when(jdbc.queryForObject(contains("from office_handover where claim_id=? and recorded_by=?"), eq(Boolean.class),
                eq(30L), eq(2L), eq(reqId), eq("reqhash"))).thenReturn(true);

        Workflow w = service.record(30L, new RecordHandover(3L, reqId, "Intact"), List.of(mock(MultipartFile.class)), ud(approver));

        verify(images, never()).upload(anyString(), any());
        assertThat(w.handover()).isEqualTo(existing);
    }

    @Test
    void utc044_record_handoverExistsDifferentRequest_throwsBadRequest() {
        grant(approver);
        Claim c = officeClaim(30L, claimant, "COMPLETED", 3L);
        stubClaim(c);
        UUID reqId2 = UUID.randomUUID();
        var prepared = new WorkflowImages.Prepared(
                List.of(new ImageValidator.ValidatedImage(new byte[]{1}, "image/jpeg", "jpg", 10, 10, "imghash")),
                List.of("proof.jpg"),
                "reqhash2");
        when(images.prepare(eq("OFFICE:30:Intact"), anyList())).thenReturn(prepared);
        Handover existing = new Handover(90L, "COMPLETED", "Intact", "Approver 2", "Claimant 5",
                OffsetDateTime.now(), OffsetDateTime.now(), null, 1L, List.of());
        when(jdbc.query(contains("from office_handover h"), any(RowMapper.class), eq(30L))).thenReturn(List.of(existing));
        when(jdbc.queryForObject(contains("from office_handover where claim_id=? and recorded_by=?"), eq(Boolean.class),
                eq(30L), eq(2L), eq(reqId2), eq("reqhash2"))).thenReturn(false);

        AppException ex = catchThrowableOfType(
                () -> service.record(30L, new RecordHandover(3L, reqId2, "Intact"), List.of(mock(MultipartFile.class)), ud(approver)),
                AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ex.getMessage()).isEqualTo("Đồ đã được ghi nhận giao. Hãy tải lại hồ sơ.");
    }

    @Test
    void utc045_record_verificationFailed_throwsBadRequest() {
        grant(approver);
        Claim c = officeClaim(30L, claimant, "APPROVED", 3L);
        Appointment appt = appointment(c, 8L, "CONFIRMED");
        stubClaim(c);
        when(appointments.findFirstByClaim_IdAndStatusOrderByIdDesc(30L, "SCHEDULED")).thenReturn(Optional.of(appt));
        var prepared = new WorkflowImages.Prepared(
                List.of(new ImageValidator.ValidatedImage(new byte[]{1}, "image/jpeg", "jpg", 10, 10, "imghash")),
                List.of("proof.jpg"),
                "reqhash");
        when(images.prepare(anyString(), anyList())).thenReturn(prepared);
        Verification failed = new Verification(70L, 8L, "FAILED", "Mismatch", "Approver 2", OffsetDateTime.now());
        when(jdbc.query(contains("from office_claim_verification"), any(RowMapper.class), eq(30L)))
                .thenReturn(List.of(failed));

        AppException ex = catchThrowableOfType(
                () -> service.record(30L, new RecordHandover(3L, UUID.randomUUID(), "Intact"), List.of(mock(MultipartFile.class)), ud(approver)),
                AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ex.getMessage()).isEqualTo("Cần lịch hẹn đã xác nhận và kết quả xác minh đạt cho lịch hẹn hiện tại.");
    }

    @Test
    void utc046_record_verificationPassedForOlderAppointment_throwsBadRequest() {
        grant(approver);
        Claim c = officeClaim(30L, claimant, "APPROVED", 3L);
        Appointment currentAppt = appointment(c, 8L, "CONFIRMED");
        stubClaim(c);
        when(appointments.findFirstByClaim_IdAndStatusOrderByIdDesc(30L, "SCHEDULED")).thenReturn(Optional.of(currentAppt));
        var prepared = new WorkflowImages.Prepared(
                List.of(new ImageValidator.ValidatedImage(new byte[]{1}, "image/jpeg", "jpg", 10, 10, "imghash")),
                List.of("proof.jpg"),
                "reqhash");
        when(images.prepare(anyString(), anyList())).thenReturn(prepared);
        Verification olderPassed = new Verification(60L, 7L, "PASSED", "Matched earlier", "Approver 2", OffsetDateTime.now());
        when(jdbc.query(contains("from office_claim_verification"), any(RowMapper.class), eq(30L)))
                .thenReturn(List.of(olderPassed));

        AppException ex = catchThrowableOfType(
                () -> service.record(30L, new RecordHandover(3L, UUID.randomUUID(), "Intact"), List.of(mock(MultipartFile.class)), ud(approver)),
                AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ex.getMessage()).isEqualTo("Cần lịch hẹn đã xác nhận và kết quả xác minh đạt cho lịch hẹn hiện tại.");
    }

    @Test
    void utc047_record_counterClaimNullClaimant_throwsBadRequest() {
        grant(approver);
        Claim c = officeClaim(30L, null, "APPROVED", 3L);
        stubClaim(c);
        var prepared = new WorkflowImages.Prepared(
                List.of(new ImageValidator.ValidatedImage(new byte[]{1}, "image/jpeg", "jpg", 10, 10, "imghash")),
                List.of("proof.jpg"),
                "reqhash");
        when(images.prepare(anyString(), anyList())).thenReturn(prepared);

        AppException ex = catchThrowableOfType(
                () -> service.record(30L, new RecordHandover(3L, UUID.randomUUID(), "Intact"), List.of(mock(MultipartFile.class)), ud(approver)),
                AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ex.getMessage()).isEqualTo("Cần liên kết tài khoản người nhận trước khi trao trả.");
    }

    @Test
    void utc048_record_dbInsertFailsAfterUpload_cleansUpAndRethrows() {
        grant(approver);
        Claim c = officeClaim(30L, claimant, "APPROVED", 3L);
        Appointment appt = appointment(c, 8L, "CONFIRMED");
        stubClaim(c);
        when(appointments.findFirstByClaim_IdAndStatusOrderByIdDesc(30L, "SCHEDULED")).thenReturn(Optional.of(appt));
        Verification passed = new Verification(70L, 8L, "PASSED", "Matched", "Approver 2", OffsetDateTime.now());
        when(jdbc.query(contains("from office_claim_verification"), any(RowMapper.class), eq(30L)))
                .thenReturn(List.of(passed));
        UUID reqId = UUID.randomUUID();
        var prepared = new WorkflowImages.Prepared(
                List.of(new ImageValidator.ValidatedImage(new byte[]{1}, "image/jpeg", "jpg", 10, 10, "imghash")),
                List.of("proof.jpg"),
                "reqhash");
        var uploaded = List.of(new WorkflowImages.Image("office-handovers/30/1.jpg", "proof.jpg", "image/jpeg", 1L, "imghash", 0));
        when(images.prepare(eq("OFFICE:30:Intact"), anyList())).thenReturn(prepared);
        when(images.upload(eq("office-handovers/30"), eq(prepared))).thenReturn(uploaded);
        when(jdbc.queryForObject(contains("insert into office_handover"), eq(Long.class),
                any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("DB constraint failure"));

        assertThatThrownBy(() -> service.record(30L, new RecordHandover(3L, reqId, "Intact"), List.of(mock(MultipartFile.class)), ud(approver)))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB constraint failure");
        verify(images).cleanup(uploaded);
    }

    @Test
    void utc049_complete_waitingConfirmation_completesHandoverClaimAndPost() {
        grant(approver);
        Claim c = officeClaim(30L, claimant, "APPROVED", 3L);
        stubClaim(c);
        Handover waiting = new Handover(90L, "WAITING_CONFIRMATION", "Intact", "Approver 2", "Claimant 5",
                OffsetDateTime.now(), null, null, 1L, List.of());
        Handover done = new Handover(90L, "COMPLETED", "Intact", "Approver 2", "Claimant 5",
                OffsetDateTime.now(), OffsetDateTime.now(), null, 2L, List.of());
        when(jdbc.query(contains("from office_handover h"), any(RowMapper.class), eq(30L)))
                .thenReturn(List.of(waiting), List.of(done));

        Workflow w = service.complete(30L, new Confirm(1L), ud(approver));

        assertThat(c.getStatus()).isEqualTo("COMPLETED");
        assertThat(post.getStatus()).isEqualTo("RETURNED");
        assertThat(w.handover().status()).isEqualTo("COMPLETED");
    }

    @Test
    void utc050_complete_alreadyCompleted_idempotentNoUpdate() {
        grant(approver);
        Claim c = officeClaim(30L, claimant, "COMPLETED", 3L);
        stubClaim(c);
        Handover done = new Handover(90L, "COMPLETED", "Intact", "Approver 2", "Claimant 5",
                OffsetDateTime.now(), OffsetDateTime.now(), null, 1L, List.of());
        when(jdbc.query(contains("from office_handover h"), any(RowMapper.class), eq(30L)))
                .thenReturn(List.of(done));

        Workflow w = service.complete(30L, new Confirm(1L), ud(approver));

        assertThat(w.handover().status()).isEqualTo("COMPLETED");
        verify(jdbc, never()).update(contains("update office_handover"), anyLong());
    }

    @Test
    void utc051_complete_noHandover_throwsBadRequest() {
        grant(approver);
        Claim c = officeClaim(30L, claimant, "APPROVED", 3L);
        stubClaim(c);

        AppException ex = catchThrowableOfType(() -> service.complete(30L, new Confirm(1L), ud(approver)), AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ex.getMessage()).isEqualTo("Chưa có biên bản giao đồ.");
    }

    @Test
    void utc052_complete_versionMismatch_throwsBadRequest() {
        grant(approver);
        Claim c = officeClaim(30L, claimant, "APPROVED", 3L);
        stubClaim(c);
        Handover waiting = new Handover(90L, "WAITING_CONFIRMATION", "Intact", "Approver 2", "Claimant 5",
                OffsetDateTime.now(), null, null, 2L, List.of());
        when(jdbc.query(contains("from office_handover h"), any(RowMapper.class), eq(30L)))
                .thenReturn(List.of(waiting));

        AppException ex = catchThrowableOfType(() -> service.complete(30L, new Confirm(1L), ud(approver)), AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ex.getMessage()).contains("Biên bản đã thay đổi hoặc đang chờ xử lý phản ánh");
    }

    @Test
    void utc053_complete_approverIsClaimant_throwsBadRequest() {
        grant(approver);
        Claim c = officeClaim(30L, approver, "APPROVED", 3L);
        stubClaim(c);

        AppException ex = catchThrowableOfType(() -> service.complete(30L, new Confirm(1L), ud(approver)), AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ex.getMessage()).isEqualTo("Nhờ nhân viên khác xử lý yêu cầu của bạn.");
    }
}
