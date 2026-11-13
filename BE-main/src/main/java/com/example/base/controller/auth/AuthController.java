package com.example.base.controller.auth;

import com.example.base.dto.ApiResponse;
import com.example.base.dto.account.AccountDto;
import com.example.base.dto.auth.*;
import com.example.base.service.auth.AuthService;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@Tag(
        name = "01. Auth - Đăng nhập & Quên mật khẩu",
        description = "Đăng nhập trực tiếp (Email/Password, khóa tạm thời sau 5 lần sai) và Khôi phục mật khẩu 3 bước qua mã OTP Email"
)
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @Operation(
            summary = "[1.1] Đăng nhập bằng Email/Username & Mật khẩu (ADMIN: admin/123 | MANAGER: khanhtrinh1204@gmail.com/L!H$1204 | USER: user/123)",
            description = "Đăng nhập trực tiếp không cần mã OTP cho tất cả các Role (ADMIN, MANAGER, APPROVER, USER). Email không phân biệt chữ hoa/thường. Nhập sai mật khẩu 5 lần liên tiếp sẽ tạm khóa tài khoản 15 phút."
    )
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Đăng nhập thành công", authService.login(request)));
    }

    @PostMapping("/forgot-password")
    @Operation(
            summary = "[1.2] Quên mật khẩu (Bước 1): Nhập Email để nhận mã OTP 6 chữ số khôi phục mật khẩu",
            description = "Gửi mã OTP gồm 6 chữ số (purpose = FORGOT_PASSWORD, hiệu lực 5 phút) về địa chỉ Email của tài khoản. Dùng cho cả trường hợp quên mật khẩu hoặc cần mở khóa tài khoản."
    )
    public ResponseEntity<ApiResponse<Map<String, Object>>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Đã gửi mã OTP khôi phục mật khẩu", authService.forgotPassword(request)));
    }

    @PostMapping("/verify-reset-otp")
    @Operation(
            summary = "[1.3] Quên mật khẩu (Bước 2): Xác thực mã OTP 6 chữ số trước khi đặt mật khẩu mới",
            description = "Kiểm tra tính hợp lệ của mã OTP 6 chữ số đã gửi về Email (purpose = FORGOT_PASSWORD). Khi mã OTP hợp lệ mới chuyển sang bước 3 đặt mật khẩu mới."
    )
    public ResponseEntity<ApiResponse<Map<String, Object>>> verifyResetOtp(@Valid @RequestBody VerifyOtpRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Mã OTP hợp lệ", authService.verifyResetPasswordOtp(request)));
    }

    @PostMapping("/reset-password")
    @Operation(
            summary = "[1.4] Quên mật khẩu (Bước 3): Đặt lại mật khẩu mới, reset số lần đăng nhập sai về 0 và mở khóa",
            description = "Xác nhận mã OTP và cập nhật mật khẩu mới (8-16 ký tự, có ít nhất 1 ký tự đặc biệt, không khoảng trắng), đồng thời reset số lần đăng nhập sai về 0 và gỡ khóa tạm thời."
    )
    public ResponseEntity<ApiResponse<AccountDto>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Đặt lại mật khẩu thành công", authService.resetPassword(request)));
    }

    // ── Các endpoint OTP phụ trợ / tương thích ngược (ẩn trên Swagger để tránh trùng lặp với Quên mật khẩu & Đổi mật khẩu trong Hồ sơ) ──

    @Hidden
    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<LoginResponse>> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Xác thực OTP thành công", authService.verifyOtp(request)));
    }

    @Hidden
    @PostMapping("/send-otp")
    public ResponseEntity<ApiResponse<Map<String, Object>>> sendOtp(@Valid @RequestBody SendOtpRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Đã xử lý yêu cầu gửi OTP", authService.sendOtp(request)));
    }

    @Hidden
    @PostMapping("/change-password/request-otp")
    public ResponseEntity<ApiResponse<Map<String, Object>>> requestChangePasswordOtp(
            @Valid @RequestBody RequestChangePasswordOtpRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Đã gửi mã OTP xác nhận đổi mật khẩu",
                authService.requestChangePasswordOtp(request)
        ));
    }

    @Hidden
    @PostMapping("/change-password/confirm")
    public ResponseEntity<ApiResponse<AccountDto>> confirmChangePassword(
            @Valid @RequestBody ChangePasswordConfirmRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Đổi mật khẩu thành công",
                authService.confirmChangePassword(request)
        ));
    }
}
