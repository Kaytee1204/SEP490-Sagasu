package com.example.base.controller.approver;

import com.example.base.dto.ApiResponse;
import com.example.base.dto.PageResponse;
import com.example.base.dto.receipt.*;
import com.example.base.security.CustomUserDetails;
import com.example.base.service.approver.ApproverIntakeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequestMapping("/api/approver/receipts")
@PreAuthorize("hasRole('APPROVER')")
@Tag(
        name = "09. Office Intake & Storage - Tiếp nhận tại quầy & Kho lưu trữ",
        description = "Lập phiếu nháp biên bản tiếp nhận tại quầy (Walk-in), Cập nhật, Hoàn tất lưu kho & đăng bài Found, Tra cứu vị trí kho (APPROVER)"
)
public class ApproverReceiptController {
    private final ApproverIntakeService service;

    public ApproverReceiptController(ApproverIntakeService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(
            summary = "[9.1] Danh sách biên bản tiếp nhận tài sản tại quầy (phân trang, tìm kiếm, lọc)",
            description = "Dành cho Cán bộ tiếp nhận (APPROVER). Lọc theo từ khóa keyword và bộ lọc filter (ALL, DRAFT, SUBMITTED)."
    )
    public ResponseEntity<ApiResponse<PageResponse<ReceiptSummaryDto>>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "ALL") String filter,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(service.list(page, size, keyword, filter, user)));
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "[9.2] Xem chi tiết biên bản tiếp nhận theo ID",
            description = "Trả về đầy đủ thông tin biên bản tiếp nhận, danh sách ảnh, thông tin người giao và vị trí lưu kho."
    )
    public ResponseEntity<ApiResponse<ReceiptDto>> detail(
            @PathVariable long id,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(service.detail(id, user)));
    }

    @PostMapping(value = "/draft", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "[9.3] Tạo mới biên bản tiếp nhận dạng nháp (DRAFT) kèm hình ảnh",
            description = "Lưu nháp biên bản tiếp nhận tài sản thất lạc tại quầy kèm ảnh minh chứng. Phần 'data' chứa JSON, phần 'files' chứa ảnh đính kèm."
    )
    public ResponseEntity<ApiResponse<ReceiptDto>> create(
            @Parameter(description = "Thông tin biên bản tiếp nhận dạng JSON") @Valid @RequestPart("data") SaveReceiptRequest request,
            @Parameter(description = "Danh sách ảnh minh chứng tài sản") @RequestPart(value = "files", required = false) List<MultipartFile> files,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.status(201).body(ApiResponse.ok("Đã lưu phiếu nháp", service.create(request, files, user)));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "[9.4] Cập nhật biên bản tiếp nhận đang ở trạng thái nháp (DRAFT)",
            description = "Chỉnh sửa thông tin biên bản tiếp nhận khi còn ở trạng thái nháp."
    )
    public ApiResponse<ReceiptDto> update(
            @PathVariable long id,
            @Parameter(description = "Thông tin biên bản cập nhật dạng JSON") @Valid @RequestPart("data") SaveReceiptRequest request,
            @Parameter(description = "Danh sách ảnh minh chứng cập nhật") @RequestPart(value = "files", required = false) List<MultipartFile> files,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return ApiResponse.ok("Đã lưu thay đổi", service.update(id, request, files, user));
    }

    @PostMapping("/{id}/submit")
    @Operation(
            summary = "[9.5] Hoàn tất tiếp nhận tài sản vào kho và tự động đăng bài FOUND lên hệ thống",
            description = "Chốt biên bản tiếp nhận (IN_STORAGE), gán vị trí kệ/tủ lưu trữ và tự động xuất bản bài đăng FOUND (custody_mode = OFFICE)."
    )
    public ApiResponse<ReceiptDto> submit(
            @PathVariable long id,
            @Valid @RequestBody SubmitReceiptRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return ApiResponse.ok("Đã tiếp nhận tài sản và đăng bài", service.submit(id, request, user));
    }
}
