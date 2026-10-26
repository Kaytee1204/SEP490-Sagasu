package com.example.base.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public class ChangePasswordConfirmRequest {

    @NotBlank(message = "Email không được để trống")
    @Schema(description = "Email tài khoản", example = "khanhtrinh1204@gmail.com")
    private String email;

    @NotBlank(message = "Mã OTP không được để trống")
    @Schema(description = "Mã OTP 6 số nhận qua Gmail", example = "123456")
    private String otp;

    @NotBlank(message = "Mật khẩu mới không được để trống")
    @Schema(description = "Mật khẩu mới muốn đổi", example = "Khanh@1234")
    private String newPassword;

    public ChangePasswordConfirmRequest() {
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getOtp() {
        return otp;
    }

    public void setOtp(String otp) {
        this.otp = otp;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
