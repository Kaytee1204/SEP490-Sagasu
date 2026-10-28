package com.example.base.dto.account;

import com.example.base.entity.AccountRole;
import com.example.base.entity.AccountStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public class UpdateAccountRequest {

    @Size(min = 2, max = 150, message = "Tên người dùng phải từ 2 đến 150 ký tự")
    @Schema(description = "Họ và tên người dùng", example = "Nguyễn Văn A")
    private String fullName;

    @Email(message = "Email không đúng định dạng (VD: user@gmail.com)")
    @Size(max = 255, message = "Email không được vượt quá 255 ký tự")
    @Schema(description = "Email mới (tùy chọn)", example = "user01@gmail.com")
    private String email;

    @Size(min = 3, max = 100, message = "Mật khẩu mới phải từ 3 đến 100 ký tự")
    @Schema(description = "Mật khẩu mới (tùy chọn - để trống nếu không đổi)")
    private String password;

    @Schema(description = "Role mới: USER, CHECKER, APPROVER, MANAGER, ADMIN", example = "CHECKER")
    private AccountRole role;

    @Schema(description = "Trạng thái mới: PENDING_ACTIVATION, ACTIVE, SUSPENDED, DELETED", example = "ACTIVE")
    private AccountStatus status;

    @Schema(description = "Bắt buộc đổi mật khẩu ở lần đăng nhập tới", example = "false")
    private Boolean mustChangePassword;

    @Schema(description = "Thời hạn hiệu lực tài khoản", example = "2027-12-31T23:59:59+07:00")
    private OffsetDateTime validUntil;

    public UpdateAccountRequest() {
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
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

    public AccountRole getRole() {
        return role;
    }

    public void setRole(AccountRole role) {
        this.role = role;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public void setStatus(AccountStatus status) {
        this.status = status;
    }

    public Boolean getMustChangePassword() {
        return mustChangePassword;
    }

    public void setMustChangePassword(Boolean mustChangePassword) {
        this.mustChangePassword = mustChangePassword;
    }

    public OffsetDateTime getValidUntil() {
        return validUntil;
    }

    public void setValidUntil(OffsetDateTime validUntil) {
        this.validUntil = validUntil;
    }
}
