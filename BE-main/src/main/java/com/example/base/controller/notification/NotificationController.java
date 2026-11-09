package com.example.base.controller.notification;

import com.example.base.dto.ApiResponse;
import com.example.base.dto.notification.NotificationDtos.*;
import com.example.base.security.CustomUserDetails;
import com.example.base.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<ApiResponse<NotificationPageDto>> list(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "15") int size,
            @AuthenticationPrincipal CustomUserDetails user
    ) {
        NotificationPageDto result = notificationService.list(user, unreadOnly, page, size);
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách thông báo thành công", result));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<UnreadCountDto>> unreadCount(
            @AuthenticationPrincipal CustomUserDetails user
    ) {
        UnreadCountDto result = notificationService.unreadCount(user);
        return ResponseEntity.ok(ApiResponse.ok("Lấy số thông báo chưa đọc thành công", result));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<ApiResponse<UnreadCountDto>> markRead(
            @PathVariable long id,
            @AuthenticationPrincipal CustomUserDetails user
    ) {
        UnreadCountDto result = notificationService.markRead(user, id);
        return ResponseEntity.ok(ApiResponse.ok("Đã đánh dấu đã đọc", result));
    }

    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse<UnreadCountDto>> markAllRead(
            @AuthenticationPrincipal CustomUserDetails user
    ) {
        UnreadCountDto result = notificationService.markAllRead(user);
        return ResponseEntity.ok(ApiResponse.ok("Đã đánh dấu tất cả là đã đọc", result));
    }

    @GetMapping("/preferences")
    public ResponseEntity<ApiResponse<NotificationPreferenceDto>> getPreferences(
            @AuthenticationPrincipal CustomUserDetails user
    ) {
        NotificationPreferenceDto result = notificationService.getPreferences(user);
        return ResponseEntity.ok(ApiResponse.ok("Lấy cài đặt thông báo thành công", result));
    }

    @PutMapping("/preferences")
    public ResponseEntity<ApiResponse<NotificationPreferenceDto>> updatePreferences(
            @RequestBody UpdatePreferenceRequest request,
            @AuthenticationPrincipal CustomUserDetails user
    ) {
        NotificationPreferenceDto result = notificationService.updatePreferences(user, request);
        return ResponseEntity.ok(ApiResponse.ok("Đã cập nhật cài đặt thông báo", result));
    }
}
