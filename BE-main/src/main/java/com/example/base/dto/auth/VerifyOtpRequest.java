package com.example.base.dto.auth;

import com.example.base.entity.OtpPurpose;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public class VerifyOtpRequest {

    @NotBlank(message = "Email không được để trống")
    @Schema(description = "Email cần xác thực OTP", example = "khanhtrinh1204@gmail.com")
    private String email;

    @NotBlank(message = "Mã OTP không được để trống")
    @Schema(description = "Mã OTP 6 chữ số nhận qua email", example = "123456")
    private String otp;

    @Schema(description = "Mục đích OTP: LOGIN, CHANGE_PASSWORD, FORGOT_PASSWORD", example = "LOGIN")
    private OtpPurpose purpose = OtpPurpose.LOGIN;

    public VerifyOtpRequest() {
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

    public OtpPurpose getPurpose() {
        return purpose;
    }

    public void setPurpose(OtpPurpose purpose) {
        this.purpose = purpose;
    }
}
