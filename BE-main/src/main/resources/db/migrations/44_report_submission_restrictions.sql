CREATE TABLE IF NOT EXISTS account_submission_restriction (
    account_id BIGINT NOT NULL REFERENCES accounts(id),
    action VARCHAR(12) NOT NULL CHECK (action IN ('POST', 'CLAIM')),
    expires_at TIMESTAMPTZ NOT NULL,
    report_id BIGINT NOT NULL REFERENCES report(report_id),
    PRIMARY KEY (account_id, action)
);
