package com.example.base.service.report;

import static com.example.base.service.office.OfficeAccess.bad;
import static com.example.base.service.report.ReportContextService.id;

import com.example.base.entity.*;
import com.example.base.security.CustomUserDetails;
import com.example.base.service.office.OfficeAccess;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReportChatService {

  private final OfficeAccess access;
  private final ReportReviewService reviews;
  private final ReportContextService context;
  private final JdbcTemplate jdbc;

  @Transactional
  public Map<String, Object> read(
    long reportId,
    Long before,
    CustomUserDetails user
  ) {
    Account actor = access.require(user, AccountRole.MANAGER);
    var r = reviews.report(reportId);
    Long conversation = null;
    if (r.get("message_id") != null) conversation = id(
      context.one(
        "select conversation_id from chat_message where message_id=?",
        r.get("message_id")
      ),
      "conversation_id"
    );
    else if (r.get("claim_id") != null) conversation = id(
      context.one(
        "select x.conversation_id from chat_conversation x join claim c on c.post_id=x.post_id and c.claimant_id=x.claimant_id where c.claim_id=?",
        r.get("claim_id")
      ),
      "conversation_id"
    );
    // A report on a user alone does not authorize browsing all of their conversations.
    if (conversation == null) return Map.of(
      "content",
      List.of(),
      "hasMore",
      false
    );
    if (reviews.participantsFor(r).contains(actor.getId())) throw bad(
      "Cần quản lý khác xem xét vụ việc có liên quan đến bạn."
    );
    long boundary = before == null ? Long.MAX_VALUE : before;
    if (boundary <= 0) throw bad("Vị trí tin nhắn không hợp lệ.");
    var rows = jdbc.queryForList(
      "select m.message_id,m.sender_id,a.full_name as sender,m.sequence_no,m.message_type,m.body,m.created_at,m.location_label from chat_message m join accounts a on a.id=m.sender_id where m.conversation_id=? and m.sequence_no<? order by m.sequence_no desc limit 51",
      conversation,
      boundary
    );
    boolean more = rows.size() > 50;
    if (more) rows = new ArrayList<>(rows.subList(0, 50));
    Collections.reverse(rows);
    for (var row : rows) {
      var attachments = jdbc.queryForList(
        "select storage_key,original_name from chat_attachment where message_id=? order by sort_order",
        row.get("message_id")
      );
      context.retainAssets(reportId, attachments, "CHAT_REVIEW");
      row.put("attachments", context.withUrls(attachments));
    }
    reviews.history(
      reportId,
      actor.getId(),
      "VIEW_CHAT",
      (String) r.get("status"),
      (String) r.get("status"),
      null,
      "Đọc ngữ cảnh hội thoại #" + conversation + ", trước tin " + boundary
    );
    return Map.of(
      "content",
      rows,
      "hasMore",
      more,
      "before",
      rows.isEmpty() ? 0 : rows.getFirst().get("sequence_no")
    );
  }
}
