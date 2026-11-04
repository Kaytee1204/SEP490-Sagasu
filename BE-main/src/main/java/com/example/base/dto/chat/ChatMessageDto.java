package com.example.base.dto.chat;

import java.time.OffsetDateTime;
import java.util.List;

public record ChatMessageDto(long id, long sequence, long senderId, String body, OffsetDateTime createdAt,
        List<Attachment> attachments, OffsetDateTime readAt, Location location) {
    public record Location(double latitude, double longitude, String label) {}
    public record Attachment(long id, String name, String mimeType, long sizeBytes, String url) {}
    public record Page(List<ChatMessageDto> items, boolean hasMore, Long nextBefore) {}
    public record Receipt(long sequence, OffsetDateTime readAt) {}
    public record Receipts(List<Receipt> items, boolean hasMore) {}
}
