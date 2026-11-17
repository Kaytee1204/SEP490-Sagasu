package com.example.base.controller.post;

import com.example.base.dto.ApiResponse;
import com.example.base.dto.post.PostPageDto;
import com.example.base.security.CustomUserDetails;
import com.example.base.service.post.SavedPostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/saved-posts")
@RequiredArgsConstructor
@Tag(name = "15. Saved Posts - Bài đăng đã lưu")
public class SavedPostController {
    private final SavedPostService saved;

    @GetMapping
    @Operation(summary = "[15.1] Xem bài đăng đã lưu của tôi", description = "Lọc LOST/FOUND; trang từ 1, tối đa 100 bài/trang. Mới lưu trước. Chỉ trả bài còn hiển thị, bao gồm bài đã trao trả.")
    public ResponseEntity<ApiResponse<PostPageDto>> list(@RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int size, @RequestParam(defaultValue = "") String postType,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(saved.list(page, size, postType, user)));
    }

    @GetMapping("/ids")
    @Operation(summary = "[15.2] Lấy mã bài đã lưu của tôi để hiển thị trạng thái nút lưu")
    public ResponseEntity<ApiResponse<List<Long>>> ids(@Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(saved.ids(user)));
    }

    @PutMapping("/{postId}")
    @Operation(summary = "[15.3] Lưu bài đăng", description = "Chỉ lưu bài đang hiển thị. Gửi lặp không tạo bản ghi trùng. Không tạo yêu cầu nhận đồ hay thông báo cho người đăng.")
    public ApiResponse<Void> save(@PathVariable long postId,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        saved.save(postId, user);
        return ApiResponse.ok("Đã lưu bài đăng", null);
    }

    @DeleteMapping("/{postId}")
    @Operation(summary = "[15.4] Bỏ lưu bài đăng của tôi")
    public ApiResponse<Void> remove(@PathVariable long postId,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        saved.remove(postId, user);
        return ApiResponse.ok("Đã bỏ lưu bài đăng", null);
    }
}
