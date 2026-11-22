BEGIN;
SET LOCAL lock_timeout = '10s';

ALTER TABLE p2p_claim_approval ADD COLUMN IF NOT EXISTS custody_mode varchar(8) NOT NULL DEFAULT 'P2P'
    CHECK (custody_mode IN ('P2P','OFFICE'));

-- Transferred claims are reassessed by the office, with a new approval round.
UPDATE p2p_claim_approval a SET closed_at=now()
FROM claim c
WHERE c.claim_id=a.approved_claim_id AND a.closed_at IS NULL AND c.claim_type<>a.custody_mode;

COMMIT;
