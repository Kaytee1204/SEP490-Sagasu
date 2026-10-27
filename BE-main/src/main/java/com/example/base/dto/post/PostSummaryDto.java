package com.example.base.dto.post;

import java.time.OffsetDateTime;

public record PostSummaryDto(Long id, String postType, String title, Long categoryId,
                             String categoryName, String locationText, OffsetDateTime eventAt,
                             String status, OffsetDateTime createdAt, String thumbnailUrl) {}
