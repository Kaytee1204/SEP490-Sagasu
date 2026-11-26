package com.example.base.service.report;

import java.util.List;
import java.util.Map;
import java.util.Objects;

final class HandoverVerification {
    record Option(String kind, Long recipientId, String role, String label, String question) {}

    static List<Option> options(Map<String,Object> report, Map<String,Object> current) {
        Long giver = account(current.get("post"), "account_id");
        Long receiver = account(current.get("claim"), "claimant_id");
        return switch (Objects.toString(report.get("reason_code"), "")) {
            case "P2P_HANDOVER_NO_CONTACT" -> pair(giver, receiver,
                "Xác minh lần hẹn giao", "Bạn có đến điểm hẹn không? Bạn còn giữ đồ không?",
                "Xác minh lần hẹn nhận", "Bạn có đến điểm hẹn không? Hai bên đã liên lạc lại được chưa?");
            case "HANDOVER_NOT_RECEIVED" -> pair(giver, receiver,
                "Kiểm tra việc giao thực tế", "Bạn đã đưa món đồ cho ai, khi nào và ở đâu? Có bằng chứng người đó đã nhận không?",
                "Kiểm tra việc nhận thực tế", "Bạn hoặc người nhận hộ đã nhận món đồ chưa? Thông tin nào trong ghi nhận giao chưa đúng?");
            case "HANDOVER_WRONG_RECIPIENT" -> pair(giver, receiver,
                "Hỏi đã giao cho ai", "Bạn giao cho ai, ở đâu và khi nào? Bạn đã đối chiếu thông tin gì?",
                "Xác minh người đến nhận", "Bạn có trực tiếp đến nhận hoặc nhờ ai nhận thay không?");
            case "HANDOVER_WRONG_ITEM" -> pair(giver, receiver,
                "Hỏi người giao về món đã bàn giao", "Ảnh bàn giao có đúng là món bạn đã đưa không? Món được mô tả trong bài đăng hiện ở đâu?",
                "Hỏi người nhận về món đang giữ", "Bạn đã nhận món nào? Chi tiết nào khác với món bạn yêu cầu? Nếu có thể, gửi ảnh rõ chi tiết đó.");
            case "HANDOVER_MISSING_ITEMS" -> pair(giver, receiver,
                "Hỏi người giao đã giao những gì", "Bạn đã giao kèm phụ kiện nào? Hiện bạn còn giữ phụ kiện nào?",
                "Hỏi người nhận còn thiếu gì", "Bạn đã nhận những gì? Còn thiếu món hoặc phụ kiện nào?");
            case "HANDOVER_CONDITION" -> pair(giver, receiver,
                "Xác minh tình trạng lúc giao", "Trước khi giao, món đồ có dấu hiệu hư hỏng nào?",
                "Xác minh tình trạng lúc nhận", "Bạn phát hiện hư hỏng gì và phát hiện khi nào?");
            case "HANDOVER_CONFIRMATION_ERROR" -> pair(giver, receiver,
                "Kiểm tra ghi nhận giao", "Bạn đã giao đồ thực tế chưa?",
                "Kiểm tra xác nhận nhận", "Bạn đã cầm món đồ hay bấm xác nhận nhầm?");
            case "P2P_HANDOVER_PAYMENT" -> reportedParties(report, giver, receiver,
                "Làm rõ khoản tiền được yêu cầu", "Ai yêu cầu khoản tiền nào, vào lúc nào? Có tin nhắn hoặc biên nhận không?",
                "Giải thích khoản tiền khi giao nhận", "Hai bên đã trao đổi thế nào về khoản tiền? Việc giao đồ có phụ thuộc vào khoản tiền này không?");
            case "P2P_HANDOVER_THREAT" -> reportedParties(report, giver, receiver,
                "Làm rõ hành vi đe dọa", "Bạn nhận được lời nói hoặc hành vi nào, khi nào? Bạn có thể gửi bằng chứng riêng cho quản lý.",
                "Đối chiếu sự việc được phản ánh", "Bạn mô tả lại diễn biến buổi hẹn hoặc cuộc trao đổi như thế nào? Có bằng chứng nào để kiểm tra?");
            case "P2P_HANDOVER_NOT_RETURNED" -> reportedParties(report, giver, receiver,
                "Làm rõ việc từ chối giao trả", "Ai đang giữ món đồ? Hai bên đã thống nhất việc trả đồ thế nào và việc giao bị dừng ở đâu?",
                "Xác minh người giữ đồ và lý do chưa giao", "Hiện ai giữ món đồ? Điều gì đang cản trở việc giao trả?");
            default -> pair(giver, receiver, "Xác minh với người giao", "Bạn đã giao đồ chưa? Có chi tiết nào cần kiểm tra?",
                "Xác minh với người nhận", "Bạn đã nhận những gì? Sự cố nào cần làm rõ?");
        };
    }

    private static List<Option> reportedParties(Map<String,Object> report, Long giver, Long receiver,
            String reporterLabel, String reporterQuestion, String otherLabel, String otherQuestion) {
        Long reporter = ReportContextService.id(report, "reporter_id");
        Long other = Objects.equals(reporter, giver) ? receiver : Objects.equals(reporter, receiver) ? giver : null;
        return List.of(new Option("ISSUE_REPORTER", reporter, "Người báo sự cố", reporterLabel, reporterQuestion),
            new Option("ISSUE_OTHER", other, "Bên được phản ánh", otherLabel, otherQuestion));
    }
    private static Long account(Object value, String key) {
        return value instanceof Map<?,?> row && row.get(key) instanceof Number id ? id.longValue() : null;
    }

    private static List<Option> pair(Long giver, Long receiver, String giverLabel, String giverQuestion, String receiverLabel, String receiverQuestion) {
        return List.of(new Option("ISSUE_GIVER", giver, "Người giao", giverLabel, giverQuestion),
            new Option("ISSUE_RECEIVER", receiver, "Người nhận đã duyệt", receiverLabel, receiverQuestion));
    }
}
