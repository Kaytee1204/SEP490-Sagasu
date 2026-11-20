package com.example.base.service.report;

import com.example.base.entity.*;
import com.example.base.exception.AppException;
import com.example.base.exception.ErrorCode;
import com.example.base.repository.ReportRepository;
import com.example.base.security.CustomUserDetails;
import com.example.base.service.chat.ChatAccessPolicy;
import com.example.base.service.chat.ChatService;
import com.example.base.service.claim.ClaimRoutingPolicy;
import com.example.base.service.notification.NotificationService;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReportServiceTest {

    @Mock ReportRepository reports;
    @Mock EntityManager em;
    @Mock ChatAccessPolicy actors;
    @Mock ChatService chats;
    @Mock ClaimRoutingPolicy claimPolicy;
    @Mock JdbcTemplate jdbc;
    @Mock WorkflowImages images;
    @Mock PlatformTransactionManager transactions;
    @Mock ReportContextService context;
    @Mock NotificationService notifications;
    @Mock ReportReasonService reasons;

    @InjectMocks ReportService service;

    Account reporter;
    CustomUserDetails user;
    UUID reqId = UUID.randomUUID();
    static final String REASON = "POST_SCAM";
    static final String LABEL = "Lừa đảo";
    List<MultipartFile> files = List.of(new MockMultipartFile("files", "a.jpg", "image/jpeg", new byte[]{1}));
    WorkflowImages.Prepared prepared = new WorkflowImages.Prepared(List.of(), List.of("a.jpg"), "hash-1");
    List<WorkflowImages.Image> uploaded = List.of(new WorkflowImages.Image("reports/7/x.jpg", "a.jpg", "image/jpeg", 1, "h", 0));

    @BeforeEach
    void setUp() {
        reporter = new Account();
        reporter.setId(7L);
        reporter.setRole(AccountRole.USER);
        reporter.setStatus(AccountStatus.ACTIVE);
        user = new CustomUserDetails(reporter);
        when(actors.actor(user)).thenReturn(reporter);
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(images.prepare(anyString(), any())).thenReturn(prepared);
        when(images.upload(anyString(), any())).thenReturn(uploaded);
        when(reports.findByReporterIdAndClientRequestId(anyLong(), any())).thenReturn(Optional.empty());
        when(jdbc.queryForObject(contains("from report where reporter_id"), eq(Long.class), any(), any())).thenReturn(0L);
        when(jdbc.queryForObject(contains("office_handover"), eq(Boolean.class), any())).thenReturn(false);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        when(reports.saveAndFlush(any(Report.class))).thenAnswer(inv -> withId(inv.getArgument(0), 100L));
        when(reasons.require(anyString(), anyString(), any())).thenAnswer(inv -> new ReportReasonService.Reason(inv.getArgument(1), LABEL, false));
    }

    private static Report withId(Report r, long id) {
        ReflectionTestUtils.setField(r, "id", id);
        return r;
    }

    private Post post(String moderation, long ownerId, boolean deleted) {
        Post p = mock(Post.class);
        Account owner = new Account();
        owner.setId(ownerId);
        when(p.getModerationStatus()).thenReturn(moderation);
        when(p.getAccount()).thenReturn(owner);
        when(p.getDeletedAt()).thenReturn(deleted ? java.time.OffsetDateTime.now() : null);
        return p;
    }

    private Claim claim(long claimantId) {
        Claim c = mock(Claim.class);
        Account a = new Account();
        a.setId(claimantId);
        when(c.getClaimant()).thenReturn(a);
        return c;
    }

    private ReportService.Submit submit(String type, Long id, String desc) {
        return new ReportService.Submit(type, id, desc, reqId, REASON);
    }

    private void assertCode(Runnable r, ErrorCode code) {
        assertThatThrownBy(r::run).isInstanceOf(AppException.class).extracting("errorCode").isEqualTo(code);
    }

    // ---------- create ----------

    @Test
    void utc001_create_visiblePost_createsReportAndNotifiesManagers() {
        Post p0 = post("VISIBLE", 99L, false); when(em.find(Post.class, 10L)).thenReturn(p0);

        ReportService.Detail d = service.create(submit("POST", 10L, " Scam post "), files, user);

        ArgumentCaptor<Report> cap = ArgumentCaptor.forClass(Report.class);
        verify(reports).saveAndFlush(cap.capture());
        assertThat(cap.getValue().getKind()).isEqualTo("GENERAL");
        assertThat(cap.getValue().getDescription()).isEqualTo(LABEL + "\n\nScam post");
        assertThat(cap.getValue().getReasonCode()).isEqualTo(REASON);
        assertThat(d.id()).isEqualTo(100L);
        assertThat(d.description()).isEqualTo("Scam post");
        assertThat(d.reasonLabel()).isEqualTo(LABEL);
        verify(reasons, atLeastOnce()).require("POST", REASON, "Scam post");
        verify(em).find(Account.class, 7L, LockModeType.PESSIMISTIC_WRITE);
        verify(jdbc).update(contains("insert into report_evidence"), any(Object[].class));
        verify(jdbc).update(contains("'CREATE','OPEN'"), eq(100L), eq(7L));
        verify(context).capture(100L, "SUBMISSION");
        verify(notifications).notifyRoles(eq(List.of(AccountRole.MANAGER)), eq(7L), eq("REPORT_CREATED"), anyString(), anyString(), eq("/admin/reports"), eq(false));
        verify(images, never()).cleanup(any());
    }

    @Test
    void utc002_create_nullRequest_invalid() {
        assertCode(() -> service.create(null, files, user), ErrorCode.WORKFLOW_INPUT_INVALID);
    }

    @Test
    void utc003_create_unknownTargetType_invalid() {
        assertCode(() -> service.create(submit("COMMENT", 10L, "x"), files, user), ErrorCode.WORKFLOW_INPUT_INVALID);
    }

    @Test
    void utc004_create_targetIdZero_invalid() {
        assertCode(() -> service.create(submit("POST", 0L, "x"), files, user), ErrorCode.WORKFLOW_INPUT_INVALID);
    }

    @Test
    void utc005_create_description4801Chars_invalid() {
        assertCode(() -> service.create(submit("POST", 10L, "a".repeat(4801)), files, user), ErrorCode.WORKFLOW_INPUT_INVALID);
    }

    @Test
    void utc006_create_nullClientRequestId_invalid() {
        assertCode(() -> service.create(new ReportService.Submit("POST", 10L, "x", null, REASON), files, user), ErrorCode.WORKFLOW_INPUT_INVALID);
    }

    @Test
    void utc007_create_deletedPost_targetUnavailable() {
        Post p0 = post("VISIBLE", 99L, true); when(em.find(Post.class, 10L)).thenReturn(p0);
        assertCode(() -> service.create(submit("POST", 10L, "x"), files, user), ErrorCode.REPORT_TARGET_UNAVAILABLE);
        verify(images, never()).upload(anyString(), any());
    }

    @Test
    void utc008_create_pendingPostOfOtherUser_targetUnavailable() {
        Post p0 = post("PENDING", 99L, false); when(em.find(Post.class, 10L)).thenReturn(p0);
        assertCode(() -> service.create(submit("POST", 10L, "x"), files, user), ErrorCode.REPORT_TARGET_UNAVAILABLE);
    }

    @Test
    void utc009_create_claimByClaimant_created() {
        Claim c = claim(7L);
        when(em.find(Claim.class, 20L)).thenReturn(c);

        ReportService.Detail d = service.create(submit("CLAIM", 20L, "Wrong item"), files, user);

        assertThat(d.targetType()).isEqualTo("CLAIM");
        assertThat(d.kind()).isEqualTo("GENERAL");
    }

    @Test
    void utc017_create_claimWithOfficeHandover_kindHandoverIssue() {
        Claim c0 = claim(7L); when(em.find(Claim.class, 20L)).thenReturn(c0);
        when(jdbc.queryForObject(contains("office_handover"), eq(Boolean.class), any())).thenReturn(true);

        ReportService.Detail d = service.create(submit("CLAIM", 20L, "Wrong item"), files, user);

        assertThat(d.kind()).isEqualTo("HANDOVER_ISSUE");
        verify(jdbc).update(contains("office_handover_issue"), eq(100L), eq(20L));
    }

    @Test
    void utc010_create_claimUnrelatedReporter_targetUnavailable() {
        Claim c = claim(55L);
        when(em.find(Claim.class, 20L)).thenReturn(c);
        when(claimPolicy.reviewer(eq(c), any())).thenReturn(false);
        assertCode(() -> service.create(submit("CLAIM", 20L, "x"), files, user), ErrorCode.REPORT_TARGET_UNAVAILABLE);
    }

    @Test
    void utc011_create_messageInForeignConversation_chatNotFound() {
        ChatMessage m = mock(ChatMessage.class);
        when(m.getConversationId()).thenReturn(5L);
        when(em.find(ChatMessage.class, 30L)).thenReturn(m);
        when(chats.requireMember(5L, 7L)).thenThrow(new AppException(ErrorCode.CHAT_NOT_FOUND));
        assertCode(() -> service.create(submit("MESSAGE", 30L, "x"), files, user), ErrorCode.CHAT_NOT_FOUND);
    }

    @Test
    void utc012_create_reportSelf_targetUnavailable() {
        assertCode(() -> service.create(submit("USER", 7L, "x"), files, user), ErrorCode.REPORT_TARGET_UNAVAILABLE);
    }

    @Test
    void utc013_create_userWithVisiblePost_created() {
        when(em.find(Account.class, 8L)).thenReturn(new Account());
        when(jdbc.queryForObject(contains("from post where account_id"), eq(Long.class), eq(8L))).thenReturn(1L);

        assertThat(service.create(submit("USER", 8L, "spam"), files, user).targetType()).isEqualTo("USER");
    }

    @Test
    void utc014_create_openReportExists_duplicateAndImagesCleaned() {
        Post p0 = post("VISIBLE", 99L, false); when(em.find(Post.class, 10L)).thenReturn(p0);
        when(jdbc.queryForObject(contains("from report where reporter_id"), eq(Long.class), any(), any())).thenReturn(1L);

        assertCode(() -> service.create(submit("POST", 10L, "x"), files, user), ErrorCode.REPORT_DUPLICATE);
        verify(images).cleanup(uploaded);
    }

    @Test
    void utc015_create_sameRequestSameHash_returnsExistingWithoutUpload() {
        Report old = withId(new Report(7L, "GENERAL", "POST", 10L, "x", reqId, "hash-1"), 55L);
        when(reports.findByReporterIdAndClientRequestId(7L, reqId)).thenReturn(Optional.of(old));

        assertThat(service.create(submit("POST", 10L, "x"), files, user).id()).isEqualTo(55L);
        verify(images, never()).upload(anyString(), any());
    }

    @Test
    void utc016_create_sameRequestDifferentPayload_duplicatePayload() {
        Report old = withId(new Report(7L, "GENERAL", "POST", 10L, "x", reqId, "other-hash"), 55L);
        when(reports.findByReporterIdAndClientRequestId(7L, reqId)).thenReturn(Optional.of(old));

        assertCode(() -> service.create(submit("POST", 10L, "y"), files, user), ErrorCode.WORKFLOW_DUPLICATE_PAYLOAD);
    }

    // ---------- mine / mineDetail ----------

    @Test
    void utc018_mine_all_returnsPage() {
        List<Report> list = IntStream.range(0, 12).mapToObj(i -> withId(new Report(7L, "GENERAL", "POST", 10L, "x", UUID.randomUUID(), "h"), i)).toList();
        when(reports.findMine(eq(7L), eq("ALL"), eq("ALL"), any())).thenReturn(new PageImpl<>(list, PageRequest.of(0, 12), 14));

        ReportService.Page p = service.mine(1, "ALL", user);

        assertThat(p.content()).hasSize(12);
        assertThat(p.totalPages()).isEqualTo(2);
        assertThat(p.totalElements()).isEqualTo(14);
    }

    @Test
    void utc019_mine_statusFilter_passedToQuery() {
        when(reports.findMine(eq(7L), eq("OPEN"), eq("ALL"), any())).thenReturn(new PageImpl<>(List.of()));
        service.mine(1, "OPEN", user);
        verify(reports).findMine(7L, "OPEN", "ALL", PageRequest.of(0, 12));
    }

    @Test
    void utc020_mine_pageZero_invalid() {
        assertCode(() -> service.mine(0, "ALL", user), ErrorCode.WORKFLOW_INPUT_INVALID);
    }

    @Test
    void utc021_mine_pageTooLarge_invalid() {
        assertCode(() -> service.mine(1000001, "ALL", user), ErrorCode.WORKFLOW_INPUT_INVALID);
    }

    @Test
    void utc022_mine_unknownStatus_invalid() {
        assertCode(() -> service.mine(1, "CLOSED", user), ErrorCode.WORKFLOW_INPUT_INVALID);
    }

    @Test
    void utc023_mineDetail_ownReport_returnsDetail() {
        when(reports.findById(5L)).thenReturn(Optional.of(withId(new Report(7L, "GENERAL", "POST", 10L, "x", reqId, "h"), 5L)));
        when(jdbc.query(contains("select title from post"), any(RowMapper.class), any(Object[].class))).thenReturn(List.of("Lost wallet"));

        ReportService.Detail d = service.mineDetail(5L, user);

        assertThat(d.id()).isEqualTo(5L);
        assertThat(d.targetLabel()).isEqualTo("Lost wallet");
    }

    @Test
    void utc024_mineDetail_otherUsersReport_notFound() {
        when(reports.findById(5L)).thenReturn(Optional.of(withId(new Report(8L, "GENERAL", "POST", 10L, "x", reqId, "h"), 5L)));
        assertCode(() -> service.mineDetail(5L, user), ErrorCode.REPORT_NOT_FOUND);
    }

    @Test
    void utc025_mineDetail_missing_notFound() {
        when(reports.findById(999L)).thenReturn(Optional.empty());
        assertCode(() -> service.mineDetail(999L, user), ErrorCode.REPORT_NOT_FOUND);
    }

    // ---------- reason codes (added after report-reason feature) ----------

    @Test
    void utc121_create_nullReasonCode_reasonInvalid() {
        assertCode(() -> service.create(new ReportService.Submit("POST", 10L, "x", reqId, null), files, user), ErrorCode.REPORT_REASON_INVALID);
    }

    @Test
    void utc122_create_reasonCode65Chars_reasonInvalid() {
        assertCode(() -> service.create(new ReportService.Submit("POST", 10L, "x", reqId, "R".repeat(65)), files, user), ErrorCode.REPORT_REASON_INVALID);
    }

    @Test
    void utc123_create_ownPost_selfPostNotAllowed() {
        Post p0 = post("VISIBLE", 7L, false); when(em.find(Post.class, 10L)).thenReturn(p0);
        assertCode(() -> service.create(submit("POST", 10L, "x"), files, user), ErrorCode.REPORT_SELF_POST_NOT_ALLOWED);
        verify(images, never()).upload(anyString(), any());
    }

    @Test
    void utc124_create_description4800Chars_accepted() {
        Post p0 = post("VISIBLE", 99L, false); when(em.find(Post.class, 10L)).thenReturn(p0);
        assertThat(service.create(submit("POST", 10L, "a".repeat(4800)), files, user).id()).isEqualTo(100L);
    }

    @Test
    void utc125_create_nullDescription_savesReasonLabelOnly() {
        Post p0 = post("VISIBLE", 99L, false); when(em.find(Post.class, 10L)).thenReturn(p0);

        ReportService.Detail d = service.create(submit("POST", 10L, null), files, user);

        ArgumentCaptor<Report> cap = ArgumentCaptor.forClass(Report.class);
        verify(reports).saveAndFlush(cap.capture());
        assertThat(cap.getValue().getDescription()).isEqualTo(LABEL);
        assertThat(d.description()).isEmpty();
    }

    @Test
    void utc126_create_reasonNotInGroup_reasonInvalid() {
        Post p0 = post("VISIBLE", 99L, false); when(em.find(Post.class, 10L)).thenReturn(p0);
        when(reasons.require("POST", REASON, "x")).thenThrow(new AppException(ErrorCode.REPORT_REASON_INVALID));
        assertCode(() -> service.create(submit("POST", 10L, "x"), files, user), ErrorCode.REPORT_REASON_INVALID);
        verify(images, never()).upload(anyString(), any());
    }

    @Test
    void utc127_mine_unknownType_invalid() {
        assertCode(() -> service.mine(1, "ALL", "COMMENT", user), ErrorCode.WORKFLOW_INPUT_INVALID);
    }

    @Test
    void utc128_mine_typeFilter_passedToQuery() {
        when(reports.findMine(eq(7L), eq("ALL"), eq("HANDOVER_ISSUE"), any())).thenReturn(new PageImpl<>(List.of()));
        service.mine(1, "ALL", "HANDOVER_ISSUE", user);
        verify(reports).findMine(7L, "ALL", "HANDOVER_ISSUE", PageRequest.of(0, 12));
    }

    @Test
    void mine_labelsLinkedAppealWithItsOwnReportType() {
        Report report = withId(new Report(7L, "HANDOVER_ISSUE", "CLAIM", 10L, "x", reqId, "h"), 5L);
        when(reports.findMine(eq(7L), eq("ALL"), eq("CLAIM_APPEAL"), any()))
                .thenReturn(new PageImpl<>(List.of(report)));
        when(jdbc.queryForObject(contains("from p2p_claim_appeal"), eq(Boolean.class), eq(5L))).thenReturn(true);

        var detail = service.mine(1, "ALL", "CLAIM_APPEAL", user).content().getFirst();

        assertThat(detail.reportType()).isEqualTo("CLAIM_APPEAL");
        assertThat(detail.kind()).isEqualTo("HANDOVER_ISSUE");
    }

    @Test
    void utc129_reasonOptions_handoverIssueOnPost_invalid() {
        Post p0 = post("VISIBLE", 99L, false); when(em.find(Post.class, 10L)).thenReturn(p0);
        assertCode(() -> service.reasonOptions("POST", 10L, true, user), ErrorCode.WORKFLOW_INPUT_INVALID);
    }

    @Test
    void utc130_reasonOptions_visiblePost_returnsPostGroup() {
        Post p0 = post("VISIBLE", 99L, false); when(em.find(Post.class, 10L)).thenReturn(p0);
        var options = new ReportReasonService.Options("POST", List.of(new ReportReasonService.Reason(REASON, LABEL, false)));
        when(reasons.options("POST")).thenReturn(options);
        assertThat(service.reasonOptions("POST", 10L, false, user)).isEqualTo(options);
    }

    @Test
    void utc026_detail_noLabelRow_fallsBackToTargetNumber() {
        ReportService.Detail d = service.detail(withId(new Report(7L, "GENERAL", "USER", 8L, "x", reqId, "h"), 1L));
        assertThat(d.targetLabel()).isEqualTo("Đối tượng #8");
    }
}
