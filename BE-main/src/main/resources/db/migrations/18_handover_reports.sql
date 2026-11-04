BEGIN;
CREATE TABLE IF NOT EXISTS p2p_handover_issue (
 report_id BIGINT PRIMARY KEY REFERENCES report(report_id),
 handover_id BIGINT NOT NULL UNIQUE REFERENCES p2p_handover(handover_id)
);
COMMIT;
