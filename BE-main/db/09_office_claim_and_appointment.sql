-- SAGASU 09: Office claims, counter submission and appointments (PostgreSQL).
-- Run ONCE after 05, 07 and 08, against the same schema/search_path.
-- Does not create audit_log/notification or send email. Existing evidence/activity is reused.
-- Read 09_office_claim_and_appointment.md before enabling office endpoints.
BEGIN;
SET LOCAL lock_timeout = '10s';

SELECT 'accounts'::regclass, 'post'::regclass, 'item_receipt'::regclass,
       'storage_location'::regclass, 'claim'::regclass,
       'claim_evidence'::regclass, 'claim_activity'::regclass;

-- Existing claims were submitted using the P2P service. Preserve that origin even
-- if post.custody_mode has subsequently changed; never infer an office approval.
ALTER TABLE claim
    ADD COLUMN claim_type VARCHAR(6) NOT NULL DEFAULT 'P2P',
    ADD COLUMN submission_channel VARCHAR(7) NOT NULL DEFAULT 'ONLINE',
    ADD COLUMN created_by BIGINT REFERENCES accounts(id) ON DELETE RESTRICT,
    ADD COLUMN claimant_name VARCHAR(150),
    ADD COLUMN claimant_email VARCHAR(255),
    ADD COLUMN claimant_phone VARCHAR(40);

UPDATE claim
SET created_by = claimant_id
WHERE created_by IS NULL;
ALTER TABLE claim ALTER COLUMN created_by SET NOT NULL;
ALTER TABLE claim ALTER COLUMN claimant_id DROP NOT NULL;

ALTER TABLE claim
    ADD CONSTRAINT chk_claim_type CHECK (claim_type IN ('P2P', 'OFFICE')),
    ADD CONSTRAINT chk_claim_channel CHECK (submission_channel IN ('ONLINE', 'COUNTER')),
    ADD CONSTRAINT chk_claim_submission_identity CHECK (
        (submission_channel = 'ONLINE' AND claimant_id IS NOT NULL AND created_by = claimant_id)
        OR (submission_channel = 'COUNTER' AND claim_type = 'OFFICE'
            AND NULLIF(btrim(claimant_name), '') IS NOT NULL)
    ),
    ADD CONSTRAINT chk_claim_contact_name CHECK (
        claimant_name IS NULL OR length(btrim(claimant_name)) > 0),
    ADD CONSTRAINT chk_claim_contact_email CHECK (
        claimant_email IS NULL OR (length(btrim(claimant_email)) > 0
            AND claimant_email = lower(btrim(claimant_email)))),
    ADD CONSTRAINT chk_claim_contact_phone CHECK (
        claimant_phone IS NULL OR length(btrim(claimant_phone)) > 0);

-- Compatibility with old P2P INSERTs, which do not yet write created_by.
-- Never infer the staff member for a counter claim.
CREATE FUNCTION fill_online_claim_creator_09() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.submission_channel = 'ONLINE' AND NEW.created_by IS NULL THEN
        NEW.created_by := NEW.claimant_id;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_claim_online_creator_09
BEFORE INSERT ON claim FOR EACH ROW EXECUTE FUNCTION fill_online_claim_creator_09();

-- Office review can request additional evidence. Rejected/cancelled dossiers
-- remain immutable; resubmission here means supplementing a NEEDS_INFO dossier.
ALTER TABLE claim DROP CONSTRAINT chk_claim_status;
ALTER TABLE claim ADD CONSTRAINT chk_claim_status CHECK
    (status IN ('PENDING', 'NEEDS_INFO', 'APPROVED', 'REJECTED', 'CANCELLED'));

DROP INDEX uq_claim_one_active_per_person;
CREATE UNIQUE INDEX uq_claim_one_active_per_person ON claim(post_id, claimant_id)
    WHERE status IN ('PENDING', 'NEEDS_INFO', 'APPROVED');
-- uq_claim_one_approved_per_post from 07 still enforces one approval per item.
-- Anonymous counter claimants have NULL claimant_id: do not deduplicate them by
-- name/email (different people can share these); staff must check existing dossiers.
CREATE INDEX idx_claim_office_queue ON claim(status, created_at, claim_id)
    WHERE claim_type = 'OFFICE';
