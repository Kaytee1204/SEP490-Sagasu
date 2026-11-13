package com.example.base.controller.account;

import com.example.base.dto.ApiResponse;
import com.example.base.dto.PageResponse;
import com.example.base.dto.account.AccountDto;
import com.example.base.dto.account.CreateAccountRequest;
import com.example.base.dto.account.ExcelImportResponse;
import com.example.base.dto.account.UpdateAccountRequest;
import com.example.base.dto.account.UpdateProfileRequest;
import com.example.base.entity.AccountRole;
import com.example.base.entity.AccountStatus;
import com.example.base.service.account.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/accounts")
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
@Tag(
        name = "02. Account Management - Quản lý tài khoản & Hồ sơ",
        description = "Hồ sơ cá nhân (/me), Lịch sử đăng nhập (audit-logs), CRUD tài khoản, Xóa mềm và Import/Export Excel (ADMIN & MANAGER)"
)
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "[2.1] Lấy thông tin hồ sơ cá nhân của tài khoản đang đăng nhập",
            description = "Dành cho mọi tài khoản đã xác thực JWT. Trả về thông tin chi tiết của chính người dùng đang đăng nhập."
    )
    public ResponseEntity<ApiResponse<AccountDto>> getMyProfile() {
        return ResponseEntity.ok(ApiResponse.ok(accountService.getMyProfile()));
    }

    @GetMapping("/me/audit-logs")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "[2.2] Lấy lịch sử hoạt động & đăng nhập (login_audit_logs) của tôi",
            description = "Trả về danh sách lịch sử đăng nhập, đổi mật khẩu và hoạt động bảo mật gần nhất của tài khoản hiện tại."
    )
    public ResponseEntity<ApiResponse<java.util.List<java.util.Map<String, Object>>>> getMyAuditLogs() {
        return ResponseEntity.ok(ApiResponse.ok(accountService.getMyAuditLogs()));
    }

    @PutMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "[2.3] Cập nhật hồ sơ cá nhân (Số điện thoại, Mật khẩu mới)",
            description = "Người dùng cập nhật số điện thoại (phone) và mật khẩu mới (newPassword). Chỉ ADMIN mới được phép đổi role."
    )
    public ResponseEntity<ApiResponse<AccountDto>> updateMyProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật hồ sơ cá nhân thành công", accountService.updateMyProfile(request)));
    }

    @GetMapping
    @Operation(
            summary = "[2.4] Danh sách tài khoản có phân trang, tìm kiếm và lọc theo Role / Status (ADMIN & MANAGER)",
            description = "Trả về PageResponse gồm content, page, size, totalElements, totalPages."
    )
    public ResponseEntity<ApiResponse<PageResponse<AccountDto>>> getAllAccounts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) AccountRole role,
            @RequestParam(required = false) AccountStatus status,
            @RequestParam(defaultValue = "true") boolean includeDeleted
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                accountService.getAccountsPage(page, size, keyword, role, status, includeDeleted)
        ));
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "[2.5] Xem chi tiết một tài khoản theo ID (ADMIN & MANAGER)",
            description = "Trả về thông tin chi tiết của tài khoản theo ID."
    )
    public ResponseEntity<ApiResponse<AccountDto>> getAccountById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(accountService.getAccountById(id)));
    }

    @PostMapping
    @Operation(
            summary = "[2.6] Tạo mới tài khoản thủ công (ADMIN & MANAGER)",
            description = "Validate tại DTO: fullName, email, password, role. Chỉ ADMIN được phép gán role khác USER."
    )
    public ResponseEntity<ApiResponse<AccountDto>> createAccount(@Valid @RequestBody CreateAccountRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Tạo tài khoản thành công", accountService.createAccount(request)));
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "[2.7] Cập nhật thông tin tài khoản theo ID (fullName, email, phone, password, role, status...)",
            description = "Chỉ ADMIN mới được phép thay đổi Role. MANAGER được sửa thông tin của các tài khoản thuộc role USER, APPROVER."
    )
    public ResponseEntity<ApiResponse<AccountDto>> updateAccount(
            @PathVariable Long id,
            @Valid @RequestBody UpdateAccountRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật tài khoản thành công", accountService.updateAccount(id, request)));
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "[2.8] Xóa mềm tài khoản (Soft Delete)",
            description = "Chuyển trạng thái tài khoản sang DELETED và thu hồi toàn bộ Refresh Token đang hoạt động."
    )
    public ResponseEntity<ApiResponse<AccountDto>> softDeleteAccount(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Đã xóa mềm tài khoản thành công", accountService.softDeleteAccount(id)));
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "[2.9] Import danh sách tài khoản từ file Excel (.xlsx / .xls)",
            description = "Dành cho cả ADMIN và MANAGER. File Excel gồm Cột A = email, Cột B = password, tùy chọn Cột C = full_name, Cột D = role."
    )
    public ResponseEntity<ApiResponse<ExcelImportResponse>> importAccountsFromExcel(
            @Parameter(description = "File Excel (.xlsx hoặc .xls) chứa cột email và password")
            @RequestPart("file") MultipartFile file
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Đã xử lý import file Excel vào database",
                accountService.importAccountsFromExcel(file)
        ));
    }

    @GetMapping("/import/template")
    @PreAuthorize("permitAll()")
    @Operation(
            summary = "[2.10] Tải file Excel mẫu (accounts_import_template.xlsx) để Import tài khoản",
            description = "Trả về file Excel mẫu có sẵn cột tiêu đề (email, password, full_name, role) và dòng dữ liệu minh họa."
    )
    public ResponseEntity<byte[]> downloadExcelTemplate() {
        byte[] bytes = accountService.generateExcelTemplate();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=accounts_import_template.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }
}
