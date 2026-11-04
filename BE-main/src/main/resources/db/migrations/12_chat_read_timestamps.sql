-- First receipt time for the single recipient of a P2P message.
-- Existing read watermarks are preserved. Historical first-read times cannot
-- be reconstructed, so they remain NULL rather than using a fabricated time.
BEGIN;
SET LOCAL lock_timeout = '10s';
ALTER TABLE chat_message ADD COLUMN IF NOT EXISTS read_at TIMESTAMPTZ;
ALTER TABLE chat_message DROP CONSTRAINT IF EXISTS chk_chat_message_read_time;
ALTER TABLE chat_message ADD CONSTRAINT chk_chat_message_read_time
    CHECK (read_at IS NULL OR read_at >= created_at);
COMMIT;
