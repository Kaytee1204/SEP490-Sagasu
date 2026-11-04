package com.example.base.dto.chat;

import java.time.OffsetDateTime;
import java.util.List;

public record ChatPageDto(List<Summary> items, String nextCursor) {
    public record Summary(long id, long postId, String postTitle, String otherName,
            String preview, OffsetDateTime lastActivityAt, long unreadCount, boolean canSend) {}
}
