package com.example.base.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public class RequestChangePasswordOtpRequest {

    @NotBlank(message = "Email không được để trống")
    @Schema(description = "Email tài khoản cần đổi mật khẩu", example = "khanhtrinh1204@gmail.com")
    private String email;

    @NotBlank(message = "Mật khẩu hiện tại không được để trống")
    @Schema(description = "Mật khẩu hiện tại", example = "123")
    private String currentPassword;

    public RequestChangePasswordOtpRequest() {
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getCurrentPassword() {
        return currentPassword;
    }

    public void setCurrentPassword(String currentPassword) {
        this.currentPassword = currentPassword;
    }
}
