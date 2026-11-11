package com.example.base.service.office;

import com.example.base.dto.office.InvitationDtos.*;
import com.example.base.dto.office.OfficeWorkflowDtos.AppointmentResponse;
import com.example.base.entity.*;
import com.example.base.exception.AppException;
import com.example.base.exception.ErrorCode;
import com.example.base.repository.*;
import com.example.base.security.CustomUserDetails;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageRequest;
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
class OfficeInvitationServiceTest {

    @Mock ClaimRepository claims;
    @Mock PostRepository posts;
    @Mock AppointmentRepository appointments;
    @Mock ClaimActivityRepository activities;
    @Mock AppointmentEmailService email;
    @Mock PlatformTransactionManager transactions;
    @Mock EntityManager em;
    @Mock OfficeAccess access;

    @InjectMocks
    OfficeInvitationService service;

    Account approver;
    Account claimant;
    Account otherUser;
    Category category;
    StorageLocation location;
    Post post;
    ItemReceipt receipt;

    @BeforeEach
    void setUp() {
        approver = account(2L, AccountRole.APPROVER, "Approver 2", "approver2@example.com");
        claimant = account(5L, AccountRole.USER, "Claimant 5", "a@x.com");
        otherUser = account(6L, AccountRole.USER, "Other 6", "other6@example.com");

        category = new Category();
        ReflectionTestUtils.setField(category, "categoryId", 10L);
        category.setName("Electronics");

        location = StorageLocation.create("A-01", "Shelf A1", null);
        ReflectionTestUtils.setField(location, "id", 1L);

        post = Post.receivedByOffice(approver, category, "Black Wallet", "Leather wallet", "Hall A",
                OffsetDateTime.now().minusDays(1));
        ReflectionTestUtils.setField(post, "id", 10L);

        receipt = ItemReceipt.walkInDraft("VP-1", approver);
        ReflectionTestUtils.setField(receipt, "id", 20L);
        receipt.edit(category, "Black Wallet", "Leather wallet", "Good", "Hall A",
                OffsetDateTime.now().minusDays(1), null, null);
        receipt.assignLocation(location);
        receipt.receiveAndPublish(approver, 10L);

        when(transactions.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
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

    private Claim officeClaim(long id, Account ownerClaimant, String status, String contactEmail) {
        Claim c = Claim.office(post, ownerClaimant, approver, "ONLINE",
                ownerClaimant != null ? ownerClaimant.getFullName() : "Guest",
                contactEmail, "0900000000", "My wallet", null, receipt);
        ReflectionTestUtils.setField(c, "id", id);
        c.review(status);
        return c;
    }

    private Appointment scheduleFor(Claim c, long apptId, OffsetDateTime scheduledAt) {
        Appointment a = Appointment.schedule(c, approver, scheduledAt, "Room 101", "Bring ID");
        ReflectionTestUtils.setField(a, "id", apptId);
        return a;
    }

    private void stubClaimLock(Claim c) {
        long cid = c.getId();
        long pid = c.getPost().getId();
        when(claims.findPostId(cid)).thenReturn(Optional.of(pid));
        when(posts.findForClaimUpdate(pid)).thenReturn(Optional.of(c.getPost()));
        when(claims.findForUpdate(cid)).thenReturn(Optional.of(c));
    }

    @Test
    void utc001_send_validApprovedClaim_sendsEmailAndSavesTokenHash() {
        Claim c = officeClaim(30L, claimant, "APPROVED", "claimant@x.com");
        Appointment a = scheduleFor(c, 8L, OffsetDateTime.now().plusDays(1));
        stubClaimLock(c);
        when(appointments.findFirstByClaim_IdAndStatusOrderByIdDesc(30L, "SCHEDULED")).thenReturn(Optional.of(a));
        when(email.send(any(), anyString(), any())).thenReturn(true);

        boolean sent = service.send(30L);

        assertThat(sent).isTrue();
        assertThat(a.getResponseStatus()).isEqualTo("PENDING");
        assertThat(a.getResponseTokenHash()).hasSize(64);
        assertThat(a.getEmailSentAt()).isNotNull();
        verify(appointments).flush();
    }

    @Test
    void utc002_send_emailReturnsFalse_keepsOldTokenAndReturnsFalse() {
        Claim c = officeClaim(30L, claimant, "APPROVED", "claimant@x.com");
        Appointment a = scheduleFor(c, 8L, OffsetDateTime.now().plusDays(1));
        a.invitationSent("o".repeat(64), OffsetDateTime.now().minusHours(1));
        String oldHash = a.getResponseTokenHash();
        stubClaimLock(c);
        when(appointments.findFirstByClaim_IdAndStatusOrderByIdDesc(30L, "SCHEDULED")).thenReturn(Optional.of(a));
        when(email.send(any(), anyString(), any())).thenReturn(false);

        boolean sent = service.send(30L);

        assertThat(sent).isFalse();
        assertThat(a.getResponseTokenHash()).isEqualTo(oldHash);
        verify(appointments, never()).flush();
    }

    @Test
    void utc003_send_claimPending_throwsBadRequest() {
        Claim c = officeClaim(30L, claimant, "PENDING", "claimant@x.com");
        Appointment a = scheduleFor(c, 8L, OffsetDateTime.now().plusDays(1));
        stubClaimLock(c);
        when(appointments.findFirstByClaim_IdAndStatusOrderByIdDesc(30L, "SCHEDULED")).thenReturn(Optional.of(a));

        AppException ex = catchThrowableOfType(() -> service.send(30L), AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ex.getMessage()).isEqualTo("Yêu cầu chưa được duyệt.");
    }

    @Test
    void utc004_send_alreadyConfirmed_throwsBadRequest() {
        Claim c = officeClaim(30L, claimant, "APPROVED", "claimant@x.com");
        Appointment a = scheduleFor(c, 8L, OffsetDateTime.now().plusDays(1));
        a.confirmInvitation(OffsetDateTime.now());
        stubClaimLock(c);
        when(appointments.findFirstByClaim_IdAndStatusOrderByIdDesc(30L, "SCHEDULED")).thenReturn(Optional.of(a));

        AppException ex = catchThrowableOfType(() -> service.send(30L), AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ex.getMessage()).isEqualTo("Người nhận đã đồng ý lịch hẹn, không cần gửi lại lời mời.");
    }

    @Test
    void utc005_send_deadlinePassed_rejectsClaimReopensPostAndRecordsExpireActivity() {
        post.reserveForClaim();
        Claim c = officeClaim(30L, claimant, "APPROVED", "claimant@x.com");
        Appointment a = scheduleFor(c, 8L, OffsetDateTime.now().plusDays(1));
        a.invitationSent("a".repeat(64), OffsetDateTime.now().minusHours(13));
        stubClaimLock(c);
        when(appointments.findFirstByClaim_IdAndStatusOrderByIdDesc(30L, "SCHEDULED")).thenReturn(Optional.of(a));

        boolean sent = service.send(30L);

        assertThat(sent).isFalse();
        assertThat(c.getStatus()).isEqualTo("REJECTED");
        assertThat(post.getStatus()).isEqualTo("OPEN");
        ArgumentCaptor<ClaimActivity> cap = ArgumentCaptor.forClass(ClaimActivity.class);
        verify(activities).save(cap.capture());
        assertThat(cap.getValue().getAction()).isEqualTo("INVITE_EXPIRE");
    }

    @Test
    void utc006_send_neverSentAndScheduledWithin12Hours_throwsBadRequest() {
        Claim c = officeClaim(30L, claimant, "APPROVED", "claimant@x.com");
        Appointment a = scheduleFor(c, 8L, OffsetDateTime.now().plusHours(12));
        stubClaimLock(c);
        when(appointments.findFirstByClaim_IdAndStatusOrderByIdDesc(30L, "SCHEDULED")).thenReturn(Optional.of(a));

        AppException ex = catchThrowableOfType(() -> service.send(30L), AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ex.getMessage()).contains("Giờ hẹn quá gần để dành đủ 12 giờ phản hồi");
    }

    @Test
    void utc007_send_noScheduledAppointment_throwsBadRequest() {
        Claim c = officeClaim(30L, claimant, "APPROVED", "claimant@x.com");
        stubClaimLock(c);
        when(appointments.findFirstByClaim_IdAndStatusOrderByIdDesc(30L, "SCHEDULED")).thenReturn(Optional.empty());

        AppException ex = catchThrowableOfType(() -> service.send(30L), AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ex.getMessage()).isEqualTo("Lịch hẹn không còn hiệu lực.");
    }

    @Test
    void utc008_send_p2pClaim_throwsBadRequest() {
        Claim p2p = Claim.submit(post, claimant, "Mine", null);
        ReflectionTestUtils.setField(p2p, "id", 30L);
        stubClaimLock(p2p);

        AppException ex = catchThrowableOfType(() -> service.send(30L), AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void utc009_send_nullClaimantEmail_fallsBackToClaimantAccountEmail() {
        Claim c = officeClaim(30L, claimant, "APPROVED", null);
        Appointment a = scheduleFor(c, 8L, OffsetDateTime.now().plusDays(1));
        stubClaimLock(c);
        when(appointments.findFirstByClaim_IdAndStatusOrderByIdDesc(30L, "SCHEDULED")).thenReturn(Optional.of(a));
        when(email.send(any(), anyString(), any())).thenReturn(true);

        boolean sent = service.send(30L);

        assertThat(sent).isTrue();
        ArgumentCaptor<AppointmentEmailService.Mail> mailCap = ArgumentCaptor.forClass(AppointmentEmailService.Mail.class);
        verify(email).send(mailCap.capture(), anyString(), any());
        assertThat(mailCap.getValue().recipient()).isEqualTo("a@x.com");
    }

    @Test
    void utc010_preview_validToken_returnsPendingView() {
        String token = InvitationTokens.create();
        String hash = InvitationTokens.hash(token);
        Claim c = officeClaim(30L, claimant, "APPROVED", "a@x.com");
        Appointment a = scheduleFor(c, 8L, OffsetDateTime.now().plusDays(1));
        a.invitationSent(hash, OffsetDateTime.now().minusHours(1));
        when(appointments.findByResponseTokenHash(hash)).thenReturn(Optional.of(a));

        View view = service.preview(token);

        assertThat(view.status()).isEqualTo("PENDING");
        assertThat(view.title()).isEqualTo("Black Wallet");
    }

    @Test
    void utc011_preview_unknownToken_throwsBadRequest() {
        String token = InvitationTokens.create();
        when(appointments.findByResponseTokenHash(anyString())).thenReturn(Optional.empty());

        AppException ex = catchThrowableOfType(() -> service.preview(token), AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ex.getMessage()).contains("Liên kết không hợp lệ hoặc đã được thay thế.");
    }

    @Test
    void utc012_respond_confirmViaEmail_confirmsAndLogsActivity() {
        String token = InvitationTokens.create();
        String hash = InvitationTokens.hash(token);
        Claim c = officeClaim(30L, claimant, "APPROVED", "a@x.com");
        Appointment a = scheduleFor(c, 8L, OffsetDateTime.now().plusDays(1));
        a.invitationSent(hash, OffsetDateTime.now().minusHours(1));
        stubClaimLock(c);
        when(appointments.findByResponseTokenHash(hash)).thenReturn(Optional.of(a));

        View view = service.respond(new Response(token, "CONFIRM", null));

        assertThat(view.status()).isEqualTo("CONFIRMED");
        assertThat(a.getResponseStatus()).isEqualTo("CONFIRMED");
        ArgumentCaptor<ClaimActivity> cap = ArgumentCaptor.forClass(ClaimActivity.class);
        verify(activities).save(cap.capture());
        assertThat(cap.getValue().getAction()).isEqualTo("INVITE_CONFIRM");
        assertThat(cap.getValue().getReason()).endsWith("qua email.");
    }

    @Test
    void utc013_respond_declineViaEmail_rejectsClaimReopensPostAndLogsActivity() {
        post.reserveForClaim();
        String token = InvitationTokens.create();
        String hash = InvitationTokens.hash(token);
        Claim c = officeClaim(30L, claimant, "APPROVED", "a@x.com");
        Appointment a = scheduleFor(c, 8L, OffsetDateTime.now().plusDays(1));
        a.invitationSent(hash, OffsetDateTime.now().minusHours(1));
        stubClaimLock(c);
        when(appointments.findByResponseTokenHash(hash)).thenReturn(Optional.of(a));

        View view = service.respond(new Response(token, "DECLINE", null));

        assertThat(view.status()).isEqualTo("DECLINED");
        assertThat(c.getStatus()).isEqualTo("REJECTED");
        assertThat(post.getStatus()).isEqualTo("OPEN");
        ArgumentCaptor<ClaimActivity> cap = ArgumentCaptor.forClass(ClaimActivity.class);
        verify(activities).save(cap.capture());
        assertThat(cap.getValue().getAction()).isEqualTo("INVITE_DECLINE");
    }

    @Test
    void utc014_respond_tokenRotatedAfterLookup_throwsBadRequest() {
        String token = InvitationTokens.create();
        String hash = InvitationTokens.hash(token);
        Claim c = officeClaim(30L, claimant, "APPROVED", "a@x.com");
        Appointment a = scheduleFor(c, 8L, OffsetDateTime.now().plusDays(1));
        a.invitationSent(hash, OffsetDateTime.now().minusHours(1));
        stubClaimLock(c);
        when(appointments.findByResponseTokenHash(hash)).thenReturn(Optional.of(a));
        doAnswer(inv -> {
            ReflectionTestUtils.setField(a, "responseTokenHash", "b".repeat(64));
            return null;
        }).when(em).refresh(a, LockModeType.PESSIMISTIC_WRITE);

        AppException ex = catchThrowableOfType(() -> service.respond(new Response(token, "CONFIRM", null)), AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void utc015_respond_invalidDecision_throwsBadRequest() {
        String token = InvitationTokens.create();
        String hash = InvitationTokens.hash(token);
        Claim c = officeClaim(30L, claimant, "APPROVED", "a@x.com");
        Appointment a = scheduleFor(c, 8L, OffsetDateTime.now().plusDays(1));
        a.invitationSent(hash, OffsetDateTime.now().minusHours(1));
        stubClaimLock(c);
        when(appointments.findByResponseTokenHash(hash)).thenReturn(Optional.of(a));

        AppException ex = catchThrowableOfType(() -> service.respond(new Response(token, "OK", null)), AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ex.getMessage()).isEqualTo("Lựa chọn không hợp lệ.");
    }

    @Test
    void utc016_respondOnSite_claimantConfirm_confirmsWithOnSiteMessage() {
        when(access.require(any())).thenReturn(claimant);
        Claim c = officeClaim(30L, claimant, "APPROVED", "a@x.com");
        Appointment a = scheduleFor(c, 8L, OffsetDateTime.now().plusDays(1));
        a.invitationSent("h".repeat(64), OffsetDateTime.now().minusHours(1));
        stubClaimLock(c);
        when(appointments.findById(8L)).thenReturn(Optional.of(a));

        View view = service.respondOnSite(30L, new AppointmentResponse(8L, "CONFIRM", null), ud(claimant));

        assertThat(view.status()).isEqualTo("CONFIRMED");
        ArgumentCaptor<ClaimActivity> cap = ArgumentCaptor.forClass(ClaimActivity.class);
        verify(activities).save(cap.capture());
        assertThat(cap.getValue().getReason()).endsWith("trên hệ thống.");
    }

    @Test
    void utc017_respondOnSite_appointmentBelongsToOtherClaim_throwsBadRequest() {
        when(access.require(any())).thenReturn(claimant);
        Claim c30 = officeClaim(30L, claimant, "APPROVED", "a@x.com");
        Claim c31 = officeClaim(31L, claimant, "APPROVED", "a@x.com");
        Appointment a8 = scheduleFor(c31, 8L, OffsetDateTime.now().plusDays(1));
        stubClaimLock(c30);
        when(appointments.findById(8L)).thenReturn(Optional.of(a8));

        AppException ex = catchThrowableOfType(
                () -> service.respondOnSite(30L, new AppointmentResponse(8L, "CONFIRM", null), ud(claimant)),
                AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ex.getMessage()).isEqualTo("Lịch hẹn không thuộc yêu cầu này.");
    }

    @Test
    void utc018_respondOnSite_notClaimant_throwsBadRequest() {
        when(access.require(any())).thenReturn(otherUser);
        Claim c = officeClaim(30L, claimant, "APPROVED", "a@x.com");
        stubClaimLock(c);

        AppException ex = catchThrowableOfType(
                () -> service.respondOnSite(30L, new AppointmentResponse(8L, "CONFIRM", null), ud(otherUser)),
                AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ex.getMessage()).isEqualTo("Chỉ người yêu cầu nhận đồ được phản hồi lịch hẹn này.");
    }

    @Test
    void utc019_respondOnSite_appointmentCancelledByReschedule_throwsBadRequest() {
        when(access.require(any())).thenReturn(claimant);
        Claim c = officeClaim(30L, claimant, "APPROVED", "a@x.com");
        Appointment a = scheduleFor(c, 8L, OffsetDateTime.now().plusDays(1));
        a.invitationSent("h".repeat(64), OffsetDateTime.now().minusHours(1));
        a.cancelByOffice("Rescheduled");
        stubClaimLock(c);
        when(appointments.findById(8L)).thenReturn(Optional.of(a));

        AppException ex = catchThrowableOfType(
                () -> service.respondOnSite(30L, new AppointmentResponse(8L, "CONFIRM", null), ud(claimant)),
                AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ex.getMessage()).isEqualTo("Lịch hẹn đã thay đổi. Hãy tải lại để xem lịch mới.");
    }

    @Test
    void utc020_expireDue_twoExpiredClaims_rejectsBothWithExpireActivity() {
        Claim c1 = officeClaim(31L, claimant, "APPROVED", "a@x.com");
        Appointment a1 = scheduleFor(c1, 81L, OffsetDateTime.now().plusDays(1));
        a1.invitationSent("1".repeat(64), OffsetDateTime.now().minusHours(13));

        Claim c2 = officeClaim(32L, otherUser, "APPROVED", "b@x.com");
        Appointment a2 = scheduleFor(c2, 82L, OffsetDateTime.now().plusDays(1));
        a2.invitationSent("2".repeat(64), OffsetDateTime.now().minusHours(13));

        when(appointments.findExpiredOfficeClaims(any(OffsetDateTime.class), eq(PageRequest.of(0, 100))))
                .thenReturn(List.of(31L, 32L));
        stubClaimLock(c1);
        stubClaimLock(c2);
        when(appointments.findFirstByClaim_IdAndStatusOrderByIdDesc(31L, "SCHEDULED")).thenReturn(Optional.of(a1));
        when(appointments.findFirstByClaim_IdAndStatusOrderByIdDesc(32L, "SCHEDULED")).thenReturn(Optional.of(a2));

        service.expireDue();

        assertThat(c1.getStatus()).isEqualTo("REJECTED");
        assertThat(c2.getStatus()).isEqualTo("REJECTED");
        verify(activities, times(2)).save(argThat(act -> "INVITE_EXPIRE".equals(act.getAction())));
    }

    @Test
    void utc021_expireDue_firstClaimLockThrows_stillProcessesSecond() {
        Claim c2 = officeClaim(32L, otherUser, "APPROVED", "b@x.com");
        Appointment a2 = scheduleFor(c2, 82L, OffsetDateTime.now().plusDays(1));
        a2.invitationSent("2".repeat(64), OffsetDateTime.now().minusHours(13));

        when(appointments.findExpiredOfficeClaims(any(OffsetDateTime.class), eq(PageRequest.of(0, 100))))
                .thenReturn(List.of(31L, 32L));
        when(claims.findPostId(31L)).thenThrow(new RuntimeException("Lock timeout"));
        stubClaimLock(c2);
        when(appointments.findFirstByClaim_IdAndStatusOrderByIdDesc(32L, "SCHEDULED")).thenReturn(Optional.of(a2));

        assertThatCode(() -> service.expireDue()).doesNotThrowAnyException();
        assertThat(c2.getStatus()).isEqualTo("REJECTED");
    }

    @Test
    void utc022_expireDue_claimNoLongerApproved_skipsWithoutRejecting() {
        Claim c = officeClaim(31L, claimant, "REJECTED", "a@x.com");
        Appointment a = scheduleFor(c, 81L, OffsetDateTime.now().plusDays(1));
        a.invitationSent("1".repeat(64), OffsetDateTime.now().minusHours(13));

        when(appointments.findExpiredOfficeClaims(any(OffsetDateTime.class), eq(PageRequest.of(0, 100))))
                .thenReturn(List.of(31L));
        stubClaimLock(c);
        when(appointments.findFirstByClaim_IdAndStatusOrderByIdDesc(31L, "SCHEDULED")).thenReturn(Optional.of(a));

        service.expireDue();

        verifyNoInteractions(activities);
        assertThat(a.getResponseStatus()).isEqualTo("PENDING");
    }
}
