-- =========================================================
-- SAGASU - SCRIPT 03: DONG BO SCHEMA VOI BACKEND HIEN TAI
-- =========================================================
-- Chay SAU hai script Authentication/Account va Category/Post/Post_Image,
-- trong CUNG database/schema da chay hai script do.
-- Chay toan bo file truoc khi khoi dong backend. Co the chay lai.
-- Chi bo sung cot va thay hai CHECK; khong UPDATE/DELETE du lieu,
-- khong tao bang claim/dispute, khong thay trigger hay Category CRUD.
-- Khong tu dong chuyen trang thai cac bai dang da ton tai.

SET client_encoding TO 'UTF8';

BEGIN;
SET LOCAL lock_timeout = '10s';

-- Dung som neu chua chay day du hai script nen.
DO $$
BEGIN
    IF to_regclass('accounts') IS NULL
       OR to_regclass('category') IS NULL
       OR to_regclass('post') IS NULL
       OR to_regclass('post_image') IS NULL THEN
        RAISE EXCEPTION
            'Chay hai script nen trong dung database/schema truoc khi chay script 03.';
    END IF;
END;
$$;

-- 1. Entity Account hien tai co truong fullName; cho phep NULL voi tai khoan cu.
ALTER TABLE accounts
    ADD COLUMN IF NOT EXISTS full_name VARCHAR(150);

-- 2. Bo sung role nguoi dung ben ngoai, giu nguyen cac role dang co.
ALTER TABLE accounts
    DROP CONSTRAINT IF EXISTS accounts_role_check;

ALTER TABLE accounts
    ADD CONSTRAINT accounts_role_check CHECK (
        role IN ('ADMIN', 'USER', 'EXTERNAL_USER', 'CHECKER', 'APPROVER', 'MANAGER')
    );

-- 3. Cot duoc backend dung khi xoa mem va de xuat lich giao do.
-- deleted_at da co trong script 02 nen khong can tao lai.
-- handover_requested_at la lich DE XUAT, chua phai xac nhan da nhan do.
-- Khong dat CHECK ngay > NOW(): ngay hen cu se thanh qua khu theo thoi gian.
ALTER TABLE post
    ADD COLUMN IF NOT EXISTS deletion_reason TEXT,
    ADD COLUMN IF NOT EXISTS handover_requested_at TIMESTAMPTZ;

-- 4. Cho phep VISIBLE khong co nguoi duyet khi dang truc tiep.
-- Backend quyet dinh quyen dang truc tiep theo role:
--   USER: VISIBLE; CHECKER/EXTERNAL_USER: PENDING.
--   FOUND + OFFICE: luon PENDING va yeu cau de xuat lich o backend.
-- REJECTED/HIDDEN van bat buoc co nguoi va thoi diem xu ly;
-- rang buoc ly do chk_post_moderation_reason cua script 02 duoc giu nguyen.
ALTER TABLE post
    DROP CONSTRAINT IF EXISTS chk_post_moderation_actor;

ALTER TABLE post
    ADD CONSTRAINT chk_post_moderation_actor CHECK (
        moderation_status = 'PENDING'
        OR (moderated_by IS NOT NULL AND moderated_at IS NOT NULL)
        OR (
            moderation_status = 'VISIBLE'
            AND moderated_by IS NULL
            AND moderated_at IS NULL
        )
    );

COMMIT;

-- 5. Kiem tra schema sau khi chay; khong doc du lieu tai khoan/bai dang.
SELECT current_database() AS database_name, current_schema() AS schema_name;

SELECT table_name, column_name, data_type
FROM information_schema.columns
WHERE table_schema = current_schema()
  AND (
      (table_name = 'accounts' AND column_name = 'full_name')
      OR (table_name = 'post' AND column_name IN ('deletion_reason', 'handover_requested_at'))
  )
ORDER BY table_name, column_name;

SELECT conname, pg_get_constraintdef(oid) AS definition
FROM pg_constraint
WHERE (conrelid = 'accounts'::regclass AND conname = 'accounts_role_check')
   OR (conrelid = 'post'::regclass AND conname = 'chk_post_moderation_actor')
ORDER BY conname;
