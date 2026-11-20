package com.example.base.dto.news;

import java.time.OffsetDateTime;

public record NewsArticleDto(
        Long id,
        String title,
        String summary,
        String content,
        String category,
        String categoryLabel,
        String coverImageUrl,
        boolean published,
        Long authorId,
        String authorName,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
