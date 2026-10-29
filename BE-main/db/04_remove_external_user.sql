-- =========================================================
-- SAGASU - V4: BO ROLE EXTERNAL_USER
-- =========================================================
-- Chay SAU script 03_sync_post_schema.sql trong cung database/schema.
-- Chi thay accounts_role_check; cac cot va rang buoc Post cua v3 giu nguyen.
-- Co the chay lai. Khong tu dong doi role, xoa tai khoan hay doi trang thai bai.

SET client_encoding TO 'UTF8';

BEGIN;
SET LOCAL lock_timeout = '10s';

-- Khoa bang trong luc kiem tra va thay CHECK de tranh role cu duoc them xen vao.
LOCK TABLE accounts IN ACCESS EXCLUSIVE MODE;

-- Dung neu con tai khoan mang role cu: can quyet dinh role thay the truoc.
-- Khong tu dong doi sang USER vi USER duoc dang bai hien thi ngay.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM accounts WHERE role = 'EXTERNAL_USER') THEN
        RAISE EXCEPTION
            'Con tai khoan EXTERNAL_USER. Hay gan role phu hop cho cac tai khoan do truoc khi chay lai v4.';
    END IF;
END;
$$;

ALTER TABLE accounts
    DROP CONSTRAINT IF EXISTS accounts_role_check;

ALTER TABLE accounts
    ADD CONSTRAINT accounts_role_check CHECK (
        role IN ('ADMIN', 'USER', 'CHECKER', 'APPROVER', 'MANAGER')
    );

COMMIT;

SELECT conname, pg_get_constraintdef(oid) AS definition
FROM pg_constraint
WHERE conrelid = 'accounts'::regclass
  AND conname = 'accounts_role_check';
