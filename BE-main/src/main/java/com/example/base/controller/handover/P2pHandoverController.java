package com.example.base.controller.handover;

import com.example.base.dto.ApiResponse;
import com.example.base.security.CustomUserDetails;
import com.example.base.service.handover.P2pHandoverService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequestMapping("/api/claims/{claimId}/handover")
@RequiredArgsConstructor
@Tag(
        name = "08. Handover (P2P) - Bàn giao tài sản trực tiếp",
        description = "Lập biên bản bàn giao P2P kèm ảnh minh chứng, Xác nhận hoàn tất nhận đồ và Báo cáo sự cố bàn giao P2P"
)
public class P2pHandoverController {
    private final P2pHandoverService service;

    @GetMapping
    @Operation(
            summary = "[8.1] Xem chi tiết biên bản bàn giao P2P của một Claim",
            description = "Trả về trạng thái bàn giao (WAITING_CONFIRMATION, COMPLETED, DISPUTED), ảnh bằng chứng và quyền thao tác của người dùng."
    )
    public ResponseEntity<?> detail(
            @PathVariable long claimId,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(service.detail(claimId, user)));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "[8.2] Người nhặt (Finder) lập biên bản đã bàn giao đồ kèm ảnh minh chứng",
            description = "Chuyển trạng thái bàn giao sang WAITING_CONFIRMATION và gửi thông báo cho người nhận xác nhận."
    )
    public ResponseEntity<?> record(
            @PathVariable long claimId,
            @RequestPart("data") P2pHandoverService.RecordRequest data,
            @RequestPart(value = "images", required = false) List<MultipartFile> images,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(service.record(claimId, data, images, user)));
    }

    @PostMapping("/confirm")
    @Operation(
            summary = "[8.3] Người nhận (Claimant) xác nhận đã nhận lại đúng tài sản",
            description = "Hoàn tất quá trình trao trả (COMPLETED): bài đăng chuyển sang RETURNED và tự động đóng các Claim còn lại."
    )
    public ResponseEntity<?> confirm(
            @PathVariable long claimId,
            @RequestBody P2pHandoverService.ConfirmRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(service.confirm(claimId, request, user)));
    }

    @PostMapping(value = "/issues", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "[8.4] Báo cáo sự cố / tranh chấp trong quá trình bàn giao P2P",
            description = "Chuyển biên bản bàn giao sang DISPUTED và tự động tạo phiếu báo cáo (kind = HANDOVER_ISSUE) để Manager điều tra."
    )
    public ResponseEntity<?> issue(
            @PathVariable long claimId,
            @RequestPart("data") P2pHandoverService.IssueRequest data,
            @RequestPart(value = "images", required = false) List<MultipartFile> images,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(service.issue(claimId, data, images, user)));
    }
}
