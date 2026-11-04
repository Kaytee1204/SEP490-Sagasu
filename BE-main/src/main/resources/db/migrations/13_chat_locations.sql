-- Static locations shared explicitly by participants in an eligible P2P chat.
BEGIN;
SET LOCAL lock_timeout = '10s';
ALTER TABLE chat_message ADD COLUMN IF NOT EXISTS latitude DOUBLE PRECISION;
ALTER TABLE chat_message ADD COLUMN IF NOT EXISTS longitude DOUBLE PRECISION;
ALTER TABLE chat_message ADD COLUMN IF NOT EXISTS location_label VARCHAR(200);
ALTER TABLE chat_message DROP CONSTRAINT IF EXISTS chk_chat_message_type;
ALTER TABLE chat_message ADD CONSTRAINT chk_chat_message_type
    CHECK (message_type IN ('TEXT','ATTACHMENT','MIXED','LOCATION'));
ALTER TABLE chat_message DROP CONSTRAINT IF EXISTS chk_chat_message_type_body;
ALTER TABLE chat_message ADD CONSTRAINT chk_chat_message_type_body CHECK ((
    (message_type IN ('TEXT','MIXED') AND body IS NOT NULL)
    OR (message_type IN ('ATTACHMENT','LOCATION') AND body IS NULL)
) IS TRUE);
ALTER TABLE chat_message DROP CONSTRAINT IF EXISTS chk_chat_message_location;
ALTER TABLE chat_message ADD CONSTRAINT chk_chat_message_location CHECK ((
    (message_type='LOCATION' AND latitude IS NOT NULL AND longitude IS NOT NULL
        AND latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180
        AND (location_label IS NULL OR char_length(btrim(location_label)) BETWEEN 1 AND 200))
    OR (message_type<>'LOCATION' AND latitude IS NULL AND longitude IS NULL AND location_label IS NULL)
) IS TRUE);
COMMIT;
