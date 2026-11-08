-- DaiHV: appointment for bringing an item to the office, separate from collection appointments.
BEGIN;
SET LOCAL lock_timeout = '10s';
CREATE TABLE IF NOT EXISTS office_intake_appointment (
 appointment_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 transfer_id BIGINT NOT NULL REFERENCES office_transfer(transfer_id),
 scheduled_at TIMESTAMPTZ NOT NULL, location TEXT NOT NULL CHECK(length(btrim(location)) BETWEEN 1 AND 1000),
 note TEXT, created_by BIGINT NOT NULL REFERENCES accounts(id), created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 status VARCHAR(12) NOT NULL DEFAULT 'SCHEDULED' CHECK(status IN ('SCHEDULED','CANCELLED','COMPLETED')),
 email_status VARCHAR(8) NOT NULL DEFAULT 'UNSENT' CHECK(email_status IN ('UNSENT','SENT','FAILED')),
 response_status VARCHAR(10) NOT NULL DEFAULT 'UNSENT' CHECK(response_status IN ('UNSENT','PENDING','CONFIRMED','DECLINED','EXPIRED')),
 response_token_hash VARCHAR(64), email_sent_at TIMESTAMPTZ, response_deadline TIMESTAMPTZ, responded_at TIMESTAMPTZ,
 emailed_at TIMESTAMPTZ, cancellation_reason TEXT,
 CHECK(status<>'CANCELLED' OR length(btrim(cancellation_reason)) BETWEEN 1 AND 2000)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_intake_appointment_active ON office_intake_appointment(transfer_id) WHERE status='SCHEDULED';
CREATE INDEX IF NOT EXISTS idx_intake_appointment_history ON office_intake_appointment(transfer_id,appointment_id DESC);
CREATE UNIQUE INDEX IF NOT EXISTS uq_intake_invitation_token ON office_intake_appointment(response_token_hash) WHERE response_token_hash IS NOT NULL;
-- Keep transfer.status=PENDING while waiting to receive the item. A separate schedule
-- distinguishes unscheduled/scheduled requests without rewriting the earlier team scripts.
COMMIT;
