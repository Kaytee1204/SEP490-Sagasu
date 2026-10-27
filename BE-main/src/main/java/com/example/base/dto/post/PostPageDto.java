package com.example.base.dto.post;

import java.util.List;

public record PostPageDto(List<PostSummaryDto> content, int page, int size,
                          long totalElements, int totalPages, boolean first, boolean last) {}
