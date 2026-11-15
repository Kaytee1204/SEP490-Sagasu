package com.example.base.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity @Table(name="report") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class Report {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="report_id") private Long id;
    @Column(name="reporter_id",nullable=false) private long reporterId;
    @Column(nullable=false,length=24) private String kind;
    @Column(name="post_id") private Long postId;
    @Column(name="claim_id") private Long claimId;
    @Column(name="message_id") private Long messageId;
    @Column(name="reported_account_id") private Long reportedAccountId;
    @Column(nullable=false,columnDefinition="text") private String description;
    @Column(name="reason_code",length=64,updatable=false) private String reasonCode;
    @Column(name="reason_label_snapshot",length=200,updatable=false) private String reasonLabelSnapshot;
    @Column(nullable=false,length=16) private String status="OPEN";
    @Column(name="resolution_note",columnDefinition="text") private String resolutionNote;
    @Column(name="resolved_at") private OffsetDateTime resolvedAt;
    @Column(name="client_request_id",nullable=false) private UUID clientRequestId;
    @Column(name="request_hash",nullable=false,length=64) private String requestHash;
    @Column(name="created_at",nullable=false) private OffsetDateTime createdAt;
    @Column(name="updated_at",nullable=false) private OffsetDateTime updatedAt;
    @Version private long version;
    public Report(long actor, String kind, String type, long target, String description, UUID request, String hash) {
        reporterId=actor; this.kind=kind; this.description=description; clientRequestId=request; requestHash=hash;
        switch(type) { case "POST" -> postId=target; case "CLAIM" -> claimId=target;
            case "MESSAGE" -> messageId=target; case "USER" -> reportedAccountId=target;
            default -> throw new IllegalArgumentException("Invalid report target"); }
    }
    public String targetType() { return postId!=null?"POST":claimId!=null?"CLAIM":messageId!=null?"MESSAGE":"USER"; }
    public long targetId() { return postId!=null?postId:claimId!=null?claimId:messageId!=null?messageId:reportedAccountId; }
    public void selectReason(String code, String label) {
        reasonCode = code;
        reasonLabelSnapshot = label;
    }
    public String additionalDescription() {
        // Keep the stored summary readable by the existing review workflow.
        if (reasonLabelSnapshot == null || reasonCode == null || reasonCode.endsWith("_LEGACY")) return description;
        if (description.equals(reasonLabelSnapshot)) return "";
        String prefix = reasonLabelSnapshot + "\n\n";
        return description.startsWith(prefix) ? description.substring(prefix.length()) : description;
    }
    @PrePersist void create() { createdAt=updatedAt=OffsetDateTime.now(); }
}
