package com.example.base.dto.claim;

import java.time.OffsetDateTime;

public record ClaimSummaryDto(Long id, Long postId, String postTitle, String claimantName,
        String finderName, String status, OffsetDateTime createdAt, OffsetDateTime updatedAt, String claimType, String closeReason, String descriptionPreview, String postThumbnailUrl) {}
