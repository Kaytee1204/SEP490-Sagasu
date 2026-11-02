package com.example.base.dto.account;

import com.example.base.entity.AccountRole;
import com.example.base.entity.AccountStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public class CreateAccountRequest {

    @NotBlank(message = "Tên người dùng không được để trống")
    @Size(min = 2, max = 150, message = "Tên người dùng phải từ 2 đến 150 ký tự")
    @Pattern(
            regexp = "^[\\p{L}0-9 .,'()_-]{2,150}$",
            message = "Tên người dùng chỉ được chứa chữ cái, chữ số và khoảng trắng (2-150 ký tự)"
    )
    @Schema(description = "Họ và tên người dùng", example = "Nguyễn Văn A")
    private String fullName;

    @NotBlank(message = "Email không được để trống")
    @Size(min = 5, max = 255, message = "Email phải từ 5 đến 255 ký tự")
    @Pattern(
            regexp = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$",
            message = "Định dạng Email không hợp lệ (VD: user01@gmail.com)"
    )
    @Schema(description = "Địa chỉ Email của tài khoản", example = "user01@gmail.com")
    private String email;

    @Pattern(
            regexp = "^$|^(0|\\+84)[0-9]{9,10}$",
            message = "Số điện thoại không hợp lệ (phải gồm 10-11 chữ số, bắt đầu bằng 0 hoặc +84)"
    )
    @Schema(description = "Số điện thoại liên hệ (10-11 số)", example = "0912345678")
    private String phone;

    @Pattern(
            regexp = "^$|^(?=.*[^a-zA-Z0-9\\s])\\S{8,16}$",
            message = "Mật khẩu phải dài từ 8 đến 16 ký tự, chứa ít nhất 1 ký tự đặc biệt và không có khoảng trắng"
    )
    @Schema(description = "Mật khẩu từ 8-16 ký tự, có ít nhất 1 ký tự đặc biệt (nếu để trống hệ thống tự sinh ngẫu nhiên)", example = "Abc@1234")
    private String password;

    @NotNull(message = "Role không được để trống")
    @Schema(description = "Phân quyền: USER, CHECKER, APPROVER, MANAGER, ADMIN", example = "USER")
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

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
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
