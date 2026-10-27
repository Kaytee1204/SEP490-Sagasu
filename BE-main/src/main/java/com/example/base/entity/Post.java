package com.example.base.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "post")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Post {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "post_id")
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, foreignKey = @ForeignKey(name = "post_account_id_fkey"))
    private Account account;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false, foreignKey = @ForeignKey(name = "post_created_by_fkey"))
    private Account createdBy;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false, foreignKey = @ForeignKey(name = "post_category_id_fkey"))
    private Category category;
    @Column(name = "post_type", nullable = false, length = 5)
    private String postType;
    @Column(name = "title", nullable = false, length = 200)
    private String title;
    @Column(name = "description", nullable = false, columnDefinition = "text", length = org.hibernate.Length.LONG32)
    private String description;
    @Column(name = "location_text", nullable = false, columnDefinition = "text", length = org.hibernate.Length.LONG32)
    private String locationText;
    @Column(name = "latitude", precision = 9, scale = 6)
    private BigDecimal latitude;
    @Column(name = "longitude", precision = 9, scale = 6)
    private BigDecimal longitude;
    @Column(name = "event_at", nullable = false)
    private OffsetDateTime eventAt;
    @JsonIgnore
    @Column(name = "private_identifiers", columnDefinition = "text", length = org.hibernate.Length.LONG32)
    private String privateIdentifiers;
    @Column(name = "status", nullable = false, length = 12)
    private String status = "OPEN";
    @Column(name = "custody_mode", nullable = false, length = 6)
    private String custodyMode = "P2P";
    @Column(name = "moderation_status", nullable = false, length = 10)
    private String moderationStatus = "PENDING";
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "moderated_by", foreignKey = @ForeignKey(name = "post_moderated_by_fkey"))
    private Account moderatedBy;
    @Column(name = "moderated_at")
    private OffsetDateTime moderatedAt;
    @JsonIgnore
    @Column(name = "moderation_note", columnDefinition = "text", length = org.hibernate.Length.LONG32)
    private String moderationNote;
    @Column(name = "close_reason", columnDefinition = "text", length = org.hibernate.Length.LONG32)
    private String closeReason;
    @Column(name = "withdrawal_reason", columnDefinition = "text", length = org.hibernate.Length.LONG32)
    private String withdrawalReason;
    @Column(name = "withdrawn_at")
    private OffsetDateTime withdrawnAt;
    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
    @OneToMany(mappedBy = "post", cascade = CascadeType.PERSIST)
    @OrderBy("sortOrder ASC, id ASC")
    @Getter(AccessLevel.NONE)
    private List<PostImage> images = new ArrayList<>();

    public static Post lost(Account owner, Category category, String title, String description,
            String locationText, OffsetDateTime eventAt) {
        Post post = new Post();
        post.account = owner;
        post.createdBy = owner;
        post.category = category;
        post.postType = "LOST";
        post.title = title;
        post.description = description;
        post.locationText = locationText;
        post.eventAt = eventAt;
        post.moderationStatus = owner.getRole() == AccountRole.USER ? "VISIBLE" : "PENDING";
        return post;
    }

    public void addImage(PostImage image) {
        image.attachTo(this);
        images.add(image);
    }

    public static Post found(Account owner, Category category, String title, String description,
            String locationText, OffsetDateTime eventAt, String custodyMode) {
        Post post = lost(owner, category, title, description, locationText, eventAt);
        post.postType = "FOUND";
        post.custodyMode = custodyMode;
        return post;
    }

    public void updatePrivateIdentifiers(String value) {
        privateIdentifiers = value == null || value.isBlank() ? null : value.strip();
    }

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public List<PostImage> getImages() {
        return java.util.Collections.unmodifiableList(images);
    }
}
