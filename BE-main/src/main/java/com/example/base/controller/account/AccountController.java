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
@Tag(name = "2. Account Manager (ADMIN & MANAGER)", description = "Phân trang, CRUD tài khoản (Tạo tay, Update, Xóa mềm), Hồ sơ cá nhân và Import Excel")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Lấy thông tin hồ sơ cá nhân của tài khoản đang đăng nhập")
    public ResponseEntity<ApiResponse<AccountDto>> getMyProfile() {
        return ResponseEntity.ok(ApiResponse.ok(accountService.getMyProfile()));
    }

    @GetMapping("/me/audit-logs")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Lấy lịch sử hoạt động (login_audit_logs) của tài khoản đang đăng nhập từ DB")
    public ResponseEntity<ApiResponse< java.util.List<java.util.Map<String, Object>> >> getMyAuditLogs() {
        return ResponseEntity.ok(ApiResponse.ok(accountService.getMyAuditLogs()));
    }

    @PutMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Cập nhật hồ sơ cá nhân (Chỉ cho phép đổi số điện thoại, mật khẩu; chỉ ADMIN mới được đổi role)",
            description = "Người dùng thường chỉ được cập nhật phone và newPassword. Không được phép đổi email hoặc role."
    )
    public ResponseEntity<ApiResponse<AccountDto>> updateMyProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật hồ sơ cá nhân thành công", accountService.updateMyProfile(request)));
    }

    @GetMapping
    @Operation(
            summary = "Danh sách tài khoản có phân trang ở Backend (hỗ trợ tìm kiếm, lọc theo role/status)",
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
    @Operation(summary = "Xem chi tiết tài khoản theo ID")
    public ResponseEntity<ApiResponse<AccountDto>> getAccountById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(accountService.getAccountById(id)));
    }

    @PostMapping
    @Operation(
            summary = "Tạo tài khoản bằng tay (dành cho cả ADMIN và MANAGER)",
            description = "Validate tại DTO: fullName, email, password, role. ID tự động tăng trong DB. Chỉ ADMIN được set role khác USER."
    )
    public ResponseEntity<ApiResponse<AccountDto>> createAccount(@Valid @RequestBody CreateAccountRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Tạo tài khoản thành công", accountService.createAccount(request)));
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Update thông tin tài khoản (fullName, email, phone, password, role, status...)",
            description = "Chỉ ADMIN mới được phép thay đổi Role. MANAGER được sửa các thông tin khác của tài khoản thuộc role USER, CHECKER, APPROVER."
    )
    public ResponseEntity<ApiResponse<AccountDto>> updateAccount(
            @PathVariable Long id,
            @Valid @RequestBody UpdateAccountRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật tài khoản thành công", accountService.updateAccount(id, request)));
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Xóa mềm tài khoản (Soft Delete)",
            description = "Chuyển trạng thái tài khoản sang DELETED và thu hồi toàn bộ Refresh Token đang hoạt động."
    )
    public ResponseEntity<ApiResponse<AccountDto>> softDeleteAccount(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Đã xóa mềm tài khoản thành công", accountService.softDeleteAccount(id)));
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Import file Excel chứa Gmail và Mật khẩu người dùng vào DB",
            description = "Dành cho cả ADMIN và MANAGER. File Excel gồm Cột A = email (gmail), Cột B = password (mật khẩu), tùy chọn Cột C = full_name, Cột D = role."
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
            summary = "Tải file Excel mẫu (email + password + full_name + role) để Import tài khoản",
            description = "Trả về file accounts_import_template.xlsx có sẵn cột tiêu đề và dữ liệu mẫu."
    )
    public ResponseEntity<byte[]> downloadExcelTemplate() {
        byte[] bytes = accountService.generateExcelTemplate();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=accounts_import_template.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }
}