CREATE INDEX idx_claim_created_by ON claim(created_by);

ALTER TABLE claim_activity DROP CONSTRAINT chk_claim_activity_review_reason;
ALTER TABLE claim_activity ADD CONSTRAINT chk_claim_activity_review_reason CHECK
    (action NOT IN ('APPROVE', 'REJECT', 'REQUEST_INFO')
        OR NULLIF(btrim(reason), '') IS NOT NULL);
ALTER TABLE claim_activity DROP CONSTRAINT chk_claim_activity_transition;
ALTER TABLE claim_activity ADD CONSTRAINT chk_claim_activity_transition CHECK ((
    (action = 'SUBMIT' AND from_status IS NULL AND to_status = 'PENDING')
    OR (action = 'APPROVE' AND from_status = 'PENDING' AND to_status = 'APPROVED')
    OR (action = 'REJECT' AND from_status IN ('PENDING', 'NEEDS_INFO') AND to_status = 'REJECTED')
    OR (action = 'REQUEST_INFO' AND from_status = 'PENDING' AND to_status = 'NEEDS_INFO')
    OR (action = 'RESUBMIT' AND from_status = 'NEEDS_INFO' AND to_status = 'PENDING')
    OR (action = 'CANCEL' AND from_status IN ('PENDING', 'NEEDS_INFO', 'APPROVED')
        AND to_status = 'CANCELLED')
) IS TRUE);

-- Both P2P and office appointments reference a claim, not a separate claimant.
-- Proposal: one active appointment per claim. Cancelled/completed/no-show rows
-- are retained; a subsequent appointment can be created for the same claim.
CREATE TABLE appointment (
    appointment_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    claim_id BIGINT NOT NULL REFERENCES claim(claim_id) ON DELETE RESTRICT,
    scheduled_at TIMESTAMPTZ NOT NULL,
    location TEXT NOT NULL,
    status VARCHAR(12) NOT NULL DEFAULT 'SCHEDULED',
    created_by BIGINT NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    note TEXT,
    cancellation_reason TEXT,
    cancelled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_appointment_location CHECK (length(btrim(location)) > 0),
    CONSTRAINT chk_appointment_status CHECK
        (status IN ('SCHEDULED', 'COMPLETED', 'CANCELLED', 'NO_SHOW')),
    CONSTRAINT chk_appointment_cancellation CHECK (
        (status = 'CANCELLED' AND cancelled_at IS NOT NULL
            AND NULLIF(btrim(cancellation_reason), '') IS NOT NULL)
        OR (status <> 'CANCELLED' AND cancelled_at IS NULL AND cancellation_reason IS NULL)
    ),
    CONSTRAINT chk_appointment_cancel_time CHECK (cancelled_at >= created_at),
    CONSTRAINT chk_appointment_updated_time CHECK (updated_at >= created_at),
    CONSTRAINT chk_appointment_version CHECK (version >= 0)
);
CREATE UNIQUE INDEX uq_appointment_one_scheduled_per_claim ON appointment(claim_id)
    WHERE status = 'SCHEDULED';
CREATE INDEX idx_appointment_claim_history ON appointment(claim_id, created_at DESC);
CREATE INDEX idx_appointment_schedule ON appointment(scheduled_at)
    WHERE status = 'SCHEDULED';
CREATE TRIGGER trg_appointment_updated_at
BEFORE UPDATE ON appointment FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON COLUMN claim.created_by IS 'Actual form author; counter staff is not the claimant.';
COMMENT ON COLUMN claim.claimant_id IS 'Optional only for COUNTER office claims without an account.';
COMMENT ON COLUMN claim.claim_type IS 'Handling route for this dossier; never infer approval from current post custody.';
COMMENT ON COLUMN claim.claimant_email IS 'Optional contact email, including counter claimants without an account.';
COMMENT ON TABLE appointment IS 'Claim collection appointment; COMPLETED is not proof of handover.';
COMMENT ON TABLE claim IS 'Shared P2P/office ownership claims. APPROVED is not proof of handover.';

COMMIT;
