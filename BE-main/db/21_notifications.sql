-- KhanhTB: In-App & Email Notification System with User Preferences
BEGIN;
SET LOCAL lock_timeout = '10s';

CREATE TABLE IF NOT EXISTS notification (
    notification_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    recipient_id BIGINT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    type VARCHAR(48) NOT NULL,
    channel VARCHAR(24) NOT NULL DEFAULT 'IN_APP' CHECK (channel IN ('IN_APP', 'BOTH')),
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    target_url VARCHAR(500),
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_notification_recipient_unread
    ON notification(recipient_id, is_read, created_at DESC, notification_id DESC);

CREATE INDEX IF NOT EXISTS idx_notification_recipient_history
    ON notification(recipient_id, created_at DESC, notification_id DESC);

CREATE TABLE IF NOT EXISTS notification_preference (
    account_id BIGINT PRIMARY KEY REFERENCES accounts(id) ON DELETE CASCADE,
    in_app_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    email_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    browser_push_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMIT;
