package com.example.base.service.report;

import static com.example.base.service.office.OfficeAccess.bad;
import static com.example.base.service.report.ReportContextService.id;

import com.example.base.dto.account.UpdateAccountRequest;
import com.example.base.entity.*;
import com.example.base.repository.*;
import com.example.base.security.CustomUserDetails;
import com.example.base.service.account.AccountService;
import com.example.base.service.chat.ChatConversationService;
import com.example.base.service.notification.NotificationService;
import com.example.base.service.office.*;
import jakarta.validation.constraints.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReportReviewService {

  private final OfficeAccess access;
  private final JdbcTemplate jdbc;
  private final ReportContextService context;
  private final AccountService accounts;
  private final AccountRepository accountRepository;
  private final OfficeManagementService moderation;
  private final PostRepository posts;
  private final NotificationService notifications;
  private final ChatConversationService chat;

  public record Action(
    @NotNull @PositiveOrZero Long version,
    @NotBlank String action,
    @Size(max = 5000) String conclusion,
    @Size(max = 5000) String basis,
    Long targetId,
    boolean custodyVerified
  ) {}

  @Transactional(readOnly = true)
  public Map<String, Object> list(
    int page,
    String status,
    String kind,
    String target,
    String q,
    CustomUserDetails user
  ) {
    return list(page,status,kind,target,q,"ALL",null,user);
  }

  @Transactional(readOnly = true)
  public Map<String,Object> list(int page, String status, String kind, String target, String q,
      String queue, Long targetId, CustomUserDetails user) {
    Account actor = access.require(user, AccountRole.MANAGER);
    if (!Set.of("ALL","UNASSIGNED","MINE","WAITING","REPLIED").contains(queue)) throw bad("Hàng đợi không hợp lệ.");
    if (
      page < 1 ||
      page > 100000 ||
      !Set.of("ALL", "OPEN", "IN_REVIEW", "RESOLVED", "REJECTED").contains(
        status
      ) ||
      !Set.of("ALL", "GENERAL", "HANDOVER_ISSUE").contains(kind) ||
      !Set.of("ALL", "POST", "CLAIM", "MESSAGE", "USER").contains(target) ||
      q.length() > 200
    ) throw bad("Bộ lọc không hợp lệ.");
    String where = "\n" + """
      from report r
      join accounts a on a.id=r.reporter_id
      left join accounts m on m.id=r.assigned_to
      left join claim c on c.claim_id=r.claim_id
      left join accounts claimant on claimant.id=c.claimant_id
      left join post p on p.post_id=coalesce(r.post_id,c.post_id)
      left join chat_message msg on msg.message_id=r.message_id
      left join accounts subject on subject.id=coalesce(r.reported_account_id,msg.sender_id,p.created_by)
      where (?='ALL' or r.status=?) and (?='ALL' or r.kind=?)
      and (?='ALL' or case when r.post_id is not null then 'POST' when r.claim_id is not null
        then 'CLAIM' when r.message_id is not null then 'MESSAGE' else 'USER' end=?)
      and (r.description ilike ? or a.full_name ilike ? or cast(r.report_id as text)=?
        or p.title ilike ? or subject.full_name ilike ? or r.reason_label_snapshot ilike ?)
      """;
    String pattern =
      "%" +
      q.strip().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") +
      "%";
    Object[] args = {
      status,
      status,
      kind,
      kind,
      target,
      target,
      pattern,
      pattern,
      q.strip(),
      pattern,
      pattern,
      pattern,
    };
    if (targetId != null) {
      if (targetId <= 0 || "ALL".equals(target)) throw bad("Chọn loại đối tượng hợp lệ.");
      String column = switch(target) { case "POST" -> "post_id"; case "CLAIM" -> "claim_id"; case "MESSAGE" -> "message_id"; default -> "reported_account_id"; };
      where += " and r." + column + "=" + targetId;
    }
    if (!"ALL".equals(queue)) where += " and r.status in ('OPEN','IN_REVIEW')";
    where += switch(queue) {
      case "UNASSIGNED" -> " and r.assigned_to is null";
      case "MINE" -> " and r.assigned_to=" + actor.getId();
      case "WAITING" -> " and exists(select 1 from report_inquiry i where i.report_id=r.report_id and i.responded_at is null)";
      case "REPLIED" -> " and exists(select 1 from report_inquiry i where i.report_id=r.report_id and i.responded_at > coalesce((select max(d.created_at) from report_decision d where d.report_id=r.report_id and d.action not in ('PRIORITY_HIGH','PRIORITY_NORMAL','PRIORITY_AUTO')),r.created_at))";
      default -> "";
    };
    String objectKey = "case when r.post_id is not null then 'POST:'||r.post_id when r.claim_id is not null then 'CLAIM:'||r.claim_id when r.message_id is not null then 'MESSAGE:'||r.message_id else 'USER:'||r.reported_account_id end";
    var totals = jdbc.queryForMap("select count(*) as reports, count(distinct " + objectKey + ") as groups" + where,args);
    long reportCount = ((Number) totals.get("reports")).longValue();
    long count = targetId == null ? ((Number) totals.get("groups")).longValue() : reportCount;
    var params = new ArrayList<>(Arrays.asList(args));
    params.add((page - 1) * 12);
    String priorityReason = "case when r.status not in ('OPEN','IN_REVIEW') then null when r.kind='HANDOVER_ISSUE' and exists(select 1 from claim c join appointment ap on ap.claim_id=c.claim_id where (c.claim_id=r.claim_id or c.post_id=r.post_id) and c.status='APPROVED' and ap.status='SCHEDULED' and ap.scheduled_at between now() and now()+interval '24 hours') then 'OFFICE_SOON' when r.kind='HANDOVER_ISSUE' and exists(select 1 from claim c join p2p_handover h on h.claim_id=c.claim_id where (c.claim_id=r.claim_id or c.post_id=r.post_id) and h.status in ('WAITING_CONFIRMATION','DISPUTED')) then 'P2P_ACTIVE' when r.kind='HANDOVER_ISSUE' then 'HANDOVER' else null end";
    String projection = """
      select r.report_id,r.kind,r.post_id,r.claim_id,r.message_id,r.reported_account_id,
        r.description,r.reason_code,r.reason_label_snapshot,r.status,r.created_at,
        a.full_name as reporter,m.full_name as assignee,
        coalesce(nullif(p.title,''),case when r.reported_account_id is not null then nullif(subject.full_name,'') end) as subject_title,
        nullif(subject.full_name,'') as subject_name,
        left(msg.body,240) as message_preview, msg.message_type,
        coalesce(nullif(c.claimant_name,''), claimant.full_name) as claimant_name, c.status as claim_status,
        p.post_id as related_post_id, p.moderation_status,
        coalesce(nullif(r.reason_label_snapshot,''),case when r.kind='HANDOVER_ISSUE'
          then 'Vướng mắc trao trả' else 'Báo cáo vi phạm' end) as reason_label,
      """ + priorityReason + " as priority_reason," + objectKey + " as report_key" + where;
    String ranked = "select reports.*,case when status not in ('OPEN','IN_REVIEW') then 3 when priority_reason='HANDOVER' then 1 when priority_reason is not null then 0 else 2 end as priority_rank from (" + projection + ") reports";
    String grouped = "select ranked.*, count(*) over(partition by report_key) as object_count,"
      + " count(*) filter (where status in ('OPEN','IN_REVIEW')) over(partition by report_key) as open_count,"
      + " row_number() over(partition by report_key order by priority_rank,created_at,report_id) as object_row from (" + ranked + ") ranked";
    var rows = jdbc.queryForList(
      "with queue_rows as (" + grouped + ") select queue_rows.*, "
        + "(select jsonb_object_agg(reason_label, amount)::text from "
        + "(select reason_label,count(*) as amount from queue_rows sibling where sibling.report_key=queue_rows.report_key group by reason_label) reasons) as reason_summary "
        + "from queue_rows " + (targetId == null ? "where object_row=1 " : "")
        + "order by priority_rank,created_at,report_id limit 12 offset ?",
      params.toArray()
    );
    rows.forEach(row -> row.put("reason_summary", context.parse((String) row.get("reason_summary"))));
    return Map.of(
      "content",
      rows,
      "page",
      page,
      "totalElements",
      count,
      "totalReports",
      reportCount,
      "totalPages",
      Math.max(1, (count + 11) / 12)
    );
  }

  @Transactional
  public Map<String, Object> detail(long reportId, CustomUserDetails user) {
    Account actor = access.require(user, AccountRole.MANAGER);
    var r = report(reportId);
    context.capture(reportId, "FIRST_REVIEW");
    var current = context.context(r);
    boolean conflict = participantsFor(r).contains(actor.getId());
    var out = new LinkedHashMap<String, Object>();
    out.put("report", r);
    out.put("current", context.withUrls(current));
    out.put("snapshot", context.snapshot(reportId));
    out.put("canTake", !conflict && r.get("assigned_to") == null && open(r));
    out.put(
      "canProcess",
      !conflict &&
        Objects.equals(id(r, "assigned_to"), actor.getId()) &&
        open(r)
    );
    out.put("conflict", conflict);
    out.put(
      "evidence",
      context.withUrls(
        jdbc.queryForList(
          "select evidence_id,storage_key,original_name from report_evidence where report_id=? order by sort_order",
          reportId
        )
      )
    );
    out.put(
      "history",
      jdbc.queryForList(
        "select x.*,a.full_name as actor from report_activity x join accounts a on a.id=x.actor_id where report_id=? order by activity_id",
        reportId
      )
    );
    out.put(
      "decisions",
      jdbc.queryForList(
        "select d.decision_id,d.action,d.conclusion,d.basis,d.created_at,a.full_name as actor from report_decision d join accounts a on a.id=d.actor_id where report_id=? order by decision_id",
        reportId
      )
    );
    out.put("inquiries", inquiries(reportId));
    out.put(
      "hold",
      context.one(
        "select * from report_hold where report_id=? and released_at is null",
        reportId
      )
    );
    out.put("p2pDispute", activeP2pDispute(reportId));
    out.put(
      "related",
      jdbc.queryForList(
        "select x.report_id,x.kind,x.status,x.version,x.assigned_to,x.reason_code,x.reason_label_snapshot,x.description,x.post_id,x.claim_id,x.message_id,x.reported_account_id,x.resolution_note,x.created_at from report x where x.report_id<>? and ((x.post_id is not null and x.post_id=?) or (x.claim_id is not null and x.claim_id=?) or (x.message_id is not null and x.message_id=?) or (x.reported_account_id is not null and x.reported_account_id=?)) order by x.created_at desc limit 30",
        reportId,
        r.get("post_id"),
        r.get("claim_id"),
        r.get("message_id"),
        r.get("reported_account_id")
      )
    );
    return out;
  }

  @SuppressWarnings("unchecked")
  public Set<Long> participants(Map<String, Object> current) {
    Set<Long> result = new HashSet<>();
    for (var row : (List<Map<String, Object>>) current.getOrDefault(
      "participants",
      List.of()
    ))
      result.add(id(row, "id"));
    return result;
  }

  public Set<Long> participantsFor(Map<String, Object> report) {
    Set<Long> result = participants(context.context(report));
    var snapshot = context.one(
      "select payload::text from report_snapshot where report_id=?",
      report.get("report_id")
    );
    if (!snapshot.isEmpty()) result.addAll(
      participants(context.parse((String) snapshot.get("payload")))
    );
    return result;
  }

  public Map<String, Object> report(long id) {
    var r = context.one("select * from report where report_id=?", id);
    if (r.isEmpty()) throw bad("Không tìm thấy báo cáo.");
    return r;
  }

  public boolean open(Map<String, Object> r) {
    return Set.of("OPEN", "IN_REVIEW").contains(r.get("status"));
  }

  private boolean activeP2pDispute(long reportId) {
    return Boolean.TRUE.equals(
      jdbc.queryForObject(
        "select exists(select 1 from p2p_handover_issue i join p2p_handover h using(handover_id) join claim c on c.claim_id=h.claim_id join post p on p.post_id=c.post_id where i.report_id=? and h.status='DISPUTED' and c.claim_type='P2P' and c.status='APPROVED' and p.custody_mode='P2P')",
        Boolean.class,
        reportId
      )
    );
  }

  public List<Map<String, Object>> inquiries(long reportId) {
    var rows = jdbc.queryForList(
      "select i.*,a.full_name as recipient from report_inquiry i join accounts a on a.id=i.recipient_id where report_id=? order by inquiry_id",
      reportId
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

  public Map<String, Object> lock(long reportId) {
    var r = report(reportId);
    var c = context.context(r);
    @SuppressWarnings("unchecked")
    var p = (Map<String, Object>) c.get("post");
    if (p != null && !p.isEmpty()) jdbc.queryForList(
      "select post_id from post where post_id=? for update",
      p.get("post_id")
    );
    return jdbc.queryForMap(
      "select * from report where report_id=? for update",
      reportId
    );
  }

  public void processing(Map<String, Object> r, Account actor, long version) {
    if (!open(r) || !Objects.equals(id(r, "version"), version)) throw bad(
      "Báo cáo đã thay đổi. Hãy tải lại."
    );
    if (participantsFor(r).contains(actor.getId())) throw bad(
      "Bạn có liên quan đến vụ việc. Cần một quản lý khác xử lý."
    );
    if (!Objects.equals(id(r, "assigned_to"), actor.getId())) throw bad(
      "Cần nhận xử lý báo cáo trước khi thực hiện thao tác."
    );
  }

  @Transactional
  public Map<String, Object> act(
    long reportId,
    Action request,
    CustomUserDetails user
  ) {
    Account actor = access.require(user, AccountRole.MANAGER);
    var r = lock(reportId);
    String action = request.action();
    if ("TAKE".equals(action)) {
      if (
        !open(r) ||
        id(r, "version") != request.version().longValue() ||
        r.get("assigned_to") != null
      ) throw bad("Báo cáo đã được nhận hoặc thay đổi. Hãy tải lại.");
      if (participantsFor(r).contains(actor.getId())) throw bad(
        "Bạn có liên quan đến vụ việc; cần quản lý khác xử lý."
      );
      jdbc.update(
        "update report set assigned_to=?,assigned_at=now(),status='IN_REVIEW',version=version+1,updated_at=now() where report_id=?",
        actor.getId(),
        reportId
      );
      history(
        reportId,
        actor.getId(),
        "TAKE",
        (String) r.get("status"),
        "IN_REVIEW",
        "Quản lý đã nhận xử lý.",
        null
      );
      return detail(reportId, user);
    }
    processing(r, actor, request.version());
    String conclusion = text(request.conclusion()),
      basis = text(request.basis());
    String status = (String) r.get("status");
    var current = context.context(r);
    var before = new LinkedHashMap<String, Object>();
    before.put("reportStatus", status);
    String next = status;
    switch (action) {
      case "HOLD", "RELEASE" -> {
        @SuppressWarnings("unchecked")
        var post = (Map<String, Object>) current.get("post");
        if (post == null || post.isEmpty()) throw bad(
          "Báo cáo không có đồ vật để tạm dừng trao trả."
        );
        if (
          "HOLD".equals(action) &&
          (!"FOUND".equals(post.get("post_type")) ||
            !Set.of("OPEN", "RESERVED").contains(
              Objects.toString(post.get("status"), "")
            ) ||
            post.get("deleted_at") != null)
        ) throw bad("Đồ vật không còn ở bước có thể tạm dừng giao nhận.");
        if ("HOLD".equals(action)) jdbc.update(
          "insert into report_hold(report_id,post_id,actor_id,reason) values(?,?,?,?) on conflict(report_id) do update set released_at=null,actor_id=excluded.actor_id,reason=excluded.reason,created_at=now()",
          reportId,
          post.get("post_id"),
          actor.getId(),
          conclusion
        );
        else jdbc.update(
          "update report_hold set released_at=now() where report_id=? and released_at is null",
          reportId
        );
      }
      case "HIDE_POST" -> hidePost(current, conclusion, user, before);
      case "SUSPEND_ACCOUNT" -> {
        if (request.targetId() == null) throw bad("Chọn tài khoản cần xử lý.");
        basis = suspendAccount(r, current, request.targetId(), before) + "\n" + basis;
      }
      case "RESOLVE", "DISMISS" -> {
        requireGeneralConclusion(reportId);
        next = "DISMISS".equals(action) ? "REJECTED" : "RESOLVED";
      }
      case "HANDOVER_COMPLETED", "HANDOVER_CONTINUE", "CLAIM_REJECTED" -> {
        if (
          "CLAIM_REJECTED".equals(action) && !request.custodyVerified()
        ) throw bad(
          "Cần xác minh đồ vẫn ở người giữ trước khi mở lại cho người khác nhận."
        );
        resolveHandover(r, action, actor.getId(), conclusion, before);
        next = "RESOLVED";
      }
      default -> throw bad("Thao tác không hợp lệ.");
    }
    before.put("context", current);
    var after = context.context(r);
    context.retainAssets(reportId, current, "DECISION");
    context.retainAssets(reportId, after, "DECISION");
    jdbc.update(
      "insert into report_decision(report_id,actor_id,action,conclusion,basis,before_state,after_state) values(?,?,?,?,?,?::jsonb,?::jsonb)",
      reportId,
      actor.getId(),
      action,
      conclusion,
      basis,
      context.json(before),
      context.json(Map.of("reportStatus", next, "context", after))
    );
    history(reportId, actor.getId(), action, status, next, conclusion, basis);
    boolean finished = Set.of("RESOLVED", "REJECTED").contains(next);
    jdbc.update(
      "update report set status=?,resolution_note=case when ? then ? else resolution_note end,resolved_by=case when ? then ? else resolved_by end,resolved_at=case when ? then now() else resolved_at end,version=version+1,updated_at=now() where report_id=?",
      next,
      finished,
      conclusion,
      finished,
      actor.getId(),
      finished,
      reportId
    );
    if (finished) {
      chat.notifyAppealChanged(reportId);
      jdbc.update(
        "update report_hold set released_at=now() where report_id=? and released_at is null",
        reportId
      );
      for (Long recipient : participantsFor(r)) {
        jdbc.update(
          "insert into report_recipient(report_id,account_id) values(?,?) on conflict do nothing",
          reportId,
          recipient
        );
        notifications.notifyUser(
          recipient,
          "REPORT_RESOLVED",
          "Báo cáo #" + reportId + " đã có kết quả",
          conclusion,
          "/bao-cao/" + reportId + "#ket-qua",
          false
        );
      }
    }
    return detail(reportId, user);
  }

  private void requireGeneralConclusion(long reportId) {
    if (activeP2pDispute(reportId)) throw bad(
      "Cần chọn kết quả trao trả để giải quyết tranh chấp này."
    );
  }

  @SuppressWarnings("unchecked")
  private String hidePost(
    Map<String, Object> current,
    String conclusion,
    CustomUserDetails user,
    Map<String, Object> before
  ) {
    var post = (Map<String, Object>) current.get("post");
    if (post == null || post.isEmpty()) throw bad(
      "Báo cáo không có bài đăng để ẩn."
    );
    var p = posts.findById(id(post, "post_id")).orElseThrow();
    if (!"VISIBLE".equals(p.getModerationStatus())) throw bad("Bài đăng đã bị ẩn. Hãy tải lại để xem trạng thái hiện tại.");
    before.put("moderation", p.getModerationStatus());
    moderation.moderate(
      p.getId(),
      new com.example.base.dto.office.OfficeDtos.Moderation(
        "HIDDEN",
        conclusion.substring(0, Math.min(2000, conclusion.length())),
        p.getUpdatedAt()
      ),
      user
    );
    return "Đã ẩn bài đăng #" + p.getId() + ".";
  }

  private String suspendAccount(
    Map<String, Object> report,
    Map<String, Object> current,
    long target,
    Map<String, Object> before
  ) {
    if (
      !participants(current).contains(target) ||
      Objects.equals(target, id(report, "reporter_id"))
    ) throw bad("Chọn đúng tài khoản liên quan bị báo cáo.");
    var account = context.one(
      "select status,role from accounts where id=?",
      target
    );
    if (!"ACTIVE".equals(account.get("status"))) throw bad(
      "Tài khoản không còn hoạt động. Hãy tải lại và kiểm tra biện pháp đã áp dụng."
    );
    before.put("account", account);
    before.put("targetAccountId", target);
    UpdateAccountRequest update = new UpdateAccountRequest();
    update.setStatus(AccountStatus.SUSPENDED);
    accounts.updateAccount(target, update);
    Account suspended = accountRepository.findById(target).orElseThrow();
    suspended.setFailedLoginCount(0);
    suspended.setLockedUntil(null);
    accountRepository.flush();
    return (
      "Đã đình chỉ tài khoản " + suspended.getFullName() + " (#" + target + ")."
    );
  }

  private void resolveHandover(
    Map<String, Object> r,
    String action,
    long actor,
    String note,
    Map<String, Object> before
  ) {
    if (!"HANDOVER_ISSUE".equals(r.get("kind"))) throw bad(
      "Đây không phải tranh chấp bàn giao."
    );
    var h = context.one(
      "select h.* from p2p_handover h join p2p_handover_issue i using(handover_id) where i.report_id=? for update of h",
      r.get("report_id")
    );
    if (h.isEmpty()) throw bad(
      "Trao trả văn phòng đã hoàn tất được kết luận bằng ghi nhận xử lý, không đảo ngược phiếu kho."
    );
    var c = context.one(
      "select claim_id,post_id,status,claim_type from claim where claim_id=? for update",
      r.get("claim_id")
    );
    if (
      !"DISPUTED".equals(h.get("status")) ||
      !"P2P".equals(c.get("claim_type")) ||
      !"APPROVED".equals(c.get("status"))
    ) throw bad(
      "Luồng trao trả đã thay đổi; cần đối chiếu lại trước khi kết luận."
    );
    before.put("handover", h);
    before.put("claim", c);
    // The full conclusion remains in report_decision; claim_activity has the team's 2,000-character limit.
    String activityNote =
      "Báo cáo #" +
      r.get("report_id") +
      ": " +
      note.substring(0, Math.min(note.length(), 1800));
    if (
      !"HANDOVER_CONTINUE".equals(action) &&
      Boolean.TRUE.equals(
        jdbc.queryForObject(
          "select exists(select 1 from report_hold where post_id=? and report_id<>? and released_at is null)",
          Boolean.class,
          c.get("post_id"),
          r.get("report_id")
        )
      )
    ) throw bad(
      "Đồ còn đang được tạm dừng bởi báo cáo khác. Cần phối hợp xử lý trước khi kết luận trao trả."
    );
    if ("HANDOVER_COMPLETED".equals(action)) {
      jdbc.update(
        "update p2p_handover set status='COMPLETED',confirmed_by=?,confirmed_at=now(),updated_at=now(),version=version+1 where handover_id=?",
        actor,
        h.get("handover_id")
      );
      jdbc.update(
        "update claim set status='COMPLETED',updated_at=now(),version=version+1 where claim_id=?",
        c.get("claim_id")
      );
      jdbc.update(
        "update post set status='RETURNED',updated_at=now() where post_id=?",
        c.get("post_id")
      );
      jdbc.update(
        "insert into claim_activity(claim_id,actor_id,action,from_status,to_status,reason,created_at) values(?,?,'CONFIRM_RECEIPT','APPROVED','COMPLETED',?,now())",
        c.get("claim_id"),
        actor,
        "Quản lý kết luận hoàn tất: " + activityNote
      );
      jdbc.update(
        "insert into claim_activity(claim_id,actor_id,action,from_status,to_status,reason,created_at) select claim_id,?,'AUTO_CLOSE',status,'CLOSED','Đồ đã được trao trả theo kết luận quản lý',now() from claim where post_id=? and status in ('PENDING','NEEDS_INFO')",
        actor,
        c.get("post_id")
      );
      jdbc.update(
        "update claim set status='CLOSED',closed_at=now(),close_reason='ITEM_RETURNED',version=version+1 where post_id=? and status in ('PENDING','NEEDS_INFO')",
        c.get("post_id")
      );
    } else if ("HANDOVER_CONTINUE".equals(action)) {
      jdbc.update(
        "update p2p_handover set status='WAITING_CONFIRMATION',updated_at=now(),version=version+1 where handover_id=?",
        h.get("handover_id")
      );
      jdbc.update(
        "insert into claim_activity(claim_id,actor_id,action,from_status,to_status,reason,created_at) values(?,?,'DISPUTE_RESOLVE','APPROVED','APPROVED',?,now())",
        c.get("claim_id"),
        actor,
        activityNote
      );
      jdbc.update(
        "update claim set version=version+1,updated_at=now() where claim_id=?",
        c.get("claim_id")
      );
    } else {
      jdbc.update(
        "update p2p_handover set status='CANCELLED',updated_at=now(),version=version+1 where handover_id=?",
        h.get("handover_id")
      );
      jdbc.update(
        "update claim set status='REJECTED',updated_at=now(),version=version+1 where claim_id=?",
        c.get("claim_id")
      );
      jdbc.update(
        "update post set status='OPEN',updated_at=now() where post_id=? and status='RESERVED'",
        c.get("post_id")
      );
      jdbc.update(
        "insert into claim_activity(claim_id,actor_id,action,from_status,to_status,reason,created_at) values(?,?,'DISPUTE_RESOLVE','APPROVED','REJECTED',?,now())",
        c.get("claim_id"),
        actor,
        activityNote
      );
    }
  }

  public void history(
    long report,
    long actor,
    String action,
    String from,
    String to,
    String note,
    String internal
  ) {
    jdbc.update(
      "insert into report_activity(report_id,actor_id,action,from_status,to_status,public_note,internal_note) values(?,?,?,?,?,?,?)",
      report,
      actor,
      action,
      from,
      to,
      note,
      internal
    );
  }

  public static String text(String value) {
    if (value == null || value.isBlank() || value.length() > 5000) throw bad(
      "Cần ghi kết luận và căn cứ xử lý (tối đa 5.000 ký tự)."
    );
    return value.strip();
  }
}
