BEGIN;

CREATE TABLE IF NOT EXISTS p2p_claim_approval (
    approval_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    approved_claim_id BIGINT NOT NULL REFERENCES claim(claim_id),
    appeal_until TIMESTAMPTZ NOT NULL,
    closed_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_p2p_claim_approval_open
    ON p2p_claim_approval (approved_claim_id) WHERE closed_at IS NULL;

CREATE TABLE IF NOT EXISTS p2p_claim_appeal (
    approval_id BIGINT NOT NULL REFERENCES p2p_claim_approval(approval_id),
    appellant_claim_id BIGINT NOT NULL REFERENCES claim(claim_id),
    report_id BIGINT NOT NULL UNIQUE REFERENCES report(report_id),
    PRIMARY KEY (approval_id, appellant_claim_id)
);

COMMIT;
