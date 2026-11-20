package com.example.base.service.report;

import static com.example.base.service.office.OfficeAccess.bad;
import static com.example.base.service.report.ReportContextService.id;

import com.example.base.entity.*;
import com.example.base.security.CustomUserDetails;
import com.example.base.service.notification.NotificationService;
import com.example.base.service.office.OfficeAccess;
import jakarta.validation.constraints.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ReportInquiryService {

  static final String REPORT_SUBJECT_COLUMNS = """
      case
        when appeal.report_id is not null then 'CLAIM_APPEAL'
        when r.kind = 'HANDOVER_ISSUE' then 'HANDOVER_ISSUE'
        when r.post_id is not null then 'POST'
        when r.claim_id is not null then 'CLAIM'
        when r.message_id is not null then 'MESSAGE'
        when r.reported_account_id is not null then 'USER'
      end as report_type,
      coalesce(nullif(btrim(p.title), ''), nullif(btrim(a.full_name), '')) as report_subject,
      coalesce(r.post_id, c.post_id, conversation.post_id, r.reported_account_id) as report_subject_id
      """;

  private static final String PARTICIPANT_REPORT_COLUMNS = """
      r.report_id, r.status, r.resolution_note, %s,
      case when appeal.report_id is null then r.reason_label_snapshot end as report_reason,
      case when p.account_id = ? then approved.claim_id end as finder_approved_claim_id
      """.formatted(REPORT_SUBJECT_COLUMNS);

  static final String PARTICIPANT_REPORT_JOINS = """
      left join p2p_claim_appeal appeal on appeal.report_id = r.report_id
      left join claim c on c.claim_id = r.claim_id
      left join chat_message m on m.message_id = r.message_id
      left join chat_conversation conversation on conversation.conversation_id = m.conversation_id
      left join post p on p.post_id = coalesce(r.post_id, c.post_id, conversation.post_id)
      left join accounts a on a.id = r.reported_account_id
      left join p2p_claim_approval approval on approval.approval_id = appeal.approval_id and approval.closed_at is null
      left join claim approved on approved.claim_id = approval.approved_claim_id and approved.post_id = p.post_id
          and approved.status = 'APPROVED' and approved.claim_type = 'P2P'
          and p.status = 'RESERVED' and p.custody_mode = 'P2P'
      """;

  private final OfficeAccess access;
  private final ReportReviewService reviews;
  private final ReportContextService context;
  private final JdbcTemplate jdbc;
  private final NotificationService notifications;
  private final WorkflowImages images;
  private final PlatformTransactionManager transactions;

  public record Ask(
    @NotNull @PositiveOrZero Long version,
    @NotNull @Positive Long recipientId,
    @NotBlank @Size(max = 5000) String question
  ) {}

  public record Reply(@NotBlank @Size(max = 5000) String response) {}

  @Transactional
  public Map<String, Object> ask(
    long report,
    Ask request,
    CustomUserDetails user
  ) {
    Account actor = access.require(user, AccountRole.MANAGER);
    var r = reviews.lock(report);
    reviews.processing(r, actor, request.version());
    if (
      !reviews.participants(context.context(r)).contains(request.recipientId())
    ) throw bad("Người được hỏi phải liên quan đến vụ việc.");
    if (
      Boolean.TRUE.equals(
        jdbc.queryForObject(
          "select exists(select 1 from report_inquiry where report_id=? and recipient_id=? and responded_at is null)",
          Boolean.class,
          report,
          request.recipientId()
        )
      )
    ) throw bad("Người này đang có câu hỏi chưa phản hồi.");
    jdbc.update(
      "insert into report_inquiry(report_id,recipient_id,created_by,question) values(?,?,?,?)",
      report,
      request.recipientId(),
      actor.getId(),
      request.question().strip()
    );
    jdbc.update(
      "update report set version=version+1,updated_at=now() where report_id=?",
      report
    );
    reviews.history(
      report,
      actor.getId(),
      "ASK_INFORMATION",
      (String) r.get("status"),
      (String) r.get("status"),
      "Đang đối chiếu thêm thông tin với người liên quan.",
      request.question()
    );
    notifications.notifyUser(
      request.recipientId(),
      "REPORT_INFORMATION",
      "Cần bổ sung thông tin cho báo cáo #" + report,
      request.question(),
      "/bao-cao/" + report + "#phan-hoi",
      false
    );
    return reviews.detail(report, user);
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> mine(CustomUserDetails user) {
    long actor = access.require(user).getId();
    var rows = jdbc.queryForList("""
        select i.inquiry_id, i.question, i.created_at, i.response, i.responded_at, %s
        from report_inquiry i join report r using (report_id)
        %s
        where i.recipient_id = ?
        order by i.created_at desc
        limit 100
        """.formatted(PARTICIPANT_REPORT_COLUMNS, PARTICIPANT_REPORT_JOINS), actor, actor);
    attachEvidence(rows);
    return rows;
  }

  List<Map<String, Object>> forReport(long reportId, long recipientId) {
    var rows = jdbc.queryForList("""
        select i.inquiry_id, i.question, i.created_at, i.response, i.responded_at, r.status
        from report_inquiry i join report r using (report_id)
        where i.report_id = ? and i.recipient_id = ?
        order by i.created_at desc, i.inquiry_id desc
        """, reportId, recipientId);
    attachEvidence(rows);
    return rows;
  }

  private void attachEvidence(List<Map<String, Object>> rows) {
    rows.forEach(row -> row.put("evidence", context.withUrls(jdbc.queryForList(
        "select evidence_id,storage_key,original_name from report_reply_evidence where inquiry_id=?",
        row.get("inquiry_id")
    ))));
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> results(CustomUserDetails user) {
    long actor = access.require(user).getId();
    return jdbc.queryForList("""
        select r.resolved_at, %s
        from report r join report_recipient x using (report_id)
        %s
        where x.account_id = ?
        order by r.resolved_at desc
        limit 100
        """.formatted(PARTICIPANT_REPORT_COLUMNS, PARTICIPANT_REPORT_JOINS), actor, actor);
  }

  public Map<String, Object> reply(
    long inquiry,
    Reply request,
    List<MultipartFile> files,
    CustomUserDetails user
  ) {
    long actor = access.require(user).getId();
    var initial = context.one(
      "select * from report_inquiry where inquiry_id=? and recipient_id=?",
      inquiry,
      actor
    );
    if (initial.isEmpty()) throw bad(
      "Không tìm thấy yêu cầu bổ sung dành cho bạn."
    );
    var prepared = images.prepare(request.response().strip(), files);
    var uploaded = images.upload("report-replies/" + inquiry, prepared);
    try {
      return new TransactionTemplate(transactions).execute(tx -> {
        access.require(user);
        var r = reviews.lock(id(initial, "report_id"));
        var i = context.one(
          "select * from report_inquiry where inquiry_id=? for update",
          inquiry
        );
        if (!reviews.open(r)) throw bad("Báo cáo đã kết thúc.");
        if (i.get("responded_at") != null) throw bad(
          "Phản hồi đã được ghi nhận và giữ nguyên. Hãy tải lại."
        );
        jdbc.update(
          "update report_inquiry set response=?,responded_at=now() where inquiry_id=?",
          request.response().strip(),
          inquiry
        );
        for (var image : uploaded)
          jdbc.update(
            "insert into report_reply_evidence(inquiry_id,storage_key,original_name,content_hash) values(?,?,?,?)",
            inquiry,
            image.key(),
            image.name(),
            image.hash()
          );
        jdbc.update(
          "update report set version=version+1,updated_at=now() where report_id=?",
          r.get("report_id")
        );
        reviews.history(
          id(r, "report_id"),
          actor,
          "REPLY",
          (String) r.get("status"),
          (String) r.get("status"),
          "Đã nhận thêm thông tin đối chiếu.",
          null
        );
        if (r.get("assigned_to") != null) notifications.notifyUser(
          id(r, "assigned_to"),
          "REPORT_REPLY",
          "Có phản hồi cho báo cáo #" + r.get("report_id"),
          "Mở hồ sơ để xem thông tin bổ sung.",
          "/admin/reports?id=" + r.get("report_id"),
          false
        );
        return Map.of("saved", true);
      });
    } catch (RuntimeException failure) {
      images.cleanup(uploaded);
      throw failure;
    }
  }
}
