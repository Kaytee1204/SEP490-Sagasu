package com.example.base.service.report;

import com.example.base.dto.account.UpdateAccountRequest;
import com.example.base.dto.office.OfficeDtos;
import com.example.base.entity.*;
import com.example.base.exception.AppException;
import com.example.base.exception.ErrorCode;
import com.example.base.repository.AccountRepository;
import com.example.base.repository.PostRepository;
import com.example.base.security.CustomUserDetails;
import com.example.base.service.account.AccountService;
import com.example.base.service.chat.ChatConversationService;
import com.example.base.service.claim.ClaimService;
import com.example.base.service.notification.NotificationService;
import com.example.base.service.office.OfficeAccess;
import com.example.base.service.office.OfficeManagementService;
import com.example.base.storage.ImageStorage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;

import static com.example.base.service.report.SqlStub.row;
import static com.example.base.service.report.SqlStub.rows;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReportReviewServiceTest {

    @Mock OfficeAccess access;
    @Mock JdbcTemplate jdbc;
    @Mock ImageStorage storage;
    @Mock AccountService accounts;
    @Mock AccountRepository accountRepository;
    @Mock OfficeManagementService moderation;
    @Mock PostRepository posts;
    @Mock NotificationService notifications;
    @Mock ChatConversationService chat;
    @Mock ClaimService claims;
    @Mock com.example.base.service.claim.P2pClaimAppealService claimAppeals;

    ReportContextService context;
    ReportReviewService service;

    Account manager;
    CustomUserDetails user;
    Map<String, Object> report;
    Map<String, Object> ctx;

    @BeforeEach
    void setUp() {
        context = spy(new ReportContextService(jdbc, new ObjectMapper(), storage));
        service = new ReportReviewService(access, jdbc, context, accounts, accountRepository, moderation, posts, notifications, chat, claims, claimAppeals,
                new ReportReviewCalendar("Asia/Ho_Chi_Minh", "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY", "08:00-12:00,13:00-17:00", ""));
        manager = new Account();
        manager.setId(1L);
        manager.setRole(AccountRole.MANAGER);
        manager.setStatus(AccountStatus.ACTIVE);
        user = new CustomUserDetails(manager);
        when(access.require(eq(user), any(AccountRole[].class))).thenReturn(manager);
        SqlStub.defaults(jdbc);

        report = row("report_id", 5L, "kind", "GENERAL", "status", "IN_REVIEW", "version", 3L,
                "assigned_to", 1L, "reporter_id", 7L, "post_id", 10L);
        ctx = new LinkedHashMap<>(Map.of(
                "post", row("post_id", 10L, "post_type", "FOUND", "status", "OPEN", "moderation_status", "VISIBLE"),
                "participants", List.of(row("id", 7L), row("id", 8L))));
        givenReport(report);
        doAnswer(inv -> ctx).when(context).context(any());
    }

    private void givenReport(Map<String, Object> r) {
        rows(jdbc, "select * from report where report_id", r);
        when(jdbc.queryForMap(contains("for update"), any(Object[].class))).thenAnswer(inv -> new LinkedHashMap<>(r));
    }

    private ReportReviewService.Action action(String action) {
        return action(action, null, false);
    }

    private ReportReviewService.Action action(String action, Long targetId, boolean custody) {
        return new ReportReviewService.Action(3L, action, "Conclusion", "Basis", targetId, custody);
    }

    private void givenTotals(long reports, long groups) {
        givenTotals(reports, groups, 0L, 0L);
    }

    private void givenTotals(long reports, long groups, long ready, long waiting) {
        when(jdbc.queryForMap(startsWith("select count(*) filter (where in_list) as reports"), any(Object[].class)))
                .thenReturn(Map.of("reports", reports, "groups", groups, "ready", ready, "waiting", waiting));
    }

    private void givenVisiblePost() {
        Post post = mock(Post.class);
        when(post.getId()).thenReturn(10L);
        when(post.getModerationStatus()).thenReturn("VISIBLE");
        when(posts.findById(10L)).thenReturn(Optional.of(post));
    }

    private void assertBad(Runnable r, String messagePart) {
        assertThatThrownBy(r::run).isInstanceOf(AppException.class)
                .satisfies(e -> {
                    assertThat(((AppException) e).getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
                    assertThat(e.getMessage()).contains(messagePart);
                });
    }

    private void verifyStatusUpdate(String status) {
        verify(jdbc).update(startsWith("update report set status=?"), eq(status), any(), any(), any(), any(), any(), eq(5L));
    }

    // ---------- list ----------

    @Test
    void utc047_list_validFilters_returnsPagedContent() {
        givenTotals(30L, 25L);
        rows(jdbc, "with queue_rows as", row("report_id", 5L, "reason_summary", "{\"Spam\":2}"));

        Map<String, Object> res = service.list(1, "ALL", "ALL", "ALL", "  wallet ", user);

        // totalElements counts grouped objects; totalReports counts raw reports
        assertThat(res).containsEntry("totalElements", 25L).containsEntry("totalReports", 30L)
                .containsEntry("totalPages", 3L).containsEntry("page", 1);
        List<Map<String, Object>> content = (List<Map<String, Object>>) res.get("content");
        assertThat(content).hasSize(1);
        assertThat(content.get(0).get("reason_summary")).isEqualTo(Map.of("Spam", 2));
    }

    @Test
    void utc048_list_noRows_totalPagesAtLeastOne() {
        givenTotals(0L, 0L);
        assertThat(service.list(1, "OPEN", "GENERAL", "POST", "", user)).containsEntry("totalPages", 1L);
    }

    @Test
    void utc049_list_wildcardsInKeywordAreEscaped() {
        givenTotals(0L, 0L);

        service.list(1, "ALL", "ALL", "ALL", "50%_off", user);

        ArgumentCaptor<Object[]> cap = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).queryForMap(startsWith("select count(*) filter (where in_list) as reports"), cap.capture());
        assertThat(cap.getValue()).contains("%50\\%\\_off%");
    }

    @Test
    void assigneeCombinesWithTheSelectedQueue() {
        givenTotals(4L, 3L, 2L, 3L);
        var result = service.list(1, "ALL", "CLAIM_APPEAL", "ALL", "", "WAITING", null, "MINE", user);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).queryForMap(sql.capture(), args.capture());
        assertThat(sql.getValue()).contains("(queue_state.work_state='WAITING') as in_list", "r.assigned_to=?",
                "filter (where work_state='READY') as ready", "filter (where work_state='WAITING') as waiting");
        assertThat(args.getValue()).contains(1L);
        assertThat(result).containsEntry("totalElements", 3L)
                .containsEntry("countsByQueue", Map.of("READY", 2L, "WAITING", 3L));
    }

    @Test
    void appealHistoryGroupsByReportIdSoSeparateDisputesOnTheSameClaimStayVisible() {
        givenTotals(2L, 2L);
        service.list(1, "ALL", "CLAIM_APPEAL", "ALL", "", "HISTORY", null, user);

        String appealKey = "when appeal.report_id is not null then 'APPEAL:'||r.report_id";
        verify(jdbc).queryForMap(contains(appealKey), any(Object[].class));
        verify(jdbc).queryForList(contains(appealKey), any(Object[].class));
    }

    @Test
    void exactReportReferenceIgnoresQueueAndFiltersWithinItsKind() {
        givenTotals(1L, 1L);
        var result = service.list(4, "OPEN", "GENERAL", "POST", " # 00052 ", "READY", null, "MINE", user);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).queryForMap(sql.capture(), args.capture());
        assertThat(sql.getValue()).contains("else r.kind end=?", "(r.report_id=?) as in_list")
                .doesNotContain("and queue_state.work_state='READY'", "and r.assigned_to=?", "r.description ilike");
        assertThat(args.getValue()).startsWith(52L).contains("GENERAL").doesNotContain("OPEN", "POST");
        assertThat(result).containsEntry("page", 1);
    }

    @Test
    void utc050_list_pageZero_bad() {
        assertBad(() -> service.list(0, "ALL", "ALL", "ALL", "", user), "Bộ lọc");
    }

    @Test
    void utc051_list_page100001_bad() {
        assertBad(() -> service.list(100001, "ALL", "ALL", "ALL", "", user), "Bộ lọc");
    }

    @Test
    void utc052_list_unknownStatusKindOrTarget_bad() {
        assertBad(() -> service.list(1, "CLOSED", "ALL", "ALL", "", user), "Bộ lọc");
        assertBad(() -> service.list(1, "ALL", "OTHER", "ALL", "", user), "Bộ lọc");
        assertBad(() -> service.list(1, "ALL", "ALL", "COMMENT", "", user), "Bộ lọc");
    }

    @Test
    void utc053_list_keyword201Chars_bad() {
        assertBad(() -> service.list(1, "ALL", "ALL", "ALL", "a".repeat(201), user), "Bộ lọc");
    }

    @Test
    void utc054_list_notManager_forbidden() {
        when(access.require(eq(user), any(AccountRole[].class))).thenThrow(new AppException(ErrorCode.FORBIDDEN));
        assertThatThrownBy(() -> service.list(1, "ALL", "ALL", "ALL", "", user))
                .isInstanceOf(AppException.class).extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
    }

    // ---------- detail ----------

    @Test
    void utc055_detail_assignedToActor_canProcess() {
        Map<String, Object> d = service.detail(5L, user);

        verify(context).capture(5L, "FIRST_REVIEW");
        assertThat(d).containsEntry("canProcess", true).containsEntry("canTake", false).containsEntry("conflict", false);
        assertThat(d).containsKeys("report", "current", "snapshot", "evidence", "history", "decisions", "inquiries", "hold", "p2pDispute", "related");
    }

    @Test
    void utc056_detail_openUnassigned_canTake() {
        report.put("assigned_to", null);
        report.put("status", "OPEN");
        givenReport(report);

        assertThat(service.detail(5L, user)).containsEntry("canTake", true).containsEntry("canProcess", false);
    }

    @Test
    void utc057_detail_actorIsParticipant_conflict() {
        ctx.put("participants", List.of(row("id", 1L)));

        assertThat(service.detail(5L, user)).containsEntry("conflict", true)
                .containsEntry("canTake", false).containsEntry("canProcess", false);
    }

    @Test
    void utc058_detail_missingReport_bad() {
        rows(jdbc, "select * from report where report_id");
        assertBad(() -> service.detail(99L, user), "Không tìm thấy báo cáo");
    }

    @Test
    void utc059_participantsFor_mergesSnapshotParticipants() {
        rows(jdbc, "payload::text from report_snapshot", row("payload", "{\"participants\":[{\"id\":9}]}"));
        assertThat(service.participantsFor(report)).containsExactlyInAnyOrder(7L, 8L, 9L);
    }

    @Test
    void utc060_inquiries_attachesEvidenceUrls() {
        rows(jdbc, "from report_inquiry i join accounts", row("inquiry_id", 2L, "question", "Q"));
        rows(jdbc, "from report_reply_evidence", row("evidence_id", 1L, "storage_key", "k"));
        when(storage.readUrl("k", null)).thenReturn("https://cdn/k");

        List<Map<String, Object>> list = service.inquiries(5L);

        assertThat(list.get(0).get("evidence").toString()).contains("https://cdn/k");
    }

    // ---------- act: routing ----------

    @Test
    void utc061_act_unknownAction_rejected() {
        assertBad(() -> service.act(5L, action("CONCLUDE"), user), "Thao tác không hợp lệ");
    }

    @Test
    void utc062_suspend_withoutTarget_rejected() {
        assertBad(() -> service.act(5L, action("SUSPEND_ACCOUNT"), user), "Chọn đúng tài khoản");
    }

    @Test
    void utc063_hidePost_alreadyHidden_rejected() {
        Post post = mock(Post.class);
        when(post.getModerationStatus()).thenReturn("HIDDEN");
        when(posts.findById(10L)).thenReturn(Optional.of(post));
        assertBad(() -> service.act(5L, action("HIDE_POST"), user), "Bài đăng đã bị ẩn");
        verifyNoInteractions(moderation);
    }

    // ---------- act: TAKE ----------

    @Test
    void utc064_take_openUnassigned_assignsAndLogsHistory() {
        report.put("assigned_to", null);
        report.put("status", "OPEN");
        givenReport(report);

        service.act(5L, action("TAKE"), user);

        verify(jdbc).update(contains("set assigned_to=?"), eq(1L), eq(5L));
        verify(jdbc).update(contains("insert into report_activity"), eq(5L), eq(1L), eq("TAKE"), eq("OPEN"), eq("IN_REVIEW"), anyString(), isNull());
    }

    @Test
    void utc065_take_alreadyAssigned_rejected() {
        assertBad(() -> service.act(5L, action("TAKE"), user), "đã được nhận");
    }

    @Test
    void utc066_take_versionMismatch_rejected() {
        report.put("assigned_to", null);
        report.put("version", 4L);
        givenReport(report);
        assertBad(() -> service.act(5L, action("TAKE"), user), "đã được nhận");
    }

    @Test
    void utc067_take_actorIsParticipant_rejected() {
        report.put("assigned_to", null);
        givenReport(report);
        ctx.put("participants", List.of(row("id", 1L)));
        assertBad(() -> service.act(5L, action("TAKE"), user), "liên quan");
    }

    // ---------- act: processing guard ----------

    @Test
    void utc068_act_notAssignedToActor_rejected() {
        report.put("assigned_to", 2L);
        givenReport(report);
        assertBad(() -> service.act(5L, action("HOLD"), user), "Cần nhận xử lý");
    }

    @Test
    void utc069_act_staleVersion_rejected() {
        report.put("version", 9L);
        givenReport(report);
        assertBad(() -> service.act(5L, action("HOLD"), user), "Báo cáo đã thay đổi");
    }

    @Test
    void utc070_act_closedReport_rejected() {
        report.put("status", "RESOLVED");
        givenReport(report);
        assertBad(() -> service.act(5L, action("HOLD"), user), "Báo cáo đã thay đổi");
    }

    @Test
    void utc071_act_blankConclusion_rejected() {
        var req = new ReportReviewService.Action(3L, "HOLD", "  ", "Basis", null, false);
        assertBad(() -> service.act(5L, req, user), "Cần ghi kết luận");
    }

    // ---------- act: HOLD / RELEASE ----------

    @Test
    void utc072_hold_foundOpenPost_insertsHoldWithoutClosing() {
        service.act(5L, action("HOLD"), user);

        verify(jdbc).update(contains("insert into report_hold"), eq(5L), eq(10L), eq(1L), eq("Conclusion"));
        verifyStatusUpdate("IN_REVIEW");
        verifyNoInteractions(notifications);
    }

    @Test
    void utc073_hold_lostPost_rejected() {
        ctx.put("post", row("post_id", 10L, "post_type", "LOST", "status", "OPEN"));
        assertBad(() -> service.act(5L, action("HOLD"), user), "không còn ở bước");
    }

    @Test
    void utc074_hold_returnedPost_rejected() {
        ctx.put("post", row("post_id", 10L, "post_type", "FOUND", "status", "RETURNED"));
        assertBad(() -> service.act(5L, action("HOLD"), user), "không còn ở bước");
    }

    @Test
    void utc075_hold_noPostInContext_rejected() {
        ctx.remove("post");
        assertBad(() -> service.act(5L, action("HOLD"), user), "không có đồ vật");
    }

    @Test
    void utc076_release_releasesActiveHold() {
        ctx.put("post", row("post_id", 10L, "post_type", "FOUND", "status", "RETURNED"));

        service.act(5L, action("RELEASE"), user);

        verify(jdbc).update(contains("update report_hold set released_at=now()"), eq(5L));
    }

    // ---------- act: HIDE_POST / SUSPEND_ACCOUNT (measures keep the report open) ----------

    @Test
    void utc077_hidePost_noPostInContext_rejected() {
        ctx.remove("post");
        assertBad(() -> service.act(5L, action("HIDE_POST"), user), "không có bài đăng để ẩn");
    }

    @Test
    void utc078_hidePost_moderatesAndRecordsDecision() {
        givenVisiblePost();

        service.act(5L, action("HIDE_POST"), user);

        ArgumentCaptor<OfficeDtos.Moderation> cap = ArgumentCaptor.forClass(OfficeDtos.Moderation.class);
        verify(moderation).moderate(eq(10L), cap.capture(), eq(user));
        assertThat(cap.getValue().status()).isEqualTo("HIDDEN");
        assertThat(cap.getValue().reason()).isEqualTo("Conclusion");
        verify(jdbc).update(contains("insert into report_decision"), eq(5L), eq(1L), eq("HIDE_POST"), eq("Conclusion"),
                eq("Basis"), anyString(), anyString());
    }

    @Test
    void utc079_suspend_participant_suspendsAndResetsLock() {
        givenAccountReport();
        rows(jdbc, "select status,role from accounts", row("status", "ACTIVE", "role", "USER"));
        Account target = new Account();
        target.setId(8L);
        target.setFullName("Bad Actor");
        target.setFailedLoginCount(4);
        when(accountRepository.findById(8L)).thenReturn(Optional.of(target));

        service.act(5L, action("SUSPEND_ACCOUNT", 8L, false), user);

        ArgumentCaptor<UpdateAccountRequest> cap = ArgumentCaptor.forClass(UpdateAccountRequest.class);
        verify(accounts).updateAccount(eq(8L), cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo(AccountStatus.SUSPENDED);
        assertThat(target.getFailedLoginCount()).isZero();
        assertThat(target.getLockedUntil()).isNull();
        verify(accountRepository).flush();
    }

    @Test
    void utc080_suspend_reporter_rejected() {
        givenAccountReport();
        assertBad(() -> service.act(5L, action("SUSPEND_ACCOUNT", 7L, false), user), "Chọn đúng tài khoản");
    }

    @Test
    void utc081_suspend_nonParticipant_rejected() {
        givenAccountReport();
        assertBad(() -> service.act(5L, action("SUSPEND_ACCOUNT", 99L, false), user), "Chọn đúng tài khoản");
    }

    @Test
    void utc082_suspend_alreadySuspended_rejected() {
        givenAccountReport();
        rows(jdbc, "select status,role from accounts", row("status", "SUSPENDED"));
        assertBad(() -> service.act(5L, action("SUSPEND_ACCOUNT", 8L, false), user), "không còn hoạt động");
        verifyNoInteractions(accounts);
    }

    @Test
    void utc083_suspend_basisNamesSuspendedAccount() {
        givenAccountReport();
        rows(jdbc, "select status,role from accounts", row("status", "ACTIVE", "role", "USER"));
        Account target = new Account();
        target.setId(8L);
        target.setFullName("Bad Actor");
        when(accountRepository.findById(8L)).thenReturn(Optional.of(target));

        service.act(5L, action("SUSPEND_ACCOUNT", 8L, false), user);

        verify(jdbc).update(contains("insert into report_decision"), eq(5L), eq(1L), eq("SUSPEND_ACCOUNT"), eq("Conclusion"),
                eq("Đã đình chỉ tài khoản Bad Actor (#8).\nBasis"), anyString(), anyString());
    }

    private void givenAccountReport() {
        report.remove("post_id");
        report.put("reported_account_id", 8L);
    }

    @Test
    void warnsTheReportedAccountWithoutSuspendingIt() {
        givenAccountReport();
        rows(jdbc, "select status,role from accounts", row("status", "ACTIVE", "role", "USER"));

        service.act(5L, action("WARN_ACCOUNT"), user);

        verify(notifications).notifyUser(8L, "REPORT_ACCOUNT_WARNING", "Cảnh báo tài khoản", "Conclusion", "/bao-cao/5", false);
        verifyNoInteractions(accounts);
        verifyStatusUpdate("IN_REVIEW");
    }

    @Test
    void reportAboutAPostCannotSuspendAnAccount() {
        assertBad(() -> service.act(5L, action("SUSPEND_ACCOUNT", 8L, false), user), "Chọn đúng tài khoản");
        verifyNoInteractions(accounts);
    }

    @Test
    void structuredViolationAppliesTheMeasureAndLogsItWithTheFinding() {
        givenVisiblePost();

        var request = new ReportReviewService.Action(3L, "VIOLATION", "Conclusion", "Basis", null, false, "HIDE_POST");
        service.act(5L, request, user);

        verify(moderation).moderate(eq(10L), any(OfficeDtos.Moderation.class), eq(user));
        verifyStatusUpdate("RESOLVED");
        verify(jdbc).update(contains("insert into report_decision"), eq(5L), eq(1L), eq("VIOLATION"), eq("Conclusion"),
                eq("Basis"), anyString(), contains("\"appliedMeasure\":\"HIDE_POST\""));
    }

    @Test
    void insufficientEvidenceCannotApplyASanction() {
        var request = new ReportReviewService.Action(
                3L, "INSUFFICIENT_EVIDENCE", "Conclusion", "Basis", null, false, "HIDE_POST");

        assertBad(() -> service.act(5L, request, user), "Biện pháp không phù hợp");
        verifyNoInteractions(moderation, accounts, claims);
        service.act(5L, action("INSUFFICIENT_EVIDENCE"), user);
        verifyStatusUpdate("REJECTED");
    }

    @Test
    void appealCannotStartItemRecoveryBeforeOrAfterConclusion() {
        report.put("kind", "HANDOVER_ISSUE");
        report.put("status", "RESOLVED");
        rows(jdbc, "where a.report_id=?", row("approval_id", 30L, "approved_claim_id", 40L,
                "active", true, "follow_up", "WAIT_APPEALS"));

        assertBad(() -> service.act(5L, action("REQUEST_ITEM_RECOVERY", 8L, false), user), "Hồ sơ tranh chấp này chỉ dùng để xem lại");
        report.put("status", "IN_REVIEW");
        assertBad(() -> service.act(5L, action("REQUEST_ITEM_RECOVERY", 8L, false), user), "Hồ sơ tranh chấp này chỉ dùng để xem lại");
        verifyNoInteractions(notifications, claims);
    }

    @Test
    void recoveryRequestKeepsDisputeOpenAndNotifiesParticipants() {
        givenDispute("DISPUTED", "APPROVED");
        when(jdbc.queryForObject(contains("p2p_handover_issue"), eq(Boolean.class), any(Object[].class))).thenReturn(true);

        service.act(5L, action("REQUEST_ITEM_RECOVERY", 8L, false), user);

        verifyStatusUpdate("IN_REVIEW");
        verify(jdbc).update(contains("insert into report_hold"), eq(5L), eq(10L), eq(1L), eq("Conclusion"));
        verify(notifications).notifyUser(7L, "REPORT_RECOVERY_REQUESTED", "Yêu cầu thu hồi món đồ", "Conclusion", "/bao-cao/5", false);
        verify(notifications).notifyUser(8L, "REPORT_INFORMATION", "Cần phản hồi về việc thu hồi món đồ",
                "Conclusion", "/bao-cao/5#phan-hoi", false);
        verifyNoInteractions(claims);
    }

    @ParameterizedTest
    @ValueSource(strings = {"P2P", "OFFICE"})
    void verifiedInvalidClaimBlocksTheAppellantAndClosesTheDispute(String claimType) {
        report.put("kind", "HANDOVER_ISSUE");
        report.put("claim_id", 41L);
        rows(jdbc, "where a.report_id=?", row("approval_id", 30L, "approved_claim_id", 40L,
                "active", true, "post_id", 25L, "claim_type", claimType));
        ctx.put("claim", row("claim_id", 41L, "claimant_id", 7L, "claim_type", claimType, "status", "PENDING"));

        assertThat((List<?>) service.detail(5L, user).get("actions")).hasSize(1);
        assertBad(() -> service.act(5L, action("APPEAL_REJECT_CLAIM"), user), "Thao tác không hợp lệ");
        assertBad(() -> service.act(5L, action("APPEAL_KEEP"), user), "Chọn căn cứ giữ người đã duyệt");
        service.act(5L, new ReportReviewService.Action(3L, "APPEAL_KEEP", "Conclusion", "Basis", null,
                false, null, false, ReportReviewService.AppealFinding.INVALID_CLAIM), user);

        if ("P2P".equals(claimType)) verify(claimAppeals).releaseWindowIfUncontested(25L);
        else verifyNoInteractions(claimAppeals);
        verify(claims).rejectClaimAfterAppeal(eq(41L), contains("Bạn không thể gửi lại"), eq(user));
        verify(jdbc).update(contains("insert into report_decision"), eq(5L), eq(1L),
                eq("APPEAL_REJECT_CLAIM"), eq("Conclusion"), eq("Basis"), anyString(), anyString());
        verifyStatusUpdate("REJECTED");
    }

    @Test
    void insufficientEvidenceClosesTheDisputeAndLeavesPendingClaimUntouched() {
        report.put("kind", "HANDOVER_ISSUE");
        report.put("claim_id", 41L);
        rows(jdbc, "where a.report_id=?", row("approval_id", 30L, "approved_claim_id", 40L, "active", true));
        ctx.put("claim", row("claim_id", 41L, "claimant_id", 7L, "claim_type", "P2P", "status", "PENDING"));

        service.act(5L, new ReportReviewService.Action(3L, "APPEAL_KEEP", "Conclusion", "Basis", null,
                false, null, false, ReportReviewService.AppealFinding.INSUFFICIENT_EVIDENCE), user);

        verifyNoInteractions(claims);
        verify(jdbc).update(contains("insert into report_decision"), eq(5L), eq(1L),
                eq("APPEAL_INSUFFICIENT"), eq("Conclusion"), eq("Basis"), anyString(), anyString());
        verify(jdbc).update(contains("update report_inquiry i set closed_at"), eq(5L));
        verifyStatusUpdate("REJECTED");
    }

    @Test
    void confirmingWrongRecipientReopensTheApprovalRoundAndNotifiesTheFinder() {
        report.put("kind", "HANDOVER_ISSUE");
        rows(jdbc, "where a.report_id=?", row("approval_id", 30L, "approved_claim_id", 40L,
                "active", true, "can_reopen_approval", true, "post_id", 10L, "finder_id", 8L, "waiting_other_appeals", true));
        rows(jdbc, "where appeal.approval_id=? and r.report_id<>?", row("report_id", 6L, "reporter_id", 9L, "status", "OPEN"));
        when(jdbc.update(startsWith("update report set status='RESOLVED'"), anyString(), eq(1L), eq(6L))).thenReturn(1);
        assertThat(service.detail(5L, user).get("actions")).isEqualTo(List.of("APPEAL_KEEP", "APPEAL_WRONG_RECIPIENT"));
        assertBad(() -> service.act(5L, action("RESOLVE"), user), "quyết định xử lý tranh chấp");
        assertBad(() -> service.act(5L, action("APPEAL_REQUIRE_REVIEW"), user), "Thao tác không hợp lệ");
        assertBad(() -> service.act(5L, action("APPEAL_WRONG_RECIPIENT"), user), "Cần xác nhận đã đối chiếu");
        assertBad(() -> service.act(5L, new ReportReviewService.Action(3L, "APPEAL_WRONG_RECIPIENT",
                "Conclusion", "Basis", null, false, null, true), user), "Cần xác minh đồ");
        service.act(5L, new ReportReviewService.Action(3L, "APPEAL_WRONG_RECIPIENT",
                "Conclusion", "Basis", null, true, null, true), user);
        verify(jdbc).update(contains("insert into report_decision"), eq(5L), eq(1L),
                eq("APPEAL_WRONG_RECIPIENT"), eq("Conclusion"), eq("Basis"), anyString(),
                contains("\"approvalReopened\":true"));
        verify(jdbc).update(contains("update report_inquiry i set closed_at=now()"), eq(5L));
        verify(jdbc).update(contains("update report_inquiry i set closed_at=now()"), eq(6L));
        verify(jdbc).update(startsWith("update report set status='RESOLVED'"), contains("đã được mở lại"), eq(1L), eq(6L));
        verify(notifications).notifyUser(eq(8L), eq("REPORT_RESOLVED"), eq("Các yêu cầu nhận đồ đã mở lại để bạn xét duyệt"),
                anyString(), eq("/bai-dang/10/claims?claimStatus=PENDING"), eq(false));
        verify(notifications).notifyUser(eq(9L), eq("REPORT_RESOLVED"), anyString(), anyString(), eq("/bao-cao/6"), eq(false));
        verify(chat).notifyAppealChanged(5L);
        verifyStatusUpdate("RESOLVED");
        verify(claims).reopenApprovalForReview(40L, "Báo cáo #5: Conclusion", user);
    }

    @Test
    void wrongRecipientCannotReopenAClaimWithUnconfirmedCustodyState() {
        report.put("kind", "HANDOVER_ISSUE");
        rows(jdbc, "where a.report_id=?", row("approval_id", 30L, "approved_claim_id", 40L,
                "active", true, "can_reopen_approval", false));
        assertBad(() -> service.act(5L, new ReportReviewService.Action(3L, "APPEAL_WRONG_RECIPIENT",
                "Conclusion", "Basis", null, true, null, true), user), "sự cố bàn giao");
        verifyNoInteractions(claims);
    }

    @Test
    void appealConclusionDoesNotExposeASeparateManagerReopenAction() {
        report.put("kind", "HANDOVER_ISSUE");
        report.put("status", "RESOLVED");
        rows(jdbc, "where a.report_id=?", row("approval_id", 30L, "approved_claim_id", 40L,
                "active", true, "follow_up", "READY_TO_REOPEN"));
        assertThat(service.detail(5L, user).get("actions")).isEqualTo(List.of());
        assertBad(() -> service.act(5L, action("APPEAL_REOPEN", null, true), user), "Báo cáo đã thay đổi");

        verifyNoInteractions(claims);
    }

    @Test
    void utc084_measure_keepsReportInReviewWithoutNotifying() {
        givenVisiblePost();

        service.act(5L, action("HIDE_POST"), user);

        verifyStatusUpdate("IN_REVIEW");
        verifyNoInteractions(notifications);
        verify(jdbc, never()).update(contains("insert into report_recipient"), any(), any());
    }

    @Test
    void utc085_decision_recordsBeforeAndAfterStatus() {
        service.act(5L, action("RESOLVE"), user);

        ArgumentCaptor<String> before = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> after = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(contains("insert into report_decision"), eq(5L), eq(1L), eq("RESOLVE"), eq("Conclusion"),
                eq("Basis"), before.capture(), after.capture());
        assertThat(before.getValue()).contains("\"reportStatus\":\"IN_REVIEW\"");
        assertThat(after.getValue()).contains("\"reportStatus\":\"RESOLVED\"");
    }

    // ---------- act: RESOLVE / DISMISS ----------

    @Test
    void utc086_resolve_notifiesEveryParticipant() {
        service.act(5L, action("RESOLVE"), user);

        verify(notifications).notifyUser(eq(7L), eq("REPORT_RESOLVED"), anyString(), eq("Conclusion"), anyString(), eq(false));
        verify(notifications).notifyUser(eq(8L), eq("REPORT_RESOLVED"), anyString(), eq("Conclusion"), anyString(), eq(false));
        verify(jdbc, times(2)).update(contains("insert into report_recipient"), eq(5L), anyLong());
    }

    @Test
    void utc087_dismiss_activeP2pDispute_mustUseHandoverOutcome() {
        when(jdbc.queryForObject(contains("p2p_handover_issue"), eq(Boolean.class), any(Object[].class))).thenReturn(true);
        assertBad(() -> service.act(5L, action("DISMISS"), user), "kết quả trao trả");
    }

    @Test
    void utc088_resolve_resolvedAndHoldReleased() {
        service.act(5L, action("RESOLVE"), user);
        verifyStatusUpdate("RESOLVED");
        verify(jdbc).update(contains("update report_inquiry i set closed_at=now()"), eq(5L));
        verify(jdbc).update(contains("update report_hold set released_at=now()"), eq(5L));
        verify(chat).notifyAppealChanged(5L);
    }

    @Test
    void utc089_dismiss_rejectedStatus() {
        service.act(5L, action("DISMISS"), user);
        verifyStatusUpdate("REJECTED");
        verify(chat).notifyAppealChanged(5L);
    }

    @Test
    void utc090_resolve_activeP2pDispute_mustUseHandoverOutcome() {
        when(jdbc.queryForObject(contains("p2p_handover_issue"), eq(Boolean.class), any(Object[].class))).thenReturn(true);
        assertBad(() -> service.act(5L, action("RESOLVE"), user), "kết quả trao trả");
    }

    @Test
    void beforeHandoverIssueRequiresExplicitDecisionBeforeRelease() {
        report.put("kind", "HANDOVER_ISSUE");
        rows(jdbc, "select phase from p2p_handover_issue", row("phase", "BEFORE"));
        assertBad(() -> service.act(5L, action("RELEASE"), user), "kết luận sự cố");
        assertBad(() -> service.act(5L, action("RESOLVE"), user), "kết quả trao trả");
    }

    // ---------- act: handover outcomes ----------

    private void givenDispute(String handoverStatus, String claimStatus) {
        report.put("kind", "HANDOVER_ISSUE");
        report.put("claim_id", 20L);
        givenReport(report);
        rows(jdbc, "select h.* from p2p_handover h", row("handover_id", 70L, "status", handoverStatus));
        rows(jdbc, "claim_type from claim where claim_id", row("claim_id", 20L, "post_id", 10L, "status", claimStatus, "claim_type", "P2P"));
    }

    @Test
    void utc091_handoverCompleted_completesHandoverClaimAndPost() {
        givenDispute("DISPUTED", "APPROVED");
        when(jdbc.queryForList(contains("returning claim_id,claimant_id"), eq(10L)))
                .thenReturn(List.of(row("claim_id", 21L, "claimant_id", 9L)));

        service.act(5L, action("HANDOVER_COMPLETED", null, true), user);

        verify(jdbc).update(contains("update p2p_handover set status='COMPLETED'"), eq(1L), eq(70L));
        verify(jdbc).update(contains("update claim set status='COMPLETED'"), eq(20L));
        verify(jdbc).update(contains("update post set status='RETURNED'"), eq(10L));
        verify(notifications).notifyUser(eq(9L), eq("CLAIM_CLOSED"), anyString(), anyString(),
                eq("/bai-dang/10/claims/21"), eq(false));
        verifyStatusUpdate("RESOLVED");
    }

    @Test
    void utc092_handoverContinue_backToWaitingConfirmation() {
        givenDispute("DISPUTED", "APPROVED");

        service.act(5L, action("HANDOVER_CONTINUE", null, false), user);

        verify(jdbc).update(contains("status='WAITING_CONFIRMATION'"), eq(70L));
        verify(jdbc, never()).queryForObject(contains("report_id<>?"), eq(Boolean.class), any(Object[].class));
    }

    @Test
    void handoverDisputeCannotRejectClaimDirectly() {
        givenDispute("DISPUTED", "APPROVED");

        assertBad(() -> service.act(5L, action("CLAIM_REJECTED", null, true), user), "Thao tác không hợp lệ");
        verify(jdbc, never()).update(contains("update p2p_handover set status='CANCELLED'"), anyLong());
    }

    @Test
    void handoverRetryRequiresCustodyCheck() {
        givenDispute("DISPUTED", "APPROVED");
        assertBad(() -> service.act(5L, action("HANDOVER_RETRY", null, false), user), "Cần xác minh");
    }

    @Test
    void utc095_handoverOutcome_onGeneralReport_rejected() {
        assertBad(() -> service.act(5L, action("HANDOVER_COMPLETED", null, false), user), "không phải tranh chấp");
    }

    @Test
    void utc096_handoverOutcome_noP2pHandover_rejected() {
        givenDispute("DISPUTED", "APPROVED");
        rows(jdbc, "select h.* from p2p_handover h");
        assertBad(() -> service.act(5L, action("HANDOVER_COMPLETED", null, true), user), "chỉ hỗ trợ ghi kết quả xác minh");
    }

    @Test
    void utc097_handoverOutcome_handoverNoLongerDisputed_rejected() {
        givenDispute("COMPLETED", "APPROVED");
        assertBad(() -> service.act(5L, action("HANDOVER_COMPLETED", null, true), user), "đã thay đổi");
    }

    @Test
    void utc098_handoverCompleted_otherReportHoldsItem_rejected() {
        givenDispute("DISPUTED", "APPROVED");
        when(jdbc.queryForObject(contains("report_id<>?"), eq(Boolean.class), any(Object[].class))).thenReturn(true);
        assertBad(() -> service.act(5L, action("HANDOVER_COMPLETED", null, true), user), "báo cáo khác");
    }

    @Test
    void handoverCompletionRequiresCorrectItemAndRecipientConfirmation() {
        report.put("kind", "HANDOVER_ISSUE");
        assertBad(() -> service.act(5L, action("HANDOVER_COMPLETED"), user), "đúng người nhận");
    }

    @Test
    void retryHandoverPreservesTheSelectedRecipient() {
        givenDispute("DISPUTED", "APPROVED");
        service.act(5L, action("HANDOVER_RETRY", null, true), user);
        verify(jdbc).update(contains("update p2p_handover set status='CANCELLED'"), eq(70L));
        verify(jdbc).update(contains("update claim set status=?"), eq("APPROVED"), eq(20L));
        verify(jdbc, never()).update(contains("update post set status='OPEN'"), anyLong());
    }

    @Test
    void reopenHandoverReturnsClaimToReview() {
        givenDispute("DISPUTED", "APPROVED");
        service.act(5L, action("HANDOVER_REOPEN", null, true), user);
        verify(jdbc).update(contains("update claim set status=?"), eq("PENDING"), eq(20L));
        verify(jdbc).update(contains("update post set status='OPEN'"), eq(10L));
        verify(jdbc).update(contains("update p2p_claim_approval set closed_at"), eq(20L));
    }

    @Test
    void pendingRecoveryMustBeConfirmedBeforeFinishingTheHandoverReport() {
        report.put("kind", "HANDOVER_ISSUE");
        rows(jdbc, "from report_recovery x", row("report_id", 5L, "holder_id", 8L));
        assertBad(() -> service.act(5L, action("RESOLVE"), user), "Cần xác nhận kết quả thu hồi");
        when(jdbc.update(contains("update report_recovery set confirmed_by"), eq(1L), eq(5L))).thenReturn(1);

        service.act(5L, action("CONFIRM_ITEM_RECOVERY", null, true), user);

        verifyStatusUpdate("IN_REVIEW");
        verify(jdbc).update(contains("update report_inquiry set closed_at"), eq(5L));
        verify(notifications).notifyUser(eq(8L), eq("REPORT_RECOVERY_CONFIRMED"), anyString(), eq("Conclusion"),
                eq("/bao-cao/5"), eq(false));
    }

    // ---------- helpers ----------

    @Test
    void utc099_text_validatesAndStrips() {
        assertThat(ReportReviewService.text("  ok  ")).isEqualTo("ok");
        assertThat(ReportReviewService.text("a".repeat(5000))).hasSize(5000);
        assertBad(() -> ReportReviewService.text(null), "Cần ghi kết luận");
        assertBad(() -> ReportReviewService.text("a".repeat(5001)), "Cần ghi kết luận");
    }
}
