package com.example.base.dto.approver;

import java.time.OffsetDateTime;

public record ReceiptQueueDto(Long id, String receiptCode, String title, String categoryName,
        String source, String recordedBy, OffsetDateTime submittedAt, String status, String reviewStatus,
        String storageLocationName, String thumbnailUrl) {}
