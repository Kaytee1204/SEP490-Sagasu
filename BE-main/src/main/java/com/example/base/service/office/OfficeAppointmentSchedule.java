package com.example.base.service.office;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class OfficeAppointmentSchedule {
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalTime MORNING_OPEN = LocalTime.of(8, 0);
    private static final LocalTime MORNING_CLOSE = LocalTime.NOON;
    private static final LocalTime AFTERNOON_OPEN = LocalTime.of(13, 0);
    private static final LocalTime AFTERNOON_CLOSE = LocalTime.of(17, 0);
    private final Set<LocalDate> holidays;

    OfficeAppointmentSchedule(@Value("${office.appointments.holidays:}") String holidays) {
        this.holidays = Arrays.stream(holidays.split(",")).map(String::strip)
                .filter(date -> !date.isEmpty()).map(LocalDate::parse).collect(Collectors.toUnmodifiableSet());
    }

    void validatePickup(OffsetDateTime scheduledAt, OffsetDateTime now) {
        if (!scheduledAt.isAfter(now))
            throw OfficeAccess.bad("Vui lòng chọn giờ hẹn sau thời điểm hiện tại.");
        var local = scheduledAt.atZoneSameInstant(ZONE);
        requireOfficeHours(local, DayOfWeek.THURSDAY, "Chỉ đặt lịch xác minh từ thứ Hai đến thứ Năm.");
        if (holidays.contains(local.toLocalDate()) || holidays.contains(local.toLocalDate().plusDays(1)))
            throw OfficeAccess.bad("Giờ hẹn và hạn đến quầy cần rơi vào ngày văn phòng làm việc.");
    }

    void validateIntake(OffsetDateTime scheduledAt, OffsetDateTime now) {
        if (!scheduledAt.isAfter(now.plusHours(12).plusMinutes(1)))
            throw OfficeAccess.bad("Vui lòng đặt lịch trước ít nhất 12 giờ và 1 phút.");
        var local = scheduledAt.atZoneSameInstant(ZONE);
        requireOfficeHours(local, DayOfWeek.FRIDAY, "Chỉ đặt lịch mang đồ đến từ thứ Hai đến thứ Sáu.");
        if (holidays.contains(local.toLocalDate()))
            throw OfficeAccess.bad("Vui lòng chọn ngày hẹn mà văn phòng làm việc.");
    }

    private static void requireOfficeHours(ZonedDateTime local, DayOfWeek lastDay, String dayError) {
        if (local.getDayOfWeek().compareTo(lastDay) > 0)
            throw OfficeAccess.bad(dayError);
        LocalTime time = local.toLocalTime();
        boolean morning = !time.isBefore(MORNING_OPEN) && time.isBefore(MORNING_CLOSE);
        boolean afternoon = !time.isBefore(AFTERNOON_OPEN) && time.isBefore(AFTERNOON_CLOSE);
        if (!morning && !afternoon)
            throw OfficeAccess.bad("Chọn giờ hẹn trong ca 08:00–12:00 hoặc 13:00–17:00 (giờ Việt Nam).");
    }

    static OffsetDateTime verificationDeadline(OffsetDateTime scheduledAt) {
        return scheduledAt.plusHours(24);
    }
}
