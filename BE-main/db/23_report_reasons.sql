BEGIN;
SET LOCAL lock_timeout = '10s';

CREATE TABLE IF NOT EXISTS report_reason (
    code VARCHAR(64) PRIMARY KEY,
    target_group VARCHAR(24) NOT NULL CHECK (target_group IN ('POST','USER','CLAIM','MESSAGE','HANDOVER_ISSUE')),
    label VARCHAR(200) NOT NULL CHECK (length(btrim(label)) > 0),
    description_required BOOLEAN NOT NULL DEFAULT false,
    active BOOLEAN NOT NULL DEFAULT true,
    sort_order SMALLINT NOT NULL
);

INSERT INTO report_reason (code, target_group, label, description_required, sort_order) VALUES
    ('POST_FALSE_INFO', 'POST', 'Thông tin sai lệch', false, 1),
    ('POST_SPAM', 'POST', 'Spam hoặc đăng trùng', false, 2),
    ('POST_INAPPROPRIATE', 'POST', 'Hình ảnh hoặc nội dung không phù hợp', false, 3),
    ('POST_PRIVACY', 'POST', 'Công khai thông tin cá nhân', false, 4),
    ('POST_SCAM', 'POST', 'Nghi ngờ lừa đảo', false, 5),
    ('POST_OTHER', 'POST', 'Lý do khác', true, 6),
    ('USER_IMPERSONATION', 'USER', 'Giả mạo người khác', false, 1),
    ('USER_HARASSMENT', 'USER', 'Quấy rối hoặc đe dọa', false, 2),
    ('USER_SCAM', 'USER', 'Nghi ngờ lừa đảo hoặc đòi tiền', false, 3),
    ('USER_SPAM', 'USER', 'Spam nhiều lần', false, 4),
    ('USER_PRIVACY', 'USER', 'Tiết lộ thông tin cá nhân', false, 5),
    ('USER_OTHER', 'USER', 'Lý do khác', true, 6),
    ('CLAIM_FALSE_OWNERSHIP', 'CLAIM', 'Nghi ngờ nhận đồ không thuộc sở hữu', false, 1),
    ('CLAIM_FALSE_EVIDENCE', 'CLAIM', 'Bằng chứng giả hoặc không liên quan', false, 2),
    ('CLAIM_CONFLICTING_INFO', 'CLAIM', 'Thông tin xác minh mâu thuẫn', false, 3),
    ('CLAIM_ABUSE', 'CLAIM', 'Lạm dụng gửi yêu cầu', false, 4),
    ('CLAIM_OTHER', 'CLAIM', 'Lý do khác', true, 5),
    ('MESSAGE_HARASSMENT', 'MESSAGE', 'Quấy rối hoặc xúc phạm', false, 1),
    ('MESSAGE_THREAT', 'MESSAGE', 'Đe dọa', false, 2),
    ('MESSAGE_SCAM', 'MESSAGE', 'Lừa đảo hoặc đường link đáng ngờ', false, 3),
    ('MESSAGE_MONEY_REQUEST', 'MESSAGE', 'Yêu cầu chuyển tiền', false, 4),
    ('MESSAGE_INAPPROPRIATE', 'MESSAGE', 'Nội dung không phù hợp', false, 5),
    ('MESSAGE_OTHER', 'MESSAGE', 'Lý do khác', true, 6),
    ('HANDOVER_NOT_RECEIVED', 'HANDOVER_ISSUE', 'Chưa nhận được đồ dù đã ghi nhận giao', true, 1),
    ('HANDOVER_WRONG_ITEM', 'HANDOVER_ISSUE', 'Giao sai đồ', true, 2),
    ('HANDOVER_MISSING_ITEMS', 'HANDOVER_ISSUE', 'Thiếu đồ hoặc phụ kiện', true, 3),
    ('HANDOVER_CONDITION', 'HANDOVER_ISSUE', 'Tình trạng đồ khác mô tả', true, 4),
    ('HANDOVER_ISSUE_OTHER', 'HANDOVER_ISSUE', 'Lý do khác', true, 5)
ON CONFLICT (code) DO NOTHING;

-- Preserve old free-text reports without guessing their reason.
INSERT INTO report_reason (code, target_group, label, description_required, active, sort_order)
SELECT target_group || '_LEGACY', target_group, 'Chưa phân loại (báo cáo cũ)', false, false, 99
FROM unnest(ARRAY['POST','USER','CLAIM','MESSAGE','HANDOVER_ISSUE']) AS groups(target_group)
ON CONFLICT (code) DO NOTHING;

ALTER TABLE report ADD COLUMN IF NOT EXISTS reason_code VARCHAR(64);
ALTER TABLE report ADD COLUMN IF NOT EXISTS reason_label_snapshot VARCHAR(200);

UPDATE report SET
    reason_code = CASE
        WHEN kind = 'HANDOVER_ISSUE' THEN 'HANDOVER_ISSUE_LEGACY'
        WHEN post_id IS NOT NULL THEN 'POST_LEGACY'
        WHEN claim_id IS NOT NULL THEN 'CLAIM_LEGACY'
        WHEN message_id IS NOT NULL THEN 'MESSAGE_LEGACY'
        ELSE 'USER_LEGACY'
    END
WHERE reason_code IS NULL;

UPDATE report r SET reason_label_snapshot = reason.label
FROM report_reason reason
WHERE r.reason_code = reason.code AND r.reason_label_snapshot IS NULL;

ALTER TABLE report ALTER COLUMN reason_code SET NOT NULL;
ALTER TABLE report ALTER COLUMN reason_label_snapshot SET NOT NULL;
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'report'::regclass AND conname = 'fk_report_reason') THEN
        ALTER TABLE report ADD CONSTRAINT fk_report_reason FOREIGN KEY (reason_code) REFERENCES report_reason(code);
    END IF;
END $$;
COMMIT;
