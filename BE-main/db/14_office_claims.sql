-- Run after scripts 07-13. Existing dossiers remain P2P and keep their history.
BEGIN;
SET LOCAL lock_timeout = '10s';
ALTER TABLE claim ADD COLUMN IF NOT EXISTS claim_type VARCHAR(10) NOT NULL DEFAULT 'P2P';
ALTER TABLE claim ADD COLUMN IF NOT EXISTS item_receipt_id BIGINT;
ALTER TABLE claim DROP CONSTRAINT IF EXISTS claim_item_receipt_fk;
ALTER TABLE claim ADD CONSTRAINT claim_item_receipt_fk FOREIGN KEY (item_receipt_id)
    REFERENCES item_receipt(item_receipt_id) ON DELETE RESTRICT;
ALTER TABLE claim DROP CONSTRAINT IF EXISTS chk_claim_route;
ALTER TABLE claim ADD CONSTRAINT chk_claim_route CHECK (
    (claim_type = 'P2P' AND item_receipt_id IS NULL)
    OR (claim_type = 'OFFICE' AND item_receipt_id IS NOT NULL)
);
CREATE INDEX IF NOT EXISTS idx_claim_office_queue
    ON claim (status, created_at DESC, claim_id DESC) WHERE claim_type = 'OFFICE';
CREATE INDEX IF NOT EXISTS idx_claim_receipt ON claim (item_receipt_id) WHERE item_receipt_id IS NOT NULL;
COMMIT;
