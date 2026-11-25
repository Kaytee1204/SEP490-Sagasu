BEGIN;

ALTER TABLE item_receipt ADD COLUMN IF NOT EXISTS item_summary TEXT;
ALTER TABLE office_handover
    ADD COLUMN IF NOT EXISTS item_summary TEXT,
    ADD COLUMN IF NOT EXISTS difference_note TEXT,
    ADD COLUMN IF NOT EXISTS confirmation_deadline TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS completion_method VARCHAR(12);

ALTER TABLE office_handover
    DROP CONSTRAINT IF EXISTS chk_office_handover_confirmation,
    DROP CONSTRAINT IF EXISTS office_handover_status_check,
    DROP CONSTRAINT IF EXISTS office_handover_claim_id_key,
    DROP CONSTRAINT IF EXISTS office_handover_item_receipt_id_key;

-- Older waiting records used a seven-day response period. They require a new session at the counter.
UPDATE office_handover SET confirmation_deadline = NULL, version = version + 1
WHERE status = 'WAITING_CONFIRMATION' AND confirmation_deadline = recorded_at + interval '168 hours';

ALTER TABLE office_handover ADD CONSTRAINT office_handover_status_check
    CHECK (status IN ('WAITING_CONFIRMATION', 'COMPLETED', 'DISPUTED', 'CANCELLED'));
ALTER TABLE office_handover ADD CONSTRAINT chk_office_handover_confirmation CHECK (
    (item_summary IS NULL OR length(btrim(item_summary)) BETWEEN 1 AND 2000)
    AND (difference_note IS NULL OR length(btrim(difference_note)) BETWEEN 1 AND 2000)
    AND (confirmation_deadline IS NULL OR confirmation_deadline > recorded_at)
    AND (completion_method IS NULL OR (status = 'COMPLETED' AND completion_method IN ('RECIPIENT', 'TIMEOUT')))
);

DROP INDEX IF EXISTS idx_office_handover_confirmation_due;
CREATE UNIQUE INDEX IF NOT EXISTS uq_office_handover_active_claim
    ON office_handover(claim_id) WHERE status <> 'CANCELLED';
CREATE UNIQUE INDEX IF NOT EXISTS uq_office_handover_active_receipt
    ON office_handover(item_receipt_id) WHERE status <> 'CANCELLED';
CREATE INDEX IF NOT EXISTS idx_office_handover_issue_record ON office_handover_issue(handover_id);

COMMIT;
