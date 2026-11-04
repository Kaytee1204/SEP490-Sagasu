package com.example.base.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.OffsetDateTime;

@Entity @Table(name = "chat_participant") @IdClass(ChatParticipantId.class) @Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatParticipant {
    @Id @Column(name = "conversation_id") private Long conversationId;
    @Id @Column(name = "account_id") private Long accountId;
    @Column(name = "participant_role") private String participantRole;
    @Column(name = "last_read_sequence") private long lastReadSequence;
    @Column(name = "last_read_at") private OffsetDateTime lastReadAt;
    @Column(name = "joined_at", nullable = false, updatable = false) private OffsetDateTime joinedAt;
    public ChatParticipant(long conversationId, long accountId, String role) {
        this.conversationId = conversationId; this.accountId = accountId;
        participantRole = role; joinedAt = OffsetDateTime.now();
    }
}
