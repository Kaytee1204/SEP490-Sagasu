-- Run on databases that already have script 07. No claim/evidence/history rows are changed.
BEGIN;
SET LOCAL lock_timeout = '10s';

-- Keep one PENDING/APPROVED dossier per person/post; closed dossiers can be retried.
CREATE UNIQUE INDEX IF NOT EXISTS uq_claim_one_active_per_person
    ON claim (post_id, claimant_id) WHERE status IN ('PENDING', 'APPROVED');
DROP INDEX IF EXISTS uq_claim_one_non_rejected_per_person;

CREATE INDEX IF NOT EXISTS idx_claim_post_claimant_latest
    ON claim (post_id, claimant_id, created_at DESC, claim_id DESC);

COMMIT;
