package com.example.base.service.office;

import com.example.base.entity.Appointment;
import java.time.OffsetDateTime;

final class OfficeAppointmentGuidance {
    private OfficeAppointmentGuidance() {}

    static String pendingMessage(Appointment appointment, OffsetDateTime now) {
        if (appointment == null) return "Văn phòng cần sắp xếp lịch nhận đồ.";
        String response = appointment.getResponseStatus();
        if (appointment.responseExpired(now))
            return "Đã hết hạn phản hồi lịch hẹn. Hệ thống đang cập nhật kết quả yêu cầu.";
        if ("LEGACY".equals(response))
            return appointment.getScheduledAt().isAfter(now)
                ? "Lịch hẹn cũ chưa áp dụng xác nhận qua email. Vui lòng liên hệ văn phòng để xác nhận lịch."
                : "Lịch hẹn cũ đã qua. Vui lòng liên hệ văn phòng để được sắp xếp lại.";
        if ("UNSENT".equals(response))
            return appointment.getScheduledAt().isAfter(now)
                ? "Văn phòng đang chuẩn bị lời mời nhận đồ. Bạn chưa cần phản hồi."
                : "Lịch hẹn chưa gửi lời mời đã qua. Văn phòng cần sắp xếp lịch mới.";
        if ("DECLINED".equals(response) || "EXPIRED".equals(response))
            return "Lịch hẹn không còn hiệu lực. Vui lòng kiểm tra kết quả yêu cầu.";
        return "Chờ người nhận đồng ý lịch hẹn qua email hoặc trên hệ thống.";
    }
}
