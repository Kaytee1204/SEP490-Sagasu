package com.example.base.service.claim;

import com.example.base.entity.Account;
import com.example.base.entity.Category;
import com.example.base.entity.Claim;
import com.example.base.entity.Post;
import com.example.base.entity.Report;
import com.example.base.exception.AppException;
import com.example.base.exception.ErrorCode;
import com.example.base.repository.ClaimRepository;
import com.example.base.repository.PostRepository;
import com.example.base.security.CustomUserDetails;
import com.example.base.service.chat.ChatAccessPolicy;
import com.example.base.service.chat.ChatConversationService;
import com.example.base.service.notification.NotificationService;
import com.example.base.service.report.ReportService;
import com.example.base.service.report.WorkflowImages;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class P2pClaimAppealServiceTest {
    @Mock private JdbcTemplate jdbc;
    @Mock private ClaimRepository claims;
    @Mock private PostRepository posts;
    @Mock private ChatAccessPolicy actors;
    @Mock private ChatConversationService chat;
    @Mock private NotificationService notifications;
    @Mock private ClaimRoutingPolicy routing;
    @Mock private PlatformTransactionManager transactions;
    @Mock private WorkflowImages images;
    @Mock private ReportService reports;
    @Mock private EntityManager entityManager;
    @InjectMocks private P2pClaimAppealService service;
    private Claim selected;
    private Claim appellant;

    @BeforeEach
    void createClaims() {
        Post post = Post.found(account(10L), new Category(), "Found wallet", "Wallet", "Library",
                OffsetDateTime.now().minusDays(1), "P2P", null);
        ReflectionTestUtils.setField(post, "id", 100L);
        selected = Claim.submit(post, account(20L), "It has my initials", null);
        ReflectionTestUtils.setField(selected, "id", 500L);
        selected.review("APPROVED");
        appellant = Claim.submit(post, account(30L), "It has a red inner pocket", null);
        ReflectionTestUtils.setField(appellant, "id", 501L);
    }

    @Test
    void approvalOpens24HourWindowAndNotifiesOtherClaimantsWithTheirOwnLink() {
        when(claims.findWaitingForReturn(100L)).thenReturn(List.of(appellant));
        OffsetDateTime before = OffsetDateTime.now().plusHours(24);

        OffsetDateTime deadline = service.open(selected);

        assertFalse(deadline.isBefore(before));
        assertFalse(deadline.isAfter(OffsetDateTime.now().plusHours(24)));
        verify(jdbc).update(startsWith("insert into p2p_claim_approval"), eq(500L), eq(deadline));
        verify(notifications).notifyUser(eq(30L), eq("CLAIM_APPEAL_WINDOW"),
                anyString(), anyString(), eq("/bai-dang/100/claims/501"), eq(false));
        verifyNoMoreInteractions(notifications);
    }

    @Test
    void approvalWithoutOtherClaimantsDoesNotStartWindow() {
        assertNull(service.open(selected));
        verifyNoInteractions(jdbc, notifications);
    }

    @Test
    void dismissedDisputeDoesNotCreateAnotherAppealWindowForThatClaim() {
        when(claims.findWaitingForReturn(100L)).thenReturn(List.of(appellant));
        when(claims.findLatestDisputeOutcomes(List.of(501L)))
                .thenReturn(java.util.Collections.singletonList(new Object[]{501L, "APPEAL_KEEP"}));

        assertNull(service.open(selected));
        verifyNoInteractions(jdbc, notifications);
    }

    @Test
    void statusDoesNotRevealAnotherClaimantsAppeal() {
        Account outsider = account(40L);
        CustomUserDetails user = new CustomUserDetails(outsider);
        when(actors.actor(user)).thenReturn(outsider);
        when(claims.findById(501L)).thenReturn(Optional.of(appellant));

        assertEquals(ErrorCode.CLAIM_NOT_FOUND,
                assertThrows(AppException.class, () -> service.status(501L, user)).getErrorCode());
        verifyNoInteractions(jdbc);
    }

    @Test
    void expiredWindowRejectsNewAppealBeforeUploadingEvidence() throws Exception {
        CustomUserDetails user = new CustomUserDetails(appellant.getClaimant());
        var request = new P2pClaimAppealService.Submit(3L, "Look at the inner pocket.", UUID.randomUUID());
        givenSubmission(user);
        when(routing.matches(appellant, Set.of("RESERVED"))).thenReturn(true);
        givenWindow(OffsetDateTime.now().minusHours(1), false, false);
        doReturn(List.of()).when(jdbc).query(contains("appellant_claim_id=?"), any(RowMapper.class), eq(3L), eq(501L));

        var failure = assertThrows(AppException.class, () -> service.submit(501L, request, List.of(), user));

        assertEquals(ErrorCode.CLAIM_STATE_INVALID, failure.getErrorCode());
        verify(images, never()).upload(anyString(), any());
    }

    @Test
    void retriedSubmissionReturnsExistingReportWithoutUploadingAgain() throws Exception {
        CustomUserDetails user = new CustomUserDetails(appellant.getClaimant());
        var request = new P2pClaimAppealService.Submit(3L, "Look at the inner pocket.", UUID.randomUUID());
        givenSubmission(user);
        ResultSet report = mock(ResultSet.class);
        when(report.getLong(1)).thenReturn(900L);
        when(report.getString(2)).thenReturn("IN_REVIEW");
        when(report.getObject(3, UUID.class)).thenReturn(request.clientRequestId());
        when(report.getString(4)).thenReturn("hash");
        when(jdbc.query(contains("appellant_claim_id=?"), any(RowMapper.class), eq(3L), eq(501L)))
                .thenAnswer(call -> List.of(call.<RowMapper<?>>getArgument(1).mapRow(report, 0)));

        assertEquals(900L, service.submit(501L, request, List.of(), user).reportId());
        verify(images, never()).upload(anyString(), any());
        var duplicate = new P2pClaimAppealService.Submit(3L, request.reason(), UUID.randomUUID());
        assertEquals(ErrorCode.REPORT_DUPLICATE, assertThrows(AppException.class,
                () -> service.submit(501L, duplicate, List.of(), user)).getErrorCode());
        verifyNoInteractions(chat);
    }

    @Test
    void newAppealRefreshesTheApprovedConversation() throws Exception {
        CustomUserDetails user = new CustomUserDetails(appellant.getClaimant());
        var request = new P2pClaimAppealService.Submit(3L, "Look at the inner pocket.", UUID.randomUUID());
        givenSubmission(user);
        when(routing.matches(appellant, Set.of("RESERVED"))).thenReturn(true);
        givenWindow(OffsetDateTime.now().plusHours(1), false, false);
        doReturn(List.of()).when(jdbc).query(contains("appellant_claim_id=?"), any(RowMapper.class), eq(3L), eq(501L));
        when(claims.findPostId(501L)).thenReturn(Optional.of(100L));
        when(posts.findForClaimUpdate(100L)).thenReturn(Optional.of(appellant.getPost()));
        when(claims.findForUpdate(501L)).thenReturn(Optional.of(appellant));
        when(images.upload("reports/30", new WorkflowImages.Prepared(List.of(), List.of(), "hash")))
                .thenReturn(List.of());
        Report report = mock(Report.class);
        when(report.getId()).thenReturn(900L);
        when(reports.insertAppeal(eq(30L), eq(501L), anyString(), isNull(),
                eq(request.clientRequestId()), eq("hash"), eq(List.of()))).thenReturn(report);

        assertEquals(900L, service.submit(501L, request, List.of(), user).reportId());

        var ordered = inOrder(jdbc, chat);
        ordered.verify(jdbc).update(startsWith("insert into p2p_claim_appeal"), eq(3L), eq(501L), eq(900L));
        ordered.verify(chat).notifyAppealChanged(900L);
    }

    @ParameterizedTest
    @CsvSource({
        "1, false, false, OPEN",
        "-1, false, false, CLEARED",
        "-1, true, false, UNDER_REVIEW",
        "-1, false, true, REVIEW_REQUIRED",
        "1, false, true, REVIEW_REQUIRED",
        "1, true, true, UNDER_REVIEW"
    })
    void handoverStateDependsOnBothDeadlineAndReportDecisions(long deadlineOffset, boolean pending, boolean upheld,
            P2pClaimAppealService.State state) throws Exception {
        OffsetDateTime deadline = OffsetDateTime.now().plusHours(deadlineOffset);
        givenWindow(deadline, pending, upheld);

        var window = service.handoverWindow(selected);

        assertEquals(state, window.state());
        assertEquals(state != P2pClaimAppealService.State.CLEARED, window.blocksHandover());
    }

    private void givenSubmission(CustomUserDetails user) {
        when(actors.actor(user)).thenReturn(appellant.getClaimant());
        when(claims.findById(501L)).thenReturn(Optional.of(appellant));
        when(images.prepare(anyString(), eq(List.of())))
                .thenReturn(new WorkflowImages.Prepared(List.of(), List.of(), "hash"));
        when(transactions.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
    }

    private void givenWindow(OffsetDateTime deadline, boolean pending, boolean upheld) throws Exception {
        ResultSet row = mock(ResultSet.class);
        when(row.getLong("approval_id")).thenReturn(3L);
        when(row.getLong("approved_claim_id")).thenReturn(500L);
        when(row.getObject("appeal_until", OffsetDateTime.class)).thenReturn(deadline);
        when(row.getBoolean("pending")).thenReturn(pending);
        when(row.getBoolean("upheld")).thenReturn(upheld);
        when(jdbc.query(contains("from p2p_claim_approval"), any(RowMapper.class), eq(100L)))
                .thenAnswer(call -> List.of(call.<RowMapper<?>>getArgument(1).mapRow(row, 0)));
    }

    private Account account(long id) {
        Account account = new Account();
        ReflectionTestUtils.setField(account, "id", id);
        return account;
    }
}
