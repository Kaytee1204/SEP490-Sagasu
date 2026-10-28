package com.example.base.dto.post;

import java.time.OffsetDateTime;
import java.util.List;

public record EditPostDto(Long id, String postType, String title, String description, Long categoryId,
        String categoryName, String locationText, OffsetDateTime eventAt, String custodyMode,
        OffsetDateTime updatedAt, List<PostDetailDto.ImageDto> images, OffsetDateTime handoverRequestedAt) {}
