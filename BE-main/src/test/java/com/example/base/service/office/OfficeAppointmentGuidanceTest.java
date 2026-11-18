package com.example.base.service.office;

import com.example.base.entity.Appointment;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class OfficeAppointmentGuidanceTest {
    private final OffsetDateTime now = OffsetDateTime.parse("2026-11-17T10:00:00+07:00");

    @Test void pastLegacyIsNotWaitingForEmailResponse() {
        Appointment a = mock(Appointment.class);
        when(a.getResponseStatus()).thenReturn("LEGACY");
        when(a.getScheduledAt()).thenReturn(now.minusDays(11));
        assertThat(OfficeAppointmentGuidance.pendingMessage(a, now)).contains("đã qua");
        verify(a, never()).rejectInvitation(any(), any(), any());
    }
    @Test void unsentAndExpiredAreNotPendingResponses() {
        Appointment a = mock(Appointment.class);
        when(a.getResponseStatus()).thenReturn("UNSENT");
        when(a.getScheduledAt()).thenReturn(now.plusDays(1));
        assertThat(OfficeAppointmentGuidance.pendingMessage(a, now)).contains("chưa cần phản hồi");
        when(a.responseExpired(now)).thenReturn(true);
        assertThat(OfficeAppointmentGuidance.pendingMessage(a, now)).contains("hết hạn");
    }
    @Test void validPendingStillWaitsForResponse() {
        Appointment a = mock(Appointment.class);
        when(a.getResponseStatus()).thenReturn("PENDING");
        assertThat(OfficeAppointmentGuidance.pendingMessage(a, now)).contains("Chờ người nhận đồng ý");
    }
}
