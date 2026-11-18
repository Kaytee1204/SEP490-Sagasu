package com.example.base.service.admin;

import com.example.base.service.admin.AdminGovernanceService.AuditLogEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminGovernanceServiceTest {

    @Mock
    private JdbcTemplate jdbc;

    private AdminGovernanceService service;

    @BeforeEach
    void setUp() {
        service = new AdminGovernanceService(jdbc);
    }

    @Test
    void getAuditLogs_returnsPaginatedContentAndSummary() {
        when(jdbc.queryForObject(contains("select count(*) from unified_logs"), eq(Long.class), any(Object[].class)))
                .thenReturn(2L);
        AuditLogEntry entry = new AuditLogEntry(
                "SEC-1", 1L, "SECURITY", "LOGIN_SUCCESS", true,
                10L, "Admin", "admin@sagasu.vn", "ADMIN",
                "127.0.0.1", "Mozilla/5.0", "ACCOUNT", 10L,
                "Đăng nhập hệ thống thành công", "{}", OffsetDateTime.now()
        );
        when(jdbc.query(contains("select u.* from unified_logs"), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(entry));
        when(jdbc.queryForMap(contains("count(*) as total_all"))).thenReturn(Map.of(
                "total_all", 10L,
                "security_count", 6L,
                "claim_count", 2L,
                "report_count", 2L,
                "failed_security_count", 1L,
                "today_count", 3L
        ));
        when(jdbc.queryForList(contains("select distinct event_type"), eq(String.class)))
                .thenReturn(List.of("CLAIM_SUBMIT", "LOGIN_SUCCESS"));

        Map<String, Object> result = service.getAuditLogs(
                1, 15, "SECURITY", "LOGIN_SUCCESS", "ADMIN", true, "admin", "2026-01-01", "2026-12-31"
        );

        assertEquals(1, result.get("page"));
        assertEquals(2L, result.get("totalElements"));
        List<?> content = (List<?>) result.get("content");
        assertEquals(1, content.size());
        Map<?, ?> summary = (Map<?, ?>) result.get("summary");
        assertEquals(10L, summary.get("totalAll"));
        assertEquals(6L, summary.get("securityCount"));
    }

    @Test
    void getDashboardOverview_returnsAggregatedMetrics() {
        when(jdbc.queryForMap(contains("from accounts"))).thenReturn(Map.of(
                "total", 20L, "active", 18L, "suspended", 1L, "deleted", 1L,
                "role_admin", 1L, "role_manager", 2L, "role_approver", 3L, "role_user", 13L
        ));
        Map<String, Object> postMap = new java.util.HashMap<>();
        postMap.put("total", 40L);
        postMap.put("lost", 15L);
        postMap.put("found", 25L);
        postMap.put("open_count", 22L);
        postMap.put("reserved_count", 5L);
        postMap.put("returned_count", 10L);
        postMap.put("closed_count", 3L);
        postMap.put("pending_moderation", 2L);
        postMap.put("hidden_moderation", 1L);
        postMap.put("office_custody", 12L);
        postMap.put("p2p_custody", 28L);
        when(jdbc.queryForMap(contains("from post"))).thenReturn(postMap);
        when(jdbc.queryForMap(contains("from claim"))).thenReturn(Map.of(
                "total", 30L, "pending", 8L, "needs_info", 2L, "approved", 6L,
                "rejected", 4L, "completed", 9L, "cancelled", 1L,
                "p2p_count", 20L, "office_count", 10L
        ));
        when(jdbc.queryForMap(contains("from report"))).thenReturn(Map.of(
                "total", 12L, "open_count", 3L, "in_review", 2L, "resolved", 5L,
                "rejected", 2L, "handover_issues", 1L, "active_reasons", 28L, "total_reasons", 33L
        ));
        when(jdbc.queryForMap(contains("total_receipts"))).thenReturn(Map.of(
                "total_receipts", 14L, "in_storage", 9L, "returned_receipts", 5L,
                "pending_transfers", 2L, "active_locations", 8L, "active_categories", 10L
        ));
        when(jdbc.queryForMap(contains("total_events"))).thenReturn(Map.of(
                "total_events", 120L, "security_events", 80L, "failed_logins", 4L, "today_events", 15L
        ));
        when(jdbc.query(anyString(), any(RowMapper.class))).thenReturn(List.of());

        Map<String, Object> overview = service.getDashboardOverview();

        assertNotNull(overview.get("accounts"));
        assertNotNull(overview.get("posts"));
        assertNotNull(overview.get("claims"));
        assertNotNull(overview.get("reports"));
        assertNotNull(overview.get("office"));
        assertNotNull(overview.get("audit"));
    }
}
