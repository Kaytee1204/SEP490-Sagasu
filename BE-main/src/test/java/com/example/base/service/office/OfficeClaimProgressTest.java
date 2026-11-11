package com.example.base.service.office;

import com.example.base.dto.office.OfficeDtos.PageData;
import com.example.base.exception.AppException;
import com.example.base.exception.ErrorCode;
import com.example.base.service.office.OfficeClaimProgress.Progress;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OfficeClaimProgressTest {

    @Mock
    JdbcTemplate jdbc;

    @InjectMocks
    OfficeClaimProgress progress;

    @Test
    void utc054_list_count25_returnsTotalPages3AndContent() {
        List<Progress> rows = List.of(new Progress(1L, "REVIEW", OffsetDateTime.now()));
        when(jdbc.queryForObject(startsWith("select count(*)"), eq(Long.class),
                eq("ALL"), eq("ALL"), eq("ALL"), eq("ALL"), eq(0L), eq(0L), eq("%%"), eq("%%")))
                .thenReturn(25L);
        when(jdbc.query(startsWith("select claim_id,stage,scheduled_at"), any(RowMapper.class),
                eq("ALL"), eq("ALL"), eq("ALL"), eq("ALL"), eq(0L), eq(0L), eq("%%"), eq("%%"), eq(0L)))
                .thenReturn(rows);

        PageData<Progress> res = progress.list(1, "ALL", "ALL", "", null);

        assertThat(res.totalElements()).isEqualTo(25L);
        assertThat(res.totalPages()).isEqualTo(3);
        assertThat(res.content()).hasSize(1);
    }

    @Test
    void utc055_list_pageZero_throwsBadRequest() {
        AppException ex = catchThrowableOfType(
                () -> progress.list(0, "ALL", "ALL", "", null),
                AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ex.getMessage()).isEqualTo("Bộ lọc tiến trình không hợp lệ.");
    }

    @Test
    void utc056_list_unknownStage_throwsBadRequest() {
        AppException ex = catchThrowableOfType(
                () -> progress.list(1, "ALL", "UNKNOWN", "", null),
                AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void utc057_list_escapesWildcardsInKeyword() {
        when(jdbc.queryForObject(startsWith("select count(*)"), eq(Long.class),
                eq("ALL"), eq("ALL"), eq("ALL"), eq("ALL"), eq(0L), eq(0L), eq("%50\\%%"), eq("%50\\%%")))
                .thenReturn(0L);
        when(jdbc.query(startsWith("select claim_id,stage,scheduled_at"), any(RowMapper.class),
                eq("ALL"), eq("ALL"), eq("ALL"), eq("ALL"), eq(0L), eq(0L), eq("%50\\%%"), eq("%50\\%%"), eq(0L)))
                .thenReturn(List.of());

        progress.list(1, "ALL", "ALL", "50%", null);

        verify(jdbc).query(startsWith("select claim_id,stage,scheduled_at"), any(RowMapper.class),
                eq("ALL"), eq("ALL"), eq("ALL"), eq("ALL"), eq(0L), eq(0L), eq("%50\\%%"), eq("%50\\%%"), eq(0L));
    }

    @Test
    void utc058_list_zeroCount_returnsTotalPagesMinOne() {
        when(jdbc.queryForObject(startsWith("select count(*)"), eq(Long.class),
                eq("ALL"), eq("ALL"), eq("ALL"), eq("ALL"), eq(0L), eq(0L), eq("%%"), eq("%%")))
                .thenReturn(0L);
        when(jdbc.query(startsWith("select claim_id,stage,scheduled_at"), any(RowMapper.class),
                eq("ALL"), eq("ALL"), eq("ALL"), eq("ALL"), eq(0L), eq(0L), eq("%%"), eq("%%"), eq(0L)))
                .thenReturn(List.of());

        PageData<Progress> res = progress.list(1, "ALL", "ALL", "", null);

        assertThat(res.totalPages()).isEqualTo(1);
    }

    @Test
    void utc059_of_missingRow_throwsBadRequest() {
        when(jdbc.query(startsWith("select claim_id,stage,scheduled_at"), any(RowMapper.class), eq(999L)))
                .thenReturn(List.of());

        AppException ex = catchThrowableOfType(() -> progress.of(999L), AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ex.getMessage()).isEqualTo("Không tìm thấy yêu cầu.");
    }
}

