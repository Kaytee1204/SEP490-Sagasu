package com.example.base.service.office;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class OfficePickupSchedule {
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalTime MORNING_OPEN = LocalTime.of(8, 0);
    private static final LocalTime MORNING_CLOSE = LocalTime.NOON;
    private static final LocalTime AFTERNOON_OPEN = LocalTime.of(13, 0);
    private static final LocalTime AFTERNOON_CLOSE = LocalTime.of(17, 0);
    private final Set<LocalDate> holidays;

    OfficePickupSchedule(@Value("${office.appointments.holidays:}") String holidays) {
        this.holidays = Arrays.stream(holidays.split(",")).map(String::strip)
                .filter(date -> !date.isEmpty()).map(LocalDate::parse).collect(Collectors.toUnmodifiableSet());
    }

    void validate(OffsetDateTime scheduledAt, OffsetDateTime now) {
        if (!scheduledAt.isAfter(now.plusHours(12).plusMinutes(1)))
            throw OfficeAccess.bad("Giờ hẹn cần cách hiện tại hơn 12 giờ và 1 phút để người nhận phản hồi.");
        var local = scheduledAt.atZoneSameInstant(ZONE);
        if (local.getDayOfWeek().compareTo(DayOfWeek.THURSDAY) > 0)
            throw OfficeAccess.bad("Chỉ đặt lịch xác minh từ thứ Hai đến thứ Năm.");
        LocalTime time = local.toLocalTime();
        boolean morning = !time.isBefore(MORNING_OPEN) && time.isBefore(MORNING_CLOSE);
        boolean afternoon = !time.isBefore(AFTERNOON_OPEN) && time.isBefore(AFTERNOON_CLOSE);
        if (!morning && !afternoon)
            throw OfficeAccess.bad("Chọn giờ hẹn trong ca 08:00–12:00 hoặc 13:00–17:00 (giờ Việt Nam).");
        if (holidays.contains(local.toLocalDate()) || holidays.contains(local.toLocalDate().plusDays(1)))
            throw OfficeAccess.bad("Giờ hẹn và hạn đến quầy cần rơi vào ngày văn phòng làm việc.");
    }

    static OffsetDateTime verificationDeadline(OffsetDateTime scheduledAt) {
        return scheduledAt.plusHours(24);
    }
}
