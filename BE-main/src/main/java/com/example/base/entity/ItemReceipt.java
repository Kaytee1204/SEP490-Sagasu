package com.example.base.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/** Checker intake fields mapped to the existing script 05 schema. */
@Entity
@Table(name = "item_receipt")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ItemReceipt {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "item_receipt_id") private Long id;
    @Column(name = "receipt_code", nullable = false, length = 40) private String receiptCode;
    @Column(nullable = false, length = 16) private String source = "CHECKER";
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id") private Category category;
    @Column(nullable = false, length = 200) private String title;
    @Column(nullable = false, columnDefinition = "text") private String description;
    @Column(name = "condition_at_receipt", columnDefinition = "text") private String conditionAtReceipt;
    @Column(name = "found_location", nullable = false, columnDefinition = "text") private String foundLocation;
    @Column(name = "found_at", nullable = false) private OffsetDateTime foundAt;
    @Column(name = "private_identifiers", columnDefinition = "text") private String privateIdentifiers;
    @Column(columnDefinition = "text") private String note;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recorded_by") private Account recordedBy;
    @Column(name = "recorded_at", nullable = false, updatable = false) private OffsetDateTime recordedAt;
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt;
    @Column(name = "submitted_at") private OffsetDateTime submittedAt;
    @Column(nullable = false, length = 10) private String status = "DRAFT";
    @Column(name = "review_status", nullable = false, length = 12) private String reviewStatus = "PENDING";
    @Column(name = "review_note", columnDefinition = "text") private String reviewNote;
    @Column(name = "reviewed_at") private OffsetDateTime reviewedAt;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by") private Account reviewedBy;
    @Column(name = "post_id", insertable = false, updatable = false) private Long postId;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "storage_location_id") private StorageLocation storageLocation;
    @Column(name = "received_at", insertable = false, updatable = false) private OffsetDateTime receivedAt;
    @Column(name = "finder_name", insertable = false, updatable = false) private String finderName;
    @Column(name = "finder_contact", insertable = false, updatable = false) private String finderContact;
    @OneToMany(mappedBy = "receipt", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, id ASC") private List<ItemReceiptImage> images = new ArrayList<>();

    public static ItemReceipt draft(String code, Account checker) {
        ItemReceipt receipt = new ItemReceipt();
        receipt.receiptCode = code;
        receipt.recordedBy = checker;
        receipt.recordedAt = OffsetDateTime.now();
        return receipt;
    }

    public void edit(Category category, String title, String description, String condition,
                     String location, OffsetDateTime foundAt, String identifiers, String note) {
        this.category = category;
        this.title = title.strip();
        this.description = description.strip();
        this.conditionAtReceipt = condition.strip();
        this.foundLocation = location.strip();
        this.foundAt = foundAt;
        this.privateIdentifiers = cleanOptional(identifiers);
        this.note = cleanOptional(note);
        // Dirty the parent even when only images change; DB trigger owns the final timestamp.
        this.updatedAt = OffsetDateTime.now();
    }

    public void submit() {
        status = "SUBMITTED";
        reviewStatus = "PENDING";
        submittedAt = updatedAt = OffsetDateTime.now();
    }

    public void addImage(ItemReceiptImage image) { image.attachTo(this); images.add(image); }
    public void assignLocation(StorageLocation location) {
        storageLocation = location;
        updatedAt = OffsetDateTime.now();
    }
    public void review(String decision, String note, Account approver) {
        reviewStatus = decision;
        reviewNote = cleanOptional(note);
        reviewedBy = approver;
        reviewedAt = updatedAt = OffsetDateTime.now();
        status = "REJECTED".equals(decision) ? "REJECTED" : "SUBMITTED";
    }
    public void retainImages(List<Long> ids) { images.removeIf(image -> !ids.contains(image.getId())); }
    private static String cleanOptional(String value) { return value == null || value.isBlank() ? null : value.strip(); }
}
