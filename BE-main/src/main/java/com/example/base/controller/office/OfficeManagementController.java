package com.example.base.controller.office;

import com.example.base.dto.ApiResponse;
import com.example.base.dto.office.OfficeDtos.*;
import com.example.base.security.CustomUserDetails;
import com.example.base.service.office.OfficeManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/office")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MANAGER')")
@Tag(
        name = "12. Office & Moderation Management - Kiểm duyệt & Quản trị kho",
        description = "Quản lý toàn bộ bài đăng, Kiểm duyệt bài đăng (VISIBLE/REJECTED/HIDDEN), Tra cứu đồ trong kho và Quản lý vị trí kệ/tủ (MANAGER)"
)
public class OfficeManagementController {
    private final OfficeManagementService service;

    @GetMapping("/posts")
    @Operation(
            summary = "[12.1] Danh sách toàn bộ bài đăng trong hệ thống phục vụ Kiểm duyệt & Quản trị (MANAGER)",
            description = "Hỗ trợ lọc theo từ khóa q, loại bài (LOST/FOUND), trạng thái bài (status), trạng thái kiểm duyệt (moderation: PENDING/VISIBLE/REJECTED/HIDDEN), nơi giữ đồ (custody: P2P/OFFICE) và danh mục."
    )
    public ApiResponse<PageData<PostView>> posts(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "q", defaultValue = "") String q,
            @RequestParam(name = "type", defaultValue = "ALL") String type,
            @RequestParam(name = "status", defaultValue = "ALL") String status,
            @RequestParam(name = "moderation", defaultValue = "ALL") String moderation,
            @RequestParam(name = "custody", defaultValue = "ALL") String custody,
            @RequestParam(name = "category", required = false) Long category,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return ApiResponse.ok(service.posts(page, q, type, status, moderation, custody, category, user));
    }

    @GetMapping("/posts/{id}")
    @Operation(
            summary = "[12.2] Xem chi tiết đầy đủ bài đăng dành cho Quản lý (bao gồm đặc điểm riêng tư & lịch sử kiểm duyệt)",
            description = "Dành cho MANAGER kiểm tra toàn bộ thông tin của bài đăng theo ID."
    )
    public ApiResponse<PostView> detail(
            @PathVariable("id") long id,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return ApiResponse.ok(service.detail(id, user));
    }

    @PostMapping("/posts/{id}/moderation")
    @Operation(
            summary = "[12.3] Kiểm duyệt bài đăng: Duyệt hiển thị (VISIBLE), Từ chối (REJECTED) hoặc Ẩn bài (HIDDEN)",
            description = "Cập nhật trạng thái kiểm duyệt kèm ghi chú kiểm duyệt (moderation_note)."
    )
    public ApiResponse<PostView> moderate(
            @PathVariable("id") long id,
            @Valid @RequestBody Moderation request,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return ApiResponse.ok(service.moderate(id, request, user));
    }

    @GetMapping("/stored-items")
    @PreAuthorize("hasAnyRole('APPROVER','MANAGER')")
    @Operation(
            summary = "[12.4] Danh sách tài sản đang được lưu trữ trong Kho Văn phòng (APPROVER & MANAGER)",
            description = "Lọc theo từ khóa q và vị trí kệ/tủ lưu trữ (locationId)."
    )
    public ApiResponse<PageData<ReceiptView>> stored(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "q", defaultValue = "") String q,
            @RequestParam(name = "locationId", required = false) Long locationId,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return ApiResponse.ok(service.stored(page, q, locationId, user));
    }

    @GetMapping("/locations")
    @PreAuthorize("hasAnyRole('APPROVER','MANAGER')")
    @Operation(
            summary = "[12.5] Danh sách toàn bộ vị trí kệ / tủ lưu trữ kèm số lượng đồ đang chứa (APPROVER & MANAGER)",
            description = "Trả về danh sách vị trí kho lưu trữ, sức chứa (capacity) và số lượng vật phẩm hiện tại."
    )
    public ApiResponse<List<LocationView>> locations(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return ApiResponse.ok(service.locations(user));
    }

    @PostMapping("/locations")
    @Operation(
            summary = "[12.6] Tạo mới vị trí kệ / tủ lưu trữ trong Kho Văn phòng (MANAGER)",
            description = "Thêm mới mã vị trí kho (code), tên vị trí, sức chứa và trạng thái kích hoạt."
    )
    public ApiResponse<LocationView> create(
            @Valid @RequestBody LocationInput r,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return ApiResponse.ok(service.saveLocation(null, r, user));
    }

    @PutMapping("/locations/{id}")
    @Operation(
            summary = "[12.7] Cập nhật thông tin vị trí kệ / tủ lưu trữ theo ID (MANAGER)",
            description = "Chỉnh sửa thông tin hoặc bật/tắt trạng thái hoạt động của vị trí lưu trữ."
    )
    public ApiResponse<LocationView> update(
            @PathVariable("id") long id,
            @Valid @RequestBody LocationInput r,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return ApiResponse.ok(service.saveLocation(id, r, user));
    }
}
