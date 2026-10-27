package com.example.base.controller.category;

import com.example.base.dto.ApiResponse;
import com.example.base.dto.PageResponse;
import com.example.base.dto.category.*;
import com.example.base.service.category.CategoryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/categories")
@PreAuthorize("hasRole('MANAGER')")
@Tag(name = "Category Management (MANAGER)")
public class CategoryController {
    private final CategoryService service;
    public CategoryController(CategoryService service) { this.service = service; }

    @GetMapping
    public ApiResponse<PageResponse<CategoryResponse>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean active) {
        return ApiResponse.ok(service.getCategories(page, size, keyword, active));
    }

    @PostMapping
    public ApiResponse<CategoryResponse> create(@Valid @RequestBody CreateCategoryRequest request) {
        return ApiResponse.ok("Tạo danh mục thành công", service.createCategory(request.name()));
    }

    @PutMapping("/{id}")
    public ApiResponse<CategoryResponse> update(@PathVariable Long id, @Valid @RequestBody UpdateCategoryRequest request) {
        return ApiResponse.ok("Đổi tên danh mục thành công", service.updateCategory(id, request.name()));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<CategoryResponse> status(@PathVariable Long id, @Valid @RequestBody UpdateCategoryStatusRequest request) {
        return ApiResponse.ok("Cập nhật trạng thái thành công", service.updateCategoryStatus(id, request.active()));
    }
}
