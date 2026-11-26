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

import java.time.OffsetDateTime;
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
        when(jdbc.update(contains("update report_inquiry set response=?"), anyString(), eq(2L))).thenReturn(1);
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
        var deadline = OffsetDateTime.parse("2026-11-24T09:00:00Z");
        when(reviews.inquiryResponseDue()).thenReturn(deadline);
        Map<String, Object> res = service.ask(5L, new ReportInquiryService.Ask(3L, 7L, "  Where did you lose it? ", null), managerUser);

        verify(reviews).processing(report, manager, 3L);
        verify(jdbc).update(contains("response_due_at"), eq(5L), eq(7L), eq(1L), eq("Where did you lose it?"), eq(deadline), isNull());
        verify(jdbc).update(contains("update report set version=version+1"), eq(5L));
        verify(reviews).history(eq(5L), eq(1L), eq("ASK_INFORMATION"), eq("IN_REVIEW"), eq("IN_REVIEW"), anyString(), anyString());
        verify(notifications).notifyUser(eq(7L), eq("REPORT_INFORMATION"), anyString(), anyString(), eq("/bao-cao/5#phan-hoi"), eq(false));
        assertThat(res).containsEntry("ok", true);
    }

    @Test
    void utc101_ask_nonParticipant_rejected() {
        assertBad(() -> service.ask(5L, new ReportInquiryService.Ask(3L, 99L, "Q", null), managerUser), "liên quan");
        verify(jdbc, never()).update(contains("insert into report_inquiry"), any(Object[].class));
    }

    @Test
    void utc102_ask_pendingInquiryExists_rejected() {
        when(jdbc.queryForObject(contains("from report_inquiry"), eq(Boolean.class), any(Object[].class))).thenReturn(true);
        assertBad(() -> service.ask(5L, new ReportInquiryService.Ask(3L, 7L, "Q", null), managerUser), "chưa phản hồi");
    }

    @Test
    void utc103_ask_processingGuardFails_propagates() {
        doThrow(OfficeAccess.bad("Cần nhận xử lý báo cáo trước khi thực hiện thao tác."))
                .when(reviews).processing(any(), any(), anyLong());
        assertBad(() -> service.ask(5L, new ReportInquiryService.Ask(3L, 7L, "Q", null), managerUser), "Cần nhận xử lý");
    }

    @Test
    void missingAccessoriesQuestionsGoToTheCorrectHandoverParty() {
        report.put("kind", "HANDOVER_ISSUE");
        report.put("reason_code", "HANDOVER_MISSING_ITEMS");
        when(reviews.issuePhase(5L)).thenReturn("DURING");
        when(context.context(report)).thenReturn(row("post", row("account_id", 8L), "claim", row("claimant_id", 7L)));
        service.ask(5L, new ReportInquiryService.Ask(3L, 7L, "Còn thiếu gì?", "ISSUE_RECEIVER"), managerUser);
        assertBad(() -> service.ask(5L, new ReportInquiryService.Ask(3L, 8L, "Còn thiếu gì?", "ISSUE_RECEIVER"), managerUser), "phù hợp");
    }

    @Test
    void p2pVerificationUsesOnlyTheParticipantForTheSelectedPurpose() {
        report.put("reporter_id", 7L);
        when(reviews.participants(any())).thenReturn(Set.of(7L, 8L, 9L));
        var deadline = OffsetDateTime.parse("2026-11-24T09:00:00Z");
        when(reviews.inquiryResponseDue()).thenReturn(deadline);
        var appeal = row("claim_type", "P2P", "active", true, "can_reopen_approval", true,
                "finder_id", 9L, "approved_recipient_id", 8L);
        when(reviews.appeal(5L)).thenReturn(appeal);
        service.ask(5L, new ReportInquiryService.Ask(3L, 7L, "Ảnh nào cho thấy dấu riêng?", "OWNERSHIP"), managerUser);
        service.ask(5L, new ReportInquiryService.Ask(3L, 8L, "Vết xước nằm ở đâu?", "DISCREPANCY"), managerUser);
        verify(notifications).notifyUserWithEmail(eq(7L), eq("REPORT_INFORMATION"), anyString(),
                eq("Ảnh nào cho thấy dấu riêng?"), eq("/bao-cao/5#phan-hoi"),
                eq("Quản lý cần bạn xác minh thông tin. Vui lòng mở hồ sơ và phản hồi trước 16:00 24/11/2026 (giờ Việt Nam)."));
        assertBad(() -> service.ask(5L, new ReportInquiryService.Ask(3L, 9L, null, "CUSTODY"), managerUser), "chọn");
        assertBad(() -> service.ask(5L, new ReportInquiryService.Ask(3L, 8L, "?", "OWNERSHIP"), managerUser), "vai trò");
        assertBad(() -> service.ask(5L, new ReportInquiryService.Ask(3L, 7L, "?", null), managerUser), "chọn");
    }

    @Test
    void askBothSendsSeparateQuestionsWithTheSameDeadlineAndUpdatesReportOnce() {
        report.put("reporter_id", 7L);
        when(reviews.appeal(5L)).thenReturn(row("claim_type", "P2P", "active", true, "approved_recipient_id", 8L));
        var deadline = OffsetDateTime.parse("2026-11-24T09:00:00Z");
        when(reviews.inquiryResponseDue()).thenReturn(deadline);
        var request = new ReportInquiryService.AskBoth(3L, List.of(
                new ReportInquiryService.Question(7L, "Bạn có ảnh chứng minh sở hữu không?", "OWNERSHIP"),
                new ReportInquiryService.Question(8L, "Vết xước nằm ở đâu?", "DISCREPANCY")));

        assertThat(service.askBoth(5L, request, managerUser)).containsEntry("ok", true);

        verify(reviews).processing(report, manager, 3L);
        verify(jdbc).update(contains("insert into report_inquiry"), eq(5L), eq(7L), eq(1L),
                eq("Bạn có ảnh chứng minh sở hữu không?"), eq(deadline), eq("OWNERSHIP"));
        verify(jdbc).update(contains("insert into report_inquiry"), eq(5L), eq(8L), eq(1L),
                eq("Vết xước nằm ở đâu?"), eq(deadline), eq("DISCREPANCY"));
        verify(jdbc).update(contains("update report set version=version+1"), eq(5L));
        verify(notifications, times(2)).notifyUserWithEmail(anyLong(), eq("REPORT_INFORMATION"),
                anyString(), anyString(), eq("/bao-cao/5#phan-hoi"), anyString());
    }

    @Test
    void askBothDoesNotCreateEitherQuestionWhenOneTargetIsInvalid() {
        report.put("reporter_id", 7L);
        when(reviews.appeal(5L)).thenReturn(row("claim_type", "P2P", "active", true, "approved_recipient_id", 8L));
        var request = new ReportInquiryService.AskBoth(3L, List.of(
                new ReportInquiryService.Question(7L, "Bạn có ảnh chứng minh sở hữu không?", "OWNERSHIP"),
                new ReportInquiryService.Question(9L, "Vết xước nằm ở đâu?", "DISCREPANCY")));

        assertBad(() -> service.askBoth(5L, request, managerUser), "vai trò");

        verify(jdbc, never()).update(contains("insert into report_inquiry"), any(Object[].class));
        verifyNoInteractions(notifications);
    }

    @Test
    void custodyOverrideRequiresAnOverdueUnansweredFinderInquiry() {
        when(reviews.appeal(5L)).thenReturn(row("claim_type","P2P","active",true,"finder_id",9L));
        var check = new ReportInquiryService.CustodyCheck(3L,"HELD","Tại quầy","Đối chiếu máy và số sê-ri");
        assertBad(() -> service.verifyOverdueCustody(5L,2L,check,managerUser),"hết hạn");
        when(jdbc.update(contains("response_due_at<=now()"), any(Object[].class))).thenReturn(1);
        service.verifyOverdueCustody(5L,2L,check,managerUser);
        verify(reviews).history(eq(5L),eq(1L),eq("CUSTODY_CHECK"),eq("IN_REVIEW"),eq("IN_REVIEW"),
                contains("vẫn giữ đồ"),contains("sê-ri"));
    }

    @Test
    void finderMustChooseACustodyAnswerBeforeUploadingEvidence() {
        when(context.one(contains("recipient_id=?"), any(Object[].class)))
                .thenReturn(row("inquiry_id", 2L, "verification_kind", "CUSTODY"));
        assertBad(() -> service.reply(2L, new ReportInquiryService.Reply("Không rõ"), files, claimantUser), "giữ đồ");
        verifyNoInteractions(images);
    }

    @Test
    void reminderNotifiesOnlyRecipientsClaimedByTheDatabaseUpdate() {
        when(jdbc.queryForList(contains("set reminded_at = now()")))
                .thenReturn(List.of(row("report_id", 5L, "recipient_id", 7L)))
                .thenReturn(List.of());

        service.remindBeforeDeadline();
        service.remindBeforeDeadline();

        verify(notifications).notifyUser(7L, "REPORT_INFORMATION", "Sắp đến hạn bổ sung báo cáo #5",
                "Mở báo cáo để xem yêu cầu và gửi thông tin trước hạn.", "/bao-cao/5#phan-hoi", false);
        verifyNoMoreInteractions(notifications);
    }

    @Test
    void emailsP2pReminderWithExactDeadline() {
        when(jdbc.queryForList(contains("set reminded_at = now()")))
                .thenReturn(List.of(row("report_id", 5L, "recipient_id", 7L,
                        "email_enabled", true, "local_deadline", "16:00 24/11/2026")));

        service.remindBeforeDeadline();

        verify(notifications).notifyUser(7L, "REPORT_INFORMATION", "Sắp đến hạn bổ sung báo cáo #5",
                "Bạn có một yêu cầu chưa phản hồi. Vui lòng mở hồ sơ và phản hồi trước 16:00 24/11/2026 (giờ Việt Nam).",
                "/bao-cao/5#phan-hoi", true);
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
        verify(notifications).notifyUser(eq(1L), eq("REPORT_REPLY"), anyString(), anyString(), eq("/admin/reports/5#trao-doi"), eq(false));
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
    void reply_afterDeadlineIsRejectedBeforeUploads() {
        when(context.one(contains("recipient_id=?"), any(Object[].class)))
                .thenReturn(row("inquiry_id", 2L, "report_id", 5L, "overdue", true));
        assertBad(() -> service.reply(2L, new ReportInquiryService.Reply("Late reply"), files, claimantUser), "quá hạn");
        verify(images, never()).upload(anyString(), any());
    }

    @Test
    void reply_expiringAfterUploadIsRejectedAndImagesAreCleaned() {
        when(jdbc.update(contains("statement_timestamp() <"), anyString(), eq(2L))).thenReturn(0);
        assertBad(() -> service.reply(2L, new ReportInquiryService.Reply("Late reply"), files, claimantUser), "quá hạn");
        verify(images).cleanup(uploaded);
        verifyNoInteractions(notifications);
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
