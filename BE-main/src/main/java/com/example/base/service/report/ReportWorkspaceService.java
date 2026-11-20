package com.example.base.service.report;

import com.example.base.exception.AppException;
import com.example.base.exception.ErrorCode;
import com.example.base.security.CustomUserDetails;
import com.example.base.service.office.OfficeAccess;
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
                select report_id, bool_or(responded_at is null) as unanswered
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
                    r.status in ('OPEN', 'IN_REVIEW') and coalesce(i.unanswered, false) as needs_reply,
                    case when p.account_id = :actor then approved.claim_id end as finder_approved_claim_id,
                    coalesce(r.status = 'RESOLVED' and p.account_id = :actor
                        and approved.claim_id is not null
                        and approved.claimant_id <> :actor
                        and p.deleted_at is null and p.post_type = 'FOUND' and p.moderation_status = 'VISIBLE'
                        and not exists (select 1 from item_receipt ir where ir.post_id = p.post_id)
                        and not exists (select 1 from p2p_handover h where h.claim_id = approved.claim_id)
                        and not exists (select 1 from report_hold h where h.post_id = p.post_id and h.released_at is null)
                        and not exists (
                            select 1 from claim selected
                            join p2p_claim_approval round on round.approved_claim_id = selected.claim_id
                            join p2p_claim_appeal other on other.approval_id = round.approval_id
                            join report pending on pending.report_id = other.report_id
                            where selected.post_id = p.post_id and pending.status in ('OPEN', 'IN_REVIEW')
                        ), false) as needs_reopen,
                    r.resolution_note, case when appeal.report_id is null then r.reason_label_snapshot end as report_reason
                from visible_reports v join report r using (report_id)
                %s
                left join my_inquiries i on i.report_id = r.report_id
            )
            """.formatted(ReportInquiryService.REPORT_SUBJECT_COLUMNS, ReportInquiryService.PARTICIPANT_REPORT_JOINS);
    private static final String FILTERS = """
            from workspace
            where mine = :mine
                and (:status = 'ALL'
                    or (:status = 'IN_PROGRESS' and status in ('OPEN', 'IN_REVIEW'))
                    or (:status = 'FINISHED' and status in ('RESOLVED', 'REJECTED')))
                and (:type = 'ALL' or report_type = :type
                    or (:type = 'VIOLATION' and report_type in ('POST', 'USER', 'CLAIM', 'MESSAGE')))
            """;
    private static final String LIST_FIELDS = """
            select report_id, status, created_at, updated_at, mine, report_type, report_subject,
                report_subject_id, needs_reply, needs_reopen
            """;

    private final OfficeAccess access;
    private final ReportService reports;
    private final ReportInquiryService inquiries;
    private final NamedParameterJdbcTemplate sql;

    public ReportWorkspaceService(OfficeAccess access, ReportService reports, ReportInquiryService inquiries,
                                  JdbcTemplate jdbc) {
        this.access = access;
        this.reports = reports;
        this.inquiries = inquiries;
        this.sql = new NamedParameterJdbcTemplate(jdbc);
    }

    public enum Status { ALL, IN_PROGRESS, FINISHED }
    public enum Type { ALL, VIOLATION, HANDOVER_ISSUE, CLAIM_APPEAL }

    public record Page(List<Map<String, Object>> content, long totalElements, long totalPages) {}
    public record Detail(Map<String, Object> report, ReportService.Detail submission,
                         List<Map<String, Object>> inquiries) {}

    public Page list(int page, boolean mine, Status status, Type type, CustomUserDetails user) {
        if (page < 1) throw new AppException(ErrorCode.WORKFLOW_INPUT_INVALID);
        long actor = access.require(user).getId();
        var params = Map.of("actor", actor, "mine", mine, "status", status.name(), "type", type.name(),
                "offset", (page - 1L) * PAGE_SIZE, "size", PAGE_SIZE);
        long count = sql.queryForObject(WORKSPACE + "select count(*) " + FILTERS, params, Long.class);
        long totalPages = (count + PAGE_SIZE - 1) / PAGE_SIZE;
        var rows = page > totalPages ? List.<Map<String, Object>>of() : sql.queryForList(
                WORKSPACE + LIST_FIELDS + FILTERS + """
                order by (needs_reply or needs_reopen) desc, updated_at desc, report_id desc
                limit :size offset :offset
                """, params);
        return new Page(rows, count, totalPages);
    }

    public Map<String, Object> actionCounts(CustomUserDetails user) {
        return sql.queryForMap(WORKSPACE + """
                select count(*) filter (where mine) as mine, count(*) filter (where not mine) as related
                from workspace where needs_reply or needs_reopen
                """, Map.of("actor", access.require(user).getId()));
    }

    public Detail detail(long reportId, CustomUserDetails user) {
        long actor = access.require(user).getId();
        var report = sql.queryForList(WORKSPACE + "select * from workspace where report_id = :reportId",
                        Map.of("actor", actor, "reportId", reportId))
                .stream().findFirst().orElseThrow(() -> new AppException(ErrorCode.REPORT_NOT_FOUND));
        var submission = Boolean.TRUE.equals(report.get("mine")) ? reports.mineDetail(reportId, user) : null;
        return new Detail(report, submission, inquiries.forReport(reportId, actor));
    }
}
