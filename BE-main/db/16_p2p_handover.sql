BEGIN;
CREATE TABLE IF NOT EXISTS p2p_handover (
 handover_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 claim_id BIGINT NOT NULL UNIQUE REFERENCES claim(claim_id),
 status VARCHAR(32) NOT NULL DEFAULT 'WAITING_CONFIRMATION' CHECK(status IN ('WAITING_CONFIRMATION','COMPLETED','DISPUTED')),
 recorded_by BIGINT NOT NULL REFERENCES accounts(id), recorded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 note TEXT CHECK(note IS NULL OR char_length(btrim(note)) BETWEEN 1 AND 2000),
 confirmed_by BIGINT REFERENCES accounts(id), confirmed_at TIMESTAMPTZ,
 client_request_id UUID NOT NULL, request_hash CHAR(64) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),version BIGINT NOT NULL DEFAULT 0,
 UNIQUE(recorded_by,client_request_id),
 CHECK((status='COMPLETED' AND confirmed_by IS NOT NULL AND confirmed_at IS NOT NULL) OR (status<>'COMPLETED' AND confirmed_by IS NULL AND confirmed_at IS NULL)),
 CHECK(confirmed_at>=recorded_at)
);
CREATE INDEX IF NOT EXISTS idx_handover_status ON p2p_handover(status,recorded_at,handover_id);
CREATE TABLE IF NOT EXISTS p2p_handover_evidence (
 evidence_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, handover_id BIGINT NOT NULL REFERENCES p2p_handover(handover_id),
 storage_key TEXT NOT NULL UNIQUE, original_name VARCHAR(255), mime_type VARCHAR(32) NOT NULL CHECK(mime_type IN ('image/jpeg','image/png')),
 size_bytes BIGINT NOT NULL CHECK(size_bytes BETWEEN 1 AND 5242880),content_hash CHAR(64) NOT NULL,
 sort_order SMALLINT NOT NULL CHECK(sort_order BETWEEN 0 AND 4), UNIQUE(handover_id,sort_order)
);
ALTER TABLE claim_activity DROP CONSTRAINT IF EXISTS chk_claim_activity_transition;
ALTER TABLE claim_activity ADD CONSTRAINT chk_claim_activity_transition CHECK ((
 (action='SUBMIT' AND from_status IS NULL AND to_status='PENDING') OR
 (action='APPROVE' AND from_status='PENDING' AND to_status='APPROVED') OR
 (action='REJECT' AND from_status='PENDING' AND to_status='REJECTED') OR
 (action='CANCEL' AND from_status IN ('PENDING','APPROVED') AND to_status='CANCELLED') OR
 (action='HANDOVER' AND from_status='APPROVED' AND to_status='APPROVED') OR
 (action='CONFIRM_RECEIPT' AND from_status='APPROVED' AND to_status='COMPLETED') OR
 (action='AUTO_CLOSE' AND from_status='PENDING' AND to_status='CLOSED') OR
 (action='REPORT_ISSUE' AND from_status='APPROVED' AND to_status='APPROVED' AND nullif(btrim(reason),'') IS NOT NULL)
) IS TRUE);
COMMIT;
