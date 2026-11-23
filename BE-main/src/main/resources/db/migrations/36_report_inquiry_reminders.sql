ALTER TABLE report_inquiry ADD COLUMN IF NOT EXISTS reminded_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_report_inquiry_pending_reminder ON report_inquiry (response_due_at)
WHERE reminded_at IS NULL AND responded_at IS NULL AND closed_at IS NULL;
