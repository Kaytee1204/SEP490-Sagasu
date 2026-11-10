package com.example.base.dto.claim;

import java.util.List;

public record ClaimPostGroupDto(Long postId, String postTitle, String postThumbnailUrl,
        long claimantCount, long pendingCount, List<ClaimSummaryDto> claims) {
    public record PageData(List<ClaimPostGroupDto> content, int page, int size, long totalElements, int totalPages) {}
}
