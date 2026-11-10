package com.example.base.dto.notification;

import java.time.OffsetDateTime;
import java.util.List;

public final class NotificationDtos {

    private NotificationDtos() {
    }

    public record NotificationItemDto(
            Long id,
            String type,
            String channel,
            String title,
            String content,
            String targetUrl,
            boolean read,
            OffsetDateTime readAt,
            OffsetDateTime createdAt
    ) {
    }

    public record NotificationPreferenceDto(
            boolean inAppEnabled,
            boolean emailEnabled,
            boolean browserPushEnabled
    ) {
    }

    public record UpdatePreferenceRequest(
            Boolean inAppEnabled,
            Boolean emailEnabled,
            Boolean browserPushEnabled
    ) {
    }

    public record NotificationPageDto(
            List<NotificationItemDto> content,
            int page,
            int size,
            long totalElements,
            int totalPages,
            long unreadCount,
            NotificationPreferenceDto preferences
    ) {
    }

    public record UnreadCountDto(
            long unreadCount,
            NotificationPreferenceDto preferences
    ) {
    }
}
