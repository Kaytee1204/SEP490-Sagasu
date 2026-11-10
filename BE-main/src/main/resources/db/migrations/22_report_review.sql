-- DaiHV: report investigation, participant replies and recorded decisions.
BEGIN;
SET LOCAL lock_timeout='10s';
ALTER TABLE report ADD COLUMN IF NOT EXISTS assigned_to BIGINT REFERENCES accounts(id);
ALTER TABLE report ADD COLUMN IF NOT EXISTS assigned_at TIMESTAMPTZ;
ALTER TABLE report ADD COLUMN IF NOT EXISTS resolved_by BIGINT REFERENCES accounts(id);
ALTER TABLE report_activity ADD COLUMN IF NOT EXISTS internal_note TEXT;
CREATE TABLE IF NOT EXISTS report_snapshot (
 report_id BIGINT PRIMARY KEY REFERENCES report(report_id), captured_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 capture_source VARCHAR(16) NOT NULL CHECK(capture_source IN ('SUBMISSION','FIRST_REVIEW')),
 payload JSONB NOT NULL
);
CREATE TABLE IF NOT EXISTS report_asset (
 asset_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, report_id BIGINT NOT NULL REFERENCES report(report_id),
 storage_key TEXT NOT NULL, original_name TEXT, source VARCHAR(40) NOT NULL,
 UNIQUE(report_id,storage_key)
);
CREATE INDEX IF NOT EXISTS idx_report_asset_key ON report_asset(storage_key);
CREATE TABLE IF NOT EXISTS report_inquiry (
 inquiry_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,report_id BIGINT NOT NULL REFERENCES report(report_id),
 recipient_id BIGINT NOT NULL REFERENCES accounts(id),created_by BIGINT NOT NULL REFERENCES accounts(id),
 question TEXT NOT NULL CHECK(length(btrim(question)) BETWEEN 1 AND 5000),created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 response TEXT,responded_at TIMESTAMPTZ,
 CHECK((response IS NULL)=(responded_at IS NULL)),CHECK(response IS NULL OR length(btrim(response)) BETWEEN 1 AND 5000)
);
CREATE INDEX IF NOT EXISTS idx_report_inquiry_recipient ON report_inquiry(recipient_id,created_at DESC);
CREATE TABLE IF NOT EXISTS report_reply_evidence (
 evidence_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,inquiry_id BIGINT NOT NULL REFERENCES report_inquiry(inquiry_id),
 storage_key TEXT NOT NULL UNIQUE,original_name TEXT NOT NULL,content_hash CHAR(64) NOT NULL
);
CREATE TABLE IF NOT EXISTS report_decision (
 decision_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,report_id BIGINT NOT NULL REFERENCES report(report_id),
 actor_id BIGINT NOT NULL REFERENCES accounts(id),action VARCHAR(32) NOT NULL,
 conclusion TEXT NOT NULL,basis TEXT NOT NULL,created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 before_state JSONB NOT NULL DEFAULT '{}',after_state JSONB NOT NULL DEFAULT '{}'
);
CREATE TABLE IF NOT EXISTS report_hold (
 report_id BIGINT PRIMARY KEY REFERENCES report(report_id),post_id BIGINT NOT NULL REFERENCES post(post_id),
 actor_id BIGINT NOT NULL REFERENCES accounts(id),reason TEXT NOT NULL,created_at TIMESTAMPTZ NOT NULL DEFAULT now(),released_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_report_hold_post ON report_hold(post_id) WHERE released_at IS NULL;
CREATE TABLE IF NOT EXISTS office_handover_issue (
 report_id BIGINT PRIMARY KEY REFERENCES report(report_id),handover_id BIGINT NOT NULL REFERENCES office_handover(handover_id)
);
-- Preserve successive disputes rather than overwriting the original report.
ALTER TABLE p2p_handover_issue DROP CONSTRAINT IF EXISTS p2p_handover_issue_handover_id_key;
CREATE INDEX IF NOT EXISTS idx_handover_issue_history ON p2p_handover_issue(handover_id,report_id);
ALTER TABLE p2p_handover DROP CONSTRAINT IF EXISTS p2p_handover_status_check;
ALTER TABLE p2p_handover ADD CONSTRAINT p2p_handover_status_check CHECK(status IN ('WAITING_CONFIRMATION','COMPLETED','DISPUTED','CANCELLED'));
CREATE TABLE IF NOT EXISTS report_recipient (
 report_id BIGINT NOT NULL REFERENCES report(report_id),account_id BIGINT NOT NULL REFERENCES accounts(id),
 PRIMARY KEY(report_id,account_id)
);
-- Add missing validation without replacing any team migration.
DO $$ BEGIN
 IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conname='chk_report_review_target') THEN
  ALTER TABLE report ADD CONSTRAINT chk_report_review_target CHECK(num_nonnulls(post_id,claim_id,message_id,reported_account_id)=1);
 END IF;
 IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conname='chk_report_review_status') THEN
  ALTER TABLE report ADD CONSTRAINT chk_report_review_status CHECK(status IN ('OPEN','IN_REVIEW','RESOLVED','REJECTED'));
 END IF;
 IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conname='chk_report_review_kind') THEN
  ALTER TABLE report ADD CONSTRAINT chk_report_review_kind CHECK(kind IN ('GENERAL','HANDOVER_ISSUE'));
 END IF;
END $$;
CREATE INDEX IF NOT EXISTS idx_report_assigned ON report(assigned_to,status,created_at);
-- Extend the current team's transition rule without copying or modifying its earlier scripts.
DO $$ DECLARE existing_rule TEXT; BEGIN
 SELECT pg_get_expr(conbin,conrelid) INTO existing_rule FROM pg_constraint
 WHERE conrelid='claim_activity'::regclass AND conname='chk_claim_activity_transition';
 IF existing_rule IS NOT NULL AND position('DISPUTE_RESOLVE' in existing_rule)=0 THEN
  ALTER TABLE claim_activity DROP CONSTRAINT chk_claim_activity_transition;
  EXECUTE 'ALTER TABLE claim_activity ADD CONSTRAINT chk_claim_activity_transition CHECK ((' || existing_rule || ') OR (action=''DISPUTE_RESOLVE'' AND from_status=''APPROVED'' AND to_status IN (''APPROVED'',''REJECTED'') AND nullif(btrim(reason),'''') IS NOT NULL))';
 END IF;
END $$;
COMMIT;
