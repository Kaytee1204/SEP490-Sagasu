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
      "/bao-cao-trao-doi",
      false
    );
    return reviews.detail(report, user);
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> mine(CustomUserDetails user) {
    long actor = access.require(user).getId();
    var rows = jdbc.queryForList(
      "select i.inquiry_id,i.report_id,i.question,i.created_at,i.response,i.responded_at,r.status,r.resolution_note from report_inquiry i join report r using(report_id) where i.recipient_id=? order by i.created_at desc limit 100",
      actor
    );
    rows.forEach(r ->
      r.put(
        "evidence",
        context.withUrls(
          jdbc.queryForList(
            "select evidence_id,storage_key,original_name from report_reply_evidence where inquiry_id=?",
            r.get("inquiry_id")
          )
        )
      )
    );
    return rows;
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> results(CustomUserDetails user) {
    long actor = access.require(user).getId();
    return jdbc.queryForList(
      "select r.report_id,r.status,r.resolution_note,r.resolved_at from report r join report_recipient x using(report_id) where x.account_id=? order by r.resolved_at desc limit 100",
      actor
    );
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
