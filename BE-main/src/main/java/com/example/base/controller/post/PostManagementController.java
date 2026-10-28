package com.example.base.controller.post;

import com.example.base.dto.ApiResponse;
import com.example.base.dto.post.*;
import com.example.base.exception.*;
import com.example.base.security.CustomUserDetails;
import com.example.base.service.post.PostManagementService;
import io.swagger.v3.oas.annotations.Operation;
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
@PreAuthorize("hasAnyRole('USER','EXTERNAL_USER','CHECKER')")
public class PostManagementController {
    private final PostManagementService management;

    @GetMapping("/mine")
    @Operation(summary = "UC18: Bài do tài khoản hiện tại đăng, kèm trạng thái và quyền thao tác")
    public ResponseEntity<ApiResponse<MyPostPageDto>> mine(@RequestParam(defaultValue="1") String page,
            @RequestParam(defaultValue="12") String size, @RequestParam(defaultValue="ALL") String filter,
            @AuthenticationPrincipal CustomUserDetails user) {
        try { return noStore(management.listMine(Integer.parseInt(page), Integer.parseInt(size), filter, user)); }
        catch (NumberFormatException ex) { throw new AppException(ErrorCode.POST_PAGINATION_FORMAT_INVALID); }
    }

    @GetMapping("/{id}/edit")
    @Operation(summary = "UC19: Lấy dữ liệu cho form sửa bài của chính mình")
    public ResponseEntity<ApiResponse<EditPostDto>> edit(@PathVariable long id, @AuthenticationPrincipal CustomUserDetails user) {
        return noStore(management.getForEdit(id, user));
    }

    @PutMapping(value="/{id}", consumes="multipart/form-data")
    @Operation(summary = "UC19: Sửa bài của chính mình; kiểm tra lại sau khi khóa bản ghi")
    public ResponseEntity<ApiResponse<CreatedPostDto>> update(@PathVariable long id,
            @Valid @RequestPart("data") UpdatePostRequest request,
            @RequestPart(value="images", required=false) List<MultipartFile> images,
            @AuthenticationPrincipal CustomUserDetails user) {
        return noStore(management.update(id, request, images, user));
    }



    @DeleteMapping("/{id}")
    @Operation(summary = "UC20: Xóa mềm bài, giữ lịch sử và lý do")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable long id, @Valid @RequestBody DeletePostRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        management.delete(id, request, user);
        return noStore(null);
    }

    private <T> ResponseEntity<ApiResponse<T>> noStore(T value) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(value));
    }
}
