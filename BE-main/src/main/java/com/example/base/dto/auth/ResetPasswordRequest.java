package com.example.base.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class ResetPasswordRequest {

    @NotBlank(message = "Email không được để trống")
    @Pattern(
            regexp = "^([A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}|admin|user)$",
            message = "Định dạng Email không hợp lệ (VD: example@gmail.com)"
    )
    @Schema(description = "Email tài khoản", example = "khanhtrinh1204@gmail.com")
    private String email;

    @NotBlank(message = "Mã OTP không được để trống")
    @Pattern(regexp = "^[0-9]{6}$", message = "Mã OTP phải gồm đúng 6 chữ số")
    @Schema(description = "Mã OTP 6 số đã nhận qua email", example = "123456")
    private String otp;

    @NotBlank(message = "Mật khẩu mới không được để trống")
    @Pattern(
            regexp = "^(?=.*[^a-zA-Z0-9\\s])\\S{8,16}$",
            message = "Mật khẩu mới phải dài từ 8 đến 16 ký tự, chứa ít nhất 1 ký tự đặc biệt và không có khoảng trắng"
    )
    @Schema(description = "Mật khẩu mới (8-16 ký tự, có ký tự đặc biệt)", example = "NewPass@123")
    private String newPassword;

    public ResetPasswordRequest() {
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
