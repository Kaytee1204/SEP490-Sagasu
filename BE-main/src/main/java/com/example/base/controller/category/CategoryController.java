package com.example.base.controller.category;

import com.example.base.dto.ApiResponse;
import com.example.base.dto.PageResponse;
import com.example.base.dto.category.*;
import com.example.base.service.category.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/categories")
@PreAuthorize("hasRole('MANAGER')")
@Tag(
        name = "03. Category Management - Quản lý danh mục đồ vật",
        description = "Tra cứu, Tạo mới, Đổi tên và Bật/Tắt trạng thái hoạt động của danh mục đồ thất lạc (MANAGER)"
)
public class CategoryController {
    private final CategoryService service;
    public CategoryController(CategoryService service) { this.service = service; }

    @GetMapping
    @Operation(
            summary = "[3.1] Danh sách danh mục đồ vật có phân trang và tìm kiếm (MANAGER)",
            description = "Lọc danh mục theo từ khóa (keyword) và trạng thái hoạt động (active = true/false)."
    )
    public ApiResponse<PageResponse<CategoryResponse>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean active) {
        return ApiResponse.ok(service.getCategories(page, size, keyword, active));
    }

    @PostMapping
    @Operation(
            summary = "[3.2] Tạo danh mục đồ thất lạc mới (MANAGER)",
            description = "Tạo mới một danh mục đồ vật với tên duy nhất (không trùng lặp)."
    )
    public ApiResponse<CategoryResponse> create(@Valid @RequestBody CreateCategoryRequest request) {
        return ApiResponse.ok("Tạo danh mục thành công", service.createCategory(request.name()));
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "[3.3] Đổi tên danh mục đồ vật theo ID (MANAGER)",
            description = "Cập nhật tên danh mục theo ID."
    )
    public ApiResponse<CategoryResponse> update(@PathVariable Long id, @Valid @RequestBody UpdateCategoryRequest request) {
        return ApiResponse.ok("Đổi tên danh mục thành công", service.updateCategory(id, request.name()));
    }

    @PatchMapping("/{id}/status")
    @Operation(
            summary = "[3.4] Bật / Tắt trạng thái hoạt động của danh mục (MANAGER)",
            description = "Kích hoạt hoặc vô hiệu hóa danh mục đồ vật (active = true / false)."
    )
    public ApiResponse<CategoryResponse> status(@PathVariable Long id, @Valid @RequestBody UpdateCategoryStatusRequest request) {
        return ApiResponse.ok("Cập nhật trạng thái thành công", service.updateCategoryStatus(id, request.active()));
    }
}
