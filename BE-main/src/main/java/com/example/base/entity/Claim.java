package com.example.base.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "claim")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Claim {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "claim_id") private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false) private Post post;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claimant_id", nullable = false) private Account claimant;
    @Column(nullable = false, columnDefinition = "text") private String description;
    @Column(name = "evidence_note", columnDefinition = "text") private String evidenceNote;
    @Column(nullable = false, length = 16) private String status = "PENDING";
    @Column(name = "created_at", nullable = false, updatable = false) private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt;
    @Column(name = "cancelled_at") private OffsetDateTime cancelledAt;
    @Version @Column(nullable = false) private long version;
    @OneToMany(mappedBy = "claim", cascade = CascadeType.PERSIST)
    @OrderBy("sortOrder ASC, id ASC") @Getter(AccessLevel.NONE)
    private List<ClaimEvidence> evidence = new ArrayList<>();
    @OneToMany(mappedBy = "claim", cascade = CascadeType.PERSIST)
    @OrderBy("sortOrder ASC, id ASC") @Getter(AccessLevel.NONE)
    private List<ClaimAnswer> answers = new ArrayList<>();

    public static Claim submit(Post post, Account claimant, String description, String evidenceNote) {
        Claim claim = new Claim();
        claim.post = post;
        claim.claimant = claimant;
        claim.description = description.strip();
        claim.evidenceNote = evidenceNote == null || evidenceNote.isBlank() ? null : evidenceNote.strip();
        return claim;
    }

    public void addEvidence(ClaimEvidence image) { image.attachTo(this); evidence.add(image); }
    public List<ClaimEvidence> getEvidence() { return Collections.unmodifiableList(evidence); }
    public void addAnswer(ClaimAnswer answer) { answer.attachTo(this); answers.add(answer); }
    public List<ClaimAnswer> getAnswers() { return Collections.unmodifiableList(answers); }
    public void review(String decision) { status = decision; }
    public void cancel() { status = "CANCELLED"; cancelledAt = OffsetDateTime.now(); }
    @PrePersist void onCreate() { createdAt = OffsetDateTime.now(); updatedAt = createdAt; }
    @PreUpdate void onUpdate() { updatedAt = OffsetDateTime.now(); }
}
