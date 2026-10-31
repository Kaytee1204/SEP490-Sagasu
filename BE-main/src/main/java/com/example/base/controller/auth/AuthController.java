package com.example.base.controller.auth;

import com.example.base.dto.ApiResponse;
import com.example.base.dto.account.AccountDto;
import com.example.base.dto.auth.*;
import com.example.base.service.auth.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "1. Auth & OTP", description = "Đăng nhập, Đổi mật khẩu nhận OTP qua Gmail và Khôi phục mật khẩu")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @Operation(
            summary = "Đăng nhập (Admin: admin/123 | Manager: khanhtrinh1204@gmail.com/123)",
            description = "Đăng nhập bằng tài khoản & mật khẩu, nhận JWT accessToken để dán vào nút Authorize trên Swagger KhanhTB."
    )
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Đăng nhập thành công", authService.login(request)));
    }

    @PostMapping("/google-login")
    @Operation(
            summary = "Đăng nhập bằng tài khoản Google đã được hệ thống cung cấp",
            description = "Kiểm tra email Google có tồn tại và đang hoạt động trong hệ thống hay không, sau đó cấp JWT Token."
    )
    public ResponseEntity<ApiResponse<LoginResponse>> loginWithGoogle(@Valid @RequestBody GoogleLoginRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Đăng nhập Google thành công", authService.loginWithGoogle(request)));
    }

    @GetMapping("/google-accounts")
    @Operation(
            summary = "Danh sách các tài khoản Google đã được hệ thống cung cấp (để chọn nhanh khi Đăng nhập với Google)"
    )
    public ResponseEntity<ApiResponse<java.util.List<Map<String, Object>>>> getAvailableGoogleAccounts() {
        return ResponseEntity.ok(ApiResponse.ok(authService.getAvailableGoogleAccounts()));
    }

    @PostMapping("/change-password/request-otp")
    @Operation(
            summary = "Bước 1 Đổi mật khẩu: Nhập mật khẩu hiện tại (123) để nhận mã OTP qua Gmail",
            description = "Kiểm tra email (khanhtrinh1204@gmail.com) và mật khẩu hiện tại (123). Nếu đúng sẽ gửi mã OTP 6 số về Gmail."
    )
    public ResponseEntity<ApiResponse<Map<String, Object>>> requestChangePasswordOtp(
            @Valid @RequestBody RequestChangePasswordOtpRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Đã gửi mã OTP xác nhận đổi mật khẩu",
                authService.requestChangePasswordOtp(request)
        ));
    }

    @PostMapping("/change-password/confirm")
    @Operation(
            summary = "Bước 2 Đổi mật khẩu: Nhập mã OTP nhận từ Gmail và mật khẩu mới",
            description = "Xác thực mã OTP (CHANGE_PASSWORD) và lưu mật khẩu mới vào database."
    )
    public ResponseEntity<ApiResponse<AccountDto>> confirmChangePassword(
            @Valid @RequestBody ChangePasswordConfirmRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Đổi mật khẩu thành công",
                authService.confirmChangePassword(request)
        ));
    }

    @PostMapping("/send-otp")
    @Operation(
            summary = "Yêu cầu gửi mã OTP 6 số về Email (LOGIN / CHANGE_PASSWORD / FORGOT_PASSWORD)",
            description = "Gửi mã OTP thật về email (VD: khanhtrinh1204@gmail.com)."
    )
    public ResponseEntity<ApiResponse<Map<String, Object>>> sendOtp(@Valid @RequestBody SendOtpRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Đã xử lý yêu cầu gửi OTP", authService.sendOtp(request)));
    }

    @PostMapping("/verify-otp")
    @Operation(
            summary = "Xác thực mã OTP và nhận JWT Token",
            description = "Kiểm tra mã OTP 6 số đã nhận qua email. Nếu hợp lệ sẽ cấp JWT Access Token & Refresh Token."
    )
    public ResponseEntity<ApiResponse<LoginResponse>> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Xác thực OTP thành công", authService.verifyOtp(request)));
    }

    @PostMapping("/forgot-password")
    @Operation(
            summary = "Quên mật khẩu - Gửi mã OTP khôi phục về Email",
            description = "Gửi mã OTP (FORGOT_PASSWORD) về email."
    )
    public ResponseEntity<ApiResponse<Map<String, Object>>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Đã gửi mã OTP khôi phục mật khẩu", authService.forgotPassword(request)));
    }

    @PostMapping("/reset-password")
    @Operation(
            summary = "Đặt lại mật khẩu mới bằng mã OTP (Quên mật khẩu)",
            description = "Xác thực mã OTP nhận từ forgot-password và cập nhật mật khẩu mới."
    )
    public ResponseEntity<ApiResponse<AccountDto>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Đặt lại mật khẩu thành công", authService.resetPassword(request)));
    }
}
