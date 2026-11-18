package com.example.base.controller.claim;

import com.example.base.dto.ApiResponse;
import com.example.base.security.CustomUserDetails;
import com.example.base.service.chat.ChatAccessPolicy;
import com.example.base.service.claim.ClaimDailyLimit;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;

@RestController
@RequiredArgsConstructor
@Tag(
        name = "06. Claims (P2P) - Yêu cầu nhận đồ & Bộ câu hỏi xác minh",
        description = "Lấy bộ câu hỏi xác minh theo danh mục, Gửi/Gửi lại Claim, Gom nhóm Claim theo bài đăng, Duyệt/Từ chối/Yêu cầu bổ sung và Hủy Claim"
)
public class ClaimDailyLimitController {
    private final ClaimDailyLimit limit;
    private final ChatAccessPolicy access;

    @GetMapping("/api/claims/daily-limit")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "[6.17] Kiểm tra hạn mức gửi yêu cầu nhận đồ trong ngày của tài khoản (tối đa 2 lần/ngày)",
            description = "Trả về hạn mức gửi Claim dùng chung cho cả P2P và Văn phòng trong ngày hiện tại."
    )
    public ResponseEntity<ApiResponse<ClaimDailyLimit.Status>> status(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.ok(limit.status(access.actor(user).getId())));
    }
}
