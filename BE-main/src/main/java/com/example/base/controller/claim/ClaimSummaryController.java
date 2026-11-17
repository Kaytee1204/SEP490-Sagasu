package com.example.base.controller.claim;

import com.example.base.dto.ApiResponse;
import com.example.base.dto.claim.ClaimReviewSummaryDto;
import com.example.base.security.CustomUserDetails;
import com.example.base.service.claim.ClaimSummaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/posts/{postId}/claims/summary")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('USER', 'APPROVER', 'MANAGER', 'ADMIN')")
@Tag(name = "06. Claims (P2P) - Yêu cầu nhận đồ & Bộ câu hỏi xác minh")
public class ClaimSummaryController {
    private final ClaimSummaryService service;

    @PostMapping
    @Operation(summary = "[6.16] Tóm tắt mô tả vật theo ba tiêu chí cho chủ bài đăng P2P",
            description = "Cùng phân trang/bộ lọc với đối chiếu. Chỉ gửi mô tả từ vựng kiểm soát tới Gemini khi bật; không gửi dữ liệu cá nhân, căn cứ riêng hay ảnh. Thiếu cấu hình/lỗi/hết hạn mức trả bản trích theo mẫu. Không chấm quyền sở hữu.")
    public ResponseEntity<ApiResponse<ClaimReviewSummaryDto>> summary(@PathVariable long postId,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "5") int size,
            @RequestParam(defaultValue = "ALL") String status, @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.ok(service.summarize(postId, page, size, status, user)));
    }
}
