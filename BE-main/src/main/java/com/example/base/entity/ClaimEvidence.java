package com.example.base.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.OffsetDateTime;

@Entity @Table(name = "claim_evidence") @Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClaimEvidence {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "claim_evidence_id") private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id", nullable = false) private Claim claim;
    @Column(name = "storage_key", nullable = false, columnDefinition = "text") private String storageKey;
    @Column(name = "original_name", length = 255) private String originalName;
    @Column(name = "mime_type", nullable = false, length = 32) private String mimeType;
    @Column(name = "size_bytes", nullable = false) private long sizeBytes;
    @Column(name = "content_hash", length = 64) private String contentHash;
    @Column(name = "sort_order", nullable = false) private short sortOrder;
    @Column(name = "uploaded_at", nullable = false, updatable = false) private OffsetDateTime uploadedAt;

    public ClaimEvidence(String key, String name, String mime, long size, String hash, int position) {
        storageKey = key; originalName = name; mimeType = mime;
        sizeBytes = size; contentHash = hash; sortOrder = (short) position;
    }
    void attachTo(Claim owner) { claim = owner; }
    @PrePersist void onCreate() { uploadedAt = OffsetDateTime.now(); }
}
