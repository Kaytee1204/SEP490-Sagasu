BEGIN;
SET LOCAL lock_timeout = '10s';

-- The pickup response window begins when the appointment is published on the web.
-- Preserve previously issued deadlines and legacy schedules that have yet to be published.
ALTER TABLE appointment DROP CONSTRAINT IF EXISTS chk_appointment_response_deadline;
ALTER TABLE appointment ADD CONSTRAINT chk_appointment_response_deadline CHECK
    (response_status IN ('LEGACY','UNSENT') OR response_deadline IS NOT NULL);

COMMIT;
