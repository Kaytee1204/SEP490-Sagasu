package com.example.base.config;

import com.example.base.security.CustomUserDetails;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.util.List;

@Configuration
public class OpenApiConfig {

    static {
        // Automatically hide @AuthenticationPrincipal and CustomUserDetails from Swagger UI schemas & parameter lists
        SpringDocUtils.getConfig()
                .addAnnotationsToIgnore(AuthenticationPrincipal.class)
                .addRequestWrapperToIgnore(CustomUserDetails.class);
    }

    @Bean
    public OpenAPI khanhTbOpenAPI() {
        final String securitySchemeName = "bearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("Sagasu - Lost & Found Management System API")
                        .description(
                                "### Tài liệu API Backend Sagasu (Hệ thống Quản lý và Tìm kiếm Đồ thất lạc)\n\n"
                                + "#### 🔑 Hướng dẫn Xác thực & Đăng nhập:\n"
                                + "- **Đăng nhập trực tiếp (Email/Password)**: Không bắt buộc mã OTP cho mọi role (`ADMIN`, `MANAGER`, `APPROVER`, `USER`) khi đăng nhập thường qua `/api/auth/login`.\n"
                                + "- **Tài khoản test có sẵn**:\n"
                                + "  - `ADMIN`: `admin` / `123`\n"
                                + "  - `MANAGER`: `khanhtrinh1204@gmail.com` / `123`\n"
                                + "  - `USER`: `user` / `123`\n"
                                + "- **Xác thực OTP**: Chỉ áp dụng khi Đổi mật khẩu (`/api/auth/change-password/*`) hoặc Quên mật khẩu (`/api/auth/forgot-password`, `/api/auth/reset-password`).\n"
                                + "- **JWT Bearer Token**: Nhập token nhận được từ `/api/auth/login` vào nút **Authorize** ở góc trên bên phải theo cú pháp `Bearer <token>` (hoặc chỉ cần dán token).\n\n"
                                + "#### 📦 Các phân hệ chính:\n"
                                + "1. **1. Auth & OTP**: Đăng nhập, Gửi OTP, Đổi mật khẩu & Quên mật khẩu.\n"
                                + "2. **2. Account Management**: Quản lý tài khoản (CRUD, Xóa mềm, Import/Export Excel), Lịch sử đăng nhập & Hồ sơ cá nhân (ADMIN & MANAGER).\n"
                                + "3. **3. Posts - Lost & Found**: Tìm kiếm, Xem chi tiết, Đối sánh AI Multimodal và Đăng bài Lost / Found.\n"
                                + "4. **4. My Posts & Post Management**: Quản lý bài đăng cá nhân của người dùng (Xem danh sách, Sửa bài, Xóa mềm, Dấu hiệu bí mật).\n"
                                + "5. **5. Approver Intake Receipts**: Biên bản tiếp nhận tài sản dành cho Cán bộ tiếp nhận (APPROVER) - Lưu nháp, Chỉnh sửa, Đăng bài.\n"
                                + "6. **6. Receipt Storage Locations**: Danh mục kho / vị trí lưu trữ tài sản tiếp nhận.\n"
                                + "7. **7. Category Management**: Quản lý danh mục đồ thất lạc (MANAGER).\n"
                                + "8. **8. Claims P2P**: Hồ sơ nhận đồ riêng tư giữa người yêu cầu (Claimer) và người nhặt (Finder)."
                        )
                        .version("1.0.0")
                        .contact(new Contact().name("Sagasu Development Team")))
                .tags(List.of(
                        new Tag().name("1. Auth & OTP").description("Xác thực, Đăng nhập trực tiếp (Email/Password), Đổi mật khẩu và Quên mật khẩu qua mã OTP"),
                        new Tag().name("2. Account Management").description("Quản lý tài khoản (CRUD, Xóa mềm, Import/Export Excel), Lịch sử đăng nhập & Hồ sơ cá nhân (ADMIN & MANAGER)"),
                        new Tag().name("3. Posts - Lost & Found").description("Tra cứu, Lọc bài đăng công khai, Chi tiết bài đăng, AI Smart Matching và Đăng bài Lost / Found"),
                        new Tag().name("4. My Posts & Post Management").description("Quản lý bài viết của tôi (Danh sách bài của tôi, Lấy form sửa, Cập nhật bài, Xóa mềm bài)"),
                        new Tag().name("5. Approver Intake Receipts").description("Quản lý biên bản tiếp nhận tài sản thất lạc dành cho Cán bộ tiếp nhận (APPROVER)"),
                        new Tag().name("6. Receipt Storage Locations").description("Danh mục kho và vị trí lưu trữ tài sản tiếp nhận"),
                        new Tag().name("7. Category Management").description("Quản lý danh mục đồ thất lạc (Chỉ dành cho role MANAGER)"),
                        new Tag().name("8. Claims P2P").description("UC40-45: Hồ sơ nhận đồ riêng tư giữa người claim và Finder")
                ))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName, new SecurityScheme()
                                .name(securitySchemeName)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
