package com.example.base.dto.account;

import com.example.base.entity.AccountRole;
import com.example.base.entity.AccountStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public class CreateAccountRequest {

    @NotBlank(message = "Tên người dùng không được để trống")
    @Size(min = 2, max = 150, message = "Tên người dùng phải từ 2 đến 150 ký tự")
    @Schema(description = "Họ và tên người dùng", example = "Nguyễn Văn A")
    private String fullName;

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không đúng định dạng (VD: user@gmail.com)")
    @Size(max = 255, message = "Email không được vượt quá 255 ký tự")
    @Schema(description = "Email tài khoản (tự động chuyển về chữ thường)", example = "user01@gmail.com")
    private String email;

    @Size(min = 3, max = 100, message = "Mật khẩu phải từ 3 đến 100 ký tự")
    @Schema(description = "Mật khẩu (nếu để trống hệ thống sẽ tự sinh ngẫu nhiên)", example = "123456")
    private String password;

    @NotNull(message = "Role không được để trống")
    @Schema(description = "Phân quyền: USER, EXTERNAL_USER, CHECKER, APPROVER, MANAGER, ADMIN", example = "USER")
    private AccountRole role = AccountRole.USER;

    @Schema(description = "Trạng thái: PENDING_ACTIVATION, ACTIVE, SUSPENDED", example = "ACTIVE")
    private AccountStatus status = AccountStatus.ACTIVE;

    @Schema(description = "Yêu cầu đổi mật khẩu lần đầu", example = "true")
    private Boolean mustChangePassword;

    @Schema(description = "Thời hạn hiệu lực của tài khoản (ISO-8601)", example = "2027-12-31T23:59:59+07:00")
    private OffsetDateTime validUntil;

    public CreateAccountRequest() {
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
