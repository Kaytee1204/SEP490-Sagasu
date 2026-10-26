package com.example.base.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public class ForgotPasswordRequest {

    @NotBlank(message = "Email không được để trống")
    @Schema(description = "Email tài khoản cần lấy mã OTP khôi phục mật khẩu", example = "khanhtrinh1204@gmail.com")
    private String email;

    public ForgotPasswordRequest() {
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
