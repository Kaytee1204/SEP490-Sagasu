package com.example.base.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public class LoginRequest {

    @NotBlank(message = "Email/Tài khoản không được để trống")
    @Schema(description = "Email hoặc tên tài khoản (VD: admin hoặc khanhtrinh1204@gmail.com)", example = "admin")
    private String email;

    @NotBlank(message = "Mật khẩu không được để trống")
    @Schema(description = "Mật khẩu", example = "123")
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
