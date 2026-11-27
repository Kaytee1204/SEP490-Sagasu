package com.example.base.controller.admin;

import com.example.base.dto.ApiResponse;
import com.example.base.service.admin.AdminGovernanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(
        name = "02. Account Management - Quản lý tài khoản & Hồ sơ",
        description = "Hồ sơ cá nhân (/me), Lịch sử đăng nhập & Audit Logs hệ thống, Dashboard thống kê quản trị, CRUD tài khoản, Xóa mềm và Import/Export Excel"
)
public class AdminGovernanceController {

    private final AdminGovernanceService governanceService;

    @GetMapping("/dashboard")
    @Operation(
            summary = "[2.11] Thống kê tổng quan Bảng điều khiển Quản trị (ADMIN Dashboard)",
            description = "Trả về toàn bộ chỉ số KPI thời gian thực (Tài khoản, Bài đăng Lost/Found, Kho văn phòng, Claim P2P/Office, Báo cáo vi phạm, Xu hướng 7 ngày, Danh mục hàng đầu và Hoạt động gần đây)."
    )
    public ApiResponse<Map<String, Object>> getDashboardOverview() {
        return ApiResponse.ok(governanceService.getDashboardOverview());
    }

    @GetMapping("/audit-logs")
    @Operation(
            summary = "[2.12] Quản lý & Tra cứu Nhật ký Hệ thống hợp nhất (Audit Logs) dành cho ADMIN",
            description = "Hợp nhất nhật ký bảo mật & đăng nhập (SECURITY), nhật ký xử lý yêu cầu nhận đồ (CLAIM) và nhật ký điều tra báo cáo (REPORT). Hỗ trợ lọc theo nguồn (source), loại sự kiện (eventType), vai trò (role), trạng thái thành công (success), khoảng ngày và từ khóa."
    )
    public ApiResponse<Map<String, Object>> getAuditLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "15") int size,
            @RequestParam(required = false, defaultValue = "ALL") String source,
            @RequestParam(required = false, defaultValue = "ALL") String eventType,
            @RequestParam(required = false, defaultValue = "ALL") String role,
            @RequestParam(required = false) Boolean success,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate
    ) {
        return ApiResponse.ok(governanceService.getAuditLogs(
                page, size, source, eventType, role, success, keyword, fromDate, toDate
        ));
    }
}
