-- DaiHV: UC58 and UC63-66. Run after 18 and the office appointment schema.
-- Existing team migration files are unchanged. No historical claims are reopened here.
BEGIN;
SET LOCAL lock_timeout = '10s';
CREATE TABLE IF NOT EXISTS office_transfer (
 transfer_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 post_id BIGINT NOT NULL REFERENCES post(post_id),
 requested_by BIGINT NOT NULL REFERENCES accounts(id),
 requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 status VARCHAR(12) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','RECEIVED','REJECTED','CANCELLED')),
 processed_by BIGINT REFERENCES accounts(id), processed_at TIMESTAMPTZ,
 item_receipt_id BIGINT REFERENCES item_receipt(item_receipt_id),
 reason TEXT, reconciliation_note TEXT,
 version BIGINT NOT NULL DEFAULT 0,
 CHECK ((status='PENDING' AND processed_at IS NULL) OR (status<>'PENDING' AND processed_at IS NOT NULL)),
 CHECK (status<>'RECEIVED' OR item_receipt_id IS NOT NULL)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_office_transfer_pending ON office_transfer(post_id) WHERE status='PENDING';
CREATE INDEX IF NOT EXISTS idx_office_transfer_queue ON office_transfer(status,requested_at,transfer_id);
CREATE INDEX IF NOT EXISTS idx_office_transfer_requester ON office_transfer(requested_by,requested_at DESC);
CREATE TABLE IF NOT EXISTS office_claim_verification (
 verification_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 claim_id BIGINT NOT NULL REFERENCES claim(claim_id),
 appointment_id BIGINT NOT NULL REFERENCES appointment(appointment_id),
 verified_by BIGINT NOT NULL REFERENCES accounts(id), verified_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 result VARCHAR(6) NOT NULL CHECK(result IN ('PASSED','FAILED')),
 note TEXT NOT NULL CHECK(length(btrim(note)) BETWEEN 1 AND 2000)
);
CREATE INDEX IF NOT EXISTS idx_office_verification_claim ON office_claim_verification(claim_id,verification_id DESC);
CREATE TABLE IF NOT EXISTS office_handover (
 handover_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 claim_id BIGINT NOT NULL UNIQUE REFERENCES claim(claim_id),
 item_receipt_id BIGINT NOT NULL UNIQUE REFERENCES item_receipt(item_receipt_id),
 verification_id BIGINT NOT NULL REFERENCES office_claim_verification(verification_id),
 recorded_by BIGINT NOT NULL REFERENCES accounts(id), recipient_id BIGINT NOT NULL REFERENCES accounts(id),
 recorded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 condition_note TEXT NOT NULL CHECK(length(btrim(condition_note)) BETWEEN 1 AND 2000),
 status VARCHAR(24) NOT NULL DEFAULT 'WAITING_CONFIRMATION' CHECK(status IN ('WAITING_CONFIRMATION','COMPLETED','DISPUTED')),
 confirmed_at TIMESTAMPTZ, issue_note TEXT,
 client_request_id UUID NOT NULL, request_hash VARCHAR(64) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 UNIQUE(recorded_by,client_request_id),
 CHECK((status='COMPLETED')=(confirmed_at IS NOT NULL)),
 CHECK(status<>'DISPUTED' OR length(btrim(issue_note)) BETWEEN 1 AND 2000)
);
CREATE TABLE IF NOT EXISTS office_handover_evidence (
 evidence_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 handover_id BIGINT NOT NULL REFERENCES office_handover(handover_id),
 storage_key TEXT NOT NULL UNIQUE, original_name VARCHAR(255), mime_type VARCHAR(32) NOT NULL,
 size_bytes BIGINT NOT NULL CHECK(size_bytes BETWEEN 1 AND 5242880), content_hash VARCHAR(64) NOT NULL,
 sort_order INTEGER NOT NULL CHECK(sort_order BETWEEN 0 AND 4), UNIQUE(handover_id,sort_order)
);
-- Shared integration: preserve the complete P2P + office transition vocabulary.
ALTER TABLE claim_activity DROP CONSTRAINT IF EXISTS chk_claim_activity_transition;
ALTER TABLE claim_activity ADD CONSTRAINT chk_claim_activity_transition CHECK ((
 (action='SUBMIT' AND from_status IS NULL AND to_status='PENDING') OR
 (action='APPROVE' AND from_status='PENDING' AND to_status='APPROVED') OR
 (action='REJECT' AND from_status IN ('PENDING','NEEDS_INFO') AND to_status='REJECTED') OR
 (action='REQUEST_INFO' AND from_status='PENDING' AND to_status='NEEDS_INFO') OR
 (action='RESUBMIT' AND from_status='NEEDS_INFO' AND to_status='PENDING') OR
 (action='CANCEL' AND from_status IN ('PENDING','NEEDS_INFO','APPROVED') AND to_status='CANCELLED') OR
 (action IN ('HANDOVER','INVITE_CONFIRM','RESCHEDULE') AND from_status='APPROVED' AND to_status='APPROVED') OR
 (action='CONFIRM_RECEIPT' AND from_status='APPROVED' AND to_status='COMPLETED') OR
 (action='AUTO_CLOSE' AND from_status IN ('PENDING','NEEDS_INFO') AND to_status='CLOSED') OR
 (action='REPORT_ISSUE' AND from_status='APPROVED' AND to_status='APPROVED' AND nullif(btrim(reason),'') IS NOT NULL) OR
 (action IN ('INVITE_DECLINE','INVITE_EXPIRE','OFFICE_CANCEL') AND from_status='APPROVED' AND to_status='REJECTED') OR
 (action='TRANSFER_OFFICE' AND ((from_status='APPROVED' AND to_status='PENDING') OR (from_status=to_status AND from_status IN ('PENDING','NEEDS_INFO','REJECTED','CANCELLED','CLOSED'))))
) IS TRUE);
ALTER TABLE claim DROP CONSTRAINT IF EXISTS chk_claim_status;
ALTER TABLE claim ADD CONSTRAINT chk_claim_status CHECK(status IN ('PENDING','NEEDS_INFO','APPROVED','REJECTED','CANCELLED','COMPLETED','CLOSED'));
-- Include NEEDS_INFO in the existing active-dossier invariant.
DROP INDEX IF EXISTS uq_claim_one_active_per_person;
CREATE UNIQUE INDEX uq_claim_one_active_per_person ON claim(post_id,claimant_id) WHERE status IN ('PENDING','NEEDS_INFO','APPROVED');
-- Preserve requests made through the previous office handover action.
INSERT INTO office_transfer(post_id,requested_by,requested_at)
SELECT p.post_id,p.account_id,p.updated_at FROM post p
WHERE p.custody_mode='OFFICE' AND p.post_type='FOUND' AND p.status IN ('OPEN','RESERVED')
 AND p.deleted_at IS NULL AND NOT EXISTS(SELECT 1 FROM item_receipt r WHERE r.post_id=p.post_id)
 AND NOT EXISTS(SELECT 1 FROM office_transfer t WHERE t.post_id=p.post_id)
ON CONFLICT DO NOTHING;
COMMIT;
