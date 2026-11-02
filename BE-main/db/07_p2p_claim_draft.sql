-- =========================================================
-- SAGASU - SCRIPT 07: CLAIM MANAGEMENT P2P (UC40-45)
-- Schema su dung boi ClaimService, ClaimController va giao dien Claims.
-- Dieu kien nen: accounts(id), post(post_id, account_id, ...) da ton tai.
-- Khong them role; dung 4 role USER, APPROVER, MANAGER, ADMIN hien co.
-- Khong thay doi du lieu/bang post, auth, tiep nhan, ban giao hay dispute.
-- =========================================================
-- Quy tac da chot:
--   * Claim REJECTED/CANCELLED duoc giu nguyen; nguoi dung tao claim_id moi de gui lai.
--   * Moi nguoi/moi bai co toi da 1 claim active (PENDING/APPROVED).
--   * Moi bai co toi da 1 claim APPROVED tai mot thoi diem.
--   * APPROVED chi la dong y claim, CHUA co nghia da giao/nhan do.
--   * Huy truoc ban giao duoc phep tao claim moi neu bai van nhan claim.
--
-- State flow:
--   SUBMIT:       NULL       -> PENDING    (moi lan gui la mot claim moi)
--   APPROVE:      PENDING    -> APPROVED   (Finder bat buoc ghi ly do)
--   REJECT:       PENDING    -> REJECTED   (Finder bat buoc ghi ly do)
--   CANCEL:       PENDING/APPROVED -> CANCELLED (truoc ban giao)
--   Gui lai sau REJECTED khong doi trang thai ho so cu ve PENDING.
-- =========================================================

BEGIN;
SET LOCAL lock_timeout = '10s';

-- SQL thuan de IDE khong tach nham khoi DO/IF.
-- Ep kieu regclass se bao loi ngay neu chua co bang nen.
SELECT 'accounts'::regclass AS accounts_table, 'post'::regclass AS post_table;

