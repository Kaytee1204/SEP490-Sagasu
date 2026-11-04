-- Requires 09_office_claim_and_appointment.sql. Compatible with the earlier local response prototype.
BEGIN;
SET LOCAL lock_timeout = '10s';
ALTER TABLE appointment
    ADD COLUMN IF NOT EXISTS response_status VARCHAR(12) NOT NULL DEFAULT 'LEGACY',
    ADD COLUMN IF NOT EXISTS email_sent_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS response_deadline TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS responded_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS response_token_hash VARCHAR(64);
-- Old appointments stay LEGACY; no retrospective expiry or automatic email sends.
ALTER TABLE appointment DROP CONSTRAINT IF EXISTS chk_appointment_response;
ALTER TABLE appointment ADD CONSTRAINT chk_appointment_response CHECK
    (response_status IN ('LEGACY','UNSENT','PENDING','CONFIRMED','DECLINED','EXPIRED'));
ALTER TABLE appointment DROP CONSTRAINT IF EXISTS chk_appointment_response_deadline;
ALTER TABLE appointment ADD CONSTRAINT chk_appointment_response_deadline CHECK
    (response_status IN ('LEGACY','UNSENT') OR
      (email_sent_at IS NOT NULL AND response_deadline IS NOT NULL AND response_deadline = email_sent_at + interval '12 hours'));
CREATE UNIQUE INDEX IF NOT EXISTS uq_appointment_response_token ON appointment(response_token_hash) WHERE response_token_hash IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_appointment_pending_response ON appointment(response_deadline)
    WHERE status = 'SCHEDULED' AND response_status = 'PENDING';
ALTER TABLE claim_activity DROP CONSTRAINT chk_claim_activity_transition;
ALTER TABLE claim_activity ADD CONSTRAINT chk_claim_activity_transition CHECK ((
    (action = 'SUBMIT' AND from_status IS NULL AND to_status = 'PENDING')
    OR (action = 'APPROVE' AND from_status = 'PENDING' AND to_status = 'APPROVED')
    OR (action = 'REJECT' AND from_status IN ('PENDING','NEEDS_INFO') AND to_status = 'REJECTED')
    OR (action = 'REQUEST_INFO' AND from_status = 'PENDING' AND to_status = 'NEEDS_INFO')
    OR (action = 'RESUBMIT' AND from_status = 'NEEDS_INFO' AND to_status = 'PENDING')
    OR (action = 'CANCEL' AND from_status IN ('PENDING','NEEDS_INFO','APPROVED') AND to_status = 'CANCELLED')
    OR (action IN ('INVITE_DECLINE','INVITE_EXPIRE') AND from_status = 'APPROVED' AND to_status = 'REJECTED')
    OR (action = 'INVITE_CONFIRM' AND from_status = 'APPROVED' AND to_status = 'APPROVED')
) IS TRUE);
COMMIT;
