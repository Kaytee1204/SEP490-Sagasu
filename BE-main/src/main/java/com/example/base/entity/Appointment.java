package com.example.base.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;

@Entity @Table(name = "appointment") @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Appointment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "appointment_id") private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "claim_id") private Claim claim;
    @Column(name = "scheduled_at", nullable = false) private OffsetDateTime scheduledAt;
    @Column(nullable = false, columnDefinition = "text") private String location;
    @Column(nullable = false, length = 12) private String status = "SCHEDULED";
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "created_by") private Account createdBy;
    @Column(columnDefinition = "text") private String note;
    @Column(name = "cancellation_reason", columnDefinition = "text") private String cancellationReason;
    @Column(name = "cancelled_at") private OffsetDateTime cancelledAt;
    @Column(name = "created_at", nullable = false, updatable = false) private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt;
    @Version private long version;
    public static Appointment schedule(Claim claim, Account actor, OffsetDateTime time, String location, String note) {
        Appointment a = new Appointment(); a.claim = claim; a.createdBy = actor;
        a.scheduledAt = time; a.location = location; a.note = note; return a;
    }
    @PrePersist void create() { createdAt = updatedAt = OffsetDateTime.now(); }
    @PreUpdate void update() { updatedAt = OffsetDateTime.now(); }
}