-- 1. Ho so chinh: UC40 Submit, UC41 My Claims, UC42 gui claim moi sau tu choi,
--    UC43 Cancel, UC44 Claims on My Post.
-- Finder lay tu post.account_id; khong copy finder_id hoac private_identifiers.
CREATE TABLE IF NOT EXISTS claim (
    claim_id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    post_id        BIGINT NOT NULL,
    claimant_id    BIGINT NOT NULL,
    description    TEXT NOT NULL,
    evidence_note  TEXT,
    status         VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    cancelled_at   TIMESTAMPTZ,
    version        BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT claim_post_fk FOREIGN KEY (post_id)
        REFERENCES post(post_id) ON DELETE RESTRICT,
    CONSTRAINT claim_claimant_fk FOREIGN KEY (claimant_id)
        REFERENCES accounts(id) ON DELETE RESTRICT,
    CONSTRAINT chk_claim_status CHECK (status IN
        ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT chk_claim_description CHECK
        (char_length(btrim(description)) BETWEEN 1 AND 5000),
    CONSTRAINT chk_claim_evidence_note CHECK
        (evidence_note IS NULL OR char_length(btrim(evidence_note)) BETWEEN 1 AND 5000),
    CONSTRAINT chk_claim_cancelled_at CHECK
        ((status = 'CANCELLED') = (cancelled_at IS NOT NULL)),
    CONSTRAINT chk_claim_version CHECK (version >= 0),
    CONSTRAINT chk_claim_updated_at CHECK (updated_at >= created_at),
    CONSTRAINT chk_claim_cancel_time CHECK (cancelled_at >= created_at)
);

-- Ho so REJECTED/CANCELLED khong ngan claimant gui mot claim moi cho cung bai.
-- Khong cho spam nhieu claim dang cho xu ly, hoac tao claim moi khi da duoc duyet.
CREATE UNIQUE INDEX IF NOT EXISTS uq_claim_one_active_per_person
    ON claim (post_id, claimant_id) WHERE status IN ('PENDING', 'APPROVED');
DROP INDEX IF EXISTS uq_claim_one_non_rejected_per_person;

CREATE INDEX IF NOT EXISTS idx_claim_post_claimant_latest
    ON claim (post_id, claimant_id, created_at DESC, claim_id DESC);

-- Unique partial index bao ve ca khi hai request approve chay dong thoi.
CREATE UNIQUE INDEX IF NOT EXISTS uq_claim_one_approved_per_post
    ON claim (post_id) WHERE status = 'APPROVED';

CREATE INDEX IF NOT EXISTS idx_claim_claimant_status_created
    ON claim (claimant_id, status, created_at DESC, claim_id DESC);
CREATE INDEX IF NOT EXISTS idx_claim_claimant_created
    ON claim (claimant_id, created_at DESC, claim_id DESC);
CREATE INDEX IF NOT EXISTS idx_claim_post_status_created
    ON claim (post_id, status, created_at DESC, claim_id DESC);
CREATE INDEX IF NOT EXISTS idx_claim_post_created
    ON claim (post_id, created_at DESC, claim_id DESC);

-- 2. Anh bang chung cua tung claim; su dung R2 private hien co.
-- Luu storage_key, KHONG luu public URL / presigned URL het han.
-- De xuat dung lai validator hien co: toi da 5 anh JPG/PNG, 5 MB/anh moi lan nop.
-- Bang chung co the la mo ta/ghi chu, khong bat buoc co anh.
CREATE TABLE IF NOT EXISTS claim_evidence (
    claim_evidence_id    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    claim_id            BIGINT NOT NULL,
    storage_key         TEXT NOT NULL,
    original_name       VARCHAR(255),
    mime_type           VARCHAR(32) NOT NULL,
    size_bytes          BIGINT NOT NULL,
    content_hash        VARCHAR(64),
    sort_order          SMALLINT NOT NULL,
    uploaded_at         TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT claim_evidence_claim_fk FOREIGN KEY (claim_id)
        REFERENCES claim(claim_id) ON DELETE RESTRICT,
    CONSTRAINT uq_claim_evidence_storage_key UNIQUE (storage_key),
    CONSTRAINT uq_claim_evidence_order UNIQUE (claim_id, sort_order),
    CONSTRAINT chk_claim_evidence_key CHECK (char_length(btrim(storage_key)) > 0),
    CONSTRAINT chk_claim_evidence_mime CHECK (mime_type IN ('image/jpeg', 'image/png')),
    CONSTRAINT chk_claim_evidence_size CHECK (size_bytes BETWEEN 1 AND 5242880),
    CONSTRAINT chk_claim_evidence_hash CHECK
        (content_hash IS NULL OR content_hash ~ '^[0-9a-fA-F]{64}$'),
    CONSTRAINT chk_claim_evidence_order CHECK (sort_order BETWEEN 0 AND 4)
);

-- 3. Lich su xu ly: UC45 Review, UC43 huy.
-- Moi event gan voi dung claim_id, luu nguoi thao tac va ly do.
-- Quy tac doi trang thai trong CHECK; quyen actor phai kiem tra o backend.
CREATE TABLE IF NOT EXISTS claim_activity (
    claim_activity_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    claim_id          BIGINT NOT NULL,
    actor_id          BIGINT NOT NULL,
    action            VARCHAR(16) NOT NULL,
    from_status       VARCHAR(16),
    to_status         VARCHAR(16) NOT NULL,
    reason            TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT claim_activity_claim_fk FOREIGN KEY (claim_id)
        REFERENCES claim(claim_id) ON DELETE RESTRICT,
    CONSTRAINT claim_activity_actor_fk FOREIGN KEY (actor_id)
        REFERENCES accounts(id) ON DELETE RESTRICT,
    CONSTRAINT chk_claim_activity_reason_length CHECK
        (reason IS NULL OR char_length(btrim(reason)) BETWEEN 1 AND 2000),
    CONSTRAINT chk_claim_activity_review_reason CHECK
        (action NOT IN ('APPROVE', 'REJECT')
            OR NULLIF(btrim(reason), '') IS NOT NULL),
    -- IS TRUE ngan SQL NULL lam lot qua CHECK khi from_status bi thieu.
    CONSTRAINT chk_claim_activity_transition CHECK ((
        (action = 'SUBMIT' AND from_status IS NULL AND to_status = 'PENDING')
        OR (action = 'APPROVE' AND from_status = 'PENDING' AND to_status = 'APPROVED')
        OR (action = 'REJECT' AND from_status = 'PENDING' AND to_status = 'REJECTED')
        OR (action = 'CANCEL' AND from_status IN ('PENDING', 'APPROVED') AND to_status = 'CANCELLED')
    ) IS TRUE)
);

CREATE INDEX IF NOT EXISTS idx_claim_activity_timeline
    ON claim_activity (claim_id, created_at, claim_activity_id);
CREATE INDEX IF NOT EXISTS idx_claim_activity_actor
    ON claim_activity (actor_id);

COMMENT ON TABLE claim IS 'UC40-45: Private P2P ownership claim; APPROVED is not proof of handover.';
COMMENT ON TABLE claim_evidence IS 'Private R2 evidence of one claim; a retry uses a new claim and new evidence.';
COMMENT ON TABLE claim_activity IS 'Append-only application audit of submission, approval, rejection and cancellation.';

COMMIT;

-- =========================================================
-- QUY TAC NGHIEP VU DUOC KIEM TRA BOI BACKEND:
-- 1. Submit: actor ACTIVE, khong khoa/het han; actor != post.account_id;
--    post FOUND + P2P + VISIBLE + OPEN, deleted_at IS NULL, chua co item_receipt.
-- 2. My Claims: loc claimant_id theo tai khoan dang nhap.
--    Claims on My Post/Review: actor phai la post.account_id (Finder).
--    Khong coi ADMIN/MANAGER tu dong co quyen doc bang chung cua nguoi khac.
-- 3. UC42: sau REJECTED/CANCELLED, claimant tao claim moi (claim_id moi), mo ta/anh moi.
--    Ho so cu, ly do tu choi va bang chung cu duoc giu nguyen de xem lich su.
--    Khong sua noi dung ho so da nop. Khong chuyen REJECTED ve PENDING.
--    Cancel: chi claimant.
--    Cancel chi truoc ban giao; APPROVED van duoc huy neu chua giao do.
-- 4. Moi thao tac lock post roi claim theo cung thu tu (SELECT FOR UPDATE),
--    kiem tra lai quyen/trang thai hien tai, xu ly trong MOT transaction:
--    - Submit/Gui lai: claim moi + evidence moi + activity SUBMIT moi.
--    - Review/Cancel: claim.status + updated_at/version + activity.
--    - Approve: dat post.status='RESERVED'; cancel claim da approve thi
--      mo lai OPEN neu post chua RETURNED/CLOSED va khong co claim approved khac.
--    - REJECTED/CANCELLED khong dong nghia da giao tra do.
-- 5. Truoc Review/Gui lai, kiem tra lai post van P2P/chua giao van phong.
--    Neu sau nay chuyen sang OFFICE: giu claim/evidence/lich su; can nghiep vu
--    chuyen giao rieng, khong xoa hay tu dong duyet lai trong draft nay.
-- 6. UC19/20: them kiem tra claim vao TODO hien co truoc khi sua/xoa bai.
--    PostManagementService chan sua khi da co claim, chan xoa khi co claim active.
-- 7. Khong copy/tra post.private_identifiers trong response claim cong khai.
--    Noi dung claim/evidence chi claimant va Finder duoc truy cap theo quyen.
--    Presign URL R2 sau khi check quyen; khong dua evidence vao search/AI/feed.
-- 8. Giu nguyen noi dung claim/evidence da nop, activity append-only o service; CHECK/FK khong
--    tu dong dong bo claim.status voi activity hay kiem tra actor/post o tren.
--    Can xay transaction va cac test phan quyen/dong thoi khi trien khai UC.
-- 9. File dung CREATE IF NOT EXISTS de chay lai voi CUNG ban schema.
--    Neu sua draft sau khi da tao bang o moi truong test, dung DB test moi;
--    IF NOT EXISTS khong tu nang cap bang co cau truc cu.
