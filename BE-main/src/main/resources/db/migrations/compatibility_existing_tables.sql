-- Runs after 09_add_claim_categories and before 10. Team scripts remain unchanged.
BEGIN;
SET LOCAL lock_timeout = '10s';
-- Tables may have been created by Hibernate without SQL-only keys/defaults.
DO $$ BEGIN
 IF to_regclass('claim_question_template') IS NOT NULL THEN
  ALTER TABLE claim_question_template ALTER COLUMN is_active SET DEFAULT true;
  CREATE UNIQUE INDEX IF NOT EXISTS uq_claim_template_code_version ON claim_question_template(code, version);
 END IF;
 IF to_regclass('claim_question') IS NOT NULL THEN
  CREATE UNIQUE INDEX IF NOT EXISTS uq_claim_question_template_code ON claim_question(template_id, question_code);
 END IF;
 IF to_regclass('claim_answer') IS NOT NULL THEN
  CREATE UNIQUE INDEX IF NOT EXISTS uq_claim_answer_claim_code ON claim_answer(claim_id, question_code);
 END IF;
 IF to_regclass('chat_conversation') IS NOT NULL THEN
  CREATE UNIQUE INDEX IF NOT EXISTS uq_chat_conversation_post_claimant ON chat_conversation(post_id, claimant_id);
  ALTER TABLE chat_conversation ALTER COLUMN last_message_sequence SET DEFAULT 0;
  ALTER TABLE chat_conversation ALTER COLUMN version SET DEFAULT 0;
  ALTER TABLE chat_conversation ALTER COLUMN created_at SET DEFAULT now();
  ALTER TABLE chat_conversation ALTER COLUMN updated_at SET DEFAULT now();
  ALTER TABLE chat_conversation ALTER COLUMN last_activity_at SET DEFAULT now();
 END IF;
 IF to_regclass('chat_participant') IS NOT NULL THEN
  ALTER TABLE chat_participant ALTER COLUMN last_read_sequence SET DEFAULT 0;
 END IF;
END $$;
ALTER TABLE claim ADD COLUMN IF NOT EXISTS item_receipt_id BIGINT;
UPDATE claim c SET item_receipt_id = r.item_receipt_id
FROM item_receipt r
WHERE c.claim_type='OFFICE' AND c.item_receipt_id IS NULL AND r.post_id=c.post_id;
COMMIT;
