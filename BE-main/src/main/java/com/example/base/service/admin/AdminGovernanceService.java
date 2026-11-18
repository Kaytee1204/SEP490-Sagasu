package com.example.base.service.admin;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminGovernanceService {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Set<String> VALID_SOURCES = Set.of("ALL", "SECURITY", "CLAIM", "REPORT");
    private static final Set<String> VALID_ROLES = Set.of("ADMIN", "MANAGER", "APPROVER", "USER");

    private final JdbcTemplate jdbc;

    public record AuditLogEntry(
            String uid,
            long id,
            String source,
            String eventType,
            boolean success,
            Long actorId,
            String actorName,
            String actorEmail,
            String actorRole,
            String ipAddress,
            String userAgent,
            String targetType,
            Long targetId,
            String summary,
            String details,
            OffsetDateTime createdAt
    ) {}

    private static final String UNIFIED_AUDIT_CTE = """
            with unified_logs as (
                select
                    'SEC-' || l.id as uid,
                    l.id as id,
                    'SECURITY' as source,
                    l.event_type as event_type,
                    l.success as success,
                    a.id as actor_id,
                    coalesce(nullif(a.full_name, ''), a.email, 'Hệ thống / Khách') as actor_name,
                    coalesce(a.email, '—') as actor_email,
                    coalesce(a.role, 'USER') as actor_role,
                    coalesce(host(l.ip_address), '—') as ip_address,
                    coalesce(l.user_agent, '—') as user_agent,
                    'ACCOUNT' as target_type,
                    l.account_id as target_id,
                    case l.event_type
                        when 'LOGIN_SUCCESS' then 'Đăng nhập hệ thống thành công'
                        when 'LOGIN_PASSWORD' then 'Xác thực mật khẩu đăng nhập'
                        when 'LOGIN_FAILED' then 'Đăng nhập thất bại (sai thông tin xác thực)'
                        when 'ACCOUNT_LOCKED' then 'Tài khoản bị khóa tạm thời do đăng nhập sai nhiều lần'
                        when 'PASSWORD_RESET_OTP_SENT' then 'Yêu cầu gửi mã OTP khôi phục mật khẩu'
                        when 'PASSWORD_RESET_SUCCESS' then 'Đặt lại mật khẩu mới thành công'
                        when 'PROFILE_UPDATED' then 'Cập nhật thông tin hồ sơ cá nhân / mật khẩu'
                        else 'Sự kiện bảo mật & tài khoản: ' || l.event_type
                    end as summary,
                    coalesce(l.details::text, '{}') as details,
                    l.created_at as created_at
                from login_audit_logs l
                left join accounts a on a.id = l.account_id

                union all

                select
                    'CLM-' || ca.claim_activity_id as uid,
                    ca.claim_activity_id as id,
                    'CLAIM' as source,
                    'CLAIM_' || ca.action as event_type,
                    true as success,
                    a.id as actor_id,
                    coalesce(nullif(a.full_name, ''), a.email, 'Người dùng') as actor_name,
                    coalesce(a.email, '—') as actor_email,
                    coalesce(a.role, 'USER') as actor_role,
                    '—' as ip_address,
                    'Web Application' as user_agent,
                    'CLAIM' as target_type,
                    ca.claim_id as target_id,
                    'Xử lý yêu cầu nhận đồ #' || ca.claim_id || ' (' || ca.action || coalesce(' từ ' || ca.from_status, '') || coalesce(' -> ' || ca.to_status, '') || ')' as summary,
                    coalesce(ca.reason, 'Không có ghi chú bổ sung') as details,
                    ca.created_at as created_at
                from claim_activity ca
                left join accounts a on a.id = ca.actor_id

                union all

                select
                    'RPT-' || ra.activity_id as uid,
                    ra.activity_id as id,
                    'REPORT' as source,
                    'REPORT_' || ra.action as event_type,
                    true as success,
                    a.id as actor_id,
                    coalesce(nullif(a.full_name, ''), a.email, 'Quản lý') as actor_name,
                    coalesce(a.email, '—') as actor_email,
                    coalesce(a.role, 'MANAGER') as actor_role,
                    '—' as ip_address,
                    'Web Application' as user_agent,
                    'REPORT' as target_type,
                    ra.report_id as target_id,
                    'Điều tra báo cáo #' || ra.report_id || ' (' || ra.action || coalesce(' -> ' || ra.to_status, '') || ')' as summary,
                    coalesce(nullif(ra.public_note, ''), nullif(ra.internal_note, ''), 'Thao tác trên hồ sơ báo cáo #' || ra.report_id) as details,
                    ra.created_at as created_at
                from report_activity ra
                left join accounts a on a.id = ra.actor_id
            )
            """;

    @Transactional(readOnly = true)
    public Map<String, Object> getAuditLogs(
            int page,
            int size,
            String source,
            String eventType,
            String role,
            Boolean success,
            String keyword,
            String fromDate,
            String toDate
    ) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));

        StringBuilder where = new StringBuilder(" where 1 = 1");
        List<Object> params = new ArrayList<>();

        if (source != null && !source.isBlank() && !"ALL".equalsIgnoreCase(source.trim())) {
            String normalizedSource = source.trim().toUpperCase(Locale.ROOT);
            if (VALID_SOURCES.contains(normalizedSource)) {
                where.append(" and u.source = ?");
                params.add(normalizedSource);
            }
        }

        if (eventType != null && !eventType.isBlank() && !"ALL".equalsIgnoreCase(eventType.trim())) {
            where.append(" and upper(u.event_type) = ?");
            params.add(eventType.trim().toUpperCase(Locale.ROOT));
        }

        if (role != null && !role.isBlank() && !"ALL".equalsIgnoreCase(role.trim())) {
            String normalizedRole = role.trim().toUpperCase(Locale.ROOT);
            if (VALID_ROLES.contains(normalizedRole)) {
                where.append(" and u.actor_role = ?");
                params.add(normalizedRole);
            }
        }

        if (success != null) {
            where.append(" and u.success = ?");
            params.add(success);
        }

        if (keyword != null && !keyword.isBlank()) {
            String like = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
            where.append(" and (lower(u.actor_name) like ? or lower(u.actor_email) like ? or lower(u.event_type) like ? or lower(u.summary) like ? or lower(u.details) like ? or lower(u.ip_address) like ?)");
            for (int i = 0; i < 6; i++) {
                params.add(like);
            }
        }

        if (fromDate != null && !fromDate.isBlank()) {
            try {
                LocalDate start = LocalDate.parse(fromDate.trim());
                where.append(" and u.created_at >= ?");
                params.add(start.atStartOfDay(VN_ZONE).toOffsetDateTime());
            } catch (Exception ignored) {
                // Ignore invalid date filter
            }
        }

        if (toDate != null && !toDate.isBlank()) {
            try {
                LocalDate end = LocalDate.parse(toDate.trim()).plusDays(1);
                where.append(" and u.created_at < ?");
                params.add(end.atStartOfDay(VN_ZONE).toOffsetDateTime());
            } catch (Exception ignored) {
                // Ignore invalid date filter
            }
        }

        Long totalElements = jdbc.queryForObject(
                UNIFIED_AUDIT_CTE + " select count(*) from unified_logs u" + where,
                Long.class,
                params.toArray()
        );
        long total = totalElements == null ? 0L : totalElements;
        int totalPages = (int) Math.max(1, (total + safeSize - 1) / safeSize);

        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(safeSize);
        pageParams.add((long) (safePage - 1) * safeSize);

        List<AuditLogEntry> content = jdbc.query(
                UNIFIED_AUDIT_CTE + " select u.* from unified_logs u" + where + " order by u.created_at desc, u.id desc limit ? offset ?",
                (rs, rowNum) -> mapAuditLogRow(rs),
                pageParams.toArray()
        );

        Map<String, Object> summaryCounts = jdbc.queryForMap(
                UNIFIED_AUDIT_CTE + """
                select
                    count(*) as total_all,
                    count(*) filter (where source = 'SECURITY') as security_count,
                    count(*) filter (where source = 'CLAIM') as claim_count,
                    count(*) filter (where source = 'REPORT') as report_count,
                    count(*) filter (where source = 'SECURITY' and not success) as failed_security_count,
                    count(*) filter (where created_at >= (current_date at time zone 'Asia/Ho_Chi_Minh')) as today_count
                from unified_logs
                """
        );

        List<String> eventTypes = jdbc.queryForList(
                UNIFIED_AUDIT_CTE + " select distinct event_type from unified_logs order by event_type asc",
                String.class
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", content);
        result.put("page", safePage);
        result.put("size", safeSize);
        result.put("totalElements", total);
        result.put("totalPages", totalPages);
        result.put("summary", Map.of(
                "totalAll", toLong(summaryCounts.get("total_all")),
                "securityCount", toLong(summaryCounts.get("security_count")),
                "claimCount", toLong(summaryCounts.get("claim_count")),
                "reportCount", toLong(summaryCounts.get("report_count")),
                "failedSecurityCount", toLong(summaryCounts.get("failed_security_count")),
                "todayCount", toLong(summaryCounts.get("today_count"))
        ));
        result.put("eventTypes", eventTypes);
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getDashboardOverview() {
        Map<String, Object> out = new LinkedHashMap<>();

        // 1. Account stats
        Map<String, Object> accountRow = jdbc.queryForMap("""
                select
                    count(*) as total,
                    count(*) filter (where status = 'ACTIVE') as active,
                    count(*) filter (where status = 'SUSPENDED') as suspended,
                    count(*) filter (where status = 'DELETED') as deleted,
                    count(*) filter (where role = 'ADMIN' and status <> 'DELETED') as role_admin,
                    count(*) filter (where role = 'MANAGER' and status <> 'DELETED') as role_manager,
                    count(*) filter (where role = 'APPROVER' and status <> 'DELETED') as role_approver,
                    count(*) filter (where role = 'USER' and status <> 'DELETED') as role_user
                from accounts
                """);
        out.put("accounts", Map.of(
                "total", toLong(accountRow.get("total")),
                "active", toLong(accountRow.get("active")),
                "suspended", toLong(accountRow.get("suspended")),
                "deleted", toLong(accountRow.get("deleted")),
                "byRole", Map.of(
                        "ADMIN", toLong(accountRow.get("role_admin")),
                        "MANAGER", toLong(accountRow.get("role_manager")),
                        "APPROVER", toLong(accountRow.get("role_approver")),
                        "USER", toLong(accountRow.get("role_user"))
                )
        ));

        // 2. Post stats
        Map<String, Object> postRow = jdbc.queryForMap("""
                select
                    count(*) filter (where deleted_at is null) as total,
                    count(*) filter (where deleted_at is null and post_type = 'LOST') as lost,
                    count(*) filter (where deleted_at is null and post_type = 'FOUND') as found,
                    count(*) filter (where deleted_at is null and status = 'OPEN') as open_count,
                    count(*) filter (where deleted_at is null and status = 'RESERVED') as reserved_count,
                    count(*) filter (where deleted_at is null and status = 'RETURNED') as returned_count,
                    count(*) filter (where deleted_at is null and status = 'CLOSED') as closed_count,
                    count(*) filter (where deleted_at is null and moderation_status = 'PENDING') as pending_moderation,
                    count(*) filter (where deleted_at is null and moderation_status = 'HIDDEN') as hidden_moderation,
                    count(*) filter (where deleted_at is null and custody_mode = 'OFFICE') as office_custody,
                    count(*) filter (where deleted_at is null and custody_mode = 'P2P') as p2p_custody
                from post
                """);
        Map<String, Object> postStats = new LinkedHashMap<>();
        postStats.put("total", toLong(postRow.get("total")));
        postStats.put("lost", toLong(postRow.get("lost")));
        postStats.put("found", toLong(postRow.get("found")));
        postStats.put("open", toLong(postRow.get("open_count")));
        postStats.put("reserved", toLong(postRow.get("reserved_count")));
        postStats.put("returned", toLong(postRow.get("returned_count")));
        postStats.put("closed", toLong(postRow.get("closed_count")));
        postStats.put("pendingModeration", toLong(postRow.get("pending_moderation")));
        postStats.put("hiddenModeration", toLong(postRow.get("hidden_moderation")));
        postStats.put("officeCustody", toLong(postRow.get("office_custody")));
        postStats.put("p2pCustody", toLong(postRow.get("p2p_custody")));
        out.put("posts", postStats);

        // 3. Claim stats
        Map<String, Object> claimRow = jdbc.queryForMap("""
                select
                    count(*) as total,
                    count(*) filter (where status = 'PENDING') as pending,
                    count(*) filter (where status = 'NEEDS_INFO') as needs_info,
                    count(*) filter (where status = 'APPROVED') as approved,
                    count(*) filter (where status = 'REJECTED') as rejected,
                    count(*) filter (where status = 'COMPLETED') as completed,
                    count(*) filter (where status = 'CANCELLED') as cancelled,
                    count(*) filter (where claim_type = 'P2P') as p2p_count,
                    count(*) filter (where claim_type = 'OFFICE') as office_count
                from claim
                """);
        Map<String, Object> claimStats = new LinkedHashMap<>();
        claimStats.put("total", toLong(claimRow.get("total")));
        claimStats.put("pending", toLong(claimRow.get("pending")));
        claimStats.put("needsInfo", toLong(claimRow.get("needs_info")));
        claimStats.put("approved", toLong(claimRow.get("approved")));
        claimStats.put("rejected", toLong(claimRow.get("rejected")));
        claimStats.put("completed", toLong(claimRow.get("completed")));
        claimStats.put("cancelled", toLong(claimRow.get("cancelled")));
        claimStats.put("p2pCount", toLong(claimRow.get("p2p_count")));
        claimStats.put("officeCount", toLong(claimRow.get("office_count")));
        out.put("claims", claimStats);

        // 4. Report & Reason stats
        Map<String, Object> reportRow = jdbc.queryForMap("""
                select
                    count(*) as total,
                    count(*) filter (where status = 'OPEN') as open_count,
                    count(*) filter (where status = 'IN_REVIEW') as in_review,
                    count(*) filter (where status = 'RESOLVED') as resolved,
                    count(*) filter (where status = 'REJECTED') as rejected,
                    count(*) filter (where kind = 'HANDOVER_ISSUE') as handover_issues,
                    (select count(*) from report_reason where active = true) as active_reasons,
                    (select count(*) from report_reason) as total_reasons
                from report
                """);
        out.put("reports", Map.of(
                "total", toLong(reportRow.get("total")),
                "open", toLong(reportRow.get("open_count")),
                "inReview", toLong(reportRow.get("in_review")),
                "resolved", toLong(reportRow.get("resolved")),
                "rejected", toLong(reportRow.get("rejected")),
                "handoverIssues", toLong(reportRow.get("handover_issues")),
                "activeReasonsCount", toLong(reportRow.get("active_reasons")),
                "totalReasonsCount", toLong(reportRow.get("total_reasons"))
        ));

        // 5. Office & Storage stats
        Map<String, Object> officeRow = jdbc.queryForMap("""
                select
                    (select count(*) from item_receipt) as total_receipts,
                    (select count(*) from item_receipt where status = 'IN_STORAGE') as in_storage,
                    (select count(*) from item_receipt where status = 'RETURNED') as returned_receipts,
                    (select count(*) from office_transfer where status = 'PENDING') as pending_transfers,
                    (select count(*) from storage_location where is_active = true) as active_locations,
                    (select count(*) from category where is_active = true) as active_categories
                """);
        out.put("office", Map.of(
                "totalReceipts", toLong(officeRow.get("total_receipts")),
                "inStorage", toLong(officeRow.get("in_storage")),
                "returnedReceipts", toLong(officeRow.get("returned_receipts")),
                "pendingTransfers", toLong(officeRow.get("pending_transfers")),
                "activeLocations", toLong(officeRow.get("active_locations")),
                "activeCategories", toLong(officeRow.get("active_categories"))
        ));

        // 6. Audit overview
        Map<String, Object> auditRow = jdbc.queryForMap(
                UNIFIED_AUDIT_CTE + """
                select
                    count(*) as total_events,
                    count(*) filter (where source = 'SECURITY') as security_events,
                    count(*) filter (where source = 'SECURITY' and not success) as failed_logins,
                    count(*) filter (where created_at >= (current_date at time zone 'Asia/Ho_Chi_Minh')) as today_events
                from unified_logs
                """
        );
        out.put("audit", Map.of(
                "totalEvents", toLong(auditRow.get("total_events")),
                "securityEvents", toLong(auditRow.get("security_events")),
                "failedLogins", toLong(auditRow.get("failed_logins")),
                "todayEvents", toLong(auditRow.get("today_events"))
        ));

        // 7. 7-day daily trend (Posts, Claims, Reports)
        DateTimeFormatter labelFmt = DateTimeFormatter.ofPattern("dd/MM");
        List<Map<String, Object>> dailyTrends = jdbc.query("""
                with days as (
                    select generate_series(
                        (current_date at time zone 'Asia/Ho_Chi_Minh')::date - interval '6 days',
                        (current_date at time zone 'Asia/Ho_Chi_Minh')::date,
                        interval '1 day'
                    )::date as day
                )
                select
                    d.day,
                    (select count(*) from post p where p.deleted_at is null and (p.created_at at time zone 'Asia/Ho_Chi_Minh')::date = d.day) as posts_count,
                    (select count(*) from claim c where (c.created_at at time zone 'Asia/Ho_Chi_Minh')::date = d.day) as claims_count,
                    (select count(*) from report r where (r.created_at at time zone 'Asia/Ho_Chi_Minh')::date = d.day) as reports_count
                from days d
                order by d.day asc
                """, (rs, rowNum) -> {
            LocalDate day = rs.getObject("day", LocalDate.class);
            return Map.of(
                    "date", day.toString(),
                    "label", day.format(labelFmt),
                    "postsCount", rs.getLong("posts_count"),
                    "claimsCount", rs.getLong("claims_count"),
                    "reportsCount", rs.getLong("reports_count")
            );
        });
        out.put("dailyTrends", dailyTrends);

        // 8. Top categories breakdown
        List<Map<String, Object>> topCategories = jdbc.query("""
                select
                    c.category_id as id,
                    c.name as name,
                    c.is_active as active,
                    count(p.post_id) filter (where p.deleted_at is null) as total_posts,
                    count(p.post_id) filter (where p.deleted_at is null and p.post_type = 'LOST') as lost_count,
                    count(p.post_id) filter (where p.deleted_at is null and p.post_type = 'FOUND') as found_count
                from category c
                left join post p on p.category_id = c.category_id
                group by c.category_id, c.name, c.is_active
                order by total_posts desc, c.name asc
                limit 6
                """, (rs, rowNum) -> Map.of(
                "id", rs.getLong("id"),
                "name", rs.getString("name"),
                "active", rs.getBoolean("active"),
                "totalPosts", rs.getLong("total_posts"),
                "lostCount", rs.getLong("lost_count"),
                "foundCount", rs.getLong("found_count")
        ));
        out.put("topCategories", topCategories);

        // 9. Recent posts
        List<Map<String, Object>> recentPosts = jdbc.query("""
                select
                    p.post_id as id,
                    p.title as title,
                    p.post_type as post_type,
                    p.status as status,
                    p.moderation_status as moderation_status,
                    p.custody_mode as custody_mode,
                    coalesce(c.name, 'Khác') as category_name,
                    coalesce(nullif(a.full_name, ''), a.email, 'Ẩn danh') as author_name,
                    coalesce(p.location_text, 'Chưa cập nhật') as location_text,
                    p.created_at as created_at
                from post p
                left join category c on c.category_id = p.category_id
                left join accounts a on a.id = p.account_id
                where p.deleted_at is null
                order by p.created_at desc, p.post_id desc
                limit 6
                """, (rs, rowNum) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getLong("id"));
            m.put("title", rs.getString("title"));
            m.put("postType", rs.getString("post_type"));
            m.put("status", rs.getString("status"));
            m.put("moderationStatus", rs.getString("moderation_status"));
            m.put("custodyMode", rs.getString("custody_mode"));
            m.put("categoryName", rs.getString("category_name"));
            m.put("authorName", rs.getString("author_name"));
            m.put("locationText", rs.getString("location_text"));
            m.put("createdAt", toOffsetDateTime(rs.getTimestamp("created_at")));
            return m;
        });
        out.put("recentPosts", recentPosts);

        // 10. Pending reports
        List<Map<String, Object>> pendingReports = jdbc.query("""
                select
                    r.report_id as id,
                    r.kind as kind,
                    r.status as status,
                    r.reason_code as reason_code,
                    r.reason_label_snapshot as reason_label,
                    coalesce(r.description, '') as description,
                    coalesce(nullif(a.full_name, ''), a.email, 'Người dùng') as reporter_name,
                    case
                        when r.post_id is not null then 'POST'
                        when r.claim_id is not null then 'CLAIM'
                        when r.message_id is not null then 'MESSAGE'
                        else 'USER'
                    end as target_type,
                    coalesce(r.post_id, r.claim_id, r.message_id, r.reported_account_id) as target_id,
                    r.created_at as created_at
                from report r
                left join accounts a on a.id = r.reporter_id
                where r.status in ('OPEN', 'IN_REVIEW')
                order by case when r.kind = 'HANDOVER_ISSUE' then 0 else 1 end, r.created_at desc
                limit 6
                """, (rs, rowNum) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getLong("id"));
            m.put("kind", rs.getString("kind"));
            m.put("status", rs.getString("status"));
            m.put("reasonCode", rs.getString("reason_code"));
            m.put("reasonLabel", rs.getString("reason_label"));
            m.put("description", rs.getString("description"));
            m.put("reporterName", rs.getString("reporter_name"));
            m.put("targetType", rs.getString("target_type"));
            m.put("targetId", rs.getObject("target_id") == null ? null : rs.getLong("target_id"));
            m.put("createdAt", toOffsetDateTime(rs.getTimestamp("created_at")));
            return m;
        });
        out.put("pendingReports", pendingReports);

        // 11. Recent audit logs
        List<AuditLogEntry> recentAuditLogs = jdbc.query(
                UNIFIED_AUDIT_CTE + " select u.* from unified_logs u order by u.created_at desc, u.id desc limit 8",
                (rs, rowNum) -> mapAuditLogRow(rs)
        );
        out.put("recentAuditLogs", recentAuditLogs);

        return out;
    }

    private AuditLogEntry mapAuditLogRow(ResultSet rs) throws SQLException {
        Long actorId = rs.getObject("actor_id") == null ? null : rs.getLong("actor_id");
        Long targetId = rs.getObject("target_id") == null ? null : rs.getLong("target_id");
        return new AuditLogEntry(
                rs.getString("uid"),
                rs.getLong("id"),
                rs.getString("source"),
                rs.getString("event_type"),
                rs.getBoolean("success"),
                actorId,
                rs.getString("actor_name"),
                rs.getString("actor_email"),
                rs.getString("actor_role"),
                rs.getString("ip_address"),
                rs.getString("user_agent"),
                rs.getString("target_type"),
                targetId,
                rs.getString("summary"),
                rs.getString("details"),
                toOffsetDateTime(rs.getTimestamp("created_at"))
        );
    }

    private static long toLong(Object val) {
        return val instanceof Number n ? n.longValue() : 0L;
    }

    private static OffsetDateTime toOffsetDateTime(Timestamp ts) {
        return ts == null ? null : ts.toInstant().atZone(VN_ZONE).toOffsetDateTime();
    }
}
