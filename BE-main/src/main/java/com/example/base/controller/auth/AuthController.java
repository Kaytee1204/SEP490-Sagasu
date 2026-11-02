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
@Tag(
        name = "1. Auth & OTP",
        description = "Xác thực, Đăng nhập trực tiếp (Email/Password), Đổi mật khẩu và Quên mật khẩu qua mã OTP"
)
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @Operation(
            summary = "Đăng nhập bằng Email/Username & Mật khẩu (ADMIN: admin/123 | MANAGER: khanhtrinh1204@gmail.com/123 | USER: user/123)",
            description = "Đăng nhập trực tiếp không cần mã OTP cho tất cả các Role (ADMIN, MANAGER, CHECKER, APPROVER, USER). Trả về Access Token (JWT), Refresh Token và thông tin tài khoản."
    )
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Đăng nhập thành công", authService.login(request)));
    }

    @PostMapping("/verify-otp")
    @Operation(
            summary = "Xác thực mã OTP 6 chữ số thủ công nếu cần",
            description = "Nhập email, mã OTP 6 số đã nhận qua email và purpose để xác thực mã OTP khi cần thiết."
    )
    public ResponseEntity<ApiResponse<LoginResponse>> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Xác thực OTP thành công", authService.verifyOtp(request)));
    }

    @PostMapping("/send-otp")
    @Operation(
            summary = "Yêu cầu gửi / gửi lại mã OTP 6 số về Email (CHANGE_PASSWORD / FORGOT_PASSWORD)",
            description = "Tạo mã OTP 6 chữ số mới (hiệu lực 5 phút) và gửi về email đã đăng ký."
    )
    public ResponseEntity<ApiResponse<Map<String, Object>>> sendOtp(@Valid @RequestBody SendOtpRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Đã xử lý yêu cầu gửi OTP", authService.sendOtp(request)));
    }

    @PostMapping("/change-password/request-otp")
    @Operation(
            summary = "Bước 1 Đổi mật khẩu: Nhập email và mật khẩu hiện tại để nhận mã OTP qua Gmail",
            description = "Kiểm tra email và mật khẩu hiện tại. Nếu chính xác sẽ gửi mã OTP 6 số (purpose = CHANGE_PASSWORD) về Gmail."
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
            summary = "Bước 2 Đổi mật khẩu: Nhập mã OTP và mật khẩu mới (8-16 ký tự, có ký tự đặc biệt)",
            description = "Xác thực mã OTP (CHANGE_PASSWORD) và cập nhật mật khẩu mới vào database."
    )
    public ResponseEntity<ApiResponse<AccountDto>> confirmChangePassword(
            @Valid @RequestBody ChangePasswordConfirmRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Đổi mật khẩu thành công",
                authService.confirmChangePassword(request)
        ));
    }

    @PostMapping("/forgot-password")
    @Operation(
            summary = "Quên mật khẩu - Bước 1: Gửi mã OTP khôi phục về Email",
            description = "Gửi mã OTP 6 số (FORGOT_PASSWORD) về email để khôi phục mật khẩu."
    )
    public ResponseEntity<ApiResponse<Map<String, Object>>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Đã gửi mã OTP khôi phục mật khẩu", authService.forgotPassword(request)));
    }

    @PostMapping("/reset-password")
    @Operation(
            summary = "Quên mật khẩu - Bước 2: Đặt lại mật khẩu mới bằng mã OTP",
            description = "Xác thực mã OTP nhận từ /api/auth/forgot-password và cập nhật mật khẩu mới (8-16 ký tự, có ký tự đặc biệt)."
    )
    public ResponseEntity<ApiResponse<AccountDto>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Đặt lại mật khẩu thành công", authService.resetPassword(request)));
    }
}
