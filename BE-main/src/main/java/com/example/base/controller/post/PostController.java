package com.example.base.controller.post;

import com.example.base.dto.ApiResponse;
import com.example.base.dto.post.CategoryDto;
import com.example.base.dto.post.CreateLostPostRequest;
import com.example.base.dto.post.CreateFoundPostRequest;
import com.example.base.dto.post.PrivateIdentifiersDto;
import com.example.base.service.post.PostPrivateIdentifiersService;
import com.example.base.dto.post.CreatedPostDto;
import com.example.base.dto.post.PostDetailDto;
import com.example.base.dto.post.PostPageDto;
import com.example.base.exception.AppException;
import com.example.base.exception.ErrorCode;
import com.example.base.service.post.PostService;
import com.example.base.service.post.PostCreationService;
import com.example.base.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/posts")
@Tag(name = "3. Posts", description = "Bài đăng đồ thất lạc")
public class PostController {
        private final PostService posts;
        private final PostCreationService creation;
        private final PostPrivateIdentifiersService privateIdentifiers;

        @GetMapping
        @Operation(summary = "UC13: Danh sách bài đăng đang hiển thị, mới nhất trước", description = "Yêu cầu đăng nhập. Trang bắt đầu từ 1, tối đa 100 bài/trang. Không trả thông tin nhận dạng bí mật.")
        public ResponseEntity<ApiResponse<PostPageDto>> list(
                        @RequestParam(defaultValue = "1") String page,
                        @RequestParam(defaultValue = "12") String size) {
                try {
                        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                                        .body(ApiResponse.ok(posts.listVisible(Integer.parseInt(page),
                                                        Integer.parseInt(size))));
                } catch (NumberFormatException ex) {
                        throw new AppException(ErrorCode.POST_PAGINATION_FORMAT_INVALID);
                }
        }

        @GetMapping("/categories")
        @Operation(summary = "Danh mục đang hoạt động để đăng bài")
        public ApiResponse<List<CategoryDto>> categories() {
                return ApiResponse.ok(posts.getActiveCategories());
        }

        @PostMapping(value = "/lost", consumes = "multipart/form-data")
        @PreAuthorize("hasAnyRole('USER', 'EXTERNAL_USER', 'CHECKER')")
        @Operation(summary = "UC15: Đăng bài Lost; USER hiển thị ngay, CHECKER/EXTERNAL_USER chờ duyệt", description = "Phần data là JSON; images là 0–5 ảnh JPG/PNG, tối đa 5 MB/ảnh. Người đăng lấy từ JWT.")
        public ResponseEntity<ApiResponse<CreatedPostDto>> createLost(
                        @Valid @RequestPart("data") CreateLostPostRequest request,
                        @RequestPart(value = "images", required = false) List<MultipartFile> images,
                        @AuthenticationPrincipal CustomUserDetails user) {
                return ResponseEntity.status(201).body(ApiResponse.ok("Đăng bài thành công",
                                creation.createLost(request, images, user)));
        }

        @PostMapping(value = "/found", consumes = "multipart/form-data")
        @PreAuthorize("hasAnyRole('USER', 'EXTERNAL_USER', 'CHECKER')")
        @Operation(summary = "UC16: Đăng bài nhặt được; OFFICE là lựa chọn giao văn phòng, chưa xác nhận tiếp nhận")
        public ResponseEntity<ApiResponse<CreatedPostDto>> createFound(
                        @Valid @RequestPart("data") CreateFoundPostRequest request,
                        @RequestPart(value = "images", required = false) List<MultipartFile> images,
                        @AuthenticationPrincipal CustomUserDetails user) {
                return ResponseEntity.status(201).body(ApiResponse.ok("Đăng bài thành công",
                                creation.createFound(request, images, user)));
        }

        @GetMapping("/{id}/private-identifiers")
        @Operation(summary = "UC17: Chủ bài đọc dấu hiệu nhận dạng riêng tư")
        public ResponseEntity<ApiResponse<PrivateIdentifiersDto>> readPrivateIdentifiers(
                        @PathVariable long id, @AuthenticationPrincipal CustomUserDetails user) {
                return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                                .body(ApiResponse.ok(privateIdentifiers.read(id, user)));
        }

        @PutMapping("/{id}/private-identifiers")
        @Operation(summary = "UC17: Chủ bài thêm/sửa dấu hiệu riêng tư; chuỗi rỗng để xóa")
        public ResponseEntity<ApiResponse<Void>> updatePrivateIdentifiers(
                        @PathVariable long id, @Valid @RequestBody PrivateIdentifiersDto request,
                        @AuthenticationPrincipal CustomUserDetails user) {
                privateIdentifiers.update(id, request, user);
                return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                                .body(ApiResponse.ok("Đã lưu dấu hiệu nhận dạng riêng tư", null));
        }

        @GetMapping("/{id}")
        @Operation(summary = "UC14: Xem chi tiết bài đăng đang hiển thị", description = "Yêu cầu đăng nhập. Không trả đặc điểm bí mật hoặc ghi chú kiểm duyệt.")
        public ResponseEntity<ApiResponse<PostDetailDto>> detail(@PathVariable String id,
                        @AuthenticationPrincipal CustomUserDetails user) {
                long postId;
                try {
                        postId = Long.parseLong(id);
                } catch (NumberFormatException ex) {
                        throw new AppException(ErrorCode.POST_ID_FORMAT_INVALID);
                }
                return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                                .body(ApiResponse.ok(
                                                posts.getVisibleDetail(postId, user == null ? null : user.getId())));
        }
}
