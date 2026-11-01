package com.example.base.dto.receipt;

import java.time.OffsetDateTime;

public record ReceiptSummaryDto(Long id, String receiptCode, String title, String categoryName,
        String foundLocation, OffsetDateTime foundAt, OffsetDateTime recordedAt,
        String status, String reviewStatus, String thumbnailUrl, String storageLocationName,
        String source, String recordedBy, boolean canProcess) {}

