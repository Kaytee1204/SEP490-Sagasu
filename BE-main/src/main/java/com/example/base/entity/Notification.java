package com.example.base.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "notification")
@Getter
@Setter
@NoArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long id;

    @Column(name = "recipient_id", nullable = false)
    private Long recipientId;

    @Column(name = "type", length = 48, nullable = false)
    private String type;

    @Column(name = "channel", length = 24, nullable = false)
    private String channel = "IN_APP";

    @Column(name = "title", length = 255, nullable = false)
    private String title;

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "target_url", length = 500)
    private String targetUrl;

    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    @Column(name = "read_at")
    private OffsetDateTime readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public Notification(Long recipientId, String type, String channel, String title, String content, String targetUrl) {
        this.recipientId = recipientId;
        this.type = type;
        this.channel = channel != null ? channel : "IN_APP";
        this.title = title;
        this.content = content;
        this.targetUrl = targetUrl;
        this.read = false;
        this.createdAt = OffsetDateTime.now();
    }

    public void markRead() {
        if (!this.read) {
            this.read = true;
            this.readAt = OffsetDateTime.now();
        }
    }
}
