package com.example.base.dto.post;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Public post projection: never include private identifiers or moderation
 * notes.
 */
public record PostDetailDto(
        Long id, String postType, String title, String description,
        Long categoryId, String categoryName, String locationText,
        BigDecimal latitude, BigDecimal longitude, OffsetDateTime eventAt,
        String status, String custodyMode, OffsetDateTime createdAt,
        List<ImageDto> images, boolean canEditOwnPost, String moderationStatus) {
    public record ImageDto(Long id, String url, int sortOrder) {
    }
}
