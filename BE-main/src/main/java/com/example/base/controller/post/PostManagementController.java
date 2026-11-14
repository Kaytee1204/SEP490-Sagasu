package com.example.base.controller.post;

import com.example.base.dto.ApiResponse;
import com.example.base.dto.post.*;
import com.example.base.exception.*;
import com.example.base.security.CustomUserDetails;
import com.example.base.service.post.PostManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequestMapping("/api/posts")
@PreAuthorize("hasAnyRole('USER', 'APPROVER', 'MANAGER', 'ADMIN')")
@Tag(
        name = "05. My Posts - Quản lý bài đăng cá nhân",
        description = "Danh sách bài đăng của tôi, Lấy dữ liệu sửa bài, Cập nhật bài đăng, Gửi yêu cầu giao đồ cho văn phòng và Xóa mềm bài đăng"
)
public class PostManagementController {
    private final PostManagementService management;

    public PostManagementController(PostManagementService management) {
        this.management = management;
    }

    @GetMapping("/mine")
    @Operation(
            summary = "[5.1] UC18: Danh sách bài đăng của tôi (kèm trạng thái và quyền thao tác)",
            description = "Trả về danh sách bài đăng do tài khoản hiện tại tạo, hỗ trợ phân trang và lọc (ALL, LOST, FOUND, PENDING, REJECTED...)."
    )
    public ResponseEntity<ApiResponse<MyPostPageDto>> mine(
            @RequestParam(defaultValue="1") String page,
            @RequestParam(defaultValue="12") String size,
            @RequestParam(defaultValue="ALL") String filter,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        try { return noStore(management.listMine(Integer.parseInt(page), Integer.parseInt(size), filter, user)); }
        catch (NumberFormatException ex) { throw new AppException(ErrorCode.POST_PAGINATION_FORMAT_INVALID); }
    }

    @GetMapping("/{id}/edit")
    @Operation(
            summary = "[5.2] UC19: Lấy dữ liệu chi tiết để hiển thị lên form Sửa bài đăng của tôi",
            description = "Chỉ chủ sở hữu bài đăng mới được lấy dữ liệu sửa bài."
    )
    public ResponseEntity<ApiResponse<EditPostDto>> edit(
            @PathVariable long id,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return noStore(management.getForEdit(id, user));
    }

    @PutMapping(value="/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "[5.3] UC19: Cập nhật nội dung và hình ảnh bài đăng của chính mình",
            description = "Cập nhật thông tin bài đăng (data dạng JSON) và danh sách ảnh mới (images, tùy chọn)."
    )
    public ResponseEntity<ApiResponse<CreatedPostDto>> update(
            @PathVariable long id,
            @Parameter(description = "Dữ liệu cập nhật bài đăng dạng JSON") @Valid @RequestPart("data") UpdatePostRequest request,
            @Parameter(description = "Danh sách ảnh mới (tùy chọn)") @RequestPart(value="images", required=false) List<MultipartFile> images,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return noStore(management.update(id, request, images, user));
    }

    @PostMapping("/{id}/office-handover")
    @Operation(
            summary = "[5.4] Chủ bài gửi yêu cầu bàn giao đồ nhặt được (Found P2P) cho Văn phòng lưu giữ",
            description = "Tạo yêu cầu chuyển giao tài sản (office_transfer = PENDING) từ người nhặt sang Văn phòng tiếp nhận."
    )
    public ResponseEntity<ApiResponse<CreatedPostDto>> requestOfficeHandover(
            @PathVariable long id,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return noStore(management.requestOfficeHandover(id, user));
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "[5.5] UC20: Xóa mềm bài đăng của tôi kèm lý do xóa",
            description = "Đánh dấu xóa mềm bài đăng (deleted_at) và lưu lại lý do xóa (deletion_reason)."
    )
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable long id,
            @Valid @RequestBody DeletePostRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        management.delete(id, request, user);
        return noStore(null);
    }

    private <T> ResponseEntity<ApiResponse<T>> noStore(T value) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(value));
    }
}
