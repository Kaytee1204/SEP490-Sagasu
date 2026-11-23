BEGIN;
SET LOCAL lock_timeout = '10s';

ALTER TABLE p2p_handover_issue
  DROP COLUMN IF EXISTS incident_phase,
  ADD COLUMN IF NOT EXISTS submission_status varchar(24),
  ADD COLUMN IF NOT EXISTS message_snapshot jsonb;

INSERT INTO report_reason(code,target_group,label,description_required,sort_order) VALUES
  ('P2P_HANDOVER_NOT_RETURNED','HANDOVER_ISSUE','Không trao trả tài sản',true,1),
  ('P2P_HANDOVER_NO_CONTACT','HANDOVER_ISSUE','Không liên lạc được để thực hiện việc giao đã thống nhất',true,1),
  ('P2P_HANDOVER_PAYMENT','HANDOVER_ISSUE','Bị yêu cầu đưa tiền hoặc tài sản để được nhận lại đồ',true,2),
  ('P2P_HANDOVER_THREAT','HANDOVER_ISSUE','Bị đe dọa hoặc ép buộc khi trao trả',true,3)
ON CONFLICT (code) DO NOTHING;

COMMIT;
