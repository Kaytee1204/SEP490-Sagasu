package com.example.base.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.OffsetDateTime;

@Entity @Table(name = "chat_conversation") @Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatConversation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "conversation_id") private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id") private Post post;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claimant_id") private Account claimant;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "latest_claim_id") private Claim latestClaim;
    @Column(name = "last_message_sequence", nullable = false) private long lastMessageSequence;
    @Column(name = "last_activity_at", nullable = false) private OffsetDateTime lastActivityAt;
    @Column(name = "created_at", nullable = false, updatable = false) private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt;
    @Version private long version;

    public ChatConversation(Claim claim) {
        post = claim.getPost(); claimant = claim.getClaimant(); latestClaim = claim;
    }
    public void useClaim(Claim claim) { latestClaim = claim; }
    public long nextMessageSequence() {
        lastActivityAt = OffsetDateTime.now();
        return ++lastMessageSequence;
    }
    @PrePersist void create() { createdAt = OffsetDateTime.now(); updatedAt = createdAt; lastActivityAt = createdAt; }
    @PreUpdate void update() { updatedAt = OffsetDateTime.now(); }
}
