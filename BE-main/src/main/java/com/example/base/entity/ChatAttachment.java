package com.example.base.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.OffsetDateTime;

@Entity @Table(name = "chat_attachment") @Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatAttachment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "attachment_id") private Long id;
    @Column(name = "message_id") private long messageId;
    @Column(name = "storage_key", columnDefinition = "text") private String storageKey;
    @Column(name = "original_name") private String originalName;
    @Column(name = "mime_type") private String mimeType;
    @Column(name = "size_bytes") private long sizeBytes;
    @Column(name = "content_hash") private String contentHash;
    @Column(name = "sort_order") private short sortOrder;
    @Column(name = "uploaded_at", nullable = false, updatable = false) private OffsetDateTime uploadedAt;
    public ChatAttachment(long messageId, String key, String name, String mime, long size, String hash, int order) {
        this.messageId = messageId; storageKey = key; originalName = name; mimeType = mime;
        sizeBytes = size; contentHash = hash; sortOrder = (short) order;
    }
    @PrePersist void create() { uploadedAt = OffsetDateTime.now(); }
}
