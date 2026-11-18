package com.example.base.service.report;

import com.example.base.exception.AppException;
import com.example.base.exception.ErrorCode;
import com.example.base.service.report.ReportReasonService.AdminReasonItem;
import com.example.base.service.report.ReportReasonService.UpsertReasonRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportReasonServiceTest {

    @Mock
    private JdbcTemplate jdbc;

    private ReportReasonService service;

    @BeforeEach
    void setUp() {
        service = new ReportReasonService(jdbc);
    }

    @Test
    void listAdmin_filtersByTargetGroupActiveAndKeyword() {
        AdminReasonItem sample = new AdminReasonItem("POST_SPAM", "POST", "Spam hoặc đăng trùng", false, true, 2, 4L);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of(sample));

        List<AdminReasonItem> result = service.listAdmin("POST", true, "spam");

        assertEquals(1, result.size());
        assertEquals("POST_SPAM", result.get(0).code());
        assertEquals(4L, result.get(0).usageCount());
    }

    @Test
    void listAdmin_invalidGroup_throwsBadRequest() {
        AppException ex = assertThrows(AppException.class, () -> service.listAdmin("INVALID_GROUP", null, null));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
    }

    @Test
    void create_autoGeneratesCodeAndInsertsReason() {
        when(jdbc.queryForObject(contains("select exists"), eq(Boolean.class), anyString())).thenReturn(false);
        when(jdbc.queryForObject(contains("coalesce(max(sort_order)"), eq(Integer.class), eq("POST"))).thenReturn(6);
        AdminReasonItem created = new AdminReasonItem("POST_TIN_GIA_MAO", "POST", "Tin giả mạo", true, true, 7, 0L);
        when(jdbc.query(contains("where r.code = ?"), any(RowMapper.class), eq("POST_TIN_GIA_MAO")))
                .thenReturn(List.of(created));

        AdminReasonItem res = service.create(new UpsertReasonRequest(null, "POST", "Tin giả mạo", true, true, null));

        assertEquals("POST_TIN_GIA_MAO", res.code());
        assertEquals(7, res.sortOrder());
        verify(jdbc).update(contains("insert into report_reason"), eq("POST_TIN_GIA_MAO"), eq("POST"), eq("Tin giả mạo"), eq(true), eq(true), eq(7));
    }

    @Test
    void create_duplicateCode_throwsBadRequest() {
        when(jdbc.queryForObject(contains("select exists"), eq(Boolean.class), eq("POST_SPAM"))).thenReturn(true);

        AppException ex = assertThrows(AppException.class,
                () -> service.create(new UpsertReasonRequest("POST_SPAM", "POST", "Spam", false, true, 1)));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
    }

    @Test
    void update_existingReason_updatesFields() {
        AdminReasonItem existing = new AdminReasonItem("POST_SPAM", "POST", "Spam", false, true, 2, 1L);
        AdminReasonItem updated = new AdminReasonItem("POST_SPAM", "POST", "Spam bài đăng", true, false, 3, 1L);
        when(jdbc.query(contains("where r.code = ?"), any(RowMapper.class), eq("POST_SPAM")))
                .thenReturn(List.of(existing))
                .thenReturn(List.of(updated));

        AdminReasonItem res = service.update("post_spam", new UpsertReasonRequest(null, "POST", "Spam bài đăng", true, false, 3));

        assertEquals("Spam bài đăng", res.label());
        assertTrue(res.descriptionRequired());
        assertFalse(res.active());
        verify(jdbc).update(contains("update report_reason"), eq("POST"), eq("Spam bài đăng"), eq(true), eq(false), eq(3), eq("POST_SPAM"));
    }

    @Test
    void delete_whenUnused_hardDeletesRow() {
        AdminReasonItem unused = new AdminReasonItem("POST_CUSTOM", "POST", "Tùy chỉnh", false, true, 8, 0L);
        when(jdbc.query(contains("where r.code = ?"), any(RowMapper.class), eq("POST_CUSTOM")))
                .thenReturn(List.of(unused));

        Map<String, Object> res = service.delete("POST_CUSTOM");

        assertEquals(true, res.get("deleted"));
        assertEquals(false, res.get("deactivated"));
        verify(jdbc).update("delete from report_reason where code = ?", "POST_CUSTOM");
    }

    @Test
    void delete_whenInUse_softDeactivatesRow() {
        AdminReasonItem inUse = new AdminReasonItem("POST_SPAM", "POST", "Spam", false, true, 2, 5L);
        when(jdbc.query(contains("where r.code = ?"), any(RowMapper.class), eq("POST_SPAM")))
                .thenReturn(List.of(inUse));

        Map<String, Object> res = service.delete("POST_SPAM");

        assertEquals(false, res.get("deleted"));
        assertEquals(true, res.get("deactivated"));
        assertEquals(5L, res.get("usageCount"));
        verify(jdbc).update("update report_reason set active = false where code = ?", "POST_SPAM");
    }
}
