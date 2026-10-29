-- =========================================================
-- SAGASU - SCRIPT 05: PATROL INTAKE & STORAGE MANAGEMENT
-- =========================================================
-- Chay SAU script 04_remove_external_user.sql trong cung database/schema.
-- Tinh nang:
--   1. storage_location: Quan ly vi tri kho luu tru (Toa nha, phong, ke, ngan tu).
--   2. item_receipt: Bien ban tiep nhan do nhat duoc khi di tuan tra (Checker).
--   3. item_receipt_image: Anh vat chung & hien truong tiep nhan (luu tru R2).
-- =========================================================

SET client_encoding TO 'UTF8';

BEGIN;
SET LOCAL lock_timeout = '10s';

-- 1. Kiem tra cac bang tien de
DO $$
BEGIN
    IF to_regclass('accounts') IS NULL
       OR to_regclass('category') IS NULL
       OR to_regclass('post') IS NULL THEN
        RAISE EXCEPTION 'Chay cac script nen truoc khi chay script 05.';
    END IF;
END;
$$;

-- 2. BANG VI TRI LUU KHO (STORAGE LOCATION)
CREATE TABLE IF NOT EXISTS storage_location (
    storage_location_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    parent_location_id  BIGINT,
    code                VARCHAR(40)  NOT NULL UNIQUE,
    name                VARCHAR(150) NOT NULL,
    capacity            INTEGER,
    is_active           BOOLEAN      NOT NULL DEFAULT TRUE,

    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT storage_location_parent_fk
        FOREIGN KEY (parent_location_id)
        REFERENCES storage_location(storage_location_id),

    CONSTRAINT storage_location_capacity_check
        CHECK (capacity IS NULL OR capacity > 0),

    CONSTRAINT storage_location_parent_check
        CHECK (parent_location_id IS DISTINCT FROM storage_location_id)
);

-- 3. BANG BIEN BAN TIEP NHAN TAI SAN (ITEM RECEIPT)
CREATE TABLE IF NOT EXISTS item_receipt (
    item_receipt_id      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    receipt_code         VARCHAR(40)  NOT NULL UNIQUE,

    -- Lien ket voi Post sau khi Approver duyet va he thong tu dong dang bai.
    -- NULL khi bien ban dang o trang thai DRAFT / SUBMITTED.
    post_id              BIGINT UNIQUE,

    source               VARCHAR(16)  NOT NULL DEFAULT 'CHECKER',

    category_id          BIGINT       NOT NULL,
    title                VARCHAR(200) NOT NULL,
    description          TEXT         NOT NULL,
    private_identifiers  TEXT,

    found_at             TIMESTAMPTZ  NOT NULL,
    found_location       TEXT         NOT NULL,
    temporary_location   TEXT,

    -- Nguoi lap bien ban (Checker) -> Tham chieu accounts(id)
    recorded_by          BIGINT       NOT NULL,
    recorded_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    submitted_at         TIMESTAMPTZ,

    -- Thong tin xet duyet (Approver) -> Tham chieu accounts(id)
    review_status        VARCHAR(12)  NOT NULL DEFAULT 'PENDING',
    reviewed_by          BIGINT,
    reviewed_at          TIMESTAMPTZ,
    review_note          TEXT,

    -- Nguoi nhat duoc do (neu co tai khoan) -> Tham chieu accounts(id)
    finder_account_id    BIGINT,
    finder_name          VARCHAR(150),
    finder_contact       VARCHAR(254),

    -- Thong tin nhan vao kho -> Tham chieu accounts(id)
    received_by          BIGINT,
    received_at          TIMESTAMPTZ,
    condition_at_receipt TEXT,
    storage_location_id  BIGINT,

    assigned_officer_id  BIGINT,

    status               VARCHAR(10)  NOT NULL DEFAULT 'DRAFT',
    note                 TEXT,
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT item_receipt_post_fk
        FOREIGN KEY (post_id)
        REFERENCES post(post_id)
        ON DELETE SET NULL,

    CONSTRAINT item_receipt_category_fk
        FOREIGN KEY (category_id)
        REFERENCES category(category_id)
        ON DELETE RESTRICT,

    CONSTRAINT item_receipt_recorded_by_fk
        FOREIGN KEY (recorded_by)
        REFERENCES accounts(id)
        ON DELETE RESTRICT,

    CONSTRAINT item_receipt_reviewed_by_fk
        FOREIGN KEY (reviewed_by)
        REFERENCES accounts(id)
        ON DELETE SET NULL,

    CONSTRAINT item_receipt_finder_fk
        FOREIGN KEY (finder_account_id)
        REFERENCES accounts(id)
        ON DELETE SET NULL,

    CONSTRAINT item_receipt_received_by_fk
        FOREIGN KEY (received_by)
        REFERENCES accounts(id)
        ON DELETE SET NULL,

    CONSTRAINT item_receipt_officer_fk
        FOREIGN KEY (assigned_officer_id)
        REFERENCES accounts(id)
        ON DELETE SET NULL,

    CONSTRAINT item_receipt_storage_fk
        FOREIGN KEY (storage_location_id)
        REFERENCES storage_location(storage_location_id)
        ON DELETE SET NULL,

    CONSTRAINT item_receipt_source_check
        CHECK (source IN ('CHECKER', 'WALK_IN', 'EXISTING_POST')),

    CONSTRAINT item_receipt_status_check
        CHECK (status IN ('DRAFT', 'SUBMITTED', 'IN_STORAGE', 'RETURNED', 'REJECTED')),

    CONSTRAINT item_receipt_review_status_check
        CHECK (review_status IN ('PENDING', 'NEEDS_INFO', 'ACCEPTED', 'REJECTED')),

    CONSTRAINT item_receipt_title_check
        CHECK (LENGTH(BTRIM(title)) > 0),

    CONSTRAINT item_receipt_storage_details_check
        CHECK (
            status NOT IN ('IN_STORAGE', 'RETURNED')
            OR (
                received_by IS NOT NULL
                AND received_at IS NOT NULL
                AND storage_location_id IS NOT NULL
                AND NULLIF(BTRIM(condition_at_receipt), '') IS NOT NULL
            )
        )
);

