package com.example.base.service.report;

import com.example.base.exception.AppException;
import com.example.base.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportHoldGuardTest {

    @Mock JdbcTemplate jdbc;
    @InjectMocks ReportHoldGuard guard;

    private void held(Boolean value) {
        when(jdbc.queryForObject(contains("report_hold"), eq(Boolean.class), eq(10L))).thenReturn(value);
    }

    @Test
    void utc117_held_activeHold_true() {
        held(true);
        assertThat(guard.held(10L)).isTrue();
    }

    @Test
    void utc118_held_noHoldOrNull_false() {
        held(null);
        assertThat(guard.held(10L)).isFalse();
    }

    @Test
    void utc119_requireAvailable_held_throwsBadRequest() {
        held(true);
        assertThatThrownBy(() -> guard.requireAvailable(10L)).isInstanceOf(AppException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void utc120_requireAvailable_notHeld_passes() {
        held(false);
        assertThatCode(() -> guard.requireAvailable(10L)).doesNotThrowAnyException();
    }
}
