package com.example.base.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "notification_preference")
@Getter
@Setter
@NoArgsConstructor
public class NotificationPreference {

    @Id
    @Column(name = "account_id")
    private Long accountId;

    @Column(name = "in_app_enabled", nullable = false)
    private boolean inAppEnabled = true;

    @Column(name = "email_enabled", nullable = false)
    private boolean emailEnabled = true;

    @Column(name = "browser_push_enabled", nullable = false)
    private boolean browserPushEnabled = false;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public NotificationPreference(Long accountId) {
        this.accountId = accountId;
        this.inAppEnabled = true;
        this.emailEnabled = true;
        this.browserPushEnabled = false;
        this.updatedAt = OffsetDateTime.now();
    }
}
