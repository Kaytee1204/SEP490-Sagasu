package com.example.base.service.report;

import static com.example.base.service.report.ReportContextService.id;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;

record ViolationReportGroup(String column, long targetId) {
    static ViolationReportGroup forReport(Map<String, Object> report) {
        for (String column : List.of("post_id", "claim_id", "message_id", "reported_account_id")) {
            Long target = id(report, column);
            if (target != null) return new ViolationReportGroup(column, target);
        }
        throw new IllegalArgumentException("A violation report must have a target");
    }

    static ViolationReportGroup forTarget(String type, long targetId) {
        String column = switch (type) {
            case "POST" -> "post_id";
            case "CLAIM" -> "claim_id";
            case "MESSAGE" -> "message_id";
            case "USER" -> "reported_account_id";
            default -> throw new IllegalArgumentException("Invalid report target");
        };
        return new ViolationReportGroup(column, targetId);
    }

    void lock(JdbcTemplate jdbc) {
        jdbc.query("select pg_advisory_xact_lock(hashtextextended(?, 0))",
                rs -> {}, "report-violation:" + column + ":" + targetId);
    }

    List<Map<String, Object>> openSiblings(JdbcTemplate jdbc, long reportId) {
        return jdbc.queryForList("""
            select * from report where kind='GENERAL' and status in ('OPEN','IN_REVIEW')
              and %s=? and report_id<>? order by report_id
            """.formatted(column), targetId, reportId);
    }

    Long ownerForNewReport(JdbcTemplate jdbc, long reporterId) {
        return jdbc.queryForList("""
            with active as (
              select assigned_to,reporter_id from report
              where kind='GENERAL' and status in ('OPEN','IN_REVIEW') and %s=?
            )
            select owner.id from accounts owner
            where owner.id=(select min(assigned_to) from active having count(distinct assigned_to)=1)
              and owner.id<>? and owner.role='MANAGER' and owner.status='ACTIVE'
              and (owner.locked_until is null or owner.locked_until<now())
              and not exists (select 1 from active where reporter_id=owner.id)
            """.formatted(column), Long.class, targetId, reporterId)
            .stream().findFirst().orElse(null);
    }
}
