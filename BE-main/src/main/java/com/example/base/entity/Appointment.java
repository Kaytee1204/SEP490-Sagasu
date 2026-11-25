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
    @Column(name = "response_status", nullable = false, length = 12) private String responseStatus = "UNSENT";
    @Column(name = "email_sent_at") private OffsetDateTime emailSentAt;
    @Column(name = "response_deadline") private OffsetDateTime responseDeadline;
    @Column(name = "responded_at") private OffsetDateTime respondedAt;
    @Column(name = "response_token_hash", length = 64) private String responseTokenHash;
    public void openResponseWindow(OffsetDateTime now) {
        responseStatus = "PENDING";
        responseDeadline = now.plusHours(12);
    }
    public void invitationSent(String hash, OffsetDateTime now) {
        if (emailSentAt == null) emailSentAt = now;
        responseTokenHash = hash;
    }
    public boolean responseExpired(OffsetDateTime now) {
        return "PENDING".equals(responseStatus) && responseDeadline != null && !now.isBefore(responseDeadline);
    }
    public void confirmInvitation(OffsetDateTime now) { responseStatus = "CONFIRMED"; respondedAt = now; }
    public void rejectInvitation(String response, String reason, OffsetDateTime now) {
        responseStatus = response; respondedAt = now; status = "CANCELLED";
        cancellationReason = reason; cancelledAt = now;
    }
    public static Appointment schedule(Claim claim, Account actor, OffsetDateTime time, String location, String note, OffsetDateTime now) {
        Appointment a = new Appointment(); a.claim = claim; a.createdBy = actor;
        a.scheduledAt = time; a.location = location; a.note = note;
        a.openResponseWindow(now);
        return a;
    }
    public void cancelByOffice(String reason) {
        status = "CANCELLED"; cancellationReason = reason; cancelledAt = OffsetDateTime.now();
        responseTokenHash = null;
    }
    public void completeAtOffice() { status = "COMPLETED"; responseTokenHash = null; }
    public void restoreAfterOfficeHandover() {
        if ("COMPLETED".equals(status) && "CONFIRMED".equals(responseStatus)) status = "SCHEDULED";
    }
    @PrePersist void create() { createdAt = updatedAt = OffsetDateTime.now(); }
    @PreUpdate void update() { updatedAt = OffsetDateTime.now(); }
}
