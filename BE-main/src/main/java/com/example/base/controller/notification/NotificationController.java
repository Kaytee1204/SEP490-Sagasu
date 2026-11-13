package com.example.base.controller.notification;

import com.example.base.dto.ApiResponse;
import com.example.base.dto.notification.NotificationDtos.*;
import com.example.base.security.CustomUserDetails;
import com.example.base.service.notification.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(
        name = "14. Notifications - Thông báo đa kênh & Cài đặt tùy chọn",
        description = "Danh sách thông báo trong ứng dụng, Đếm thông báo chưa đọc, Đánh dấu đã đọc và Bật/Tắt tùy chọn thông báo (In-App, Email, Browser Push)"
)
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @Operation(
            summary = "[14.1] Lấy danh sách thông báo của người dùng hiện tại (phân trang, lọc chưa đọc)",
            description = "Truyền unreadOnly = true để chỉ lấy thông báo chưa đọc, hoặc false để lấy tất cả thông báo."
    )
    public ResponseEntity<ApiResponse<NotificationPageDto>> list(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "15") int size,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user
    ) {
        NotificationPageDto result = notificationService.list(user, unreadOnly, page, size);
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách thông báo thành công", result));
    }

    @GetMapping("/unread-count")
    @Operation(
            summary = "[14.2] Lấy số lượng thông báo chưa đọc (Unread Badge Count)",
            description = "Trả về tổng số thông báo chưa đọc của tài khoản đang đăng nhập."
    )
    public ResponseEntity<ApiResponse<UnreadCountDto>> unreadCount(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user
    ) {
        UnreadCountDto result = notificationService.unreadCount(user);
        return ResponseEntity.ok(ApiResponse.ok("Lấy số thông báo chưa đọc thành công", result));
    }

    @PatchMapping("/{id}/read")
    @Operation(
            summary = "[14.3] Đánh dấu một thông báo là đã đọc theo ID",
            description = "Cập nhật is_read = true, read_at = now() và trả về số lượng thông báo chưa đọc còn lại."
    )
    public ResponseEntity<ApiResponse<UnreadCountDto>> markRead(
            @PathVariable long id,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user
    ) {
        UnreadCountDto result = notificationService.markRead(user, id);
        return ResponseEntity.ok(ApiResponse.ok("Đã đánh dấu đã đọc", result));
    }

    @PatchMapping("/read-all")
    @Operation(
            summary = "[14.4] Đánh dấu toàn bộ thông báo của tôi là đã đọc",
            description = "Chuyển tất cả thông báo chưa đọc của người dùng sang trạng thái đã đọc."
    )
    public ResponseEntity<ApiResponse<UnreadCountDto>> markAllRead(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user
    ) {
        UnreadCountDto result = notificationService.markAllRead(user);
        return ResponseEntity.ok(ApiResponse.ok("Đã đánh dấu tất cả là đã đọc", result));
    }

    @GetMapping("/preferences")
    @Operation(
            summary = "[14.5] Lấy cấu hình tùy chọn nhận thông báo hiện tại của tôi",
            description = "Trả về trạng thái bật/tắt của 3 kênh: inAppEnabled, emailEnabled, browserPushEnabled."
    )
    public ResponseEntity<ApiResponse<NotificationPreferenceDto>> getPreferences(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user
    ) {
        NotificationPreferenceDto result = notificationService.getPreferences(user);
        return ResponseEntity.ok(ApiResponse.ok("Lấy cài đặt thông báo thành công", result));
    }

    @PutMapping("/preferences")
    @Operation(
            summary = "[14.6] Cập nhật tùy chọn chủ động bật / tắt thông báo (In-App, Email, Browser Push)",
            description = "Cho phép người dùng chủ động bật hoặc tắt nhận thông báo trong web, qua email hoặc thông báo trình duyệt."
    )
    public ResponseEntity<ApiResponse<NotificationPreferenceDto>> updatePreferences(
            @RequestBody UpdatePreferenceRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user
    ) {
        NotificationPreferenceDto result = notificationService.updatePreferences(user, request);
        return ResponseEntity.ok(ApiResponse.ok("Đã cập nhật cài đặt thông báo", result));
    }
}
