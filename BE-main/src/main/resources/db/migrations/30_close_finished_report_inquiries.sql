-- Older conclusions left ordinary unanswered inquiries open. Keep replies and later follow-up intact.
UPDATE report_inquiry i SET closed_at = r.resolved_at
FROM report r
WHERE r.report_id = i.report_id AND r.status IN ('RESOLVED', 'REJECTED')
  AND i.responded_at IS NULL AND i.closed_at IS NULL AND i.created_at <= r.resolved_at
  AND NOT EXISTS (
    SELECT 1 FROM report_recovery x JOIN p2p_claim_appeal a USING (report_id)
    JOIN p2p_claim_approval approval USING (approval_id)
    WHERE x.inquiry_id = i.inquiry_id AND x.confirmed_at IS NULL
      AND r.status = 'RESOLVED' AND approval.closed_at IS NULL
  );
