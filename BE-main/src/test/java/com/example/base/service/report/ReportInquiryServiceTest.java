package com.example.base.service.report;

import com.example.base.entity.Account;
import com.example.base.entity.AccountRole;
import com.example.base.entity.AccountStatus;
import com.example.base.exception.AppException;
import com.example.base.exception.ErrorCode;
import com.example.base.security.CustomUserDetails;
import com.example.base.service.notification.NotificationService;
import com.example.base.service.office.OfficeAccess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

import static com.example.base.service.report.SqlStub.row;
import static com.example.base.service.report.SqlStub.rows;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReportInquiryServiceTest {

    @Mock OfficeAccess access;
    @Mock ReportReviewService reviews;
    @Mock ReportContextService context;
    @Mock JdbcTemplate jdbc;
    @Mock NotificationService notifications;
    @Mock WorkflowImages images;
    @Mock PlatformTransactionManager transactions;
    @InjectMocks ReportInquiryService service;

    Account manager;
    Account claimant;
    CustomUserDetails managerUser;
    CustomUserDetails claimantUser;
    Map<String, Object> report;
    List<MultipartFile> files = List.of();
    WorkflowImages.Prepared prepared = new WorkflowImages.Prepared(List.of(), List.of(), "h");
    List<WorkflowImages.Image> uploaded = List.of(new WorkflowImages.Image("report-replies/2/a.jpg", "a.jpg", "image/jpeg", 1, "ih", 0));

    @BeforeEach
    void setUp() {
        manager = account(1L, AccountRole.MANAGER);
        claimant = account(7L, AccountRole.USER);
        managerUser = new CustomUserDetails(manager);
        claimantUser = new CustomUserDetails(claimant);
        when(access.require(eq(managerUser), any(AccountRole[].class))).thenReturn(manager);
        when(access.require(eq(claimantUser), any(AccountRole[].class))).thenReturn(claimant);
        report = row("report_id", 5L, "status", "IN_REVIEW", "assigned_to", 1L);
        when(reviews.lock(5L)).thenReturn(report);
        when(reviews.open(report)).thenReturn(true);
        when(reviews.participants(any())).thenReturn(Set.of(7L, 8L));
        when(reviews.detail(eq(5L), any())).thenReturn(Map.of("ok", true));
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(images.prepare(anyString(), any())).thenReturn(prepared);
        when(images.upload(anyString(), any())).thenReturn(uploaded);
        when(context.one(contains("recipient_id=?"), any(Object[].class))).thenReturn(row("inquiry_id", 2L, "report_id", 5L));
        when(context.one(contains("for update"), any(Object[].class))).thenReturn(row("inquiry_id", 2L, "responded_at", null));
        SqlStub.defaults(jdbc);
    }

    private static Account account(long id, AccountRole role) {
        Account a = new Account();
        a.setId(id);
        a.setRole(role);
        a.setStatus(AccountStatus.ACTIVE);
        return a;
    }

    private void assertBad(Runnable r, String part) {
        assertThatThrownBy(r::run).isInstanceOf(AppException.class).hasMessageContaining(part)
                .extracting("errorCode").isEqualTo(ErrorCode.BAD_REQUEST);
    }

    // ---------- ask ----------

    @Test
    void utc100_ask_participant_insertsInquiryAndNotifies() {
        Map<String, Object> res = service.ask(5L, new ReportInquiryService.Ask(3L, 7L, "  Where did you lose it? "), managerUser);

        verify(reviews).processing(report, manager, 3L);
        verify(jdbc).update(contains("insert into report_inquiry"), eq(5L), eq(7L), eq(1L), eq("Where did you lose it?"));
        verify(jdbc).update(contains("update report set version=version+1"), eq(5L));
        verify(reviews).history(eq(5L), eq(1L), eq("ASK_INFORMATION"), eq("IN_REVIEW"), eq("IN_REVIEW"), anyString(), anyString());
        verify(notifications).notifyUser(eq(7L), eq("REPORT_INFORMATION"), anyString(), anyString(), eq("/bao-cao-trao-doi"), eq(false));
        assertThat(res).containsEntry("ok", true);
    }

    @Test
    void utc101_ask_nonParticipant_rejected() {
        assertBad(() -> service.ask(5L, new ReportInquiryService.Ask(3L, 99L, "Q"), managerUser), "liên quan");
        verify(jdbc, never()).update(contains("insert into report_inquiry"), any(Object[].class));
    }

    @Test
    void utc102_ask_pendingInquiryExists_rejected() {
        when(jdbc.queryForObject(contains("from report_inquiry"), eq(Boolean.class), any(Object[].class))).thenReturn(true);
        assertBad(() -> service.ask(5L, new ReportInquiryService.Ask(3L, 7L, "Q"), managerUser), "chưa phản hồi");
    }

    @Test
    void utc103_ask_processingGuardFails_propagates() {
        doThrow(OfficeAccess.bad("Cần nhận xử lý báo cáo trước khi thực hiện thao tác."))
                .when(reviews).processing(any(), any(), anyLong());
        assertBad(() -> service.ask(5L, new ReportInquiryService.Ask(3L, 7L, "Q"), managerUser), "Cần nhận xử lý");
    }

    // ---------- mine / results ----------

    @Test
    void utc104_mine_returnsInquiriesWithEvidence() {
        rows(jdbc, "from report_inquiry i join report r", row("inquiry_id", 2L, "question", "Q"));
        when(context.withUrls(any())).thenReturn(List.of(Map.of("url", "u")));

        List<Map<String, Object>> list = service.mine(claimantUser);

        assertThat(list).hasSize(1);
        assertThat(list.get(0)).containsEntry("evidence", List.of(Map.of("url", "u")));
    }

    @Test
    void utc105_results_returnsResolvedReportsForRecipient() {
        rows(jdbc, "from report r join report_recipient", row("report_id", 5L, "status", "RESOLVED"));
        assertThat(service.results(claimantUser)).hasSize(1);
        verify(jdbc).queryForList(contains("report_recipient"), eq(7L), eq(7L));
    }

    // ---------- reply ----------

    @Test
    void utc106_reply_open_savesResponseEvidenceAndNotifiesAssignee() {
        Map<String, Object> res = service.reply(2L, new ReportInquiryService.Reply(" It was at gate B "), files, claimantUser);

        assertThat(res).containsEntry("saved", true);
        verify(jdbc).update(contains("update report_inquiry set response=?"), eq("It was at gate B"), eq(2L));
        verify(jdbc).update(contains("insert into report_reply_evidence"), eq(2L), eq("report-replies/2/a.jpg"), eq("a.jpg"), eq("ih"));
        verify(reviews).history(eq(5L), eq(7L), eq("REPLY"), any(), any(), anyString(), isNull());
        verify(notifications).notifyUser(eq(1L), eq("REPORT_REPLY"), anyString(), anyString(), eq("/admin/reports?id=5"), eq(false));
        verify(images, never()).cleanup(any());
    }

    @Test
    void utc107_reply_unassignedReport_noNotification() {
        report.put("assigned_to", null);
        service.reply(2L, new ReportInquiryService.Reply("ok"), files, claimantUser);
        verifyNoInteractions(notifications);
    }

    @Test
    void utc108_reply_inquiryNotForUser_rejectedBeforeUpload() {
        when(context.one(contains("recipient_id=?"), any(Object[].class))).thenReturn(new LinkedHashMap<>());
        assertBad(() -> service.reply(2L, new ReportInquiryService.Reply("ok"), files, claimantUser), "Không tìm thấy");
        verify(images, never()).upload(anyString(), any());
    }

    @Test
    void utc109_reply_reportClosed_rejectedAndImagesCleaned() {
        when(reviews.open(report)).thenReturn(false);
        assertBad(() -> service.reply(2L, new ReportInquiryService.Reply("ok"), files, claimantUser), "đã kết thúc");
        verify(images).cleanup(uploaded);
    }

    @Test
    void utc110_reply_alreadyAnswered_rejectedAndImagesCleaned() {
        when(context.one(contains("for update"), any(Object[].class))).thenReturn(row("inquiry_id", 2L, "responded_at", "2026-11-10"));
        assertBad(() -> service.reply(2L, new ReportInquiryService.Reply("ok"), files, claimantUser), "đã được ghi nhận");
        verify(images).cleanup(uploaded);
    }
}
