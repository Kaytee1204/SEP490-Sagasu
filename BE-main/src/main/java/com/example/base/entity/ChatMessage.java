package com.example.base.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity @Table(name = "chat_message") @Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "message_id") private Long id;
    @Column(name = "conversation_id") private long conversationId;
    @Column(name = "sender_id") private long senderId;
    @Column(name = "sequence_no") private long sequenceNo;
    @Column(name = "client_message_id") private UUID clientMessageId;
    @Column(name = "message_type") private String messageType;
    @Column(columnDefinition = "text") private String body;
    @Column(name = "created_at", nullable = false, updatable = false) private OffsetDateTime createdAt;
    @Column(name = "read_at") private OffsetDateTime readAt;
    public ChatMessage(long conversationId, long senderId, long sequence, UUID clientId, String type, String body) {
        this.conversationId = conversationId; this.senderId = senderId; sequenceNo = sequence;
        clientMessageId = clientId; messageType = type; this.body = body;
    }
    @PrePersist void create() { createdAt = OffsetDateTime.now(); }
}
