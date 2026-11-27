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
    @Column(name = "response_status", nullable = false, length = 12) private String responseStatus = "LEGACY"; // This appointment does not require a recipient response.
    @Column(name = "email_sent_at") private OffsetDateTime emailSentAt;
    @Column(name = "response_deadline") private OffsetDateTime responseDeadline;
    @Column(name = "responded_at") private OffsetDateTime respondedAt;
    @Column(name = "response_token_hash", length = 64) private String responseTokenHash;
    public static Appointment schedule(Claim claim, Account actor, OffsetDateTime time, String location, String note) {
        Appointment a = new Appointment(); a.claim = claim; a.createdBy = actor;
        a.scheduledAt = time; a.location = location; a.note = note;
        return a;
    }
    public void cancelByOffice(String reason) {
        status = "CANCELLED"; cancellationReason = reason; cancelledAt = OffsetDateTime.now();
        responseTokenHash = null;
    }
    public void completeAtOffice() { status = "COMPLETED"; responseTokenHash = null; }
    public void restoreAfterOfficeHandover() {
        if ("COMPLETED".equals(status)) status = "SCHEDULED";
    }
    @PrePersist void create() { createdAt = updatedAt = OffsetDateTime.now(); }
    @PreUpdate void update() { updatedAt = OffsetDateTime.now(); }
}
