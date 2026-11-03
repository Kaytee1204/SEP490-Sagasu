package com.example.base.dto.chat;

import java.time.OffsetDateTime;
import java.util.List;

public record ChatMessageDto(long id, long sequence, long senderId, String body, OffsetDateTime createdAt,
        List<Attachment> attachments) {
    public record Attachment(long id, String name, String mimeType, long sizeBytes, String url) {}
    public record Page(List<ChatMessageDto> items, boolean hasMore, Long nextBefore) {}
}
