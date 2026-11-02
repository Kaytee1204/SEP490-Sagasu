package com.example.base.dto.claim;

import java.util.List;

public record ClaimPageDto(List<ClaimSummaryDto> content, int page, int size,
        long totalElements, int totalPages) {}
