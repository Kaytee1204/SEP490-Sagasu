-- Add missing claim categories. Run after the existing category schema.
-- Existing names, IDs, inactive categories and posts are preserved.
-- Hokie P and Default are intentionally excluded.
BEGIN;
SET LOCAL lock_timeout = '5s';

INSERT INTO category (name, is_active)
VALUES
    ('Đồ dùng trẻ em', true),
    ('Máy tính & Thiết bị điện tử', true),
    ('Kính mắt', true),
    ('Giày dép', true),
    ('Giấy tờ & Thẻ', true),
    ('Bình nước', true)
ON CONFLICT DO NOTHING;

COMMIT;
