BEGIN;
SET LOCAL lock_timeout = '10s';

ALTER TABLE report_reason DROP CONSTRAINT IF EXISTS report_reason_target_group_check;
ALTER TABLE report_reason ADD CONSTRAINT report_reason_target_group_check
  CHECK (target_group IN ('POST','USER','CLAIM','MESSAGE','HANDOVER_ISSUE','CLAIM_APPEAL'));

UPDATE report_reason SET active=false WHERE target_group='MESSAGE' OR code='CLAIM_CONFLICTING_INFO';
UPDATE report_reason SET description_required=true WHERE code='POST_PRIVACY';
UPDATE report_reason SET label='Nghi dùng bằng chứng giả'
  WHERE code='CLAIM_FALSE_EVIDENCE' AND label='Bằng chứng giả hoặc không liên quan';
UPDATE report_reason SET label='Quấy rối'
  WHERE code='USER_HARASSMENT' AND label='Quấy rối hoặc đe dọa';
UPDATE report_reason SET label='Đòi tiền để trả đồ hoặc có dấu hiệu lừa đảo'
  WHERE code='USER_SCAM' AND label='Nghi ngờ lừa đảo hoặc đòi tiền';
UPDATE report_reason SET label='Đã nhận nhưng sai món'
  WHERE code='HANDOVER_WRONG_ITEM' AND label='Giao sai đồ';
UPDATE report_reason SET label='Đã nhận đúng món nhưng thiếu phụ kiện'
  WHERE code='HANDOVER_MISSING_ITEMS' AND label='Thiếu đồ hoặc phụ kiện';
UPDATE report_reason SET label='Đồ bị hỏng hoặc khác tình trạng lúc bàn giao'
  WHERE code='HANDOVER_CONDITION' AND label='Tình trạng đồ khác mô tả';

INSERT INTO report_reason(code,target_group,label,description_required,sort_order) VALUES
  ('USER_SAFETY_THREAT','USER','Đe dọa an toàn',true,2),
  ('HANDOVER_APPOINTMENT_ISSUE','HANDOVER_ISSUE','Không thể thực hiện lượt giao đã hẹn',true,1),
  ('HANDOVER_WRONG_RECIPIENT','HANDOVER_ISSUE','Nghi đã giao nhầm người',true,2),
  ('HANDOVER_CONFIRMATION_ERROR','HANDOVER_ISSUE','Ghi nhận hoặc xác nhận bàn giao nhầm',true,5),
  ('APPEAL_NEW_EVIDENCE','CLAIM_APPEAL','Có căn cứ sở hữu bổ sung',true,1),
  ('APPEAL_EVIDENCE_MISSED','CLAIM_APPEAL','Căn cứ đã gửi có thể bị bỏ sót',true,2),
  ('APPEAL_WRONG_RECIPIENT','CLAIM_APPEAL','Nghi chọn nhầm người nhận',true,3)
ON CONFLICT (code) DO NOTHING;
INSERT INTO report_reason(code,target_group,label,description_required,sort_order,active)
VALUES ('APPEAL_LEGACY','CLAIM_APPEAL','Tranh chấp kết quả nhận đồ',true,99,false)
ON CONFLICT (code) DO NOTHING;

ALTER TABLE p2p_handover DROP CONSTRAINT IF EXISTS p2p_handover_claim_id_key;
CREATE UNIQUE INDEX IF NOT EXISTS uq_p2p_handover_active_claim
  ON p2p_handover(claim_id) WHERE status <> 'CANCELLED';
ALTER TABLE p2p_handover_issue ALTER COLUMN handover_id DROP NOT NULL;
ALTER TABLE p2p_handover_issue ADD COLUMN IF NOT EXISTS phase varchar(12) NOT NULL DEFAULT 'DURING';
ALTER TABLE p2p_handover_issue DROP CONSTRAINT IF EXISTS p2p_handover_issue_phase_check;
ALTER TABLE p2p_handover_issue ADD CONSTRAINT p2p_handover_issue_phase_check
  CHECK ((phase='BEFORE' AND handover_id IS NULL) OR (phase IN ('DURING','AFTER') AND handover_id IS NOT NULL));

CREATE TABLE IF NOT EXISTS report_recovery (
  report_id BIGINT PRIMARY KEY REFERENCES report(report_id),
  holder_id BIGINT NOT NULL REFERENCES accounts(id),
  due_at TIMESTAMPTZ NOT NULL,
  confirmed_by BIGINT REFERENCES accounts(id),
  confirmed_at TIMESTAMPTZ
);
ALTER TABLE report_recovery ADD COLUMN IF NOT EXISTS inquiry_id BIGINT REFERENCES report_inquiry(inquiry_id);
ALTER TABLE report_inquiry ADD COLUMN IF NOT EXISTS response_due_at TIMESTAMPTZ;
ALTER TABLE report_inquiry ADD COLUMN IF NOT EXISTS closed_at TIMESTAMPTZ;
COMMIT;
