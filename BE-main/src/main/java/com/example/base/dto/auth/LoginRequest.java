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

    @Schema(description = "Nhóm đăng nhập: student (Sinh viên - role USER) hoặc staff (Cán bộ & Phòng ban - role ADMIN, MANAGER, APPROVER, CHECKER)", example = "student")
    private String userGroup;

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

    public String getUserGroup() {
        return userGroup;
    }

    public void setUserGroup(String userGroup) {
        this.userGroup = userGroup;
    }
}
