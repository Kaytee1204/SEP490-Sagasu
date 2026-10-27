package com.example.base.dto.category;

import com.example.base.entity.Category;
import java.time.OffsetDateTime;

public record CategoryResponse(Long categoryId, String name, boolean active,
                               OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getCategoryId(), category.getName(), category.isActive(),
                category.getCreatedAt(), category.getUpdatedAt());
    }
}
