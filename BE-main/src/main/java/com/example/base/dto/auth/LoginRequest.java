package com.example.base.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class LoginRequest {

    @NotBlank(message = "Email không được để trống")
    @Size(min = 3, max = 255, message = "Email phải từ 3 đến 255 ký tự")
    @Pattern(
            regexp = "^(?i)(admin|[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,})$",
            message = "Định dạng Email không hợp lệ (VD: example@gmail.com)"
    )
    @Schema(
            description = "Địa chỉ Email đăng nhập (VD: khanhtrinh1204@gmail.com, user01@gmail.com hoặc admin)",
            example = "khanhtrinh1204@gmail.com"
    )
    private String email;

    @NotBlank(message = "Mật khẩu không được để trống")
    @Size(min = 3, max = 100, message = "Mật khẩu phải từ 3 đến 100 ký tự")
    @Schema(description = "Mật khẩu đăng nhập", example = "123")
    private String password;

    public LoginRequest() {
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
