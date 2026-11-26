package com.example.base.dto.receipt;

import java.time.OffsetDateTime;
import java.util.List;

public record ReceiptDto(Long id, String receiptCode, String status, String reviewStatus,
        Long categoryId, String categoryName, boolean categoryActive, String title, String description,
        String conditionAtReceipt, String foundLocation, OffsetDateTime foundAt,
        String privateIdentifiers, String note, OffsetDateTime recordedAt, OffsetDateTime updatedAt,
        OffsetDateTime submittedAt, String reviewedBy, OffsetDateTime reviewedAt, String reviewNote,
        List<ImageDto> images, String source, String recordedBy,
        Long storageLocationId, String storageLocationName, boolean storageLocationActive,
        OffsetDateTime receivedAt, String finderName, String finderContact, Long postId, String receivedBy, boolean canProcess,
        String itemSummary) {
    public ReceiptDto(Long id, String receiptCode, String status, String reviewStatus,
            Long categoryId, String categoryName, boolean categoryActive, String title, String description,
            String conditionAtReceipt, String foundLocation, OffsetDateTime foundAt,
            String privateIdentifiers, String note, OffsetDateTime recordedAt, OffsetDateTime updatedAt,
            OffsetDateTime submittedAt, String reviewedBy, OffsetDateTime reviewedAt, String reviewNote,
            List<ImageDto> images, String source, String recordedBy,
            Long storageLocationId, String storageLocationName, boolean storageLocationActive,
            OffsetDateTime receivedAt, String finderName, String finderContact, Long postId, String receivedBy, boolean canProcess) {
        this(id, receiptCode, status, reviewStatus, categoryId, categoryName, categoryActive, title, description,
            conditionAtReceipt, foundLocation, foundAt, privateIdentifiers, note, recordedAt, updatedAt,
            submittedAt, reviewedBy, reviewedAt, reviewNote, images, source, recordedBy, storageLocationId,
            storageLocationName, storageLocationActive, receivedAt, finderName, finderContact, postId,
            receivedBy, canProcess, null);
    }
    public record ImageDto(Long id, String url, boolean sensitive) {}
}

