BEGIN;
SET LOCAL lock_timeout = '10s';

-- Existing schedules did not advertise an intake deadline. Staff can assign one by setting a new schedule.
ALTER TABLE office_intake_appointment ADD COLUMN IF NOT EXISTS intake_deadline TIMESTAMPTZ;
CREATE INDEX IF NOT EXISTS idx_office_intake_deadline ON office_intake_appointment(intake_deadline, transfer_id)
    WHERE status='SCHEDULED' AND intake_deadline IS NOT NULL;

COMMIT;
