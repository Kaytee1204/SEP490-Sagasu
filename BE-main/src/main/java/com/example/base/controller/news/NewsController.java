package com.example.base.controller.news;

import com.example.base.dto.ApiResponse;
import com.example.base.dto.PageResponse;
import com.example.base.dto.news.NewsArticleDto;
import com.example.base.dto.news.NewsArticleRequest;
import com.example.base.security.CustomUserDetails;
import com.example.base.service.news.NewsArticleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "System News", description = "Tin tức & thông báo hệ thống hiển thị trên Trang chủ và quản lý bởi Approver")
public class NewsController {

    private final NewsArticleService newsService;

    @GetMapping("/news")
    @Operation(summary = "Danh sách bài tin tức công khai cho Trang chủ")
    public ResponseEntity<ApiResponse<PageResponse<NewsArticleDto>>> listPublished(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "8") int size
    ) {
        return ResponseEntity.ok(ApiResponse.ok(newsService.listPublished(page, size)));
    }

    @GetMapping("/news/{id}")
    @Operation(summary = "Chi tiết bài tin tức công khai")
    public ResponseEntity<ApiResponse<NewsArticleDto>> getPublished(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(newsService.getPublished(id)));
    }

    @GetMapping("/approver/news")
    @PreAuthorize("hasAnyRole('APPROVER', 'MANAGER', 'ADMIN')")
    @Operation(summary = "Danh sách bài tin tức dành cho Approver quản lý")
    public ResponseEntity<ApiResponse<PageResponse<NewsArticleDto>>> listForApprover(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "ALL") String category,
            @RequestParam(defaultValue = "ALL") String status,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user
    ) {
        return ResponseEntity.ok(ApiResponse.ok(newsService.listForStaff(page, size, keyword, category, status, user)));
    }

    @PostMapping(value = "/approver/news", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('APPROVER', 'MANAGER', 'ADMIN')")
    @Operation(summary = "Approver tạo mới bài tin tức hệ thống")
    public ResponseEntity<ApiResponse<NewsArticleDto>> create(
            @RequestPart("data") NewsArticleRequest request,
            @RequestPart(value = "coverImage", required = false) MultipartFile coverImage,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user
    ) {
        NewsArticleDto created = newsService.create(request, coverImage, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Tạo bài tin tức thành công.", created));
    }

    @PutMapping(value = "/approver/news/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('APPROVER', 'MANAGER', 'ADMIN')")
    @Operation(summary = "Approver cập nhật bài tin tức hệ thống")
    public ResponseEntity<ApiResponse<NewsArticleDto>> update(
            @PathVariable Long id,
            @RequestPart("data") NewsArticleRequest request,
            @RequestPart(value = "coverImage", required = false) MultipartFile coverImage,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user
    ) {
        NewsArticleDto updated = newsService.update(id, request, coverImage, user);
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật bài tin tức thành công.", updated));
    }

    @DeleteMapping("/approver/news/{id}")
    @PreAuthorize("hasAnyRole('APPROVER', 'MANAGER', 'ADMIN')")
    @Operation(summary = "Approver xóa bài tin tức hệ thống")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long id,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user
    ) {
        newsService.delete(id, user);
        return ResponseEntity.ok(ApiResponse.ok("Đã xóa bài tin tức.", null));
    }
}
