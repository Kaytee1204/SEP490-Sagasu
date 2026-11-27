package com.example.base.service.office;

import com.example.base.dto.office.OfficeDtos.*;
import com.example.base.entity.*;
import com.example.base.exception.AppException;
import com.example.base.exception.ErrorCode;
import com.example.base.repository.*;
import com.example.base.security.CustomUserDetails;
import com.example.base.service.claim.ClaimDailyLimit;
import com.example.base.service.claim.OfficeClaimService;
import com.example.base.service.claim.P2pClaimAppealService;
import com.example.base.service.notification.NotificationService;
import com.example.base.service.report.SubmissionRestrictions;
import com.example.base.storage.ImageStorage;
import com.example.base.storage.ImageValidator;
import jakarta.persistence.EntityManager;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * The service opens its own {@code new TransactionTemplate(transactions)}. A real template running on the mocked
 * transaction manager executes the callback inline, so every test below runs the real service logic.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OfficeClaimServiceTest {
    private static final Validator BEAN_VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @Mock ClaimDailyLimit dailyLimit;
    @Mock SubmissionRestrictions restrictions;
    @Mock ClaimRepository claims;
    @Mock OfficeClaimProgress progress;
    @Mock PostRepository posts;
    @Mock AccountRepository accounts;
    @Mock ItemReceiptRepository receipts;
    @Mock ClaimActivityRepository activities;
    @Mock AppointmentRepository appointments;
    @Mock OfficeAccess access;
    @Mock OfficeManagementService management;
    @Mock OfficeInvitationService invitations;
    @Mock ImageStorage storage;
    @Mock ImageValidator images;
    @Mock Validator validator;
    @Mock PlatformTransactionManager transactions;
    @Mock EntityManager em;
    @Mock NotificationService notifications;
    @Mock P2pClaimAppealService appeals;

    @InjectMocks
    OfficeClaimService service;

    private final Account user7 = makeAccount(7L, AccountRole.USER, AccountStatus.ACTIVE);
    private final Account counterApprover = makeAccount(1L, AccountRole.APPROVER, AccountStatus.ACTIVE);
    private final Account approver = makeAccount(2L, AccountRole.APPROVER, AccountStatus.ACTIVE);
    private final Account finder = makeAccount(99L, AccountRole.USER, AccountStatus.ACTIVE);

    @BeforeEach
    void setUp() {
        when(transactions.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        when(validator.validate(any())).thenAnswer(inv -> BEAN_VALIDATOR.validate(inv.getArgument(0)));
        when(images.validate(any())).thenReturn(List.of());
        when(claims.saveAndFlush(any(Claim.class))).thenAnswer(inv -> {
            Claim c = inv.getArgument(0);
            if (c.getId() == null) ReflectionTestUtils.setField(c, "id", 55L);
            return c;
        });
        when(appointments.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private static Account makeAccount(long id, AccountRole role, AccountStatus status) {
        Account a = new Account();
        a.setId(id);
        a.setRole(role);
        a.setStatus(status);
        a.setEmail("user" + id + "@example.com");
        a.setFullName("User " + id);
        a.setPasswordHash("hash");
        return a;
    }

    private Post makePost(long id, String custodyMode, String status, Account owner) {
        Post p = mock(Post.class, RETURNS_DEEP_STUBS);
        when(p.getId()).thenReturn(id);
        when(p.getPostType()).thenReturn("FOUND");
        when(p.getCustodyMode()).thenReturn(custodyMode);
        when(p.getStatus()).thenReturn(status);
        when(p.getModerationStatus()).thenReturn("VISIBLE");
        when(p.getDeletedAt()).thenReturn(null);
        when(p.getTitle()).thenReturn("Test Item " + id);
        when(p.getAccount()).thenReturn(owner);
        when(p.getCreatedBy()).thenReturn(owner);
        return p;
    }

    private ItemReceipt makeReceipt(String status) {
        ItemReceipt r = mock(ItemReceipt.class);
        when(r.getStatus()).thenReturn(status);
        when(r.getReviewStatus()).thenReturn("ACCEPTED");
        when(r.getReceivedAt()).thenReturn(OffsetDateTime.now().minusDays(1));
        return r;
    }

    /** Office post 10 that the submit flow loads both unlocked (pre-check) and locked (save). */
    private Post givenSubmitPost(String custodyMode, String status, Account owner, ItemReceipt receipt) {
        Post post = makePost(10L, custodyMode, status, owner);
        when(posts.findById(10L)).thenReturn(Optional.of(post));
        when(posts.findForClaimUpdate(10L)).thenReturn(Optional.of(post));
        when(receipts.findByPostId(10L)).thenReturn(Optional.ofNullable(receipt));
        return post;
    }

    private CustomUserDetails onlineUser() {
        CustomUserDetails ud = new CustomUserDetails(user7);
        when(access.require(ud)).thenReturn(user7);
        return ud;
    }

    private CustomUserDetails counterUser() {
        CustomUserDetails ud = new CustomUserDetails(counterApprover);
        when(access.require(ud, AccountRole.APPROVER)).thenReturn(counterApprover);
        return ud;
    }

    private static MultipartFile jpg(String name) {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn(name);
        return file;
    }

    private static ImageValidator.ValidatedImage image() {
        return new ImageValidator.ValidatedImage("JPEG".getBytes(), "image/jpeg", "jpg", 100, 100, "hash1");
    }

    private static void assertBad(Executable call, String message) {
        AppException ex = assertThrows(AppException.class, call);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ex.getMessage()).contains(message);
    }

    private void assertNothingSaved() {
        verify(storage, never()).put(anyString(), any(), anyString());
        verify(claims, never()).saveAndFlush(any());
    }

    /** Real office claim 30 on post 100, locked by review(). */
    private Claim givenReviewClaim(String status, Account claimant, long version) {
        Post post = makePost(100L, "OFFICE", "OPEN", finder);
        ItemReceipt receipt = makeReceipt("IN_STORAGE");
        Claim c = Claim.office(post, claimant, claimant == null ? approver : claimant,
                claimant == null ? "COUNTER" : "ONLINE", claimant == null ? "Walk-in" : claimant.getFullName(),
                claimant == null ? null : claimant.getEmail(), null, "Description", null, receipt);
        ReflectionTestUtils.setField(c, "id", 30L);
        ReflectionTestUtils.setField(c, "status", status);
        ReflectionTestUtils.setField(c, "version", version);
        when(claims.findPostId(30L)).thenReturn(Optional.of(100L));
        when(posts.findForClaimUpdate(100L)).thenReturn(Optional.of(post));
        when(claims.findForUpdate(30L)).thenReturn(Optional.of(c));
        when(receipts.findByPostId(100L)).thenReturn(Optional.of(receipt));
        return c;
    }

    private CustomUserDetails reviewer() {
        CustomUserDetails ud = new CustomUserDetails(approver);
        when(access.require(ud, AccountRole.APPROVER)).thenReturn(approver);
        return ud;
    }

    private static Decision approve(OffsetDateTime at, String location, String email) {
        return new Decision("APPROVED", "OK", 0L, at, location, email, null);
    }

    private void assertNoApprovalSaved() {
        verify(appointments, never()).save(any());
        verify(activities, never()).save(any());
    }

    // ─── submit ────────────────────────────────────────────────────────────────

    @Test
    void utc207_submit_userOnlineFlow_createsClaimNotifiesApprovers() {
        CustomUserDetails ud = onlineUser();
        givenSubmitPost("OFFICE", "OPEN", finder, makeReceipt("IN_STORAGE"));
        when(images.validate(any())).thenReturn(List.of(image()));

        Long id = service.submit(new Submission(10L, null, null, null, null, "My laptop", null),
                List.of(jpg("photo.jpg")), false, ud);

        assertThat(id).isEqualTo(55L);
        ArgumentCaptor<Claim> saved = ArgumentCaptor.forClass(Claim.class);
        verify(claims).saveAndFlush(saved.capture());
        Claim c = saved.getValue();
        assertThat(c.getClaimType()).isEqualTo("OFFICE");
        assertThat(c.getSubmissionChannel()).isEqualTo("ONLINE");
        assertThat(c.getClaimant()).isSameAs(user7);
        assertThat(c.getDescription()).isEqualTo("My laptop");
        assertThat(c.getEvidence()).hasSize(1);
        assertThat(c.getEvidence().get(0).getStorageKey()).matches("claims/office/[0-9a-f-]{36}\\.jpg");
        assertThat(c.getEvidence().get(0).getOriginalName()).isEqualTo("photo.jpg");
        verify(storage).put(eq(c.getEvidence().get(0).getStorageKey()), any(), eq("image/jpeg"));
        verify(activities).save(any(ClaimActivity.class));
        verify(notifications).notifyRoles(eq(List.of(AccountRole.APPROVER)), eq(7L), eq("OFFICE_CLAIM_SUBMITTED"),
                anyString(), anyString(), eq("/approver/office-claims/55"), eq(false));
    }

    @Test
    void utc208_submit_approverCounter_channelCounter() {
        CustomUserDetails ud = counterUser();
        when(accounts.findById(7L)).thenReturn(Optional.of(user7));
        givenSubmitPost("OFFICE", "OPEN", finder, makeReceipt("IN_STORAGE"));

        Long id = service.submit(new Submission(10L, 7L, null, null, null, "My laptop", null), List.of(), true, ud);

        assertThat(id).isEqualTo(55L);
        ArgumentCaptor<Claim> saved = ArgumentCaptor.forClass(Claim.class);
        verify(claims).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getSubmissionChannel()).isEqualTo("COUNTER");
        assertThat(saved.getValue().getClaimant()).isSameAs(user7);
        assertThat(saved.getValue().getCreatedBy()).isSameAs(counterApprover);
        assertThat(saved.getValue().getClaimantEmail()).isEqualTo("user7@example.com");
        verify(notifications).notifyUser(eq(7L), eq("OFFICE_CLAIM_SUBMITTED"), anyString(), anyString(),
                eq("/van-phong/claims/55"), eq(false));
        verify(notifications, never()).notifyRoles(any(), any(), anyString(), anyString(), anyString(), anyString(), anyBoolean());
    }

    @Test
    void utc209_submit_approverCounterClaimantIdNull_throwsBadRequest() {
        CustomUserDetails ud = counterUser();
        givenSubmitPost("OFFICE", "OPEN", finder, makeReceipt("IN_STORAGE"));

        assertBad(() -> service.submit(new Submission(10L, null, null, null, null, "My laptop", null), List.of(), true, ud),
                "Chọn tài khoản người yêu cầu nhận đồ từ kết quả tìm kiếm.");
        assertNothingSaved();
    }

    @Test
    void utc210_submit_approverCounterClaimantSuspended_throwsBadRequest() {
        CustomUserDetails ud = counterUser();
        when(accounts.findById(7L)).thenReturn(Optional.of(makeAccount(7L, AccountRole.USER, AccountStatus.SUSPENDED)));
        givenSubmitPost("OFFICE", "OPEN", finder, makeReceipt("IN_STORAGE"));

        assertBad(() -> service.submit(new Submission(10L, 7L, null, null, null, "My laptop", null), List.of(), true, ud),
                "Tài khoản người yêu cầu nhận đồ không còn hoạt động.");
        assertNothingSaved();
    }

    @Test
    void utc211_submit_claimantAccountNotFound_throwsBadRequest() {
        CustomUserDetails ud = counterUser();
        when(accounts.findById(999L)).thenReturn(Optional.empty());
        givenSubmitPost("OFFICE", "OPEN", finder, makeReceipt("IN_STORAGE"));

        assertBad(() -> service.submit(new Submission(10L, 999L, null, null, null, "My laptop", null), List.of(), true, ud),
                "Không tìm thấy tài khoản người yêu cầu nhận đồ.");
        assertNothingSaved();
    }

    @Test
    void utc212_submit_postNotFound_throwsBadRequest() {
        CustomUserDetails ud = onlineUser();
        when(posts.findById(999L)).thenReturn(Optional.empty());

        assertBad(() -> service.submit(new Submission(999L, null, null, null, null, "desc", null), List.of(), false, ud),
                "Không tìm thấy bài đăng.");
        assertNothingSaved();
    }

    @Test
    void utc213_submit_postCustodyP2P_throwsBadRequest() {
        CustomUserDetails ud = onlineUser();
        givenSubmitPost("P2P", "OPEN", finder, makeReceipt("IN_STORAGE"));

        assertBad(() -> service.submit(new Submission(10L, null, null, null, null, "desc", null), List.of(), false, ud),
                "Đồ vật không còn thuộc luồng xử lý tại văn phòng.");
        assertNothingSaved();
    }

    @Test
    void utc214_submit_noReceipt_throwsBadRequest() {
        CustomUserDetails ud = onlineUser();
        givenSubmitPost("OFFICE", "OPEN", finder, null);

        assertBad(() -> service.submit(new Submission(10L, null, null, null, null, "desc", null), List.of(), false, ud),
                "Chưa có hồ sơ tiếp nhận.");
        assertNothingSaved();
    }

    @Test
    void utc215_submit_receiptStatusReturned_throwsBadRequest() {
        CustomUserDetails ud = onlineUser();
        givenSubmitPost("OFFICE", "OPEN", finder, makeReceipt("RETURNED"));

        assertBad(() -> service.submit(new Submission(10L, null, null, null, null, "desc", null), List.of(), false, ud),
                "Văn phòng chưa tiếp nhận hoặc đã trao trả đồ vật.");
        assertNothingSaved();
    }

    @Test
    void utc216_submit_postReserved_throwsBadRequest() {
        CustomUserDetails ud = onlineUser();
        givenSubmitPost("OFFICE", "RESERVED", finder, makeReceipt("IN_STORAGE"));

        assertBad(() -> service.submit(new Submission(10L, null, null, null, null, "desc", null), List.of(), false, ud),
                "Bài đăng hiện không nhận thêm yêu cầu.");
        assertNothingSaved();
    }

    @Test
    void utc217_submit_claimantIsPostOwner_throwsClaimSelfNotAllowed() {
        CustomUserDetails ud = onlineUser();
        givenSubmitPost("OFFICE", "OPEN", user7, makeReceipt("IN_STORAGE"));

        AppException ex = assertThrows(AppException.class,
                () -> service.submit(new Submission(10L, null, null, null, null, "desc", null), List.of(), false, ud));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CLAIM_SELF_NOT_ALLOWED);
        assertNothingSaved();
    }

    @Test
    void utc218_submit_duplicateClaim_keysDeleted() {
        CustomUserDetails ud = onlineUser();
        givenSubmitPost("OFFICE", "OPEN", finder, makeReceipt("IN_STORAGE"));
        when(claims.existsByPost_IdAndClaimant_IdAndStatusIn(eq(10L), eq(7L), any())).thenReturn(true);
        when(images.validate(any())).thenReturn(List.of(image()));

        AppException ex = assertThrows(AppException.class, () -> service.submit(
                new Submission(10L, null, null, null, null, "desc", null), List.of(jpg("a.jpg")), false, ud));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CLAIM_DUPLICATE);
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(storage).put(key.capture(), any(), anyString());
        verify(storage).delete(key.getValue());
        verify(claims, never()).saveAndFlush(any());
    }

    @Test
    void utc219_submit_storagePutFails_keysCleanedUp() {
        CustomUserDetails ud = onlineUser();
        givenSubmitPost("OFFICE", "OPEN", finder, makeReceipt("IN_STORAGE"));
        when(images.validate(any())).thenReturn(List.of(image()));
        doThrow(new RuntimeException("S3 error")).when(storage).put(anyString(), any(), anyString());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.submit(
                new Submission(10L, null, null, null, null, "desc", null), List.of(jpg("photo.jpg")), false, ud));

        assertThat(ex.getMessage()).isEqualTo("S3 error");
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(storage).put(key.capture(), any(), anyString());
        verify(storage).delete(key.getValue());
        verify(claims, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void utc220_dailyLimitAppliesToOnlineAndCounterUsingClaimantAccount(boolean counter) {
        CustomUserDetails ud = counter ? counterUser() : onlineUser();
        when(accounts.findById(7L)).thenReturn(Optional.of(user7));
        givenSubmitPost("OFFICE", "OPEN", finder, makeReceipt("IN_STORAGE"));
        doThrow(new AppException(ErrorCode.CLAIM_DAILY_LIMIT)).when(dailyLimit).requireAvailable(7L);
        var req = new Submission(10L, counter ? 7L : null, null, null, null, "My item", null);

        var error = assertThrows(AppException.class, () -> service.submit(req, List.of(), counter, ud));

        assertEquals(ErrorCode.CLAIM_DAILY_LIMIT, error.getErrorCode());
        verify(restrictions).requireAvailableForSubmission(7L, SubmissionRestrictions.Action.CLAIM);
        verify(claims, never()).saveAndFlush(any());
    }

    // ─── review ────────────────────────────────────────────────────────────────

    @Test
    void utc221_review_approved_appointmentSaved() {
        CustomUserDetails ud = reviewer();
        Claim c = givenReviewClaim("PENDING", user7, 0L);
        when(invitations.send(30L)).thenReturn(true);
        OffsetDateTime at = OffsetDateTime.now().plusDays(1);

        ReviewResult result = service.review(30L, approve(at, " Room 101 ", null), ud);

        assertThat(result).isEqualTo(new ReviewResult(30L, "APPROVED", "SENT"));
        assertThat(c.getStatus()).isEqualTo("APPROVED");
        assertThat(c.getClaimantEmail()).isEqualTo("user7@example.com");
        ArgumentCaptor<Appointment> appt = ArgumentCaptor.forClass(Appointment.class);
        verify(appointments).save(appt.capture());
        assertThat(appt.getValue().getLocation()).isEqualTo("Room 101");
        assertThat(appt.getValue().getScheduledAt()).isEqualTo(at);
        verify(invitations).validatePickupSchedule(eq(at), any());
        verify(c.getPost()).reserveForClaim();
        ArgumentCaptor<ClaimActivity> activity = ArgumentCaptor.forClass(ClaimActivity.class);
        verify(activities).save(activity.capture());
        assertThat(activity.getValue().getAction()).isEqualTo("APPROVE");
        verify(notifications).notifyUser(eq(7L), eq("APPOINTMENT_SCHEDULED"), anyString(), contains("Room 101"),
                eq("/van-phong/claims/30"), eq(false));
    }

    @Test
    void utc222_review_scheduledAtTooSoon_throwsBadRequest() {
        CustomUserDetails ud = reviewer();
        givenReviewClaim("PENDING", user7, 0L);
        OfficePickupSchedule schedule = new OfficePickupSchedule("");
        doAnswer(inv -> {
            schedule.validate(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(invitations).validatePickupSchedule(any(), any());

        assertBad(() -> service.review(30L, approve(OffsetDateTime.now().plusHours(12), "Room 101", null), ud),
                "Giờ hẹn cần cách hiện tại hơn 12 giờ và 1 phút");
        assertNoApprovalSaved();
        verify(invitations, never()).send(anyLong());
    }

    @Test
    void utc223_review_scheduledAt12h2min_accepted() {
        CustomUserDetails ud = reviewer();
        givenReviewClaim("PENDING", user7, 0L);
        when(invitations.send(30L)).thenReturn(true);
        OffsetDateTime at = OffsetDateTime.now().plusHours(12).plusMinutes(2);

        ReviewResult result = service.review(30L, approve(at, "Room 101", null), ud);

        assertThat(result.status()).isEqualTo("APPROVED");
        verify(invitations).validatePickupSchedule(eq(at), any());
        verify(appointments).save(any(Appointment.class));
    }

    @Test
    void utc224_review_scheduledAtNull_approvesWithoutAppointment() {
        CustomUserDetails ud = reviewer();
        Claim c = givenReviewClaim("PENDING", user7, 0L);

        ReviewResult result = service.review(30L, approve(null, "Room 101", null), ud);

        assertThat(result).isEqualTo(new ReviewResult(30L, "APPROVED", "NOT_REQUIRED"));
        verify(c.getPost()).reserveForClaim();
        verify(invitations, never()).validatePickupSchedule(any(), any());
        verify(appointments, never()).save(any());
        verify(invitations, never()).send(anyLong());
        verify(notifications).notifyUser(eq(7L), eq("CLAIM_APPROVED"), anyString(), anyString(),
                eq("/van-phong/claims/30"), eq(false));
    }

    @Test
    void utc225_review_locationBlank_throwsBadRequest() {
        CustomUserDetails ud = reviewer();
        givenReviewClaim("PENDING", user7, 0L);

        assertBad(() -> service.review(30L, approve(OffsetDateTime.now().plusDays(1), "  ", null), ud),
                "Nhập địa điểm nhận đồ.");
        assertNoApprovalSaved();
    }

    @Test
    void utc226_review_noClaimantEmailAndEmptyParam_throwsBadRequest() {
        CustomUserDetails ud = reviewer();
        Account noEmail = makeAccount(7L, AccountRole.USER, AccountStatus.ACTIVE);
        noEmail.setEmail(null);
        givenReviewClaim("PENDING", noEmail, 0L);

        assertBad(() -> service.review(30L, approve(OffsetDateTime.now().plusDays(1), "Room 101", ""), ud),
                "Cần email hợp lệ của người nhận để gửi lịch hẹn.");
        assertNoApprovalSaved();
    }

    @Test
    void utc227_review_invalidEmailParam_throwsBadRequest() {
        CustomUserDetails ud = reviewer();
        givenReviewClaim("PENDING", user7, 0L);

        assertBad(() -> service.review(30L, approve(OffsetDateTime.now().plusDays(1), "Room 101", "not-an-email"), ud),
                "Cần email hợp lệ của người nhận để gửi lịch hẹn.");
        assertNoApprovalSaved();
    }

    @Test
    void utc228_review_rejected_notifiesClaimant() {
        CustomUserDetails ud = reviewer();
        Claim c = givenReviewClaim("PENDING", user7, 0L);

        ReviewResult result = service.review(30L,
                new Decision("REJECTED", "Evidence mismatch", 0L, null, null, null, null), ud);

        assertThat(result).isEqualTo(new ReviewResult(30L, "REJECTED", "NOT_REQUIRED"));
        verify(c.getPost(), never()).reserveForClaim();
        ArgumentCaptor<ClaimActivity> activity = ArgumentCaptor.forClass(ClaimActivity.class);
        verify(activities).save(activity.capture());
        assertThat(activity.getValue().getAction()).isEqualTo("REJECT");
        verify(notifications).notifyUser(eq(7L), eq("CLAIM_REJECTED"), anyString(), contains("Evidence mismatch"),
                eq("/van-phong/claims/30"), eq(true));
        verify(invitations, never()).send(anyLong());
    }

    @Test
    void utc229_review_needsInfoDecision_rejectedAsInvalid() {
        CustomUserDetails ud = new CustomUserDetails(approver);
        Decision req = new Decision("NEEDS_INFO", "Send receipt photo", 0L, null, null, null, null);

        assertBad(() -> service.review(30L, req, ud), "Quyết định xét duyệt không hợp lệ");
        verifyNoInteractions(activities);
    }

    @Test
    void utc230_review_claimAlreadyApproved_throwsBadRequest() {
        CustomUserDetails ud = reviewer();
        givenReviewClaim("APPROVED", user7, 0L);

        assertBad(() -> service.review(30L, new Decision("REJECTED", "X", 0L, null, null, null, null), ud),
                "Hồ sơ này đã được xử lý. Hãy tải lại.");
        assertNoApprovalSaved();
    }

    @Test
    void utc231_review_approverIsClaimant_throwsBadRequest() {
        CustomUserDetails ud = reviewer();
        givenReviewClaim("PENDING", approver, 0L);

        assertBad(() -> service.review(30L, approve(OffsetDateTime.now().plusDays(1), "Room 101", null), ud),
                "Không được tự xét duyệt hồ sơ nhận đồ của mình.");
        assertNoApprovalSaved();
    }

    @Test
    void utc232_review_anotherClaimAlreadyApproved_throwsBadRequest() {
        CustomUserDetails ud = reviewer();
        Claim c = givenReviewClaim("PENDING", user7, 0L);
        when(claims.existsByPost_IdAndStatusIn(100L, List.of("APPROVED"))).thenReturn(true);

        assertBad(() -> service.review(30L, approve(OffsetDateTime.now().plusDays(1), "Room 101", null), ud),
                "Đồ vật đã có yêu cầu được duyệt.");
        verify(c.getPost(), never()).reserveForClaim();
        assertNoApprovalSaved();
    }

    @Test
    void utc233_review_versionMismatch_throwsClaimStale() {
        CustomUserDetails ud = reviewer();
        givenReviewClaim("PENDING", user7, 1L);

        AppException ex = assertThrows(AppException.class,
                () -> service.review(30L, new Decision("REJECTED", "X", 0L, null, null, null, null), ud));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CLAIM_STALE);
        assertNoApprovalSaved();
    }

    @Test
    void utc234_review_invitationSendReturnsFalse_deliveryFailed() {
        CustomUserDetails ud = reviewer();
        Claim c = givenReviewClaim("PENDING", user7, 0L);
        when(invitations.send(30L)).thenReturn(false);

        ReviewResult result = service.review(30L, approve(OffsetDateTime.now().plusDays(1), "Room 101", null), ud);

        assertThat(result).isEqualTo(new ReviewResult(30L, "APPROVED", "FAILED"));
        assertThat(c.getStatus()).isEqualTo("APPROVED");
        verify(appointments).save(any(Appointment.class));
    }

    @Test
    void utc235_review_invitationSendThrows_deliveryFailedNoException() {
        CustomUserDetails ud = reviewer();
        Claim c = givenReviewClaim("PENDING", user7, 0L);
        when(invitations.send(30L)).thenThrow(new RuntimeException("SMTP down"));

        ReviewResult result = service.review(30L, approve(OffsetDateTime.now().plusDays(1), "Room 101", null), ud);

        assertThat(result).isEqualTo(new ReviewResult(30L, "APPROVED", "FAILED"));
        assertThat(c.getStatus()).isEqualTo("APPROVED");
    }

    @Test
    void utc236_review_counterClaimNullClaimant_noNotification() {
        CustomUserDetails ud = reviewer();
        Claim c = givenReviewClaim("PENDING", null, 0L);

        ReviewResult result = service.review(30L, new Decision("REJECTED", "X", 0L, null, null, null, null), ud);

        assertThat(result.status()).isEqualTo("REJECTED");
        assertThat(c.getStatus()).isEqualTo("REJECTED");
        verify(activities).save(any(ClaimActivity.class));
        verifyNoInteractions(notifications);
    }
}
