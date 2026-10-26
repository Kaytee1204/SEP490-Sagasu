-- =========================================================
-- DATABASE: AUTHENTICATION & ACCOUNT MANAGEMENT (OPTIMIZED)
-- =========================================================


-- =========================================================
-- TABLE 1: accounts
-- Chức năng: Quản lý tài khoản, email, mật khẩu đã băm,
-- phân quyền, trạng thái và yêu cầu đổi mật khẩu lần đầu.
-- =========================================================

CREATE TABLE accounts (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,

    role VARCHAR(20) NOT NULL DEFAULT 'USER'
        CHECK (role IN (
            'ADMIN', 'USER', 'CHECKER', 'APPROVER', 'MANAGER'
        )),

    status VARCHAR(25) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN (
            'PENDING_ACTIVATION', 'ACTIVE', 'SUSPENDED', 'DELETED'
        )),

    must_change_password BOOLEAN NOT NULL DEFAULT TRUE,

    failed_login_count INTEGER NOT NULL DEFAULT 0
        CHECK (failed_login_count >= 0),

    locked_until TIMESTAMPTZ,

    -- Nếu tài khoản tạo tài khoản này bị xóa, set về NULL để tránh mất mát dữ liệu phân cấp
    created_by BIGINT REFERENCES accounts(id) ON DELETE SET NULL,

    valid_until TIMESTAMPTZ,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CHECK (email = LOWER(BTRIM(email)))
);

CREATE INDEX idx_accounts_status ON accounts(status);
CREATE INDEX idx_accounts_role ON accounts(role);

COMMENT ON TABLE accounts IS
'Quản lý tài khoản, mật khẩu đã băm, phân quyền, trạng thái và đổi mật khẩu lần đầu.';


-- =========================================================
-- TABLE 2: otp_challenges
-- Chức năng: Quản lý OTP email cho đăng nhập,
-- đổi mật khẩu và quên mật khẩu.
-- =========================================================

CREATE TABLE otp_challenges (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    -- Xóa tài khoản thì xóa luôn OTP liên quan
    account_id BIGINT NOT NULL
        REFERENCES accounts(id) ON DELETE CASCADE,

    otp_hash CHAR(64) NOT NULL,

    purpose VARCHAR(25) NOT NULL
        CHECK (purpose IN (
            'LOGIN', 'CHANGE_PASSWORD', 'FORGOT_PASSWORD'
        )),

    expires_at TIMESTAMPTZ NOT NULL,

    attempt_count INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 5,

    used_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CHECK (otp_hash ~ '^[0-9a-fA-F]{64}$'),
    CHECK (
        attempt_count >= 0
        AND max_attempts > 0
        AND attempt_count <= max_attempts
    ),
    CHECK (expires_at > created_at)
);

CREATE INDEX idx_otp_account_purpose_created
    ON otp_challenges(account_id, purpose, created_at DESC);

CREATE INDEX idx_otp_expires_at
    ON otp_challenges(expires_at);

-- Đảm bảo mỗi user chỉ có tối đa 1 OTP đang mở cho mỗi mục đích
CREATE UNIQUE INDEX uq_otp_one_open_per_purpose
    ON otp_challenges(account_id, purpose)
    WHERE used_at IS NULL AND revoked_at IS NULL;

COMMENT ON TABLE otp_challenges IS
'Quản lý OTP email, mục đích xác thực, thời hạn, số lần thử và trạng thái sử dụng.';


-- =========================================================
-- TABLE 3: refresh_tokens
-- Chức năng: Quản lý refresh token, duy trì phiên đăng nhập,
-- làm mới token và thu hồi phiên.
-- =========================================================

CREATE TABLE refresh_tokens (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    -- Xóa tài khoản thì thu hồi/xóa toàn bộ token của tài khoản đó
    account_id BIGINT NOT NULL
        REFERENCES accounts(id) ON DELETE CASCADE,

    -- Lưu ý: Ràng buộc UNIQUE tự động tạo một B-Tree Index cho token_hash
    token_hash VARCHAR(255) NOT NULL UNIQUE,

    family_id UUID NOT NULL,

    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    last_used_at TIMESTAMPTZ,

    replaced_by_token_id BIGINT
        REFERENCES refresh_tokens(id) ON DELETE SET NULL,

    user_agent TEXT,
    ip_address INET,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CHECK (expires_at > created_at)
);

CREATE INDEX idx_refresh_account
    ON refresh_tokens(account_id);

CREATE INDEX idx_refresh_family
    ON refresh_tokens(family_id);

CREATE INDEX idx_refresh_expires_at
    ON refresh_tokens(expires_at);

COMMENT ON TABLE refresh_tokens IS
'Quản lý hash refresh token, thời hạn, luân chuyển và thu hồi phiên đăng nhập.';


-- =========================================================
-- TABLE 4: login_audit_logs
-- Chức năng: Ghi nhận lịch sử đăng nhập và các sự kiện
-- bảo mật để phục vụ kiểm tra, giám sát bởi Admin.
-- =========================================================

CREATE TABLE login_audit_logs (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    -- Xóa tài khoản thì log vẫn giữ lại nhưng gán account_id thành NULL để phục vụ điều tra
    account_id BIGINT
        REFERENCES accounts(id)
        ON DELETE SET NULL,

    event_type VARCHAR(40) NOT NULL,

    success BOOLEAN NOT NULL DEFAULT FALSE,

    ip_address INET,
    user_agent TEXT,

    details JSONB,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_account_created
    ON login_audit_logs(account_id, created_at DESC);

CREATE INDEX idx_audit_event_created
    ON login_audit_logs(event_type, created_at DESC);

COMMENT ON TABLE login_audit_logs IS
'Ghi nhật ký đăng nhập, xác minh OTP, đổi mật khẩu và các sự kiện bảo mật.';


-- =========================================================
-- TRIGGER: AUTO UPDATE accounts.updated_at
-- Chức năng: Tự động cập nhật thời gian sửa tài khoản.
-- =========================================================

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_accounts_updated_at
BEFORE UPDATE ON accounts
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();