package com.example.base.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.OffsetDateTime;

@Entity @Table(name = "claim_activity") @Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClaimActivity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "claim_activity_id") private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id", nullable = false) private Claim claim;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", nullable = false) private Account actor;
    @Column(nullable = false, length = 16) private String action;
    @Column(name = "from_status", length = 16) private String fromStatus;
    @Column(name = "to_status", nullable = false, length = 16) private String toStatus;
    @Column(columnDefinition = "text") private String reason;
    @Column(name = "created_at", nullable = false, updatable = false) private OffsetDateTime createdAt;

    public ClaimActivity(Claim claim, Account actor, String action, String fromStatus, String reason) {
        this.claim = claim; this.actor = actor; this.action = action;
        this.fromStatus = fromStatus; toStatus = claim.getStatus();
        this.reason = reason == null || reason.isBlank() ? null : reason.strip();
    }
    @PrePersist void onCreate() { createdAt = OffsetDateTime.now(); }
}
