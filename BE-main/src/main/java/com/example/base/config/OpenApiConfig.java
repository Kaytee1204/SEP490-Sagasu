package com.example.base.config;

import com.example.base.security.CustomUserDetails;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Configuration
public class OpenApiConfig {

    static {
        // Automatically hide @AuthenticationPrincipal and CustomUserDetails from Swagger UI schemas & parameter lists
        SpringDocUtils.getConfig()
                .addAnnotationsToIgnore(AuthenticationPrincipal.class)
                .addRequestWrapperToIgnore(CustomUserDetails.class);
    }

    @Bean
    public OpenAPI sagasuOpenAPI() {
        final String securitySchemeName = "bearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("Sagasu - Lost & Found Management System API (14 Modules)")
                        .description(
                                "### Tài liệu Kiểm thử API Backend Hệ thống Sagasu\n\n"
                                + "#### 1. Hướng dẫn Xác thực & Đăng nhập:\n"
                                + "- **Đăng nhập trực tiếp (`/api/auth/login`)**: Hỗ trợ Email không phân biệt hoa/thường (`normalizeEmail`). Nhập sai mật khẩu quá **5 lần** sẽ tạm khóa tài khoản **15 phút**.\n"
                                + "- **Quên mật khẩu & Mở khóa tài khoản**: Gồm 3 bước chuẩn: Gửi OTP (`/api/auth/forgot-password`) -> Xác thực OTP (`/api/auth/verify-reset-otp`) -> Đặt lại mật khẩu mới & mở khóa (`/api/auth/reset-password`).\n"
                                + "- **Tài khoản test mặc định**:\n"
                                + "  - `ADMIN`: `admin` / `123`\n"
                                + "  - `MANAGER`: `khanhtrinh1204@gmail.com` / `L!H$1204`\n"
                                + "  - `USER`: `user` / `123`\n"
                                + "- **Sử dụng JWT Bearer Token**: Sau khi gọi `[1.1] POST /api/auth/login`, copy giá trị `accessToken` và dán vào nút **Authorize** ở góc trên bên phải.\n\n"
                                + "#### 2. Danh sách 14 Module Chức năng (Đánh số thứ tự 01 -> 14):\n"
                                + "- **01 -> 02**: Xác thực (Auth) & Quản lý Tài khoản (Account/Profile)\n"
                                + "- **03 -> 05**: Danh mục (Category), Bài đăng công khai & AI Smart Match (Posts), Bài đăng của tôi (My Posts)\n"
                                + "- **06 -> 08**: Yêu cầu nhận đồ P2P & Bộ câu hỏi (Claims P2P), Trò chuyện trực tuyến (Chat P2P), Bàn giao trực tiếp (Handover P2P)\n"
                                + "- **09 -> 12**: Nghiệp vụ Văn phòng (Biên bản tiếp nhận, Kho lưu trữ, Claim văn phòng, Chuyển giao & Lịch hẹn 12h, Kiểm duyệt bài đăng)\n"
                                + "- **13 -> 14**: Báo cáo vi phạm & Điều tra giải quyết (Reports & Review), Thông báo đa kênh (Notifications)\n"
                        )
                        .version("2.0.0")
                        .contact(new Contact().name("Sagasu Development Team")))
                .tags(List.of(
                        new Tag().name("01. Auth - Đăng nhập & Quên mật khẩu")
                                .description("Đăng nhập trực tiếp (Email/Password, khóa tạm thời sau 5 lần sai) và Khôi phục mật khẩu 3 bước qua mã OTP Email"),
                        new Tag().name("02. Account Management - Quản lý tài khoản & Hồ sơ")
                                .description("Hồ sơ cá nhân (/me), Lịch sử đăng nhập (audit-logs), CRUD tài khoản, Xóa mềm và Import/Export Excel (ADMIN & MANAGER)"),
                        new Tag().name("03. Category Management - Quản lý danh mục đồ vật")
                                .description("Tra cứu, Tạo mới, Đổi tên và Bật/Tắt trạng thái hoạt động của danh mục đồ thất lạc (MANAGER)"),
                        new Tag().name("04. Posts (Lost & Found) - Bài đăng & AI Smart Matching")
                                .description("Tìm kiếm/Lọc bài đăng công khai, Xem chi tiết, Đăng tin Lost/Found, Dấu hiệu nhận dạng riêng tư và Đối sánh thông minh Multimodal AI"),
                        new Tag().name("05. My Posts - Quản lý bài đăng cá nhân")
                                .description("Danh sách bài đăng của tôi, Lấy dữ liệu sửa bài, Cập nhật bài đăng, Gửi yêu cầu giao đồ cho văn phòng và Xóa mềm bài đăng"),
                        new Tag().name("06. Claims (P2P) - Yêu cầu nhận đồ & Bộ câu hỏi xác minh")
                                .description("Lấy bộ câu hỏi xác minh theo danh mục, Gửi/Gửi lại Claim, Gom nhóm Claim theo bài đăng, Duyệt/Từ chối/Yêu cầu bổ sung và Hủy Claim"),
                        new Tag().name("07. Chat (P2P) - Trò chuyện & Chia sẻ vị trí")
                                .description("Mở phòng chat P2P, Lịch sử tin nhắn, Gửi tin nhắn văn bản/hình ảnh/tài liệu, Chia sẻ vị trí bản đồ và Đánh dấu đã đọc"),
                        new Tag().name("08. Handover (P2P) - Bàn giao tài sản trực tiếp")
                                .description("Lập biên bản bàn giao P2P kèm ảnh minh chứng, Xác nhận hoàn tất nhận đồ và Báo cáo sự cố bàn giao P2P"),
                        new Tag().name("09. Office Intake & Storage - Tiếp nhận tại quầy & Kho lưu trữ")
                                .description("Lập phiếu nháp biên bản tiếp nhận tại quầy (Walk-in), Cập nhật, Hoàn tất lưu kho & đăng bài Found, Tra cứu vị trí kho (APPROVER)"),
                        new Tag().name("10. Office Claims - Tiếp nhận & Xét duyệt Claim tại Văn phòng")
                                .description("Nộp Claim tại quầy hoặc trực tuyến cho đồ lưu kho văn phòng, Xét duyệt sơ bộ, Yêu cầu bổ sung và Gửi lại email lịch hẹn"),
                        new Tag().name("11. Office Workflow & Invitations - Chuyển giao, Lịch hẹn & Trao trả")
                                .description("Tiếp nhận đồ chuyển giao từ bài đăng P2P, Đặt lịch hẹn nộp/nhận đồ (Token Email 12h), Xác minh tại quầy và Lập biên bản trao trả văn phòng"),
                        new Tag().name("12. Office & Moderation Management - Kiểm duyệt & Quản trị kho")
                                .description("Quản lý toàn bộ bài đăng, Kiểm duyệt bài đăng (VISIBLE/REJECTED/HIDDEN), Tra cứu đồ trong kho và Quản lý vị trí kệ/tủ (MANAGER)"),
                        new Tag().name("13. Reports & Dispute Resolution - Báo cáo & Điều tra giải quyết")
                                .description("Gửi báo cáo vi phạm, Xem báo cáo của tôi, Điều tra của Manager (Chụp Snapshot, Tạm dừng giao nhận Hold, Hỏi đáp Inquiry, Kết luận xử lý)"),
                        new Tag().name("14. Notifications - Thông báo đa kênh & Cài đặt tùy chọn")
                                .description("Danh sách thông báo trong ứng dụng, Đếm thông báo chưa đọc, Đánh dấu đã đọc và Bật/Tắt tùy chọn thông báo (In-App, Email, Browser Push)")
                ))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName, new SecurityScheme()
                                .name(securitySchemeName)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }

    private static final Pattern SUMMARY_ORDER_PATTERN = Pattern.compile("^\\[(\\d+)\\.(\\d+)]");

    @Bean
    public OpenApiCustomizer sortOperationsByModuleNumberCustomizer() {
        return openApi -> {
            Paths currentPaths = openApi.getPaths();
            if (currentPaths == null || currentPaths.isEmpty()) {
                return;
            }
            Paths sortedPaths = new Paths();
            currentPaths.entrySet().stream()
                    .sorted(Comparator.comparingInt((Map.Entry<String, PathItem> entry) -> extractOrderKey(entry.getValue()))
                            .thenComparing(Map.Entry::getKey))
                    .forEach(entry -> sortedPaths.addPathItem(entry.getKey(), entry.getValue()));
            openApi.setPaths(sortedPaths);
        };
    }

    private int extractOrderKey(PathItem pathItem) {
        if (pathItem == null || pathItem.readOperations() == null) {
            return Integer.MAX_VALUE;
        }
        return pathItem.readOperations().stream()
                .map(Operation::getSummary)
                .filter(summary -> summary != null && !summary.isBlank())
                .mapToInt(summary -> {
                    Matcher matcher = SUMMARY_ORDER_PATTERN.matcher(summary.trim());
                    if (matcher.find()) {
                        int major = Integer.parseInt(matcher.group(1));
                        int minor = Integer.parseInt(matcher.group(2));
                        return major * 1000 + minor;
                    }
                    return Integer.MAX_VALUE;
                })
                .min()
                .orElse(Integer.MAX_VALUE);
    }
}
