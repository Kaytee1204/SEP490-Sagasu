package com.example.base.dto.post;

import java.time.OffsetDateTime;

public record MyPostDto(Long id, String postType, String title, String categoryName, String locationText,
        String status, String moderationStatus, OffsetDateTime createdAt, OffsetDateTime updatedAt,
        String thumbnailUrl, boolean canEdit, boolean canDelete,
        String editBlockedReason, String deleteBlockedReason, OffsetDateTime handoverRequestedAt) {}
