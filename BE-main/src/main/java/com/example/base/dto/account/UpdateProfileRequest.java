package com.example.base.dto.account;

import com.example.base.entity.AccountRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public class UpdateProfileRequest {

    @Size(max = 30, message = "Số điện thoại không được vượt quá 30 ký tự")
    @Schema(description = "Số điện thoại liên hệ", example = "0912345678")
    private String phone;

    @Schema(description = "Mật khẩu hiện tại (tùy chọn khi đổi mật khẩu)", example = "123")
    private String currentPassword;

    @Size(min = 3, max = 100, message = "Mật khẩu mới phải từ 3 đến 100 ký tự")
    @Schema(description = "Mật khẩu mới (để trống nếu không đổi mật khẩu)", example = "123456")
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
