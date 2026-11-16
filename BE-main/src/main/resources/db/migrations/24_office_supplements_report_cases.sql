-- Additive migration: preserve original claims, evidence and report decisions.
CREATE TABLE IF NOT EXISTS office_claim_supplement_image (
  image_id BIGSERIAL PRIMARY KEY,
  activity_id BIGINT NOT NULL REFERENCES claim_activity(claim_activity_id) ON DELETE RESTRICT,
  storage_key VARCHAR(512) NOT NULL UNIQUE,
  original_name VARCHAR(255),
  content_hash VARCHAR(64) NOT NULL,
  sort_order INTEGER NOT NULL CHECK (sort_order BETWEEN 0 AND 4)
);
CREATE INDEX IF NOT EXISTS idx_office_supplement_activity ON office_claim_supplement_image(activity_id);

CREATE TABLE IF NOT EXISTS report_case (
  case_id BIGSERIAL PRIMARY KEY,
  title VARCHAR(200) NOT NULL CHECK (length(btrim(title)) > 0),
  owner_id BIGINT NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE report ADD COLUMN IF NOT EXISTS case_id BIGINT REFERENCES report_case(case_id) ON DELETE RESTRICT;
CREATE INDEX IF NOT EXISTS idx_report_case ON report(case_id) WHERE case_id IS NOT NULL;
