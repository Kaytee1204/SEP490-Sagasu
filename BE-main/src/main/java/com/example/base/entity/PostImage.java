package com.example.base.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.OffsetDateTime;

@Entity
@Table(name = "post_image", uniqueConstraints = @UniqueConstraint(name = "uq_post_image_order", columnNames = {"post_id", "sort_order"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostImage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "post_image_id")
    private Long id;
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false, foreignKey = @ForeignKey(name = "post_image_post_id_fkey"))
    private Post post;
    @JsonIgnore
    @Column(name = "storage_key", nullable = false, columnDefinition = "text", length = org.hibernate.Length.LONG32)
    private String storageKey;
    @Column(name = "image_url", columnDefinition = "text", length = org.hibernate.Length.LONG32)
    private String imageUrl;
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
    @Column(name = "mime_type", length = 100)
    private String mimeType;
    @Column(name = "size_bytes")
    private Long sizeBytes;
    @Column(name = "width")
    private Integer width;
    @Column(name = "height")
    private Integer height;
    @Column(name = "content_hash", length = 64)
    private String contentHash;
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public PostImage(String key, int order, String mimeType, long size, int width, int height, String hash) {
        this.storageKey = key;
        this.sortOrder = order;
        this.mimeType = mimeType;
        this.sizeBytes = size;
        this.width = width;
        this.height = height;
        this.contentHash = hash;
    }

    void attachTo(Post post) {
        this.post = post;
    }

    @PrePersist
    void onCreate() {
        createdAt = OffsetDateTime.now();
    }
}
