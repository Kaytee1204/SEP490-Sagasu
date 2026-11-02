package com.example.base.dto.auth;

import com.example.base.dto.account.AccountDto;
import io.swagger.v3.oas.annotations.media.Schema;

public class LoginResponse {

    @Schema(description = "JWT Access Token (dán vào nút Authorize trên Swagger, chỉ có khi không cần OTP hoặc đã xác thực OTP)")
    private String accessToken;

    @Schema(description = "Refresh Token")
    private String refreshToken;

    @Schema(description = "Loại token", example = "Bearer")
    private String tokenType = "Bearer";

    @Schema(description = "Thông tin tài khoản đăng nhập")
    private AccountDto account;

    @Schema(description = "True nếu tài khoản thuộc role USER và cần nhập mã OTP 6 chữ số tại /api/auth/verify-otp để vào hệ thống", example = "false")
    private boolean requiresOtp = false;

    @Schema(description = "Địa chỉ email nhận mã OTP (khi requiresOtp = true)", example = "user01@gmail.com")
    private String email;

    @Schema(description = "Thông báo hướng dẫn xác thực OTP (kèm mã OTP test nếu chưa cấu hình SMTP)", example = "Mã OTP 6 số đã được gửi tới email user01@gmail.com")
    private String message;

    public LoginResponse() {
    }

    public LoginResponse(String accessToken, String refreshToken, AccountDto account) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.tokenType = "Bearer";
        this.account = account;
        this.requiresOtp = false;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public AccountDto getAccount() {
        return account;
    }

    public void setAccount(AccountDto account) {
        this.account = account;
    }

    public boolean isRequiresOtp() {
        return requiresOtp;
    }

    public void setRequiresOtp(boolean requiresOtp) {
        this.requiresOtp = requiresOtp;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
