package com.example.base.dto.account;

import com.example.base.entity.AccountRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;

public class UpdateProfileRequest {

    @Pattern(
            regexp = "^$|^(0|\\+84)[0-9]{9,10}$",
            message = "Số điện thoại không hợp lệ (phải gồm 10-11 chữ số, bắt đầu bằng 0 hoặc +84)"
    )
    @Schema(description = "Số điện thoại liên hệ (10-11 chữ số)", example = "0912345678")
    private String phone;

    @Schema(description = "Mật khẩu hiện tại (tùy chọn khi đổi mật khẩu)", example = "123")
    private String currentPassword;

    @Pattern(
            regexp = "^$|^(?=.*[^a-zA-Z0-9\\s])\\S{8,16}$",
            message = "Mật khẩu mới phải dài từ 8 đến 16 ký tự, chứa ít nhất 1 ký tự đặc biệt và không có khoảng trắng"
    )
    @Schema(description = "Mật khẩu mới từ 8-16 ký tự, có ký tự đặc biệt (để trống nếu không đổi mật khẩu)", example = "Abc@1234")
    private String newPassword;

    @Schema(description = "Vai trò (Role) - Chỉ ADMIN mới có quyền thay đổi", example = "ADMIN")
    private AccountRole role;

    public UpdateProfileRequest() {
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getCurrentPassword() {
        return currentPassword;
    }

    public void setCurrentPassword(String currentPassword) {
        this.currentPassword = currentPassword;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }

    public AccountRole getRole() {
        return role;
    }

    public void setRole(AccountRole role) {
        this.role = role;
    }
}
