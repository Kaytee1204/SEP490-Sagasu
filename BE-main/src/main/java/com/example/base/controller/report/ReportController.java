package com.example.base.controller.report;

import com.example.base.dto.ApiResponse;
import com.example.base.security.CustomUserDetails;
import com.example.base.service.report.ReportService;
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
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@Tag(
        name = "13. Reports & Dispute Resolution - Báo cáo & Điều tra giải quyết",
        description = "Gửi báo cáo vi phạm, Xem báo cáo của tôi, Điều tra của Manager (Chụp Snapshot, Tạm dừng giao nhận Hold, Hỏi đáp Inquiry, Kết luận xử lý)"
)
public class ReportController {
    private final ReportService service;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "[13.1] Gửi báo cáo vi phạm mới (Bài đăng / Claim / Tin nhắn Chat / Người dùng) kèm ảnh bằng chứng",
            description = "Tạo phiếu báo cáo (status = OPEN) và tự động chụp lại Snapshot ngữ cảnh tại thời điểm gửi báo cáo."
    )
    public ResponseEntity<?> create(
            @RequestPart("data") ReportService.Submit data,
            @RequestPart(value = "images", required = false) List<MultipartFile> images,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(ApiResponse.ok(service.create(data, images, user)));
    }

    @GetMapping("/mine")
    @Operation(
            summary = "[13.2] Danh sách các báo cáo do tôi đã gửi (phân trang, lọc theo status)",
            description = "Lọc theo status (ALL, OPEN, IN_REVIEW, RESOLVED, REJECTED)."
    )
    public ResponseEntity<?> mine(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "ALL") String status,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(service.mine(page, status, user)));
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "[13.3] Xem chi tiết báo cáo của tôi theo ID",
            description = "Trả về nội dung báo cáo, bằng chứng đính kèm, tiến độ xử lý và ghi chú kết luận công khai."
    )
    public ResponseEntity<?> detail(
            @PathVariable long id,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(service.mineDetail(id, user)));
    }
}
