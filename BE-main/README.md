# SAGASU BACKEND - JAVA SPRING BOOT

Dự án Backend cho Hệ thống Tìm kiếm & Trao trả Đồ Thất lạc **SAGASU** (Trường Đại học FPT).

## 1. Cấu trúc Dự án (Feature-based Architecture)

```
com.sagasu
├── SagasuBackendApplication.java
├── common
│   ├── config (SecurityConfig, CorsConfig, OpenApiConfig, DataInitializer)
│   ├── exception (AppException, ErrorCode, ErrorResponse, GlobalExceptionHandler)
│   ├── response (ApiResponse, PagedResponse)
│   ├── security (JwtTokenProvider, JwtAuthenticationFilter, UserPrincipal, CustomUserDetailsService)
│   └── util (HashUtil, OtpUtil)
├── entity
│   ├── User.java (Khớp cấu trúc bảng "user" trong Sagasu_PostgreSQL_P0_P1_P2.sql)
│   ├── Organization.java
│   ├── OrganizationUnit.java
│   ├── VerificationToken.java (OTP/Token bảng verification_token)
│   ├── UserSession.java (Quản lý JWT + Session tracking)
│   ├── AuditLog.java (Bảng audit_log)
│   └── enums (Role, UserStatus, UserType, ProvisionMethod, PreferredContact, VerificationPurpose, AuditResult)
├── repository
│   ├── UserRepository.java
│   ├── OrganizationRepository.java
│   ├── OrganizationUnitRepository.java
│   ├── VerificationTokenRepository.java
│   ├── UserSessionRepository.java
│   └── AuditLogRepository.java
└── feature
    ├── auth (UC-01 Đăng nhập OTP/SSO, UC-02 Đăng xuất, UC-03 Khôi phục mật khẩu)
    │   ├── controller (AuthController.java)
    │   ├── dto (LoginRequest, LoginResponse, VerifyOtpRequest, SsoLoginRequest, ForgotPasswordRequest, ResetPasswordRequest, RefreshTokenRequest, AuthUserDto)
    │   └── service (AuthService.java, EmailService.java)
    ├── profile (UC-04 Cập nhật hồ sơ cá nhân)
    │   ├── controller (ProfileController.java)
    │   ├── dto (ProfileResponse, UpdateProfileRequest)
    │   └── service (ProfileService.java)
    ├── usermanagement (UC-05 Import nội bộ, UC-06 Cấp tài khoản ngoại, UC-07 Đổi trạng thái, UC-08 Phân quyền)
    │   ├── controller (UserManagementController.java)
    │   ├── dto (ImportUserRow, ImportPreviewResponse, ImportConfirmRequest, CreateExternalUserRequest, ChangeUserStatusRequest, AssignRoleRequest, UserSummaryDto, UnitDto)
    │   └── service (UserManagementService.java, ExcelImportService.java)
    └── audit
        └── service (AuditService.java)
```

---

## 2. Các Chức Năng Đã Triển Khai (UC-01 đến UC-08)

| Use Case | Tên chức năng | Đối tượng | Mô tả kỹ thuật |
| :--- | :--- | :--- | :--- |
| **UC-01** | Đăng nhập | Tất cả | Đăng nhập Email/Mật khẩu hoặc SSO. Kiểm tra trạng thái tài khoản (ACTIVE, SUSPENDED, DELETED), thời hạn external, tổ chức. Gửi mã OTP 6 số qua email. Xác thực OTP cấp JWT Access Token (24h) và Refresh Token (7 ngày) lưu session trong DB. |
| **UC-02** | Đăng xuất | Đã đăng nhập | Thu hồi session hiện tại trong DB (`is_revoked = true`), xóa SecurityContext, ghi audit log. |
| **UC-03** | Khôi phục mật khẩu | Tài khoản cục bộ | Gửi mã OTP khôi phục qua email. Kiểm tra quy tắc tài khoản SSO phải khôi phục tại nhà cung cấp SSO. Xác minh OTP và đổi mật khẩu mới (tự động tăng `auth_version` để thu hồi toàn bộ session cũ). |
| **UC-04** | Cập nhật hồ sơ cá nhân | Đã đăng nhập | Cập nhật tên hiển thị, số điện thoại, ảnh đại diện và phương thức liên hệ ưu tiên (`IN_APP`, `EMAIL`, `PHONE`), ghi audit log. |
| **UC-05** | Import tài khoản nội bộ | Manager | Chọn file Excel (`.xlsx`, `.xls`) hoặc `.csv` và chọn đơn vị (Unit). Kiểm tra tên miền email hợp lệ theo tổ chức, phát hiện trùng lặp trong file và trong DB, preview kết quả và xác nhận import hàng loạt. |
| **UC-06** | Cấp tài khoản ngoại trường | Manager | Ghi nhận thông tin định danh (CCCD/Passport), người bảo trợ (Sponsor), thời hạn hiệu lực (`valid_until`), tạo tài khoản `EXTERNAL` với `provision_method = MANUAL` tuân thủ ràng buộc SQL. |
| **UC-07** | Thay đổi trạng thái tài khoản | Manager | Khóa (`SUSPENDED`), mở khóa (`ACTIVE`), ngừng hiệu lực (`DELETED`) trong phạm vi tổ chức kèm lý do. Nếu khóa/ngừng hiệu lực, tự động thu hồi toàn bộ session của tài khoản đó ngay lập tức. |
| **UC-08** | Phân quyền nghiệp vụ | Manager | Gán quyền `USER`, `CHECKER`, `OFFICER` kèm lý do. Với quyền `CHECKER` và `OFFICER`, bắt buộc chọn đơn vị phạm vi (`scope_unit_id`) theo đúng ràng buộc cơ sở dữ liệu. |

---

## 3. Tài Khoản Thử Nghiệm Mặc Định (Đã nạp sẵn)

Mật khẩu chung: `Password@123`

1. **Quản trị viên (Admin):** `admin@fpt.edu.vn`
2. **Quản lý tổ chức (Manager):** `manager@fpt.edu.vn`
3. **Cán bộ tiếp nhận (Officer):** `officer@fpt.edu.vn`
4. **Cán bộ kiểm duyệt (Checker):** `checker@fpt.edu.vn`
5. **Sinh viên / Người dùng (User):** `student@fpt.edu.vn` (Nguyễn Văn An - MSSV: HE160001)
6. **Người dùng ngoài trường (External User):** `guest@external.com`

---

## 4. Hướng Dẫn Chạy Backend & Kiểm Thử

### Cách chạy Backend:
```bash
cd "D:\FPTU\SES_9\SEP490-Sagasu\BE-main"
# Sử dụng Maven và JDK:
mvn spring-boot:run
```

- **Swagger UI:** [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **OpenAPI JSON Docs:** [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)
- **H2 Database Console:** [http://localhost:8080/h2-console](http://localhost:8080/h2-console) (JDBC URL: `jdbc:h2:mem:sagasudb`, User: `sa`, Password để trống)

### Cách chạy Frontend:
```bash
cd "D:\FPTU\SES_9\SEP490-Sagasu\FE-main"
npm run dev
```
Truy cập [http://localhost:5173](http://localhost:5173).
- Khi chưa đăng nhập: Hiển thị **Trang chủ yêu cầu đăng nhập**.
- Đăng nhập xong: Tự động chuyển vào **Trang chủ đầy đủ tính năng**.
