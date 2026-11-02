package com.example.base.dto.claim;

import java.time.OffsetDateTime;
import java.util.List;

/** Private participant-only projection; no post private identifiers or R2 storage keys. */
public record ClaimDetailDto(Long id, Long postId, String postTitle, String postStatus,
        Long claimantId, String claimantName, String finderName, String status,
        String description, String evidenceNote, OffsetDateTime createdAt, OffsetDateTime updatedAt,
        long version, boolean canReview, boolean canCancel, boolean canRetry,
        List<EvidenceDto> evidence, List<ActivityDto> activities) {
    public record EvidenceDto(Long id, String url, String originalName, String mimeType, long sizeBytes) {}
    public record ActivityDto(Long id, String action, String actorName, String fromStatus,
            String toStatus, String reason, OffsetDateTime createdAt) {}
}
