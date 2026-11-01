package com.example.base.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.OffsetDateTime;

@Entity
@Table(name = "item_receipt_image")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ItemReceiptImage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "item_receipt_image_id") private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_receipt_id") private ItemReceipt receipt;
    @Column(name = "storage_key", nullable = false, columnDefinition = "text") private String storageKey;
    @Column(name = "image_url", columnDefinition = "text") private String imageUrl;
    @Column(name = "sort_order", nullable = false) private int sortOrder;
    @Column(name = "mime_type", length = 100) private String mimeType;
    @Column(name = "size_bytes") private Long sizeBytes;
    @Column(name = "is_sensitive", nullable = false) private boolean sensitive;
    @Column(name = "created_at", nullable = false, updatable = false) private OffsetDateTime createdAt;

    public ItemReceiptImage(String key, int order, String mime, long size) {
        storageKey = key; sortOrder = order; mimeType = mime; sizeBytes = size;
        createdAt = OffsetDateTime.now();
    }
    void attachTo(ItemReceipt receipt) { this.receipt = receipt; }
}
