-- Align existing P2P claims with the shared Claim entity after merging office claims.
BEGIN;
SET LOCAL lock_timeout = '10s';
ALTER TABLE claim ADD COLUMN IF NOT EXISTS submission_channel VARCHAR(7) NOT NULL DEFAULT 'ONLINE';
ALTER TABLE claim ADD COLUMN IF NOT EXISTS created_by BIGINT REFERENCES accounts(id) ON DELETE RESTRICT;
UPDATE claim SET created_by = claimant_id WHERE created_by IS NULL AND claimant_id IS NOT NULL;
ALTER TABLE claim ALTER COLUMN created_by SET NOT NULL;
COMMIT;
