package com.example.base.controller.post;

import com.example.base.dto.ApiResponse;
import com.example.base.dto.post.CategoryDto;
import com.example.base.dto.post.CreateLostPostRequest;
import com.example.base.dto.post.CreateFoundPostRequest;
import com.example.base.dto.post.PrivateIdentifiersDto;
import com.example.base.dto.post.SmartMatchItemDto;
import com.example.base.service.post.PostPrivateIdentifiersService;
import com.example.base.dto.post.CreatedPostDto;
import com.example.base.dto.post.PostDetailDto;
import com.example.base.dto.post.PostPageDto;
import com.example.base.exception.AppException;
import com.example.base.exception.ErrorCode;
import com.example.base.service.post.PostService;
import com.example.base.service.post.PostCreationService;
import com.example.base.service.post.SmartMatchService;
import com.example.base.security.CustomUserDetails;
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
import com.example.base.dto.post.PostSearchFilter;

@RestController
@RequestMapping("/api/posts")
@Tag(name = "3. Posts - Lost & Found", description = "Tra cứu, Lọc bài đăng công khai, Chi tiết bài đăng, AI Smart Matching và Đăng bài Lost / Found")
public class PostController {
    private final PostService posts;
    private final PostCreationService creation;
    private final PostPrivateIdentifiersService privateIdentifiers;
    private final SmartMatchService smartMatchService;

    public PostController(PostService posts,
                          PostCreationService creation,
                          PostPrivateIdentifiersService privateIdentifiers,
                          SmartMatchService smartMatchService) {
        this.posts = posts;
        this.creation = creation;
        this.privateIdentifiers = privateIdentifiers;
        this.smartMatchService = smartMatchService;
    }

    @GetMapping
    @Operation(summary = "UC13/21: Xem và tìm bài LOST/FOUND đang VISIBLE", description = "Lọc theo keyword, categoryId, postType (LOST/FOUND), location; timeRange=24H/7D cho thời gian gần đây hoặc CUSTOM với fromDate/toDate (yyyy-MM-dd, ngày mất/nhặt theo giờ Việt Nam). Trang bắt đầu từ 1; không trả bài PENDING, bị ẩn hay đã xóa.")
    public ResponseEntity<ApiResponse<PostPageDto>> list(
            @RequestParam(defaultValue = "1") String page,
            @RequestParam(defaultValue = "12") String size,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "") String categoryId,
            @RequestParam(defaultValue = "") String postType,
            @RequestParam(defaultValue = "") String location,
            @RequestParam(defaultValue = "") String fromDate,
            @RequestParam(defaultValue = "") String toDate,
            @RequestParam(defaultValue = "") String timeRange) {
        try {
            return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                    .body(ApiResponse.ok(posts.listVisible(Integer.parseInt(page),
                            Integer.parseInt(size), PostSearchFilter.parse(keyword, categoryId, postType,
                                    location, fromDate, toDate, timeRange))));
        } catch (NumberFormatException ex) {
            throw new AppException(ErrorCode.POST_PAGINATION_FORMAT_INVALID);
        }
    }

    @GetMapping("/categories")
    @Operation(summary = "Danh mục đang hoạt động để đăng bài")
    public ApiResponse<List<CategoryDto>> categories() {
        return ApiResponse.ok(posts.getActiveCategories());
    }

    @PostMapping(value = "/lost", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('USER', 'APPROVER', 'MANAGER', 'ADMIN')")
    @Operation(summary = "UC15: Mọi vai trò được đăng Lost và hiển thị ngay", description = "Phần data là JSON (tiêu đề, danh mục, thời gian, địa điểm...); images là 0–5 ảnh JPG/PNG, tối đa 5 MB/ảnh. Người đăng lấy tự động từ JWT.")
    public ResponseEntity<ApiResponse<CreatedPostDto>> createLost(
            @Parameter(description = "Dữ liệu bài đăng Lost dạng JSON") @Valid @RequestPart("data") CreateLostPostRequest request,
            @Parameter(description = "Danh sách file ảnh đính kèm (0-5 file, tối đa 5MB/file)") @RequestPart(value = "images", required = false) List<MultipartFile> images,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.status(201).body(ApiResponse.ok("Đăng bài thành công",
                creation.createLost(request, images, user)));
    }

    @PostMapping(value = "/found", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('USER', 'APPROVER', 'MANAGER', 'ADMIN')")
    @Operation(summary = "UC16: Đăng Found; giao Approver luôn PENDING và phải đề xuất lịch giao đồ")
    public ResponseEntity<ApiResponse<CreatedPostDto>> createFound(
            @Parameter(description = "Dữ liệu bài đăng Found dạng JSON") @Valid @RequestPart("data") CreateFoundPostRequest request,
            @Parameter(description = "Danh sách file ảnh đính kèm (0-5 file, tối đa 5MB/file)") @RequestPart(value = "images", required = false) List<MultipartFile> images,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.status(201).body(ApiResponse.ok("Đăng bài thành công",
                creation.createFound(request, images, user)));
    }

    @GetMapping("/{id}/private-identifiers")
    @Operation(summary = "UC17: Chủ bài đọc dấu hiệu nhận dạng riêng tư")
    public ResponseEntity<ApiResponse<PrivateIdentifiersDto>> readPrivateIdentifiers(
            @PathVariable long id,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.ok(privateIdentifiers.read(id, user)));
    }

    @PutMapping("/{id}/private-identifiers")
    @Operation(summary = "UC17: Chủ bài thêm/sửa dấu hiệu riêng tư; chuỗi rỗng để xóa")
    public ResponseEntity<ApiResponse<Void>> updatePrivateIdentifiers(
            @PathVariable long id,
            @Valid @RequestBody PrivateIdentifiersDto request,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        privateIdentifiers.update(id, request, user);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.ok("Đã lưu dấu hiệu nhận dạng riêng tư", null));
    }

    @GetMapping("/{id}")
    @Operation(summary = "UC14: Xem chi tiết bài đăng đang hiển thị", description = "Yêu cầu đăng nhập. Không trả đặc điểm bí mật hoặc ghi chú kiểm duyệt.")
    public ResponseEntity<ApiResponse<PostDetailDto>> detail(
            @PathVariable String id,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        long postId;
        try {
            postId = Long.parseLong(id);
        } catch (NumberFormatException ex) {
            throw new AppException(ErrorCode.POST_ID_FORMAT_INVALID);
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.ok(
                        posts.getDetailForViewer(postId, user == null ? null : user.getId())));
    }

    @GetMapping("/{id}/smart-matches")
    @Operation(summary = "UC21/AI: Đối sánh thông minh bằng Multimodal AI giữa bài LOST và các bài FOUND",
            description = "Chỉ dành cho chủ sở hữu bài viết, MANAGER và ADMIN. Pipeline Multimodal AI (YOLOv8 crop + ViT/CLIP text tiếng Việt + HSV màu sắc + Không/Thời gian + Visual Gating). Tự động tìm kiếm các bài viết tiềm năng trùng khớp nhất.")
    public ResponseEntity<ApiResponse<List<SmartMatchItemDto>>> getSmartMatches(
            @PathVariable long id,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.ok("Đã đối sánh thành công",
                        smartMatchService.findSmartMatches(id, user == null ? null : user.getId(), user == null ? null : user.getRole())));
    }
}
