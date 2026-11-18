package com.example.base.controller.report;

import com.example.base.dto.ApiResponse;
import com.example.base.service.report.ReportReasonService;
import com.example.base.service.report.ReportReasonService.AdminReasonItem;
import com.example.base.service.report.ReportReasonService.StatusRequest;
import com.example.base.service.report.ReportReasonService.UpsertReasonRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/report-reasons")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
@Tag(
        name = "13. Reports & Dispute Resolution - Báo cáo & Điều tra giải quyết",
        description = "Gửi báo cáo vi phạm, Xem báo cáo của tôi, Điều tra của Manager (Chụp Snapshot, Tạm dừng giao nhận Hold, Hỏi đáp Inquiry, Kết luận xử lý) và Quản trị danh mục lý do báo cáo"
)
public class ReportReasonAdminController {

    private final ReportReasonService reportReasonService;

    @GetMapping
    @Operation(
            summary = "[13.14] Danh sách toàn bộ lý do báo cáo (report_reason) kèm bộ lọc và thống kê lượt dùng (ADMIN & MANAGER)",
            description = "Lọc theo targetGroup (ALL, POST, USER, CLAIM, MESSAGE, HANDOVER_ISSUE), trạng thái active (true/false) và từ khóa tìm kiếm."
    )
    public ApiResponse<List<AdminReasonItem>> list(
            @RequestParam(required = false, defaultValue = "ALL") String targetGroup,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.ok(reportReasonService.listAdmin(targetGroup, active, keyword));
    }

    @PostMapping
    @Operation(
            summary = "[13.15] Tạo mới lý do báo cáo vi phạm / sự cố bàn giao (ADMIN & MANAGER)",
            description = "Thêm mới một bản ghi vào bảng report_reason. Nếu không truyền code, hệ thống tự sinh mã chuẩn từ targetGroup và label."
    )
    public ApiResponse<AdminReasonItem> create(@Valid @RequestBody UpsertReasonRequest request) {
        return ApiResponse.ok("Tạo lý do báo cáo thành công", reportReasonService.create(request));
    }

    @PutMapping("/{code}")
    @Operation(
            summary = "[13.16] Cập nhật thông tin lý do báo cáo theo mã code (ADMIN & MANAGER)",
            description = "Cập nhật tên hiển thị (label), nhóm đối tượng (targetGroup), cờ bắt buộc mô tả (descriptionRequired), thứ tự hiển thị (sortOrder) và trạng thái (active)."
    )
    public ApiResponse<AdminReasonItem> update(
            @PathVariable String code,
            @Valid @RequestBody UpsertReasonRequest request
    ) {
        return ApiResponse.ok("Cập nhật lý do báo cáo thành công", reportReasonService.update(code, request));
    }

    @PatchMapping("/{code}/status")
    @Operation(
            summary = "[13.17] Bật / Tắt trạng thái hoạt động của lý do báo cáo (ADMIN & MANAGER)",
            description = "Chuyển trạng thái active = true hoặc false cho lý do báo cáo theo mã code."
    )
    public ApiResponse<AdminReasonItem> updateStatus(
            @PathVariable String code,
            @RequestBody StatusRequest request
    ) {
        return ApiResponse.ok("Cập nhật trạng thái lý do báo cáo thành công", reportReasonService.updateStatus(code, request.active()));
    }

    @DeleteMapping("/{code}")
    @Operation(
            summary = "[13.18] Xóa hoặc vô hiệu hóa lý do báo cáo theo mã code (ADMIN & MANAGER)",
            description = "Xóa vĩnh viễn nếu chưa có báo cáo nào sử dụng; nếu đã được tham chiếu trong bảng report thì tự động chuyển sang Ngừng hoạt động (active = false) để bảo toàn dữ liệu lịch sử."
    )
    public ApiResponse<Map<String, Object>> delete(@PathVariable String code) {
        Map<String, Object> result = reportReasonService.delete(code);
        return ApiResponse.ok(String.valueOf(result.get("message")), result);
    }
}
