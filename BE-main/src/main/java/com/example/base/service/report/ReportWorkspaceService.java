package com.example.base.service.report;

import com.example.base.exception.AppException;
import com.example.base.exception.ErrorCode;
import com.example.base.security.CustomUserDetails;
import com.example.base.service.office.OfficeAccess;
import com.example.base.storage.ImageStorage;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ReportWorkspaceService {
    private static final int PAGE_SIZE = 12;
    private static final String WORKSPACE = """
            with my_inquiries as (
                select report_id, bool_or(responded_at is null and closed_at is null) as unanswered
                from report_inquiry
                where recipient_id = :actor
                group by report_id
            ), visible_reports as (
                select report_id from report where reporter_id = :actor
                union select report_id from my_inquiries
                union select report_id from report_recipient where account_id = :actor
            ), workspace as (
                select r.report_id, r.status, r.created_at, r.updated_at, r.resolved_at,
                    r.reporter_id = :actor as mine, %s,
                    coalesce(i.unanswered, false) and (
                        r.status in ('OPEN', 'IN_REVIEW') or (
                            r.status = 'RESOLVED' and chosen.claim_id is not null and p.deleted_at is null
                            and exists (select 1 from report_decision d
                                where d.report_id=r.report_id and d.action='REQUEST_ITEM_RECOVERY')
                        )
                    ) as needs_reply,
                    case when p.account_id = :actor then chosen.claim_id end as finder_approved_claim_id,
                    case when c.claim_type='P2P' and (c.claimant_id=:actor or p.account_id=:actor) then c.claim_id
                         when chosen.claimant_id=:actor then chosen.claim_id end as participant_claim_id,
                    p.account_id=:actor as post_owner,
                    follow_up.step as appeal_step, approval.appeal_until,
                    coalesce(follow_up.step='READY_TO_REOPEN' and p.account_id=:actor
                        and chosen.claimant_id<>:actor, false) as needs_reopen,
                    r.resolution_note, r.reason_label_snapshot as report_reason,
                    case when p.deleted_at is null and (p.moderation_status='VISIBLE' or p.account_id=:actor)
                         then p.post_id end as thumbnail_post_id,
                    holder.full_name as recovery_holder_name, recovery.due_at as recovery_due_at,
                    recovery.confirmed_at as recovery_confirmed_at,
                    case when r.status in ('OPEN','IN_REVIEW')
                        or (r.status = 'RESOLVED' and approval.approval_id is not null)
                        or recovery.confirmed_at is not null then (
                        select decision.conclusion from report_decision decision
                        where decision.report_id=r.report_id and decision.action='REQUEST_ITEM_RECOVERY'
                        order by decision.decision_id desc limit 1
                    ) end as recovery_note
                from visible_reports v join report r using (report_id)
                %s
                cross join lateral (select %s as waiting_other_appeals offset 0) pending
                cross join lateral (select %s as step offset 0) follow_up
                left join my_inquiries i on i.report_id = r.report_id
                left join report_recovery recovery on recovery.report_id = r.report_id
                left join accounts holder on holder.id = recovery.holder_id
            )
            """.formatted(ReportInquiryService.REPORT_SUBJECT_COLUMNS, ReportInquiryService.PARTICIPANT_REPORT_JOINS,
                    ReportQueueSql.OTHER_APPEALS_PENDING, ReportQueueSql.APPEAL_STEP);
    private static final String SCOPED_REPORTS = WORKSPACE + """
            , scoped_reports as (
                select workspace.*, case
                    when needs_reply or needs_reopen then 'ACTION'
                    when status in ('OPEN', 'IN_REVIEW') or appeal_step is not null then 'IN_PROGRESS'
                    when status in ('RESOLVED', 'REJECTED') then 'FINISHED'
                end as tab
                from workspace
                where mine = :mine and (:type = 'ALL' or report_type = :type
                    or (:type = 'VIOLATION' and report_type in ('POST', 'USER', 'CLAIM', 'MESSAGE')))
            )
            """;
    private static final String LIST_FIELDS = """
            select report_id, status, created_at, updated_at, mine, report_type, report_subject,
                report_subject_id, report_reason, thumbnail_post_id, needs_reply, needs_reopen, appeal_step, appeal_until
            """;

    private final OfficeAccess access;
    private final ReportService reports;
    private final ReportInquiryService inquiries;
    private final NamedParameterJdbcTemplate sql;
    private final ImageStorage storage;

    public ReportWorkspaceService(OfficeAccess access, ReportService reports, ReportInquiryService inquiries,
                                  JdbcTemplate jdbc, ImageStorage storage) {
        this.access = access;
        this.reports = reports;
        this.inquiries = inquiries;
        this.sql = new NamedParameterJdbcTemplate(jdbc);
        this.storage = storage;
    }

    public enum Status { ALL, ACTION, IN_PROGRESS, FINISHED }
    public enum Type { ALL, VIOLATION, HANDOVER_ISSUE, CLAIM_APPEAL }

    public record Page(List<Map<String, Object>> content, long totalElements, long totalPages,
                       Map<Status, Long> tabCounts) {}
    public record Detail(Map<String, Object> report, ReportService.Detail submission,
                         List<Map<String, Object>> inquiries) {}

    public Page list(int page, boolean mine, Status status, Type type, CustomUserDetails user) {
        if (page < 1) throw new AppException(ErrorCode.WORKFLOW_INPUT_INVALID);
        long actor = access.require(user).getId();
        var params = Map.of("actor", actor, "mine", mine, "status", status.name(), "type", type.name(),
                "offset", (page - 1L) * PAGE_SIZE, "size", PAGE_SIZE);
        var counts = sql.queryForMap(SCOPED_REPORTS + """
                select count(*) filter (where :status = 'ALL' or tab = :status) as total,
                       count(*) filter (where tab = 'ACTION') as action_count,
                       count(*) filter (where tab = 'IN_PROGRESS') as in_progress_count
                from scoped_reports
                """, params);
        long count = ((Number) counts.get("total")).longValue();
        long totalPages = (count + PAGE_SIZE - 1) / PAGE_SIZE;
        var rows = page > totalPages ? List.<Map<String, Object>>of() : sql.queryForList(
                SCOPED_REPORTS + ", page_rows as (" + LIST_FIELDS + """
                    from scoped_reports where :status = 'ALL' or tab = :status
                    order by (needs_reply or needs_reopen) desc, updated_at desc, report_id desc
                    limit :size offset :offset
                )
                select page_rows.*, image.storage_key, image.image_url
                from page_rows left join lateral (
                    select storage_key, image_url from post_image
                    where post_id = page_rows.thumbnail_post_id
                    order by sort_order limit 1
                ) image on true
                order by (needs_reply or needs_reopen) desc, updated_at desc, report_id desc
                """, params);
        rows.forEach(row -> {
            row.remove("thumbnail_post_id");
            String key = (String) row.remove("storage_key");
            String fallback = (String) row.remove("image_url");
            if (key != null || fallback != null) row.put("post_thumbnail_url", storage.readUrl(key, fallback));
        });
        return new Page(rows, count, totalPages, Map.of(
                Status.ACTION, ((Number) counts.get("action_count")).longValue(),
                Status.IN_PROGRESS, ((Number) counts.get("in_progress_count")).longValue()));
    }

    public Map<String, Object> actionCounts(CustomUserDetails user) {
        return sql.queryForMap(WORKSPACE + """
                select count(*) filter (where mine) as mine, count(*) filter (where not mine) as related
                from workspace where needs_reply or needs_reopen
                """, Map.of("actor", access.require(user).getId()));
    }

    public Detail detail(long reportId, CustomUserDetails user) {
        long actor = access.require(user).getId();
        var report = sql.queryForList(WORKSPACE + """
                select w.*, d.action as outcome,
                       exists (select 1 from report_decision reopened
                           where reopened.report_id=w.report_id
                             and (reopened.action='APPEAL_REOPEN'
                               or (reopened.action='APPEAL_WRONG_RECIPIENT'
                                 and reopened.after_state->>'approvalReopened'='true'))) as approval_reopened,
                       case when w.post_owner then d.after_state->>'appliedMeasure' end as post_measure
                from workspace w left join lateral (
                    select d.action, d.after_state from report_decision d
                    where d.report_id = w.report_id
                      and d.action in ('VIOLATION', 'NO_VIOLATION', 'INSUFFICIENT_EVIDENCE',
                                       'APPEAL_KEEP', 'APPEAL_REJECT_CLAIM', 'APPEAL_REQUIRE_REVIEW',
                                       'APPEAL_WRONG_RECIPIENT', 'HANDOVER_COMPLETED',
                                       'HANDOVER_CONTINUE', 'HANDOVER_RETRY', 'HANDOVER_REOPEN', 'CLAIM_REJECTED')
                    order by d.decision_id desc limit 1
                ) d on true
                where w.report_id = :reportId
                """,
                        Map.of("actor", actor, "reportId", reportId))
                .stream().findFirst().orElseThrow(() -> new AppException(ErrorCode.REPORT_NOT_FOUND));
        var submission = Boolean.TRUE.equals(report.get("mine")) ? reports.mineDetail(reportId, user) : null;
        return new Detail(report, submission, inquiries.forReport(reportId, actor));
    }
}
