BEGIN;

ALTER TABLE office_transfer
    ADD COLUMN IF NOT EXISTS original_post_title TEXT,
    ADD COLUMN IF NOT EXISTS original_post_description TEXT,
    ADD COLUMN IF NOT EXISTS original_category_id BIGINT,
    ADD COLUMN IF NOT EXISTS original_category_name TEXT;

CREATE TABLE IF NOT EXISTS office_transfer_post_image (
    transfer_id BIGINT NOT NULL REFERENCES office_transfer(transfer_id) ON DELETE CASCADE,
    post_image_id BIGINT NOT NULL,
    storage_key TEXT,
    image_url TEXT,
    sort_order INTEGER NOT NULL,
    PRIMARY KEY (transfer_id, post_image_id)
);
CREATE INDEX IF NOT EXISTS idx_office_transfer_post_image_key ON office_transfer_post_image(storage_key);

-- Published pickup appointments now use the appointment's scheduled time and verification deadline.
-- Preserve historical confirmations and terminal records for display in the activity history.
UPDATE appointment a SET response_status='LEGACY', response_deadline=NULL, response_token_hash=NULL, version=a.version+1
FROM claim c
WHERE a.claim_id=c.claim_id AND c.claim_type='OFFICE' AND a.status='SCHEDULED'
  AND (a.response_status IN ('PENDING','UNSENT') OR (a.response_status='LEGACY' AND a.response_token_hash IS NOT NULL));

COMMIT;
