package com.example.base.dto.auth;

import com.example.base.entity.OtpPurpose;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class SendOtpRequest {

    @NotBlank(message = "Email không được để trống")
    @Pattern(
            regexp = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$",
            message = "Định dạng Email không hợp lệ (VD: example@gmail.com)"
    )
    @Schema(description = "Email nhận mã OTP", example = "khanhtrinh1204@gmail.com")
    private String email;

    @Schema(description = "Mục đích lấy OTP: LOGIN, CHANGE_PASSWORD, FORGOT_PASSWORD", example = "LOGIN")
    private OtpPurpose purpose = OtpPurpose.LOGIN;

    public SendOtpRequest() {
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public OtpPurpose getPurpose() {
        return purpose;
    }

    public void setPurpose(OtpPurpose purpose) {
        this.purpose = purpose;
    }
}
