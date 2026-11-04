package com.example.base.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;
@Entity @Table(name="p2p_handover") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class P2pHandover {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="handover_id") private Long id;
    @Column(name="claim_id",nullable=false,unique=true) private long claimId;
    @Column(nullable=false,length=32) private String status="WAITING_CONFIRMATION";
    @Column(name="recorded_by",nullable=false) private long recordedBy;
    @Column(name="recorded_at",nullable=false) private OffsetDateTime recordedAt;
    @Column(columnDefinition="text") private String note;
    @Column(name="confirmed_by") private Long confirmedBy;
    @Column(name="confirmed_at") private OffsetDateTime confirmedAt;
    @Column(name="client_request_id",nullable=false) private UUID clientRequestId;
    @Column(name="request_hash",nullable=false,length=64) private String requestHash;
    @Column(name="created_at",nullable=false) private OffsetDateTime createdAt;
    @Column(name="updated_at",nullable=false) private OffsetDateTime updatedAt;
    @Version private long version;
    public P2pHandover(long claim,long actor,String note,UUID request,String hash) {
        claimId=claim; recordedBy=actor; this.note=note; clientRequestId=request;requestHash=hash;
    }
    @PrePersist void create() { recordedAt=createdAt=updatedAt=OffsetDateTime.now(); }
    @PreUpdate void update() {updatedAt=OffsetDateTime.now();}
}