-- 4. BANG ANH BIEN BAN TIEP NHAN (ITEM RECEIPT IMAGE - LUU TREN R2)
CREATE TABLE IF NOT EXISTS item_receipt_image (
    item_receipt_image_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    item_receipt_id       BIGINT       NOT NULL,
    storage_key           TEXT         NOT NULL,
    image_url             TEXT,
    sort_order            INTEGER      NOT NULL DEFAULT 0,
    mime_type             VARCHAR(100),
    size_bytes            BIGINT,
    is_sensitive          BOOLEAN      NOT NULL DEFAULT FALSE, -- True: anh chup giay to/so serial/tien mat (chi noi bo kho xem)
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT item_receipt_image_receipt_fk
        FOREIGN KEY (item_receipt_id)
        REFERENCES item_receipt(item_receipt_id)
        ON DELETE CASCADE,

    CONSTRAINT uq_item_receipt_image_order
        UNIQUE (item_receipt_id, sort_order),

    CONSTRAINT chk_receipt_image_storage_key
        CHECK (LENGTH(BTRIM(storage_key)) > 0),

    CONSTRAINT chk_receipt_image_sort_order
        CHECK (sort_order >= 0)
);

-- 5. INDEXES TOI UU TRUY VAN
CREATE INDEX IF NOT EXISTS item_receipt_recorded_by_idx
    ON item_receipt (recorded_by, recorded_at DESC);

CREATE INDEX IF NOT EXISTS item_receipt_review_idx
    ON item_receipt (review_status, submitted_at)
    WHERE submitted_at IS NOT NULL;

CREATE INDEX IF NOT EXISTS item_receipt_storage_idx
    ON item_receipt (storage_location_id);

CREATE INDEX IF NOT EXISTS item_receipt_status_idx
    ON item_receipt (status);

CREATE INDEX IF NOT EXISTS item_receipt_image_receipt_idx
    ON item_receipt_image (item_receipt_id);

-- 6. TRIGGER TU DONG CAP NHAT UPDATED_AT
CREATE OR REPLACE TRIGGER trg_storage_location_updated_at
    BEFORE UPDATE ON storage_location
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE OR REPLACE TRIGGER trg_item_receipt_updated_at
    BEFORE UPDATE ON item_receipt
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMIT;

-- 7. Kiem tra ket qua sau khi tao bang
SELECT table_name, column_name, data_type
FROM information_schema.columns
WHERE table_schema = current_schema()
  AND table_name IN ('storage_location', 'item_receipt', 'item_receipt_image')
ORDER BY table_name, ordinal_position;
