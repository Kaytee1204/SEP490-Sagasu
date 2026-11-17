package com.example.base.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import java.time.OffsetDateTime;

@Entity
@Table(name = "saved_post", indexes = @Index(name = "idx_saved_post_account_created", columnList = "account_id,created_at DESC,post_id DESC"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SavedPost {
    @EmbeddedId
    private SavedPostId id;

    @MapsId("accountId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "account_id", foreignKey = @ForeignKey(name = "saved_post_account_fk"))
    private Account account;

    @MapsId("postId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "post_id", foreignKey = @ForeignKey(name = "saved_post_post_fk"))
    private Post post;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
