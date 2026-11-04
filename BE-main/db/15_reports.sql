BEGIN;
SET LOCAL lock_timeout='10s';
CREATE TABLE IF NOT EXISTS report (
 report_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 reporter_id BIGINT NOT NULL REFERENCES accounts(id),
 kind VARCHAR(24) NOT NULL CHECK(kind IN ('GENERAL','HANDOVER_ISSUE')),
 post_id BIGINT REFERENCES post(post_id), claim_id BIGINT REFERENCES claim(claim_id),
 message_id BIGINT REFERENCES chat_message(message_id), reported_account_id BIGINT REFERENCES accounts(id),
 description TEXT NOT NULL CHECK(char_length(btrim(description)) BETWEEN 1 AND 5000),
 status VARCHAR(16) NOT NULL DEFAULT 'OPEN' CHECK(status IN ('OPEN','IN_REVIEW','RESOLVED','REJECTED')),
 resolution_note TEXT, resolved_at TIMESTAMPTZ,
 client_request_id UUID NOT NULL, request_hash CHAR(64) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(), version BIGINT NOT NULL DEFAULT 0,
 CHECK(num_nonnulls(post_id,claim_id,message_id,reported_account_id)=1),
 CHECK(kind<>'HANDOVER_ISSUE' OR claim_id IS NOT NULL),
 UNIQUE(reporter_id,client_request_id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_report_open_target ON report
 (reporter_id,coalesce(post_id,0),coalesce(claim_id,0),coalesce(message_id,0),coalesce(reported_account_id,0))
 WHERE kind='GENERAL' AND status IN ('OPEN','IN_REVIEW');
CREATE INDEX IF NOT EXISTS idx_report_mine ON report(reporter_id,created_at DESC,report_id DESC);
CREATE INDEX IF NOT EXISTS idx_report_queue ON report(status,created_at,report_id);
CREATE TABLE IF NOT EXISTS report_evidence (
 evidence_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 report_id BIGINT NOT NULL REFERENCES report(report_id), storage_key TEXT NOT NULL UNIQUE,
 original_name VARCHAR(255), mime_type VARCHAR(32) NOT NULL CHECK(mime_type IN ('image/jpeg','image/png')),
 size_bytes BIGINT NOT NULL CHECK(size_bytes BETWEEN 1 AND 5242880), content_hash CHAR(64) NOT NULL,
 sort_order SMALLINT NOT NULL CHECK(sort_order BETWEEN 0 AND 4), UNIQUE(report_id,sort_order)
);
CREATE TABLE IF NOT EXISTS report_activity (
 activity_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, report_id BIGINT NOT NULL REFERENCES report(report_id),
 actor_id BIGINT NOT NULL REFERENCES accounts(id), action VARCHAR(24) NOT NULL,
 from_status VARCHAR(16), to_status VARCHAR(16) NOT NULL, public_note TEXT,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_report_activity ON report_activity(report_id,activity_id);
COMMIT;
