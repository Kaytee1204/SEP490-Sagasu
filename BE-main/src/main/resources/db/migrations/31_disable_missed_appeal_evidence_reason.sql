UPDATE report_reason
SET active = false
WHERE code = 'APPEAL_EVIDENCE_MISSED' AND target_group = 'CLAIM_APPEAL' AND active;
