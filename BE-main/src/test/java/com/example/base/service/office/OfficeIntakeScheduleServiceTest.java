package com.example.base.service.office;

import com.example.base.dto.office.InvitationDtos;
import com.example.base.dto.office.OfficeWorkflowDtos.AppointmentResponse;
import com.example.base.dto.office.OfficeWorkflowDtos.IntakeSchedule;
import com.example.base.entity.*;
import com.example.base.exception.AppException;
import com.example.base.exception.ErrorCode;
import com.example.base.repository.PostRepository;
import com.example.base.security.CustomUserDetails;
import com.example.base.service.notification.NotificationService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OfficeIntakeScheduleServiceTest {

    @Mock OfficeAccess access;
    @Mock JdbcTemplate jdbc;
    @Mock PostRepository posts;
    @Mock EntityManager em;
    @Mock PlatformTransactionManager transactions;
    @Mock AppointmentEmailService email;
    @Mock NotificationService notifications;

    @InjectMocks
    OfficeIntakeScheduleService service;

    Account approver;
    Account requester;
    Account otherUser;
    Category category;
    Post post;

    @BeforeEach
    void setUp() {
        approver = account(2L, AccountRole.APPROVER, "Approver 2", "approver@example.com");
        requester = account(5L, AccountRole.USER, "Requester 5", "req5@example.com");
        otherUser = account(6L, AccountRole.USER, "Other 6", "other6@example.com");

        category = new Category();
        ReflectionTestUtils.setField(category, "categoryId", 10L);
        category.setName("Keys");

        post = Post.found(requester, category, "Silver Key", "House key", "Hall A",
                OffsetDateTime.now().minusDays(1), "P2P", null);
        ReflectionTestUtils.setField(post, "id", 10L);

        when(transactions.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        when(posts.findForClaimUpdate(10L)).thenReturn(Optional.of(post));
    }

    private Account account(long id, AccountRole role, String name, String mail) {
        Account a = new Account();
        a.setId(id);
        a.setRole(role);
        a.setStatus(AccountStatus.ACTIVE);
        a.setFullName(name);
        a.setEmail(mail);
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

    private Map<String, Object> transferRow(long id, long postId, long requestedBy, String status, long version) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("transfer_id", id);
        m.put("post_id", postId);
        m.put("requested_by", requestedBy);
        m.put("status", status);
        m.put("version", version);
        return m;
    }

    private Map<String, Object> apptRow(long apptId, long transferId, String status, String emailStatus,
                                        String responseStatus, OffsetDateTime scheduledAt, OffsetDateTime deadline) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("appointment_id", apptId);
        m.put("transfer_id", transferId);
        m.put("status", status);
        m.put("email_status", emailStatus);
        m.put("response_status", responseStatus);
        m.put("scheduled_at", scheduledAt);
        m.put("location", "Room 101");
        m.put("note", "Bring ID");
        m.put("response_deadline", deadline);
        m.put("transfer_status", "PENDING");
        m.put("title", "Silver Key");
        return m;
    }

    private void stubTransferLock(long transferId, Map<String, Object> row) {
        when(jdbc.queryForList(contains("select post_id from office_transfer where transfer_id=?"), eq(Long.class), eq(transferId)))
                .thenReturn(List.of(((Number) row.get("post_id")).longValue()));
        when(jdbc.queryForMap(contains("select * from office_transfer where transfer_id=? for update"), eq(transferId)))
                .thenReturn(row);
    }

    @Test
    void utc040_schedule_firstTime_insertsAppointmentIncrementsVersionNotifiesAndSends() {
        grant(approver);
        OffsetDateTime scheduledAt = OffsetDateTime.now().plusDays(1);
        Map<String, Object> t = transferRow(4L, 10L, 5L, "PENDING", 1L);
        stubTransferLock(4L, t);
        when(jdbc.queryForList(contains("from office_intake_appointment where transfer_id=? order by appointment_id desc limit 1"), eq(4L)))
                .thenReturn(List.of());
        Map<String, Object> activeAppt = apptRow(8L, 4L, "SCHEDULED", "UNSENT", "UNSENT", scheduledAt, null);
        when(jdbc.queryForList(contains("status='SCHEDULED' order by appointment_id desc limit 1 for update"), eq(4L)))
                .thenReturn(List.of(activeAppt));
        when(jdbc.queryForMap(contains("join accounts a"), eq(4L)))
                .thenReturn(Map.of("email", "req5@example.com", "full_name", "Requester 5", "title", "Silver Key"));
        when(email.sendIntake(any(), anyString(), any())).thenReturn(true);

        String result = service.schedule(4L, new IntakeSchedule(1L, scheduledAt, " Room 101 ", "Note", null), ud(approver));

        assertThat(result).isEqualTo("SENT");
        verify(jdbc).update(contains("insert into office_intake_appointment"), eq(4L), eq(scheduledAt), eq("Room 101"), eq("Note"), eq(2L));
        verify(jdbc).update(contains("update office_transfer set version=version+1"), eq(4L));
        verify(notifications).notifyUser(eq(5L), eq("INTAKE_APPOINTMENT_SCHEDULED"), anyString(), contains("Room 101"), eq("/van-phong/chuyen-giao"), eq(false));
    }

    @Test
    void utc041_schedule_at12HoursBoundary_throwsBadRequest() {
        grant(approver);
        stubTransferLock(4L, transferRow(4L, 10L, 5L, "PENDING", 1L));

        AppException ex = catchThrowableOfType(
                () -> service.schedule(4L, new IntakeSchedule(1L, OffsetDateTime.now().plusHours(12), "Room 101", null, null), ud(approver)),
                AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ex.getMessage()).contains("Chọn lịch cách hiện tại hơn 12 giờ");
    }

    @Test
    void utc042_schedule_approverIsRequester_throwsBadRequest() {
        grant(approver);
        stubTransferLock(4L, transferRow(4L, 10L, 2L, "PENDING", 1L));

        AppException ex = catchThrowableOfType(
                () -> service.schedule(4L, new IntakeSchedule(1L, OffsetDateTime.now().plusDays(1), "Room 101", null, null), ud(approver)),
                AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ex.getMessage()).isEqualTo("Nhờ nhân viên khác sắp lịch tiếp nhận đồ của bạn.");
    }

    @Test
    void utc043_schedule_versionMismatch_throwsBadRequest() {
        grant(approver);
        stubTransferLock(4L, transferRow(4L, 10L, 5L, "PENDING", 2L));

        AppException ex = catchThrowableOfType(
                () -> service.schedule(4L, new IntakeSchedule(1L, OffsetDateTime.now().plusDays(1), "Room 101", null, null), ud(approver)),
                AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ex.getMessage()).isEqualTo("Yêu cầu đã thay đổi. Hãy tải lại.");
    }

    @Test
    void utc044_schedule_previousAwaitingReplyWithoutReason_throwsBadRequest() {
        grant(approver);
        stubTransferLock(4L, transferRow(4L, 10L, 5L, "PENDING", 1L));
        Map<String, Object> prev = apptRow(7L, 4L, "SCHEDULED", "SENT", "PENDING",
                OffsetDateTime.now().plusDays(1), OffsetDateTime.now().plusHours(5));
        when(jdbc.queryForList(contains("from office_intake_appointment where transfer_id=? order by appointment_id desc limit 1"), eq(4L)))
                .thenReturn(List.of(prev));

        AppException ex = catchThrowableOfType(
                () -> service.schedule(4L, new IntakeSchedule(1L, OffsetDateTime.now().plusDays(2), "Room 102", null, null), ud(approver)),
                AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ex.getMessage()).isEqualTo("Nhập lý do văn phòng đổi lịch.");
    }

    @Test
    void utc045_schedule_previousAwaitingReplyWithReason_cancelsOldAndInsertsNew() {
        grant(approver);
        OffsetDateTime nextTime = OffsetDateTime.now().plusDays(2);
        stubTransferLock(4L, transferRow(4L, 10L, 5L, "PENDING", 1L));
        Map<String, Object> prev = apptRow(7L, 4L, "SCHEDULED", "SENT", "PENDING",
                OffsetDateTime.now().plusDays(1), OffsetDateTime.now().plusHours(5));
        when(jdbc.queryForList(contains("from office_intake_appointment where transfer_id=? order by appointment_id desc limit 1"), eq(4L)))
                .thenReturn(List.of(prev));
        Map<String, Object> nextAppt = apptRow(8L, 4L, "SCHEDULED", "UNSENT", "UNSENT", nextTime, null);
        when(jdbc.queryForList(contains("status='SCHEDULED' order by appointment_id desc limit 1 for update"), eq(4L)))
                .thenReturn(List.of(nextAppt));
        when(jdbc.queryForMap(contains("join accounts a"), eq(4L)))
                .thenReturn(Map.of("email", "req5@example.com", "full_name", "Requester 5", "title", "Silver Key"));
        when(email.sendIntake(any(), anyString(), any())).thenReturn(true);

        String res = service.schedule(4L, new IntakeSchedule(1L, nextTime, "Room 102", null, "Room changed"), ud(approver));

        assertThat(res).isEqualTo("SENT");
        verify(jdbc).update(contains("update office_intake_appointment set status='CANCELLED'"), eq("Room changed"), eq(4L));
        verify(jdbc).update(contains("insert into office_intake_appointment"), eq(4L), eq(nextTime), eq("Room 102"), isNull(), eq(2L));
    }

    @Test
    void utc046_schedule_previousPendingDeadlinePassedNullReason_usesDefaultReason() {
        grant(approver);
        OffsetDateTime nextTime = OffsetDateTime.now().plusDays(2);
        stubTransferLock(4L, transferRow(4L, 10L, 5L, "PENDING", 1L));
        Map<String, Object> prev = apptRow(7L, 4L, "SCHEDULED", "SENT", "PENDING",
                OffsetDateTime.now().plusDays(1), OffsetDateTime.now().minusMinutes(5));
        when(jdbc.queryForList(contains("from office_intake_appointment where transfer_id=? order by appointment_id desc limit 1"), eq(4L)))
                .thenReturn(List.of(prev));
        Map<String, Object> nextAppt = apptRow(8L, 4L, "SCHEDULED", "UNSENT", "UNSENT", nextTime, null);
        when(jdbc.queryForList(contains("status='SCHEDULED' order by appointment_id desc limit 1 for update"), eq(4L)))
                .thenReturn(List.of(nextAppt));
        when(jdbc.queryForMap(contains("join accounts a"), eq(4L)))
                .thenReturn(Map.of("email", "req5@example.com", "full_name", "Requester 5", "title", "Silver Key"));
        when(email.sendIntake(any(), anyString(), any())).thenReturn(true);

        String res = service.schedule(4L, new IntakeSchedule(1L, nextTime, "Room 102", null, null), ud(approver));

        assertThat(res).isEqualTo("SENT");
        verify(jdbc).update(contains("update office_intake_appointment set status='CANCELLED'"),
                contains("Sắp xếp lịch mới"), eq(4L));
    }

    @Test
    void utc047_schedule_postDeletedOrClosed_throwsBadRequest() {
        grant(approver);
        post.markReturnedFromOffice();
        when(jdbc.queryForList(contains("select post_id from office_transfer where transfer_id=?"), eq(Long.class), eq(4L)))
                .thenReturn(List.of(10L));

        AppException ex = catchThrowableOfType(
                () -> service.schedule(4L, new IntakeSchedule(1L, OffsetDateTime.now().plusDays(1), "Room 101", null, null), ud(approver)),
                AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ex.getMessage()).contains("Đồ vật không còn có thể tiếp nhận.");
    }

    @Test
    void utc048_schedule_sendThrows_returnsFailedWithoutException() {
        grant(approver);
        OffsetDateTime scheduledAt = OffsetDateTime.now().plusDays(1);
        stubTransferLock(4L, transferRow(4L, 10L, 5L, "PENDING", 1L));
        when(jdbc.queryForList(contains("from office_intake_appointment where transfer_id=? order by appointment_id desc limit 1"), eq(4L)))
                .thenReturn(List.of());
        Map<String, Object> activeAppt = apptRow(8L, 4L, "SCHEDULED", "UNSENT", "UNSENT", scheduledAt, null);
        when(jdbc.queryForList(contains("status='SCHEDULED' order by appointment_id desc limit 1 for update"), eq(4L)))
                .thenReturn(List.of(activeAppt));
        when(jdbc.queryForMap(contains("join accounts a"), eq(4L)))
                .thenReturn(Map.of("email", "req5@example.com", "full_name", "Requester 5", "title", "Silver Key"));
        when(email.sendIntake(any(), anyString(), any())).thenThrow(new RuntimeException("SMTP failure"));

        String res = service.schedule(4L, new IntakeSchedule(1L, scheduledAt, "Room 101", null, null), ud(approver));

        assertThat(res).isEqualTo("FAILED");
    }

    @Test
    void utc049_send_validUnsent_updatesAppointmentAndReturnsSent() {
        grant(approver);
        OffsetDateTime scheduledAt = OffsetDateTime.now().plusDays(1);
        stubTransferLock(4L, transferRow(4L, 10L, 5L, "PENDING", 1L));
        Map<String, Object> activeAppt = apptRow(8L, 4L, "SCHEDULED", "UNSENT", "UNSENT", scheduledAt, null);
        when(jdbc.queryForList(contains("status='SCHEDULED' order by appointment_id desc limit 1 for update"), eq(4L)))
                .thenReturn(List.of(activeAppt));
        when(jdbc.queryForMap(contains("join accounts a"), eq(4L)))
                .thenReturn(Map.of("email", "req5@example.com", "full_name", "Requester 5", "title", "Silver Key"));
        when(email.sendIntake(any(), anyString(), any())).thenReturn(true);

        String res = service.send(4L, ud(approver));

        assertThat(res).isEqualTo("SENT");
        verify(jdbc).update(contains("email_status='SENT',emailed_at=now()"), any(), any(), anyString(), eq(8L));
    }

    @Test
    void utc050_send_invalidEmail_marksFailedAndReturnsFailed() {
        grant(approver);
        OffsetDateTime scheduledAt = OffsetDateTime.now().plusDays(1);
        stubTransferLock(4L, transferRow(4L, 10L, 5L, "PENDING", 1L));
        Map<String, Object> activeAppt = apptRow(8L, 4L, "SCHEDULED", "UNSENT", "UNSENT", scheduledAt, null);
        when(jdbc.queryForList(contains("status='SCHEDULED' order by appointment_id desc limit 1 for update"), eq(4L)))
                .thenReturn(List.of(activeAppt));
        when(jdbc.queryForMap(contains("join accounts a"), eq(4L)))
                .thenReturn(Map.of("email", "bad", "full_name", "Requester 5", "title", "Silver Key"));

        String res = service.send(4L, ud(approver));

        assertThat(res).isEqualTo("FAILED");
        verify(jdbc).update(contains("set email_status='FAILED'"), eq(8L));
        verifyNoInteractions(email);
    }

    @Test
    void utc051_send_emailServiceReturnsFalse_marksFailed() {
        grant(approver);
        OffsetDateTime scheduledAt = OffsetDateTime.now().plusDays(1);
        stubTransferLock(4L, transferRow(4L, 10L, 5L, "PENDING", 1L));
        Map<String, Object> activeAppt = apptRow(8L, 4L, "SCHEDULED", "UNSENT", "UNSENT", scheduledAt, null);
        when(jdbc.queryForList(contains("status='SCHEDULED' order by appointment_id desc limit 1 for update"), eq(4L)))
                .thenReturn(List.of(activeAppt));
        when(jdbc.queryForMap(contains("join accounts a"), eq(4L)))
                .thenReturn(Map.of("email", "req5@example.com", "full_name", "Requester 5", "title", "Silver Key"));
        when(email.sendIntake(any(), anyString(), any())).thenReturn(false);

        String res = service.send(4L, ud(approver));

        assertThat(res).isEqualTo("FAILED");
        verify(jdbc).update(contains("set email_status='FAILED'"), eq(8L));
    }

    @Test
    void utc052_send_alreadyConfirmed_throwsBadRequest() {
        grant(approver);
        stubTransferLock(4L, transferRow(4L, 10L, 5L, "PENDING", 1L));
        Map<String, Object> activeAppt = apptRow(8L, 4L, "SCHEDULED", "SENT", "CONFIRMED",
                OffsetDateTime.now().plusDays(1), OffsetDateTime.now().plusHours(6));
        when(jdbc.queryForList(contains("status='SCHEDULED' order by appointment_id desc limit 1 for update"), eq(4L)))
                .thenReturn(List.of(activeAppt));

        AppException ex = catchThrowableOfType(() -> service.send(4L, ud(approver)), AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ex.getMessage()).isEqualTo("Người giao đã đồng ý lịch này.");
    }

    @Test
    void utc053_send_deadlinePassed_expiresAppointmentAndReturnsExpired() {
        grant(approver);
        stubTransferLock(4L, transferRow(4L, 10L, 5L, "PENDING", 1L));
        Map<String, Object> activeAppt = apptRow(8L, 4L, "SCHEDULED", "SENT", "PENDING",
                OffsetDateTime.now().plusDays(1), OffsetDateTime.now().minusMinutes(5));
        when(jdbc.queryForList(contains("status='SCHEDULED' order by appointment_id desc limit 1 for update"), eq(4L)))
                .thenReturn(List.of(activeAppt));

        String res = service.send(4L, ud(approver));

        assertThat(res).isEqualTo("EXPIRED");
        verify(jdbc).update(contains("response_status='EXPIRED',responded_at=now(),status='CANCELLED'"), eq(8L));
    }

    @Test
    void utc054_send_firstSendScheduledUnder12Hours_throwsBadRequest() {
        grant(approver);
        stubTransferLock(4L, transferRow(4L, 10L, 5L, "PENDING", 1L));
        Map<String, Object> activeAppt = apptRow(8L, 4L, "SCHEDULED", "UNSENT", "UNSENT",
                OffsetDateTime.now().plusHours(11), null);
        when(jdbc.queryForList(contains("status='SCHEDULED' order by appointment_id desc limit 1 for update"), eq(4L)))
                .thenReturn(List.of(activeAppt));

        AppException ex = catchThrowableOfType(() -> service.send(4L, ud(approver)), AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ex.getMessage()).isEqualTo("Lịch quá gần. Hãy đổi lịch trước khi gửi thư.");
    }

    @Test
    void utc055_send_noScheduledAppointment_throwsBadRequest() {
        grant(approver);
        stubTransferLock(4L, transferRow(4L, 10L, 5L, "PENDING", 1L));
        when(jdbc.queryForList(contains("status='SCHEDULED' order by appointment_id desc limit 1 for update"), eq(4L)))
                .thenReturn(List.of());

        AppException ex = catchThrowableOfType(() -> service.send(4L, ud(approver)), AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ex.getMessage()).isEqualTo("Cần hẹn lịch mới cho yêu cầu này.");
    }

    @Test
    void utc056_send_transferAlreadyReceived_throwsBadRequest() {
        grant(approver);
        stubTransferLock(4L, transferRow(4L, 10L, 5L, "RECEIVED", 1L));

        AppException ex = catchThrowableOfType(() -> service.send(4L, ud(approver)), AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ex.getMessage()).isEqualTo("Yêu cầu này đã kết thúc.");
    }

    @Test
    void utc057_preview_validPending_returnsPendingView() {
        String token = InvitationTokens.create();
        String hash = InvitationTokens.hash(token);
        Map<String, Object> row = apptRow(8L, 4L, "SCHEDULED", "SENT", "PENDING",
                OffsetDateTime.now().plusDays(1), OffsetDateTime.now().plusHours(6));
        when(jdbc.queryForList(contains("where response_token_hash=?"), eq(hash))).thenReturn(List.of(row));

        InvitationDtos.View view = service.preview(token);

        assertThat(view.status()).isEqualTo("PENDING");
        assertThat(view.title()).isEqualTo("Silver Key");
    }

    @Test
    void utc058_preview_deadlinePassed_returnsExpiredStatus() {
        String token = InvitationTokens.create();
        String hash = InvitationTokens.hash(token);
        Map<String, Object> row = apptRow(8L, 4L, "SCHEDULED", "SENT", "PENDING",
                OffsetDateTime.now().plusDays(1), OffsetDateTime.now().minusMinutes(5));
        when(jdbc.queryForList(contains("where response_token_hash=?"), eq(hash))).thenReturn(List.of(row));

        InvitationDtos.View view = service.preview(token);

        assertThat(view.status()).isEqualTo("EXPIRED");
    }

    @Test
    void utc059_preview_transferNotPending_returnsCancelledStatus() {
        String token = InvitationTokens.create();
        String hash = InvitationTokens.hash(token);
        Map<String, Object> row = apptRow(8L, 4L, "SCHEDULED", "SENT", "PENDING",
                OffsetDateTime.now().plusDays(1), OffsetDateTime.now().plusHours(6));
        row.put("transfer_status", "REJECTED");
        when(jdbc.queryForList(contains("where response_token_hash=?"), eq(hash))).thenReturn(List.of(row));

        InvitationDtos.View view = service.preview(token);

        assertThat(view.status()).isEqualTo("CANCELLED");
    }

    @Test
    void utc060_preview_unknownHash_throwsBadRequest() {
        String token = InvitationTokens.create();
        String hash = InvitationTokens.hash(token);
        when(jdbc.queryForList(contains("where response_token_hash=?"), eq(hash))).thenReturn(List.of());

        AppException ex = catchThrowableOfType(() -> service.preview(token), AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ex.getMessage()).isEqualTo("Liên kết đã hết hiệu lực. Hãy mở thư lịch hẹn mới nhất.");
    }

    @Test
    void utc061_respond_confirmBeforeDeadline_confirmsAppointmentAndIncrementsVersion() {
        String token = InvitationTokens.create();
        String hash = InvitationTokens.hash(token);
        Map<String, Object> pending = apptRow(8L, 4L, "SCHEDULED", "SENT", "PENDING",
                OffsetDateTime.now().plusDays(1), OffsetDateTime.now().plusHours(6));
        Map<String, Object> confirmed = apptRow(8L, 4L, "SCHEDULED", "SENT", "CONFIRMED",
                OffsetDateTime.now().plusDays(1), OffsetDateTime.now().plusHours(6));
        stubTransferLock(4L, transferRow(4L, 10L, 5L, "PENDING", 1L));
        when(jdbc.queryForList(contains("where response_token_hash=?"), eq(hash)))
                .thenReturn(List.of(pending), List.of(pending), List.of(confirmed));

        InvitationDtos.View view = service.respond(new InvitationDtos.Response(token, "CONFIRM", null));

        assertThat(view.status()).isEqualTo("CONFIRMED");
        verify(jdbc).update(contains("update office_intake_appointment set response_status=?,responded_at=now(),status=?,cancellation_reason=?"),
                eq("CONFIRMED"), eq("SCHEDULED"), isNull(), eq(8L));
        verify(jdbc).update(contains("update office_transfer set version=version+1"), eq(4L));
    }

    @Test
    void utc062_respond_declineWithReason_cancelsAppointmentWithReason() {
        String token = InvitationTokens.create();
        String hash = InvitationTokens.hash(token);
        Map<String, Object> pending = apptRow(8L, 4L, "SCHEDULED", "SENT", "PENDING",
                OffsetDateTime.now().plusDays(1), OffsetDateTime.now().plusHours(6));
        Map<String, Object> declined = apptRow(8L, 4L, "CANCELLED", "SENT", "DECLINED",
                OffsetDateTime.now().plusDays(1), OffsetDateTime.now().plusHours(6));
        stubTransferLock(4L, transferRow(4L, 10L, 5L, "PENDING", 1L));
        when(jdbc.queryForList(contains("where response_token_hash=?"), eq(hash)))
                .thenReturn(List.of(pending), List.of(pending), List.of(declined));

        InvitationDtos.View view = service.respond(new InvitationDtos.Response(token, "DECLINE", "Busy"));

        assertThat(view.status()).isEqualTo("DECLINED");
        verify(jdbc).update(contains("update office_intake_appointment set response_status=?,responded_at=now(),status=?,cancellation_reason=?"),
                eq("DECLINED"), eq("CANCELLED"), eq("Busy"), eq(8L));
    }

    @Test
    void utc063_respond_declineEmptyReason_throwsBadRequest() {
        String token = InvitationTokens.create();
        String hash = InvitationTokens.hash(token);
        Map<String, Object> pending = apptRow(8L, 4L, "SCHEDULED", "SENT", "PENDING",
                OffsetDateTime.now().plusDays(1), OffsetDateTime.now().plusHours(6));
        stubTransferLock(4L, transferRow(4L, 10L, 5L, "PENDING", 1L));
        when(jdbc.queryForList(contains("where response_token_hash=?"), eq(hash))).thenReturn(List.of(pending));

        AppException ex = catchThrowableOfType(
                () -> service.respond(new InvitationDtos.Response(token, "DECLINE", "")),
                AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ex.getMessage()).isEqualTo("Cho văn phòng biết lý do bạn cần đổi lịch.");
    }

    @Test
    void utc064_respond_invalidDecision_throwsBadRequest() {
        String token = InvitationTokens.create();
        String hash = InvitationTokens.hash(token);
        Map<String, Object> pending = apptRow(8L, 4L, "SCHEDULED", "SENT", "PENDING",
                OffsetDateTime.now().plusDays(1), OffsetDateTime.now().plusHours(6));
        stubTransferLock(4L, transferRow(4L, 10L, 5L, "PENDING", 1L));
        when(jdbc.queryForList(contains("where response_token_hash=?"), eq(hash))).thenReturn(List.of(pending));

        AppException ex = catchThrowableOfType(
                () -> service.respond(new InvitationDtos.Response(token, "MAYBE", null)),
                AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ex.getMessage()).isEqualTo("Lựa chọn không hợp lệ.");
    }

    @Test
    void utc065_respond_deadlinePassed_expiresAppointmentInsteadOfConfirming() {
        String token = InvitationTokens.create();
        String hash = InvitationTokens.hash(token);
        Map<String, Object> expiredPending = apptRow(8L, 4L, "SCHEDULED", "SENT", "PENDING",
                OffsetDateTime.now().plusDays(1), OffsetDateTime.now().minusMinutes(5));
        Map<String, Object> afterExpire = apptRow(8L, 4L, "CANCELLED", "SENT", "EXPIRED",
                OffsetDateTime.now().plusDays(1), OffsetDateTime.now().minusMinutes(5));
        stubTransferLock(4L, transferRow(4L, 10L, 5L, "PENDING", 1L));
        when(jdbc.queryForList(contains("where response_token_hash=?"), eq(hash)))
                .thenReturn(List.of(expiredPending), List.of(expiredPending), List.of(afterExpire));

        InvitationDtos.View view = service.respond(new InvitationDtos.Response(token, "CONFIRM", null));

        assertThat(view.status()).isEqualTo("EXPIRED");
        verify(jdbc).update(contains("response_status='EXPIRED'"), eq(8L));
    }

    @Test
    void utc066_respond_alreadyConfirmed_idempotentNoUpdate() {
        String token = InvitationTokens.create();
        String hash = InvitationTokens.hash(token);
        Map<String, Object> confirmed = apptRow(8L, 4L, "SCHEDULED", "SENT", "CONFIRMED",
                OffsetDateTime.now().plusDays(1), OffsetDateTime.now().plusHours(6));
        stubTransferLock(4L, transferRow(4L, 10L, 5L, "PENDING", 1L));
        when(jdbc.queryForList(contains("where response_token_hash=?"), eq(hash))).thenReturn(List.of(confirmed));

        InvitationDtos.View view = service.respond(new InvitationDtos.Response(token, "DECLINE", "Late"));

        assertThat(view.status()).isEqualTo("CONFIRMED");
        verify(jdbc, never()).update(contains("update office_intake_appointment"), any(), any(), any(), any());
    }

    @Test
    void utc067_respondOnSite_requesterMatchingLatestAppointment_confirms() {
        grant(requester);
        stubTransferLock(4L, transferRow(4L, 10L, 5L, "PENDING", 1L));
        Map<String, Object> pending = apptRow(8L, 4L, "SCHEDULED", "SENT", "PENDING",
                OffsetDateTime.now().plusDays(1), OffsetDateTime.now().plusHours(6));
        Map<String, Object> confirmed = apptRow(8L, 4L, "SCHEDULED", "SENT", "CONFIRMED",
                OffsetDateTime.now().plusDays(1), OffsetDateTime.now().plusHours(6));
        when(jdbc.queryForList(contains("order by a.appointment_id desc limit 1"), eq(4L)))
                .thenReturn(List.of(pending));
        when(jdbc.queryForMap(contains("where a.appointment_id=?"), eq(8L)))
                .thenReturn(confirmed);

        InvitationDtos.View view = service.respondOnSite(4L, new AppointmentResponse(8L, "CONFIRM", null), ud(requester));

        assertThat(view.status()).isEqualTo("CONFIRMED");
        verify(jdbc).update(contains("update office_intake_appointment set response_status=?"),
                eq("CONFIRMED"), eq("SCHEDULED"), isNull(), eq(8L));
    }

    @Test
    void utc068_respondOnSite_staleAppointmentId_throwsBadRequest() {
        grant(requester);
        stubTransferLock(4L, transferRow(4L, 10L, 5L, "PENDING", 1L));
        Map<String, Object> latest = apptRow(9L, 4L, "SCHEDULED", "SENT", "PENDING",
                OffsetDateTime.now().plusDays(1), OffsetDateTime.now().plusHours(6));
        when(jdbc.queryForList(contains("order by a.appointment_id desc limit 1"), eq(4L)))
                .thenReturn(List.of(latest));

        AppException ex = catchThrowableOfType(
                () -> service.respondOnSite(4L, new AppointmentResponse(8L, "CONFIRM", null), ud(requester)),
                AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ex.getMessage()).isEqualTo("Lịch hẹn đã thay đổi. Hãy tải lại để xem lịch mới.");
    }

    @Test
    void utc069_respondOnSite_otherUser_throwsBadRequest() {
        grant(otherUser);
        stubTransferLock(4L, transferRow(4L, 10L, 5L, "PENDING", 1L));

        AppException ex = catchThrowableOfType(
                () -> service.respondOnSite(4L, new AppointmentResponse(8L, "CONFIRM", null), ud(otherUser)),
                AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ex.getMessage()).isEqualTo("Chỉ người gửi yêu cầu được phản hồi lịch hẹn này.");
    }
}

