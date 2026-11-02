package com.example.base.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI khanhTbOpenAPI() {
        final String securitySchemeName = "bearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("Sagasu - Lost & Found System API")
                        .description(
                                "Tài liệu API Backend Sagasu:\n"
                                + "- **1. Auth & OTP**: Đăng nhập bằng Email và Mật khẩu (Role `USER` bắt buộc xác thực mã OTP 6 số qua `/api/auth/verify-otp`; các role `ADMIN`, `MANAGER`, `CHECKER`, `APPROVER` vào thẳng không cần OTP).\n"
                                + "- **2. Account Manager**: Quản lý tài khoản (Tạo tay, Cập nhật, Xóa mềm, Import/Export Excel) và Hồ sơ cá nhân (`/api/accounts/me`).\n"
                                + "- **3. Posts**: Đăng tin đồ thất lạc (Lost/Found), Tìm kiếm, Chi tiết và Quản lý bài đăng cá nhân.\n"
                                + "- **4. Category Management**: Quản lý danh mục đồ thất lạc dành cho `MANAGER`."
                        )
                        .version("1.0.0"))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName, new SecurityScheme()
                                .name(securitySchemeName)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
