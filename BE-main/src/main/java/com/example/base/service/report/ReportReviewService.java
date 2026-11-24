package com.example.base.service.report;

import static com.example.base.service.office.OfficeAccess.bad;
import static com.example.base.service.report.ReportContextService.id;

import com.example.base.dto.account.UpdateAccountRequest;
import com.example.base.entity.*;
import com.example.base.repository.*;
import com.example.base.security.CustomUserDetails;
import com.example.base.service.account.AccountService;
import com.example.base.service.chat.ChatConversationService;
import com.example.base.service.claim.ClaimService;
import com.example.base.service.claim.P2pClaimAppealService;
import com.example.base.service.notification.NotificationService;
import com.example.base.service.office.*;
import jakarta.validation.constraints.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReportReviewService {

  static final Set<String> VIOLATION_FINDINGS = Set.of("VIOLATION", "NO_VIOLATION", "INSUFFICIENT_EVIDENCE");
  private static final Pattern REPORT_REFERENCE = Pattern.compile("(?:#\\s*)?0*([0-9]+)");
  private final OfficeAccess access;
  private final JdbcTemplate jdbc;
  private final ReportContextService context;
  private final AccountService accounts;
  private final AccountRepository accountRepository;
  private final OfficeManagementService moderation;
  private final PostRepository posts;
  private final NotificationService notifications;
  private final ChatConversationService chat;
  private final ClaimService claims;
  private final P2pClaimAppealService claimAppeals;
  private final ReportReviewCalendar calendar;

  public enum AppealFinding { INSUFFICIENT_EVIDENCE, INVALID_CLAIM }

  public record Action(
    @NotNull @PositiveOrZero Long version,
    @NotBlank String action,
    @Size(max = 5000) String conclusion,
    @Size(max = 5000) String basis,
    Long targetId,
    boolean custodyVerified,
    @Size(max = 32) String measure,
    boolean wrongRecipientVerified,
    AppealFinding appealFinding
  ) {
    public Action(Long version, String action, String conclusion, String basis, Long targetId, boolean custodyVerified) {
      this(version, action, conclusion, basis, targetId, custodyVerified, null);
    }

    public Action(Long version, String action, String conclusion, String basis, Long targetId,
        boolean custodyVerified, String measure) {
      this(version, action, conclusion, basis, targetId, custodyVerified, measure, false);
    }

    public Action(Long version, String action, String conclusion, String basis, Long targetId,
        boolean custodyVerified, String measure, boolean wrongRecipientVerified) {
      this(version, action, conclusion, basis, targetId, custodyVerified, measure, wrongRecipientVerified, null);
    }
  }

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
    return list(page, status, kind, target, q, queue, targetId, "ALL", user);
  }

  @Transactional(readOnly = true)
  public Map<String,Object> list(int page, String status, String kind, String target, String q,
      String queue, Long targetId, String assignee, CustomUserDetails user) {
    return list(page, status, kind, target, q, queue, targetId, assignee, null, user);
  }

  @Transactional(readOnly = true)
  public Map<String,Object> list(int page, String status, String kind, String target, String q,
      String queue, Long targetId, String assignee, Long approvalId, CustomUserDetails user) {
    Account actor = access.require(user, AccountRole.MANAGER);
    if (!Set.of("ALL","READY","UNASSIGNED","MINE","WAITING","REPLIED","HISTORY").contains(queue))
      throw bad("Hàng đợi không hợp lệ.");
    if (
      page < 1 ||
      page > 100000 ||
      !Set.of("ALL", "OPEN", "IN_REVIEW", "RESOLVED", "REJECTED").contains(
        status
      ) ||
      !Set.of("ALL", "GENERAL", "HANDOVER_ISSUE", "CLAIM_APPEAL").contains(kind) ||
      !Set.of("ALL", "POST", "CLAIM", "MESSAGE", "USER").contains(target) ||
      !Set.of("ALL", "UNASSIGNED", "MINE").contains(assignee) ||
      q.length() > 200
    ) throw bad("Bộ lọc không hợp lệ.");
    if (approvalId != null && (approvalId <= 0 || targetId != null || !"CLAIM_APPEAL".equals(kind)))
      throw bad("Bộ lọc lần duyệt không hợp lệ.");
    boolean expandedGroup = targetId != null || approvalId != null;
    // Compiling the priority expressions can take longer than reading the report queue itself.
    jdbc.execute("set local jit = off");
    Instant now = Instant.now();
    var shift = calendar.currentOrNext(now);
    String where = "\n" + """
      from report r
      join accounts a on a.id=r.reporter_id
      left join accounts m on m.id=r.assigned_to
      left join claim c on c.claim_id=r.claim_id
      left join accounts claimant on claimant.id=c.claimant_id
      left join post p on p.post_id=coalesce(r.post_id,c.post_id)
      left join chat_message msg on msg.message_id=r.message_id
      left join accounts subject on subject.id=coalesce(r.reported_account_id,msg.sender_id,p.created_by)
      left join p2p_claim_appeal appeal on appeal.report_id=r.report_id
      left join p2p_claim_approval approval on approval.approval_id=appeal.approval_id
      """ + ReportQueueSql.CLASSIFICATION + """
      where (?='ALL' or case when appeal.report_id is not null then 'CLAIM_APPEAL' else r.kind end=?)
      """;
    var args = new ArrayList<Object>(List.of(shift.end().atOffset(ZoneOffset.UTC), kind, kind));
    String query = q.strip();
    var reference = REPORT_REFERENCE.matcher(query);
    boolean lookupById = !expandedGroup && reference.matches();
    List<Long> selectionArgs = lookupById ? List.of(parseReportId(reference.group(1))) : List.of();
    String selection = lookupById ? "r.report_id=?" : switch (queue) {
      case "READY", "WAITING", "HISTORY" -> "queue_state.work_state='" + queue + "'";
      case "UNASSIGNED" -> "queue_state.work_state='READY' and r.assigned_to is null";
      case "MINE" -> "queue_state.work_state<>'HISTORY' and r.assigned_to=" + actor.getId();
      case "REPLIED" -> "queue_state.work_state='READY' and facts.new_reply";
      default -> "true";
    };
    if (!lookupById) {
      where += """
        and (?='ALL' or r.status=?)
        and (?='ALL' or case when r.post_id is not null then 'POST' when r.claim_id is not null
          then 'CLAIM' when r.message_id is not null then 'MESSAGE' else 'USER' end=?)
        """;
      args.addAll(List.of(status, status, target, target));
      if (!query.isEmpty()) {
        String pattern = "%" + query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        where += """
          and (r.description ilike ? or a.full_name ilike ? or p.title ilike ?
            or subject.full_name ilike ? or r.reason_label_snapshot ilike ?)
          """;
        args.addAll(Collections.nCopies(5, pattern));
      }
      if ("UNASSIGNED".equals(assignee)) where += " and r.assigned_to is null";
      if ("MINE".equals(assignee)) {
        where += " and r.assigned_to=?";
        args.add(actor.getId());
      }
    }
    if (targetId != null) {
      if (targetId <= 0 || "ALL".equals(target)) throw bad("Chọn loại đối tượng hợp lệ.");
      String column = switch(target) { case "POST" -> "post_id"; case "CLAIM" -> "claim_id"; case "MESSAGE" -> "message_id"; default -> "reported_account_id"; };
      where += " and r." + column + "=?";
      args.add(targetId);
    }
    if (approvalId != null) {
      where += " and appeal.approval_id=?";
      args.add(approvalId);
    }
    String objectKey = """
        case when appeal.report_id is not null then 'APPEAL:'||appeal.approval_id
          when r.kind='HANDOVER_ISSUE' then 'HANDOVER:'||r.claim_id
          when r.post_id is not null then 'POST:'||r.post_id
          when r.claim_id is not null then 'CLAIM:'||r.claim_id
          when r.message_id is not null then 'MESSAGE:'||r.message_id
          else 'USER:'||r.reported_account_id end
        """.strip();
    var tallyArgs = new ArrayList<Object>(selectionArgs);
    tallyArgs.addAll(args);
    var totals = jdbc.queryForMap("""
        select count(*) filter (where in_list) as reports,
          count(distinct report_key) filter (where in_list) as groups,
          count(distinct report_key) filter (where in_list and kind='GENERAL' and not is_appeal) as violations,
          count(distinct report_key) filter (where in_list and kind='HANDOVER_ISSUE' and not is_appeal) as handover_issues,
          count(distinct report_key) filter (where in_list and is_appeal) as claim_appeals,
          count(distinct report_key) filter (where work_state='READY') as ready,
          count(distinct report_key) filter (where work_state='WAITING') as waiting
        from (
          select %s as report_key, queue_state.work_state, r.kind, appeal.report_id is not null as is_appeal,
            (%s) as in_list
          %s
        ) classified
        """.formatted(objectKey, selection, where), tallyArgs.toArray());
    long reportCount = ((Number) totals.get("reports")).longValue();
    long count = expandedGroup ? reportCount : ((Number) totals.get("groups")).longValue();
    int resultPage = lookupById ? 1 : page;
    var params = new ArrayList<>(args);
    params.addAll(selectionArgs);
    params.add((resultPage - 1) * 12);
    String projection = """
      select r.report_id,case when appeal.report_id is not null then 'CLAIM_APPEAL' else r.kind end as kind,
        appeal.approval_id, approval.custody_mode as appeal_custody_mode,
        r.post_id,r.claim_id,r.message_id,r.reported_account_id,r.description,r.reason_code,
        case when appeal.report_id is null then r.reason_label_snapshot end as reason_label_snapshot,
        r.status,r.created_at,
        a.full_name as reporter,m.full_name as assignee,
        coalesce(nullif(p.title,''),case when r.reported_account_id is not null then nullif(subject.full_name,'') end) as subject_title,
        nullif(subject.full_name,'') as subject_name,
        left(msg.body,240) as message_preview, msg.message_type,
        coalesce(nullif(c.claimant_name,''), claimant.full_name) as claimant_name, c.status as claim_status,
        p.post_id as related_post_id, p.moderation_status,
        case when appeal.report_id is not null then 'Tranh chấp kết quả nhận đồ'
          else coalesce(nullif(r.reason_label_snapshot,''),case when r.kind='HANDOVER_ISSUE'
          then 'Sự cố bàn giao' else 'Báo cáo vi phạm' end) end as reason_label,
        pickup.next_pickup, queue_state.work_state, queue_state.priority_rank, queue_state.priority_reason,
        step.waiting_reason, approval.appeal_until, facts.new_reply as has_new_reply,
        queue_state.work_state='READY' and not inquiry.asked and decisions.latest_decision is null
          and r.status in ('OPEN','IN_REVIEW') as needs_first_response,
      """ + objectKey + " as report_key" + where + " and " + selection;
    String order = "priority_rank,next_pickup nulls last,created_at,report_id";
    String grouped = "select ranked.*, count(*) over(partition by report_key) as object_count,"
      + " count(*) filter (where status in ('OPEN','IN_REVIEW')) over(partition by report_key) as open_count,"
      + " bool_or(has_new_reply) over(partition by report_key) as group_has_new_reply,"
      + " row_number() over(partition by report_key order by " + order + ") as object_row from (" + projection + ") ranked";
    var rows = jdbc.queryForList(
      "with queue_rows as (" + grouped + ") select queue_rows.*, "
        + "(select jsonb_object_agg(reason_label, amount)::text from "
        + "(select reason_label,count(*) as amount from queue_rows sibling where sibling.report_key=queue_rows.report_key group by reason_label) reasons) as reason_summary, "
        + """
          (select case
             when count(distinct related.assigned_to)>1
               or (count(related.assigned_to)>0 and count(related.assigned_to)<count(*))
             then 'Cần thống nhất phân công' else min(owner.full_name) end
           from p2p_claim_appeal entry join report related using(report_id)
           left join accounts owner on owner.id=related.assigned_to
           where entry.approval_id=queue_rows.approval_id and related.status in ('OPEN','IN_REVIEW')) as appeal_assignee,
          (select jsonb_build_object('storage_key', image.storage_key, 'image_url', image.image_url)::text
             from post_image image where image.post_id=queue_rows.related_post_id
             order by image.sort_order limit 1) as post_thumbnail
          """
        + "from queue_rows " + (expandedGroup ? "" : "where object_row=1 ")
        + "order by " + order + " limit 12 offset ?",
      params.toArray()
    );
    rows.forEach(row -> {
      row.put("reason_summary", context.parse((String) row.get("reason_summary")));
      String thumbnail = (String) row.remove("post_thumbnail");
      if (thumbnail != null) row.put("post_thumbnail", context.withUrls(context.parse(thumbnail)));
      if ("READY".equals(row.get("work_state"))) {
        Instant submitted = row.get("created_at") instanceof OffsetDateTime dt ? dt.toInstant()
            : ((Timestamp) row.get("created_at")).toInstant();
        row.put("carried_over", calendar.carriedOver(submitted, shift));
        if (Boolean.TRUE.equals(row.get("needs_first_response"))) {
          Instant due = calendar.responseDue(submitted, ((Number) row.get("priority_rank")).intValue());
          row.put("response_due_at", due.toString());
          row.put("response_overdue", !now.isBefore(due));
        }
      }
    });
    return Map.of(
      "content",
      rows,
      "page",
      resultPage,
      "totalElements",
      count,
      "totalReports",
      reportCount,
      "totalPages",
      Math.max(1, (count + 11) / 12),
      "countsByKind", Map.of("GENERAL", totals.getOrDefault("violations", 0),
          "HANDOVER_ISSUE", totals.getOrDefault("handover_issues", 0),
          "CLAIM_APPEAL", totals.getOrDefault("claim_appeals", 0)),
      "countsByQueue", Map.of("READY", totals.get("ready"), "WAITING", totals.get("waiting")),
      "workCalendar", Map.of("zone", calendar.zone(), "workingNow", !now.isBefore(shift.start()),
          "shiftStartsAt", shift.start().toString(), "shiftEndsAt", shift.end().toString())
    );
  }

  private static long parseReportId(String value) {
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException ignored) {
      return 0L; // A reference outside bigint cannot match a report.
    }
  }

  @Transactional
  public Map<String, Object> detail(long reportId, CustomUserDetails user) {
    Account actor = access.require(user, AccountRole.MANAGER);
    var r = report(reportId);
    context.capture(reportId, "FIRST_REVIEW");
    var current = context.context(r);
    var appeal = appeal(reportId);
    String kind = caseKind(r, appeal);
    Long approvalId = id(appeal, "approval_id");
    var reportsInRound = approvalId == null || !open(r) ? List.<Map<String, Object>>of() : appealReports(approvalId);
    var round = reportsInRound.stream().filter(this::open).toList();
    boolean conflict = hasAssignmentConflict(r, reportsInRound, actor.getId());
    boolean assigned = !conflict && Objects.equals(id(r, "assigned_to"), actor.getId())
        && round.stream().allMatch(item -> Objects.equals(id(item, "assigned_to"), actor.getId()));
    boolean p2pDispute = activeP2pDispute(reportId);
    var hold = context.one("select * from report_hold where report_id=? and released_at is null", reportId);
    boolean recoveryRequested = recoveryRequested(reportId);
    var recovery = recovery(reportId);
    String issuePhase = issuePhase(reportId);
    var out = new LinkedHashMap<String, Object>();
    out.put("report", r);
    out.put("caseKind", kind);
    out.put("appeal", appeal);
    if (!appeal.isEmpty()) {
      out.put("approvedContext", context.withUrls(context.context(
          Map.of("claim_id", appeal.get("approved_claim_id"), "report_id", reportId, "kind", kind))));
    }
    out.put("recoveryRequested", recoveryRequested);
    out.put("recovery", recovery);
    out.put("issuePhase", issuePhase);
    if (issuePhase != null) {
      out.put("incident", context.one("""
          select phase,submission_status,
            message_snapshot->>'sender' as message_sender,message_snapshot->>'body' as message_body,
            message_snapshot->>'createdAt' as message_created_at
          from p2p_handover_issue where report_id=?
          """, reportId));
    }
    out.put("current", context.withUrls(current));
    out.put("snapshot", context.snapshot(reportId));
    out.put("canTake", !conflict && open(r) && (round.isEmpty()
        ? r.get("assigned_to") == null : !assigned && canTakeAppealRound(round, reportsInRound, actor.getId())));
    out.put("appealRoundTransfer", round.stream().anyMatch(item ->
        item.get("assigned_to") != null && !Objects.equals(id(item, "assigned_to"), actor.getId())));
    out.put("appealOpenCount", round.size());
    out.put("canProcess", assigned && open(r));
    out.put("canAsk", assigned && (open(r) || activeRecoveryFollowUp(r, appeal, recoveryRequested)));
    Long assignee = id(r, "assigned_to");
    out.put("assigneeName", assignee == null ? null : context.one(
        "select full_name from accounts where id=?", assignee).get("full_name"));
    out.put("availableMeasures", assigned && open(r) && "GENERAL".equals(kind)
        ? violationMeasures(r, current) : List.of());
    out.put("actions", assigned
        ? availableActions(r, current, appeal, kind, p2pDispute, !hold.isEmpty(), recoveryRequested, recovery, issuePhase) : List.of());
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
        "select d.decision_id,d.action,d.conclusion,d.basis,d.created_at,d.after_state->>'appliedMeasure' as applied_measure,a.full_name as actor from report_decision d join accounts a on a.id=d.actor_id where report_id=? order by decision_id",
        reportId
      )
    );
    out.put("inquiries", inquiries(reportId));
    if ("HANDOVER_ISSUE".equals(kind) && issuePhase != null) out.put("verificationOptions", HandoverVerification.options(r, current));
    out.put("hold", hold);
    out.put("p2pDispute", p2pDispute);
    out.put(
      "related",
      jdbc.queryForList(
        """
        select x.report_id,case when sibling.report_id is not null then 'CLAIM_APPEAL' else x.kind end as kind,
          x.status,x.version,x.assigned_to,x.reason_code,x.reason_label_snapshot,x.description,x.post_id,x.claim_id,
          x.message_id,x.reported_account_id,x.resolution_note,x.created_at,
          coalesce(sibling.approval_id=cast(? as bigint),false) as same_approval
        from report x left join p2p_claim_appeal sibling on sibling.report_id=x.report_id
        where x.report_id<>? and (sibling.approval_id=? or (cast(? as bigint) is null
          and (x.post_id=? or x.claim_id=? or x.message_id=? or x.reported_account_id=?)))
        order by x.created_at desc,x.report_id desc
        limit (case when cast(? as bigint) is null then 30 end)
        """,
        appeal.get("approval_id"),
        reportId,
        approvalId,
        approvalId,
        r.get("post_id"),
        r.get("claim_id"),
        r.get("message_id"),
        r.get("reported_account_id"),
        approvalId
      )
    );
    return out;
  }

  Map<String, Object> appeal(long reportId) {
    return context.one("""
        select approval.approval_id, approval.approved_claim_id, approval.appeal_until, p.post_id, p.account_id as finder_id,
          approval.custody_mode as claim_type, chosen.claimant_id as approved_recipient_id,
          (%s) as active,
          approval.appeal_until <= now() as window_elapsed,
          pending.waiting_other_appeals,
          (%s) as can_reopen_approval,
          (%s) as follow_up
        from p2p_claim_appeal a join report r on r.report_id=a.report_id
        join p2p_claim_approval approval using (approval_id)
        join claim chosen on chosen.claim_id=approval.approved_claim_id
        join post p on p.post_id=chosen.post_id
        cross join lateral (select %s as waiting_other_appeals) pending
        where a.report_id=?
        """.formatted(ReportQueueSql.ACTIVE_APPROVAL, ReportQueueSql.APPROVAL_CAN_BE_REOPENED,
            ReportQueueSql.APPEAL_STEP, ReportQueueSql.OTHER_APPEALS_PENDING), reportId);
  }

  static boolean isP2pReport(Map<String, Object> report, Map<String, Object> appeal, Map<String, Object> current) {
    if (!appeal.isEmpty()) return "P2P".equals(appeal.get("claim_type"));
    return "HANDOVER_ISSUE".equals(report.get("kind")) && current.get("claim") instanceof Map<?, ?> claim
        && "P2P".equals(claim.get("claim_type"));
  }

  private static String caseKind(Map<String, Object> report, Map<String, Object> appeal) {
    return appeal.isEmpty() ? (String) report.get("kind") : "CLAIM_APPEAL";
  }

  private boolean recoveryRequested(long reportId) {
    return Boolean.TRUE.equals(jdbc.queryForObject(
        "select exists(select 1 from report_decision where report_id=? and action='REQUEST_ITEM_RECOVERY')",
        Boolean.class, reportId));
  }

  private Map<String,Object> recovery(long reportId) {
    return context.one("""
        select x.*,a.full_name as holder_name from report_recovery x
        join accounts a on a.id=x.holder_id where x.report_id=?
        """,reportId);
  }

  String issuePhase(long reportId) {
    return (String) context.one("select phase from p2p_handover_issue where report_id=?",reportId).get("phase");
  }

  OffsetDateTime inquiryResponseDue() {
    return calendar.inquiryResponseDue(Instant.now()).atOffset(ZoneOffset.UTC);
  }

  boolean canFollowUpRecovery(Map<String, Object> report) {
    if (!"RESOLVED".equals(report.get("status")) || !"HANDOVER_ISSUE".equals(report.get("kind"))) return false;
    long reportId = id(report, "report_id");
    return activeRecoveryFollowUp(report, appeal(reportId), recoveryRequested(reportId));
  }

  private static boolean activeRecoveryFollowUp(Map<String, Object> report, Map<String, Object> appeal, boolean recovery) {
    return "RESOLVED".equals(report.get("status")) && Boolean.TRUE.equals(appeal.get("active")) && recovery;
  }

  @SuppressWarnings("unchecked")
  private List<String> availableActions(Map<String, Object> report, Map<String, Object> current,
      Map<String, Object> appeal, String kind, boolean p2pDispute, boolean held, boolean recoveryRequested,
      Map<String,Object> recovery, String issuePhase) {
    boolean activeAppeal = Boolean.TRUE.equals(appeal.get("active"));
    boolean recoveryPending = !recovery.isEmpty() && recovery.get("confirmed_at")==null;
    if (!open(report)) {
      return activeAppeal && "RESOLVED".equals(report.get("status")) && recoveryPending
          ? List.of("CONFIRM_ITEM_RECOVERY") : List.of();
    }
    var actions = new ArrayList<String>();
    if ("CLAIM_APPEAL".equals(kind)) {
      if (!recoveryPending) actions.add("APPEAL_KEEP");
      if (activeAppeal && Boolean.TRUE.equals(appeal.get("can_reopen_approval"))) actions.add("APPEAL_WRONG_RECIPIENT");
    } else if ("HANDOVER_ISSUE".equals(kind)) {
      var post = (Map<String, Object>) current.getOrDefault("post", Map.of());
      if (held && !recoveryRequested && !"BEFORE".equals(issuePhase)) actions.add("RELEASE");
      if (!held && canHold(post)) actions.add("HOLD");
      if (!"BEFORE".equals(issuePhase) && (p2pDispute || issuePhase!=null) && !recoveryRequested) actions.add("REQUEST_ITEM_RECOVERY");
      if (!recoveryPending) {
        if (issuePhase != null && !"BEFORE".equals(issuePhase) && !recoveryRequested
            && Set.of("HANDOVER_WRONG_ITEM","HANDOVER_MISSING_ITEMS").contains(Objects.toString(report.get("reason_code"), ""))
            && !hasCorrection(id(report, "report_id"))) actions.add("HANDOVER_CORRECTION");
        if (issuePhase != null) actions.addAll(List.of("HANDOVER_VERIFY_HOLDER", "HANDOVER_UNVERIFIED"));
        if (p2pDispute) actions.addAll(List.of("HANDOVER_COMPLETED","HANDOVER_CONTINUE","HANDOVER_RETRY","HANDOVER_REOPEN"));
        else if ("BEFORE".equals(issuePhase)) actions.addAll(List.of("HANDOVER_CONTINUE","HANDOVER_REOPEN","DISMISS"));
        else actions.addAll(List.of("RESOLVE","DISMISS"));
      }
    } else actions.addAll(List.of("VIOLATION", "NO_VIOLATION", "INSUFFICIENT_EVIDENCE"));
    if ("HANDOVER_ISSUE".equals(kind) && issuePhase != null) actions.removeIf(action -> !fitsHandoverReason(report, action));
    if (recoveryPending) actions.add("CONFIRM_ITEM_RECOVERY");
    return actions;
  }

  private boolean hasCorrection(long reportId) {
    return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from report_decision where report_id=? and action='HANDOVER_CORRECTION')",Boolean.class,reportId));
  }

  private static boolean fitsHandoverReason(Map<String,Object> report, String action) {
    String reason = Objects.toString(report.get("reason_code"), "");
    return switch (action) {
      case "HANDOVER_REOPEN" -> Set.of("HANDOVER_WRONG_RECIPIENT", "HANDOVER_NOT_RECEIVED", "HANDOVER_ISSUE_OTHER").contains(reason) || reason.isEmpty();
      case "REQUEST_ITEM_RECOVERY" -> !Set.of("HANDOVER_MISSING_ITEMS", "HANDOVER_CONDITION", "P2P_HANDOVER_NO_CONTACT", "HANDOVER_CONFIRMATION_ERROR").contains(reason);
      case "HANDOVER_RETRY" -> !Set.of("HANDOVER_MISSING_ITEMS", "HANDOVER_CONDITION").contains(reason);
      default -> true;
    };
  }

  @SuppressWarnings("unchecked")
  private List<String> violationMeasures(Map<String, Object> report, Map<String, Object> current) {
    var post = (Map<String, Object>) current.getOrDefault("post", Map.of());
    var claim = (Map<String, Object>) current.getOrDefault("claim", Map.of());
    var account = (Map<String, Object>) current.getOrDefault("account", Map.of());
    var measures = new ArrayList<String>();
    if (report.get("post_id") != null && post.get("deleted_at") == null && "VISIBLE".equals(post.get("moderation_status"))) {
      if (post.get("account_id") != null) measures.add("REQUEST_POST_CORRECTION");
      measures.add("HIDE_POST");
    }
    if (report.get("reported_account_id") != null && "ACTIVE".equals(account.get("status"))
        && isSanctionableRole(account.get("role"))) {
      measures.add("WARN_ACCOUNT");
      measures.add("SUSPEND_ACCOUNT");
    }
    if (report.get("claim_id") != null && "P2P".equals(claim.get("claim_type"))
        && "PENDING".equals(claim.get("status")) && "OPEN".equals(post.get("status"))
        && isReportAgainstClaimant(report, claim)) measures.add("CLAIM_VIOLATION_REJECT");
    return measures;
  }

  private static boolean canHold(Map<String, Object> post) {
    return "FOUND".equals(post.get("post_type"))
        && Set.of("OPEN", "RESERVED").contains(Objects.toString(post.get("status"), ""))
        && post.get("deleted_at") == null;
  }

  private static boolean isSanctionableRole(Object role) {
    return "USER".equals(role) || "APPROVER".equals(role);
  }

  private static boolean isReportAgainstClaimant(Map<String, Object> report, Map<String, Object> claim) {
    Long claimant = id(claim, "claimant_id");
    return claimant != null && !claimant.equals(id(report, "reporter_id"));
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
      """
      select i.*,a.full_name as recipient,
        (i.responded_at is null and i.closed_at is null and i.response_due_at<=now()) as overdue
      from report_inquiry i join accounts a on a.id=i.recipient_id where report_id=? order by inquiry_id
      """,
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
    if (!open(r)) throw bad("Báo cáo đã thay đổi. Hãy tải lại.");
    requireAssignee(r, actor, version);
  }

  void requireAssignee(Map<String, Object> r, Account actor, long version) {
    if (!Objects.equals(id(r, "version"), version)) throw bad(
      "Báo cáo đã thay đổi. Hãy tải lại."
    );
    Long approvalId = open(r) ? id(context.one(
        "select approval_id from p2p_claim_appeal where report_id=?", id(r, "report_id")), "approval_id") : null;
    var round = approvalId == null ? List.<Map<String, Object>>of() : appealReports(approvalId);
    if (hasAssignmentConflict(r, round, actor.getId())) throw bad(
      "Bạn có liên quan đến vụ việc. Cần một quản lý khác xử lý."
    );
    if (!Objects.equals(id(r, "assigned_to"), actor.getId())) throw bad(
      "Cần nhận xử lý báo cáo trước khi thực hiện thao tác."
    );
    if (round.stream().anyMatch(item -> open(item) && !Objects.equals(id(item, "assigned_to"), actor.getId())))
      throw bad("Cần thống nhất người phụ trách toàn bộ vụ việc trước khi tiếp tục.");
  }

  private List<Map<String, Object>> appealReports(long approvalId) {
    return jdbc.queryForList("""
        select r.* from p2p_claim_appeal a join report r using(report_id)
        where a.approval_id=? order by r.report_id
        """, approvalId);
  }

  private boolean hasAssignmentConflict(Map<String, Object> report, List<Map<String, Object>> round, long actorId) {
    return participantsFor(report).contains(actorId) || round.stream()
        .filter(item -> !Objects.equals(item.get("report_id"), report.get("report_id")))
        .anyMatch(item -> participantsFor(item).contains(actorId));
  }

  private boolean canTakeAppealRound(List<Map<String, Object>> round, List<Map<String, Object>> reportsInRound, long actorId) {
    var assignees = round.stream().map(item -> id(item, "assigned_to")).filter(Objects::nonNull).distinct().toList();
    return assignees.isEmpty() || assignees.contains(actorId) || assignees.stream().noneMatch(assignee ->
        accountRepository.findById(assignee).filter(account -> account.getRole() == AccountRole.MANAGER
            && account.getStatus() == AccountStatus.ACTIVE && new CustomUserDetails(account).isAccountNonLocked()
            && reportsInRound.stream().noneMatch(item -> participantsFor(item).contains(assignee))).isPresent());
  }

  private void assignReport(Map<String, Object> report, long actorId, boolean round) {
    long reportId = id(report, "report_id");
    Long previous = id(report, "assigned_to");
    jdbc.update(
        "update report set assigned_to=?,assigned_at=now(),status='IN_REVIEW',version=version+1,updated_at=now() where report_id=?",
        actorId, reportId);
    history(reportId, actorId, "TAKE", (String) report.get("status"), "IN_REVIEW",
        round ? "Quản lý đã nhận xử lý báo cáo cùng vụ việc tranh chấp." : "Quản lý đã nhận xử lý.",
        previous != null && previous != actorId ? "Chuyển từ quản lý tài khoản #" + previous + "." : null);
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
    var appeal = appeal(reportId);
    Long approvalId = id(appeal, "approval_id");
    if ("TAKE".equals(action)) {
      if (!open(r) || !Objects.equals(id(r, "version"), request.version()))
        throw bad("Báo cáo đã được nhận hoặc thay đổi. Hãy tải lại.");
      var reportsInRound = approvalId == null ? List.<Map<String, Object>>of() : appealReports(approvalId);
      var round = reportsInRound.stream().filter(this::open).toList();
      if (hasAssignmentConflict(r, reportsInRound, actor.getId()))
        throw bad("Bạn có liên quan đến vụ việc; cần quản lý khác xử lý.");
      if (round.isEmpty()) {
        if (r.get("assigned_to") != null) throw bad("Báo cáo đã được nhận hoặc thay đổi. Hãy tải lại.");
        assignReport(r, actor.getId(), false);
      } else {
        if (!canTakeAppealRound(round, reportsInRound, actor.getId()))
          throw bad("Vụ việc đã có quản lý phụ trách. Hãy tải lại.");
        for (var item : round) {
          if (!Objects.equals(id(item, "assigned_to"), actor.getId()))
            assignReport(item, actor.getId(), true);
        }
      }
      return detail(reportId, user);
    }
    String kind = caseKind(r, appeal);
    boolean afterAppealConclusion = "RESOLVED".equals(r.get("status")) && "CLAIM_APPEAL".equals(kind);
    if ("REQUEST_ITEM_RECOVERY".equals(action) && "CLAIM_APPEAL".equals(kind))
      throw bad("Hồ sơ tranh chấp này chỉ dùng để xem lại kết quả chọn người nhận.");
    if (afterAppealConclusion && "CONFIRM_ITEM_RECOVERY".equals(action))
      requireAssignee(r, actor, request.version());
    else processing(r, actor, request.version());
    String conclusion = text(request.conclusion()),
      basis = Objects.toString(request.basis(), "").strip();
    String status = (String) r.get("status");
    var current = context.context(r);
    boolean emailP2pOutcome = isP2pReport(r, appeal, current);
    Long recipientWithClaimEmail = null;
    var before = new LinkedHashMap<String, Object>();
    before.put("reportStatus", status);
    String measure = request.measure();
    boolean violationFinding = VIOLATION_FINDINGS.contains(action);
    if (violationFinding) requireKind(kind, "GENERAL");
    if (measure != null && (!"VIOLATION".equals(action) || !violationMeasures(r, current).contains(measure)))
      throw bad("Biện pháp không phù hợp với kết luận hoặc đối tượng được báo cáo.");
    String next = violationFinding ? ("VIOLATION".equals(action) ? "RESOLVED" : "REJECTED") : status;
    String recordedAction = action;
    String effect = measure == null ? action : measure;
    var recovery = recovery(reportId);
    if (!recovery.isEmpty() && recovery.get("confirmed_at")==null && Set.of(
        "APPEAL_KEEP","APPEAL_WRONG_RECIPIENT","RESOLVE","DISMISS","HANDOVER_COMPLETED","HANDOVER_CONTINUE",
        "HANDOVER_RETRY","HANDOVER_REOPEN","HANDOVER_UNVERIFIED","RELEASE").contains(effect))
      throw bad("Cần xác nhận kết quả thu hồi trước khi xử lý bước này.");
    if ("HANDOVER_ISSUE".equals(kind) && !fitsHandoverReason(r, effect) && issuePhase(reportId) != null)
      throw bad("Thao tác không phù hợp với lý do sự cố bàn giao.");
    if ("HANDOVER_ISSUE".equals(kind) && "RESOLVE".equals(effect) && Set.of("HANDOVER_WRONG_ITEM", "HANDOVER_MISSING_ITEMS").contains(Objects.toString(r.get("reason_code"), "")) && !request.custodyVerified())
      throw bad("Cần xác minh kết quả đổi đồ hoặc giao bổ sung trước khi kết luận.");
    switch (effect) {
      case "VIOLATION", "NO_VIOLATION", "INSUFFICIENT_EVIDENCE" -> requireGeneralConclusion(reportId);
      case "HOLD", "RELEASE" -> {
        if ("RELEASE".equals(action) && "BEFORE".equals(issuePhase(reportId)))
          throw bad("Cần kết luận sự cố trước khi cho phép bàn giao.");
        if ("RELEASE".equals(action) && recoveryRequested(reportId) && recovery.isEmpty())
          throw bad("Cần xác minh kết quả thu hồi trước khi gỡ tạm dừng.");
        @SuppressWarnings("unchecked")
        var post = (Map<String, Object>) current.get("post");
        if (post == null || post.isEmpty()) throw bad(
          "Báo cáo không có đồ vật để tạm dừng trao trả."
        );
        if ("HOLD".equals(action) && !canHold(post)) throw bad("Đồ vật không còn ở bước có thể tạm dừng giao nhận.");
        if ("HOLD".equals(action)) holdItem(reportId, id(post, "post_id"), actor.getId(), conclusion);
        else jdbc.update(
          "update report_hold set released_at=now() where report_id=? and released_at is null",
          reportId
        );
      }
      case "HIDE_POST" -> {
        requireKind(kind, "GENERAL");
        if (r.get("post_id") == null) throw bad("Chỉ có thể ẩn bài đăng được báo cáo.");
        hidePost(current, conclusion, user, before);
      }
      case "REQUEST_POST_CORRECTION" -> {
        if (measure == null) throw bad("Cần ghi kết luận vi phạm trước khi yêu cầu chỉnh sửa bài đăng.");
        @SuppressWarnings("unchecked")
        var post = (Map<String, Object>) current.get("post");
        publishToParticipant(reportId, id(post, "account_id"), "REPORT_POST_CORRECTION",
            "Yêu cầu chỉnh sửa bài đăng", conclusion);
      }
      case "WARN_ACCOUNT", "SUSPEND_ACCOUNT" -> {
        requireKind(kind, "GENERAL");
        Long target = id(r, "reported_account_id");
        if (target == null || Objects.equals(target, id(r, "reporter_id"))
            || request.targetId() != null && !target.equals(request.targetId())) {
          throw bad("Chọn đúng tài khoản liên quan bị báo cáo.");
        }
        if ("SUSPEND_ACCOUNT".equals(effect))
          basis = suspendAccount(target, before) + (basis.isEmpty() ? "" : "\n" + basis);
        else requireActiveAccount(target);
        publishToParticipant(reportId, target,
            "WARN_ACCOUNT".equals(effect) ? "REPORT_ACCOUNT_WARNING" : "REPORT_ACCOUNT_SUSPENDED",
            "WARN_ACCOUNT".equals(effect) ? "Cảnh báo tài khoản" : "Tài khoản đã bị đình chỉ", conclusion);
      }
      case "CLAIM_VIOLATION_REJECT" -> {
        requireKind(kind, "GENERAL");
        Long claimId = id(r, "claim_id");
        @SuppressWarnings("unchecked")
        var claim = (Map<String, Object>) current.getOrDefault("claim", Map.of());
        if (claimId == null || !isReportAgainstClaimant(r, claim))
          throw bad("Chỉ có thể bác yêu cầu nhận đồ đang bị báo cáo vi phạm.");
        claims.rejectReportedClaim(claimId, claimNote(reportId, conclusion), user);
        next = "RESOLVED";
      }
      case "REQUEST_ITEM_RECOVERY" -> {
        String phase = issuePhase(reportId);
        if ("BEFORE".equals(phase) || (phase==null && !activeP2pDispute(reportId)))
          throw bad("Chỉ yêu cầu thu hồi trong hồ sơ sự cố sau khi đã ghi nhận giao đồ.");
        if (recoveryRequested(reportId)) throw bad("Đã gửi yêu cầu thu hồi món đồ.");
        @SuppressWarnings("unchecked")
        var post = (Map<String, Object>) current.getOrDefault("post", Map.of());
        boolean afterHandover = "AFTER".equals(phase);
        if (!afterHandover && !canHold(post)) throw bad("Món đồ không còn ở bước có thể tạm dừng giao nhận.");
        Long holder = request.targetId();
        if (holder==null || !participantsFor(r).contains(holder))
          throw bad("Chọn người đang giữ món đồ trong danh sách người liên quan.");
        var due = calendar.responseDue(Instant.now(), 1).atOffset(ZoneOffset.UTC);
        jdbc.update("""
            with inquiry as (
              insert into report_inquiry(report_id,recipient_id,created_by,question,response_due_at)
              values(?,?,?,?,?) returning inquiry_id
            )
            insert into report_recovery(report_id,holder_id,due_at,inquiry_id)
              select ?,?,?,inquiry_id from inquiry
            """, reportId,holder,actor.getId(),"Vui lòng phản hồi kết quả thu hồi món đồ. "
                + conclusion.substring(0, Math.min(4800, conclusion.length())),due,reportId,holder,due);
        if (!afterHandover) holdItem(reportId, id(post, "post_id"), actor.getId(), conclusion);
        for (Long recipient : participantsFor(r)) {
          if (holder.equals(recipient)) {
            notifications.notifyUserWithEmail(recipient, "REPORT_INFORMATION", "Cần phản hồi về việc thu hồi món đồ",
                conclusion, "/bao-cao/" + reportId + "#phan-hoi",
                "Mở hồ sơ để xem hướng dẫn thu hồi và phản hồi trước " + NotificationService.formatVietnamDeadline(due) + ".");
          } else {
            publishToParticipant(reportId, recipient, "REPORT_RECOVERY_REQUESTED", "Yêu cầu thu hồi món đồ", conclusion);
          }
        }
      }
      case "CONFIRM_ITEM_RECOVERY" -> {
        requireCustody(request);
        if (afterAppealConclusion && !Boolean.TRUE.equals(appeal.get("active")))
          throw bad("Lần chấp thuận này không còn hiệu lực.");
        int updated=jdbc.update("""
            update report_recovery set confirmed_by=?,confirmed_at=now()
            where report_id=? and confirmed_at is null
            """,actor.getId(),reportId);
        if (updated!=1) throw bad("Không có yêu cầu thu hồi đang chờ xác nhận.");
        jdbc.update("""
            update report_inquiry set closed_at=now() where responded_at is null
              and inquiry_id=(select inquiry_id from report_recovery where report_id=?)
            """,reportId);
        for (long recipient : participantsFor(r)) {
          publishToParticipant(reportId, recipient, "REPORT_RECOVERY_CONFIRMED", "Đã xác nhận thu hồi món đồ", conclusion);
        }
      }
      case "APPEAL_KEEP" -> {
        requireKind(kind, "CLAIM_APPEAL");
        if (recoveryRequested(reportId)) requireCustody(request);
        if (request.appealFinding() == null)
          throw bad("Chọn căn cứ giữ người đã duyệt trước khi lưu kết luận.");
        recordedAction = switch (request.appealFinding()) {
          case INSUFFICIENT_EVIDENCE -> "APPEAL_INSUFFICIENT";
          case INVALID_CLAIM -> "APPEAL_REJECT_CLAIM";
        };
        if (request.appealFinding() == AppealFinding.INVALID_CLAIM && pendingAppealingClaim(r, current)) {
          claims.rejectClaimAfterAppeal(id(r, "claim_id"),
              claimNote(reportId, conclusion) + " Bạn không thể gửi lại yêu cầu trên bài đăng này.", user);
          recipientWithClaimEmail = id(r, "reporter_id");
        }
        if (approvalId != null && request.appealFinding() == AppealFinding.INSUFFICIENT_EVIDENCE
            && Boolean.TRUE.equals(appeal.get("active"))) {
          if (Boolean.TRUE.equals(jdbc.queryForObject("""
              select exists(select 1 from p2p_claim_appeal a join report r using(report_id)
                join report_recovery x using(report_id) where a.approval_id=? and x.confirmed_at is null
                  and r.status in ('OPEN','IN_REVIEW'))
              """, Boolean.class, approvalId)))
            throw bad("Cần xác nhận kết quả thu hồi trong vụ việc trước khi lưu kết luận.");
          closeOtherAppeals(reportId, approvalId, actor.getId(), false, false);
        }
        next = "REJECTED";
      }
      case "APPEAL_WRONG_RECIPIENT" -> {
        requireKind(kind, "CLAIM_APPEAL");
        if (!Boolean.TRUE.equals(appeal.get("active"))) throw bad("Lần chấp thuận này không còn hiệu lực.");
        if (!Boolean.TRUE.equals(appeal.get("can_reopen_approval"))) {
          throw bad("Chưa đủ điều kiện mở lại xét duyệt. Nếu món đồ đã được giao, hãy xử lý qua sự cố bàn giao.");
        }
        if (!request.wrongRecipientVerified())
          throw bad("Cần xác nhận đã đối chiếu căn cứ cho thấy người được chấp thuận không phải chủ món đồ.");
        if (!"OFFICE".equals(appeal.get("claim_type"))) {
          claims.reopenApprovalForReview(id(appeal, "approved_claim_id"), claimNote(reportId, conclusion), user);
          recipientWithClaimEmail = id(appeal, "approved_recipient_id");
        }
        closeOtherAppeals(reportId, id(appeal, "approval_id"), actor.getId(), true, emailP2pOutcome);
        next = "RESOLVED";
      }
      case "HANDOVER_CORRECTION" -> {
        requireKind(kind, "HANDOVER_ISSUE");
        String reason = Objects.toString(r.get("reason_code"), "");
        String phase = issuePhase(reportId);
        boolean exchange = "HANDOVER_WRONG_ITEM".equals(reason);
        if ((!exchange && !"HANDOVER_MISSING_ITEMS".equals(reason)) || phase == null || "BEFORE".equals(phase)
            || recoveryRequested(reportId) || hasCorrection(reportId))
          throw bad("Hồ sơ không còn phù hợp để gửi yêu cầu này.");
        var parties = HandoverVerification.options(r, current);
        if (parties.stream().anyMatch(party -> party.recipientId() == null)) throw bad("Chưa xác định đủ hai bên giao nhận.");
        closeInquiries(reportId);
        var due = inquiryResponseDue();
        for (var party : parties) {
          boolean giver = "ISSUE_GIVER".equals(party.kind());
          String prompt = exchange
              ? (giver ? "Hãy hẹn đổi đúng món và xác nhận khi đã nhận lại món nhầm." : "Hãy trả lại món nhầm và xác nhận khi đã nhận đúng món.")
              : (giver ? "Hãy giao phụ kiện còn thiếu và phản hồi phần đã giao." : "Hãy xác nhận phụ kiện bạn thực tế nhận bổ sung.");
          jdbc.update("insert into report_inquiry(report_id,recipient_id,created_by,question,response_due_at,verification_kind) values(?,?,?,?,?,?)",
              reportId, party.recipientId(), actor.getId(), prompt + " " + conclusion.substring(0,Math.min(4500,conclusion.length())), due, "HANDOVER_CORRECTION");
          long recipient = party.recipientId();
          addReportRecipient(reportId, recipient);
          notifications.notifyUserWithEmail(recipient, "REPORT_INFORMATION",
              exchange ? "Cần đổi đúng món" : "Cần giao bổ sung phụ kiện", prompt,
              "/bao-cao/" + reportId + "#phan-hoi",
              "Mở hồ sơ để xem việc bạn cần làm và phản hồi trước " + NotificationService.formatVietnamDeadline(due) + ".");
        }
      }
      case "HANDOVER_VERIFY_HOLDER" -> {
        requireKind(kind, "HANDOVER_ISSUE");
        if (issuePhase(reportId) == null || !recovery.isEmpty() && recovery.get("confirmed_at") == null) throw bad("Chưa thể tạo việc xác minh cho hồ sơ này.");
        // Keep the investigation assigned and open across shifts.
      }
      case "HANDOVER_UNVERIFIED" -> {
        requireKind(kind, "HANDOVER_ISSUE");
        if (issuePhase(reportId) == null) throw bad("Cần hồ sơ sự cố bàn giao P2P.");
        if (activeP2pDispute(reportId)) resolveHandover(r, "HANDOVER_CONTINUE", actor.getId(), conclusion, before);
        next = "RESOLVED";
      }
      case "RESOLVE", "DISMISS" -> {
        if ("CLAIM_APPEAL".equals(kind)) throw bad("Chọn quyết định xử lý tranh chấp.");
        if ("RESOLVE".equals(action) && "BEFORE".equals(issuePhase(reportId)))
          throw bad("Cần chọn kết quả trao trả cho sự cố trước khi bàn giao.");
        requireGeneralConclusion(reportId);
        next = "DISMISS".equals(action) ? "REJECTED" : "RESOLVED";
      }
      case "HANDOVER_COMPLETED", "HANDOVER_CONTINUE", "HANDOVER_RETRY", "HANDOVER_REOPEN" -> {
        if (!"HANDOVER_ISSUE".equals(kind)) throw bad("Đây không phải tranh chấp bàn giao.");
        if ("HANDOVER_COMPLETED".equals(action) && !request.custodyVerified())
          throw bad("Cần xác nhận căn cứ đúng món đồ và đúng người nhận.");
        if (Set.of("HANDOVER_RETRY","HANDOVER_REOPEN").contains(action)) requireCustody(request);
        else if (recoveryRequested(reportId) && !request.custodyVerified())
          throw bad("Cần xác minh tình trạng món đồ trước khi kết thúc yêu cầu thu hồi.");
        resolveHandover(r, action, actor.getId(), conclusion, before);
        next = "RESOLVED";
      }
      default -> throw bad("Thao tác không hợp lệ.");
    }
    before.put("context", current);
    var after = context.context(r);
    var afterState = new LinkedHashMap<String, Object>();
    afterState.put("reportStatus", next);
    afterState.put("context", after);
    boolean officeAppeal = "OFFICE".equals(appeal.get("claim_type"));
    if ("APPEAL_WRONG_RECIPIENT".equals(action) && !officeAppeal) afterState.put("approvalReopened", true);
    if (measure != null) afterState.put("appliedMeasure", measure);
    context.retainAssets(reportId, current, "DECISION");
    context.retainAssets(reportId, after, "DECISION");
    jdbc.update(
      "insert into report_decision(report_id,actor_id,action,conclusion,basis,before_state,after_state) values(?,?,?,?,?,?::jsonb,?::jsonb)",
      reportId,
      actor.getId(),
      recordedAction,
      conclusion,
      basis,
      context.json(before),
      context.json(afterState)
    );
    history(reportId, actor.getId(), recordedAction, status, next, conclusion, basis);
    boolean finished = open(r) && Set.of("RESOLVED", "REJECTED").contains(next);
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
      closeInquiries(reportId);
      if ("APPEAL_REJECT_CLAIM".equals(recordedAction) && Boolean.TRUE.equals(appeal.get("active"))) {
        claimAppeals.releaseWindowIfUncontested(id(appeal, "post_id"));
      }
      chat.notifyAppealChanged(reportId);
      jdbc.update(
        "update report_hold set released_at=now() where report_id=? and released_at is null",
        reportId
      );
      if (officeAppeal && Boolean.TRUE.equals(appeal.get("active"))) {
        claims.finishOfficeAppealReview(id(appeal, "post_id"));
      }
      Set<Long> recipients = participantsFor(r);
      Long finderToReview = "APPEAL_WRONG_RECIPIENT".equals(action) && !officeAppeal ? id(appeal, "finder_id") : null;
      if (finderToReview != null) recipients.add(finderToReview);
      for (Long recipient : recipients) {
        boolean needsReview = Objects.equals(recipient, finderToReview);
        addReportRecipient(reportId, recipient);
        String title = needsReview ? "Các yêu cầu nhận đồ đã mở lại để bạn xét duyệt" : "Báo cáo #" + reportId + " đã có kết quả";
        String reviewMessage = "Hồ sơ đã chấp thuận đã về chờ duyệt. Hãy đối chiếu các hồ sơ và chọn người nhận.";
        String message = needsReview ? reviewMessage : conclusion;
        String path = needsReview ? "/bai-dang/" + id(appeal, "post_id") + "/claims?claimStatus=PENDING"
            : "/bao-cao/" + reportId + "#ket-qua";
        if (emailP2pOutcome && !Objects.equals(recipient, recipientWithClaimEmail)) {
          notifications.notifyUserWithEmail(recipient, "REPORT_RESOLVED", title, message, path,
              needsReview ? reviewMessage : p2pOutcomeEmail(action));
        } else {
          notifications.notifyUser(recipient, "REPORT_RESOLVED", title, message, path, false);
        }
      }
      if ("HANDOVER_ISSUE".equals(kind) && id(r, "claim_id") != null)
        chat.notifyHandoverChanged(id(r, "claim_id"));
    }
    return detail(reportId, user);
  }

  private void closeOtherAppeals(long reportId, long approvalId, long actorId, boolean reopen, boolean emailParticipants) {
    String outcome = reopen ? "RESOLVED" : "REJECTED";
    String conclusion = reopen
        ? "Quản lý đã quyết định mở lại xét duyệt các yêu cầu nhận đồ còn hợp lệ."
        : "Sau khi đối chiếu các báo cáo cùng lần duyệt, quản lý giữ nguyên kết quả đã chấp thuận.";
    var siblings = jdbc.queryForList("""
        select r.report_id,r.reporter_id,r.status
        from p2p_claim_appeal appeal join report r using(report_id)
        where appeal.approval_id=? and r.report_id<>? and r.status in ('OPEN','IN_REVIEW')
        """, approvalId, reportId);
    for (var sibling : siblings) {
      long siblingId = id(sibling, "report_id");
      int closed = jdbc.update("""
          update report set status=?,resolution_note=?,resolved_by=?,resolved_at=now(),
            version=version+1,updated_at=now() where report_id=? and status in ('OPEN','IN_REVIEW')
          """, outcome, conclusion, actorId, siblingId);
      if (closed != 1) continue;
      history(siblingId, actorId, reopen ? "APPEAL_ROUND_CLOSED" : "APPEAL_INSUFFICIENT",
          (String) sibling.get("status"), outcome, conclusion,
          "Kết luận vụ việc được lưu tại báo cáo #" + reportId + ".");
      closeInquiries(siblingId);
      jdbc.update("update report_hold set released_at=now() where report_id=? and released_at is null", siblingId);
      long recipient = id(sibling, "reporter_id");
      addReportRecipient(siblingId, recipient);
      String title = reopen ? "Lượt xét duyệt đã được mở lại" : "Báo cáo #" + siblingId + " đã có kết quả";
      notifications.notifyUser(recipient, "REPORT_RESOLVED", title,
          conclusion, "/bao-cao/" + siblingId + "#ket-qua", emailParticipants);
    }
  }

  private void closeInquiries(long reportId) {
    jdbc.update("""
        update report_inquiry i set closed_at=now()
        where i.report_id=? and i.responded_at is null and i.closed_at is null
        """, reportId);
  }

  private void holdItem(long reportId, long postId, long actorId, String conclusion) {
    jdbc.update(
        "insert into report_hold(report_id,post_id,actor_id,reason) values(?,?,?,?) on conflict(report_id) do update set released_at=null,actor_id=excluded.actor_id,reason=excluded.reason,created_at=now()",
        reportId, postId, actorId, conclusion);
  }

  private void publishToParticipant(long reportId, long recipient, String type, String title, String conclusion) {
    addReportRecipient(reportId, recipient);
    notifications.notifyUser(recipient, type, title, conclusion, "/bao-cao/" + reportId, false);
  }

  private void addReportRecipient(long reportId, long recipient) {
    jdbc.update("insert into report_recipient(report_id,account_id) values(?,?) on conflict do nothing", reportId, recipient);
  }

  private static String p2pOutcomeEmail(String action) {
    return switch (action) {
      case "APPEAL_KEEP" -> "Quản lý giữ kết quả xét duyệt hiện tại. Mở hồ sơ để xem kết luận và trạng thái bàn giao.";
      case "APPEAL_WRONG_RECIPIENT", "HANDOVER_REOPEN" -> "Việc xét duyệt người nhận đã được mở lại. Mở hồ sơ để xem bước tiếp theo.";
      case "HANDOVER_RETRY" -> "Hai bên cần hẹn giao lại. Mở hồ sơ để xem hướng dẫn.";
      case "HANDOVER_CONTINUE" -> "Hai bên có thể tiếp tục xử lý việc trao trả. Mở hồ sơ để xem bước tiếp theo.";
      case "HANDOVER_COMPLETED" -> "Quản lý đã ghi nhận hoàn tất bàn giao. Mở hồ sơ để xem kết quả.";
      default -> "Quản lý đã ghi kết luận. Mở hồ sơ để xem kết quả và việc cần làm tiếp theo, nếu có.";
    };
  }

  private static void requireKind(String actual, String expected) {
    if (!expected.equals(actual)) throw bad("Quyết định không phù hợp với loại báo cáo này.");
  }

  private static void requireCustody(Action request) {
    if (!request.custodyVerified())
      throw bad("Cần xác minh đồ vẫn ở người giữ trước khi mở lại cho người khác nhận.");
  }

  private static boolean pendingAppealingClaim(Map<String, Object> report, Map<String, Object> current) {
    if (!(current.get("claim") instanceof Map<?, ?> claim)) return false;
    return "PENDING".equals(claim.get("status"))
        && Objects.equals(report.get("claim_id"), claim.get("claim_id"))
        && Objects.equals(report.get("reporter_id"), claim.get("claimant_id"));
  }

  private static String claimNote(long reportId, String conclusion) {
    return "Báo cáo #" + reportId + ": " + conclusion.substring(0, Math.min(1800, conclusion.length()));
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

  private Map<String, Object> requireActiveAccount(long target) {
    var account = context.one(
      "select status,role from accounts where id=?",
      target
    );
    if (!"ACTIVE".equals(account.get("status"))) throw bad(
      "Tài khoản không còn hoạt động. Hãy tải lại và kiểm tra biện pháp đã áp dụng."
    );
    if (!isSanctionableRole(account.get("role"))) throw bad("Tài khoản này không thuộc phạm vi xử lý của Quản lý.");
    return account;
  }

  private String suspendAccount(long target, Map<String, Object> before) {
    var account = requireActiveAccount(target);
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
    boolean beforeHandover = h.isEmpty() && "BEFORE".equals(issuePhase(id(r,"report_id")));
    if (h.isEmpty() && !beforeHandover) throw bad("Báo cáo này chỉ hỗ trợ ghi kết quả xác minh.");
    if (beforeHandover && !Set.of("HANDOVER_CONTINUE","HANDOVER_REOPEN").contains(action))
      throw bad("Chưa có lượt giao đồ để xác nhận hoặc hủy.");
    var c = context.one(
      "select claim_id,post_id,status,claim_type from claim where claim_id=? for update",
      r.get("claim_id")
    );
    if (
      (!beforeHandover && !"DISPUTED".equals(h.get("status"))) ||
      !"P2P".equals(c.get("claim_type")) ||
      !"APPROVED".equals(c.get("status"))
    ) throw bad(
      "Luồng trao trả đã thay đổi; cần đối chiếu lại trước khi kết luận."
    );
    before.put("handover", h);
    before.put("claim", c);
    // The full conclusion remains in report_decision; claim_activity has the team's 2,000-character limit.
    String activityNote = claimNote(id(r, "report_id"), note);
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
    if (beforeHandover && "HANDOVER_REOPEN".equals(action) && Boolean.TRUE.equals(jdbc.queryForObject("""
        select exists (
          select 1 from p2p_claim_approval a where a.approved_claim_id=? and a.closed_at is null
            and ((a.appeal_until>now() and not exists (
              select 1 from p2p_claim_appeal p join report r using(report_id)
              where p.approval_id=a.approval_id and r.status='RESOLVED' and not exists(select 1 from report_decision d where d.report_id=r.report_id and d.action='APPEAL_WITHDRAWN'))) or exists (
              select 1 from p2p_claim_appeal p join report r using(report_id)
              where p.approval_id=a.approval_id and r.status in ('OPEN','IN_REVIEW')))
        )
        """, Boolean.class,c.get("claim_id"))))
      throw bad("Chưa thể thay đổi người nhận khi còn tranh chấp đang mở hoặc lượt chấp thuận còn trong hạn gửi tranh chấp và chưa có kết luận yêu cầu xét lại.");
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
      var closed = jdbc.queryForList(
        "update claim set status='CLOSED',closed_at=now(),close_reason='ITEM_RETURNED',version=version+1,updated_at=now() where post_id=? and status in ('PENDING','NEEDS_INFO') returning claim_id,claimant_id",
        c.get("post_id")
      );
      for (var claim : closed) {
        notifications.notifyUser(id(claim, "claimant_id"), "CLAIM_CLOSED", "Món đồ đã được trao trả",
            "Quản lý đã xác minh bàn giao. Yêu cầu nhận đồ của bạn đã được đóng.",
            "/bai-dang/" + c.get("post_id") + "/claims/" + claim.get("claim_id"), false);
      }
    } else if ("HANDOVER_CONTINUE".equals(action)) {
      if (!beforeHandover) jdbc.update(
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
      if (!beforeHandover) jdbc.update(
        "update p2p_handover set status='CANCELLED',updated_at=now(),version=version+1 where handover_id=?",
        h.get("handover_id")
      );
      boolean retry = "HANDOVER_RETRY".equals(action);
      boolean reopen = "HANDOVER_REOPEN".equals(action);
      String next = retry ? "APPROVED" : "PENDING";
      jdbc.update("update claim set status=?,updated_at=now(),version=version+1 where claim_id=?",
          next, c.get("claim_id"));
      if (!retry) jdbc.update(
          "update post set status='OPEN',updated_at=now() where post_id=? and status='RESERVED'", c.get("post_id"));
      jdbc.update(
        "insert into claim_activity(claim_id,actor_id,action,from_status,to_status,reason,created_at) values(?,?,?,'APPROVED',?,?,now())",
        c.get("claim_id"), actor, reopen ? "REVOKE_APPROVAL" : "DISPUTE_RESOLVE", next, activityNote
      );
    }
    if (Set.of("HANDOVER_COMPLETED", "HANDOVER_REOPEN").contains(action)) {
      jdbc.update("update p2p_claim_approval set closed_at=now() where approved_claim_id=? and closed_at is null",
          c.get("claim_id"));
    }
    if ("HANDOVER_REOPEN".equals(action)) {
      for (var waiting : jdbc.queryForList("""
          select claim_id,claimant_id from claim
          where post_id=? and status='PENDING' and claim_type='P2P' and claim_id<>?
          """, c.get("post_id"),c.get("claim_id"))) {
        notifications.notifyUser(id(waiting, "claimant_id"), "CLAIM_REVIEW_REOPENED",
            "Yêu cầu nhận đồ được xét duyệt lại", "Hồ sơ đang chờ của bạn tiếp tục được xét duyệt.",
            "/bai-dang/" + c.get("post_id") + "/claims/" + waiting.get("claim_id"), false);
      }
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
      "Cần ghi kết luận xử lý (tối đa 5.000 ký tự)."
    );
    return value.strip();
  }
}
