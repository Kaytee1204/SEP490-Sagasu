-- =============================================================================
-- SAGASU — LOST & FOUND MANAGEMENT SYSTEM
-- COMPLETE DATABASE SCHEMA DDL (47 TABLES ORGANIZED INTO 8 DESIGN MODULES)
-- Database Engine: PostgreSQL 15+
-- =============================================================================

BEGIN;

-- =============================================================================
-- MODULE 01: IDENTITY, AUTHENTICATION, AUDIT & NOTIFICATIONS (6 TABLES)
-- Core Entity: accounts
-- Tables: accounts, refresh_tokens, otp_challenges, login_audit_logs,
--         notification, notification_preference
-- =============================================================================

-- 1. accounts
CREATE TABLE IF NOT EXISTS accounts (
    id BIGSERIAL PRIMARY KEY,
    full_name VARCHAR(150) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    phone VARCHAR(30),
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('USER', 'APPROVER', 'MANAGER', 'ADMIN')),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DELETED')),
    must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
    failed_login_count INT NOT NULL DEFAULT 0,
    locked_until TIMESTAMPTZ,
    created_by BIGINT REFERENCES accounts(id) ON DELETE SET NULL,
    valid_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 2. refresh_tokens
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    token_hash VARCHAR(128) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 3. otp_challenges
CREATE TABLE IF NOT EXISTS otp_challenges (
    id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    purpose VARCHAR(40) NOT NULL CHECK (purpose IN ('LOGIN', 'FORGOT_PASSWORD', 'CHANGE_PASSWORD', 'ACCOUNT_UNLOCK')),
    otp_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    verified_at TIMESTAMPTZ,
    consumed_at TIMESTAMPTZ,
    attempt_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 4. login_audit_logs
CREATE TABLE IF NOT EXISTS login_audit_logs (
    id BIGSERIAL PRIMARY KEY,
    account_id BIGINT REFERENCES accounts(id) ON DELETE SET NULL,
    event_type VARCHAR(40) NOT NULL,
    success BOOLEAN NOT NULL DEFAULT FALSE,
    ip_address INET,
    user_agent TEXT,
    details JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 5. notification
CREATE TABLE IF NOT EXISTS notification (
    notification_id BIGSERIAL PRIMARY KEY,
    recipient_id BIGINT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    type VARCHAR(60) NOT NULL,
    channel VARCHAR(20) NOT NULL DEFAULT 'IN_APP' CHECK (channel IN ('IN_APP', 'BOTH')),
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    target_url VARCHAR(500),
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 6. notification_preference
CREATE TABLE IF NOT EXISTS notification_preference (
    account_id BIGINT PRIMARY KEY REFERENCES accounts(id) ON DELETE CASCADE,
    in_app_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    email_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    browser_push_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);


-- =============================================================================
-- MODULE 02: ITEM CATEGORIES, LOST & FOUND POSTS & SAVED POSTS (5 TABLES)
-- Core Entity: post
-- Tables: category, post, post_image, post_creation_request, saved_post
-- =============================================================================

-- 7. category
CREATE TABLE IF NOT EXISTS category (
    category_id BIGSERIAL PRIMARY KEY,
    name VARCHAR(120) NOT NULL UNIQUE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 8. post
CREATE TABLE IF NOT EXISTS post (
    post_id BIGSERIAL PRIMARY KEY,
    account_id BIGINT REFERENCES accounts(id),
    created_by BIGINT NOT NULL REFERENCES accounts(id),
    category_id BIGINT NOT NULL REFERENCES category(category_id),
    post_type VARCHAR(10) NOT NULL CHECK (post_type IN ('LOST', 'FOUND')),
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    location_text TEXT,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    event_at TIMESTAMPTZ NOT NULL,
    private_identifiers TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'RESERVED', 'RETURNED', 'CLOSED', 'ARCHIVED')),
    custody_mode VARCHAR(20) NOT NULL DEFAULT 'P2P' CHECK (custody_mode IN ('P2P', 'OFFICE')),
    moderation_status VARCHAR(20) NOT NULL DEFAULT 'VISIBLE' CHECK (moderation_status IN ('PENDING', 'VISIBLE', 'REJECTED', 'HIDDEN')),
    moderated_by BIGINT REFERENCES accounts(id),
    moderation_note TEXT,
    handover_requested_at TIMESTAMPTZ,
    search_document TSVECTOR,
    deleted_at TIMESTAMPTZ,
    delete_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 9. post_image
CREATE TABLE IF NOT EXISTS post_image (
    post_image_id BIGSERIAL PRIMARY KEY,
    post_id BIGINT NOT NULL REFERENCES post(post_id) ON DELETE CASCADE,
    storage_key VARCHAR(512) UNIQUE,
    image_url TEXT,
    mime_type VARCHAR(100),
    size_bytes BIGINT,
    width INT,
    height INT,
    content_hash VARCHAR(64),
    sort_order INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (post_id, sort_order)
);

-- 10. post_creation_request
CREATE TABLE IF NOT EXISTS post_creation_request (
    account_id BIGINT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    request_id VARCHAR(80) NOT NULL,
    post_id BIGINT NOT NULL REFERENCES post(post_id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (account_id, request_id)
);

-- 11. saved_post
CREATE TABLE IF NOT EXISTS saved_post (
    account_id BIGINT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    post_id BIGINT NOT NULL REFERENCES post(post_id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (account_id, post_id)
);


-- =============================================================================
-- MODULE 03: OFFICE INTAKE, WAREHOUSE STORAGE & CUSTODY TRANSFERS (5 TABLES)
-- Core Entity: item_receipt
-- Tables: storage_location, item_receipt, item_receipt_image,
--         office_transfer, office_intake_appointment
-- =============================================================================

-- 12. storage_location
CREATE TABLE IF NOT EXISTS storage_location (
    storage_location_id BIGSERIAL PRIMARY KEY,
    parent_location_id BIGINT REFERENCES storage_location(storage_location_id),
    code VARCHAR(40) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    capacity INT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 13. item_receipt
CREATE TABLE IF NOT EXISTS item_receipt (
    item_receipt_id BIGSERIAL PRIMARY KEY,
    receipt_code VARCHAR(40) NOT NULL UNIQUE,
    source VARCHAR(16) NOT NULL DEFAULT 'WALK_IN' CHECK (source IN ('WALK_IN', 'EXISTING_POST')),
    category_id BIGINT NOT NULL REFERENCES category(category_id),
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    condition_at_receipt TEXT,
    found_location TEXT NOT NULL,
    found_at TIMESTAMPTZ NOT NULL,
    private_identifiers TEXT,
    note TEXT,
    recorded_by BIGINT NOT NULL REFERENCES accounts(id),
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    submitted_at TIMESTAMPTZ,
    status VARCHAR(10) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'SUBMITTED', 'IN_STORAGE', 'RETURNED', 'REJECTED')),
    review_status VARCHAR(12) NOT NULL DEFAULT 'PENDING' CHECK (review_status IN ('PENDING', 'NEEDS_INFO', 'ACCEPTED', 'REJECTED')),
    review_note TEXT,
    reviewed_by BIGINT REFERENCES accounts(id),
    reviewed_at TIMESTAMPTZ,
    post_id BIGINT UNIQUE REFERENCES post(post_id),
    storage_location_id BIGINT REFERENCES storage_location(storage_location_id),
    received_by BIGINT REFERENCES accounts(id),
    received_at TIMESTAMPTZ,
    finder_name VARCHAR(150),
    finder_contact VARCHAR(255),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 14. item_receipt_image
CREATE TABLE IF NOT EXISTS item_receipt_image (
    item_receipt_image_id BIGSERIAL PRIMARY KEY,
    item_receipt_id BIGINT NOT NULL REFERENCES item_receipt(item_receipt_id) ON DELETE CASCADE,
    storage_key VARCHAR(512) UNIQUE,
    image_url TEXT,
    mime_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    is_sensitive BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 15. office_transfer
CREATE TABLE IF NOT EXISTS office_transfer (
    transfer_id BIGSERIAL PRIMARY KEY,
    post_id BIGINT NOT NULL REFERENCES post(post_id) ON DELETE RESTRICT,
    requested_by BIGINT NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'RECEIVED', 'REJECTED', 'CANCELLED')),
    requested_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_by BIGINT REFERENCES accounts(id) ON DELETE RESTRICT,
    processed_at TIMESTAMPTZ,
    item_receipt_id BIGINT REFERENCES item_receipt(item_receipt_id) ON DELETE RESTRICT,
    reason TEXT,
    reconciliation_note TEXT,
    version BIGINT NOT NULL DEFAULT 0
);

-- 16. office_intake_appointment
CREATE TABLE IF NOT EXISTS office_intake_appointment (
    appointment_id BIGSERIAL PRIMARY KEY,
    transfer_id BIGINT NOT NULL REFERENCES office_transfer(transfer_id) ON DELETE CASCADE,
    scheduled_by BIGINT NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    scheduled_at TIMESTAMPTZ NOT NULL,
    location VARCHAR(1000) NOT NULL,
    note TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED' CHECK (status IN ('SCHEDULED', 'CANCELLED', 'COMPLETED')),
    email_status VARCHAR(20) NOT NULL DEFAULT 'UNSENT' CHECK (email_status IN ('UNSENT', 'SENT', 'FAILED')),
    response_status VARCHAR(20) NOT NULL DEFAULT 'UNSENT' CHECK (response_status IN ('UNSENT', 'PENDING', 'CONFIRMED', 'DECLINED', 'EXPIRED')),
    response_token_hash CHAR(64),
    response_deadline TIMESTAMPTZ,
    responded_at TIMESTAMPTZ,
    cancellation_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);


-- =============================================================================
-- MODULE 04: OWNERSHIP CLAIMS & VERIFICATION QUESTIONNAIRES (7 TABLES)
-- Core Entity: claim
-- Tables: claim_question_template, claim_question, claim, claim_answer,
--         claim_evidence, claim_activity, office_claim_supplement_image
-- =============================================================================

-- 17. claim_question_template
CREATE TABLE IF NOT EXISTS claim_question_template (
    template_id BIGSERIAL PRIMARY KEY,
    category_name VARCHAR(120) NOT NULL UNIQUE,
    template_code VARCHAR(80) NOT NULL UNIQUE,
    version INT NOT NULL DEFAULT 1,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 18. claim_question
CREATE TABLE IF NOT EXISTS claim_question (
    question_id BIGSERIAL PRIMARY KEY,
    template_id BIGINT NOT NULL REFERENCES claim_question_template(template_id) ON DELETE CASCADE,
    question_code VARCHAR(80) NOT NULL,
    section_title VARCHAR(160) NOT NULL,
    question_label VARCHAR(500) NOT NULL,
    hint_text VARCHAR(500),
    required BOOLEAN NOT NULL DEFAULT TRUE,
    max_length INT NOT NULL DEFAULT 500,
    sort_order INT NOT NULL,
    UNIQUE (template_id, question_code)
);

-- 19. claim
CREATE TABLE IF NOT EXISTS claim (
    claim_id BIGSERIAL PRIMARY KEY,
    post_id BIGINT NOT NULL REFERENCES post(post_id) ON DELETE RESTRICT,
    claimant_id BIGINT REFERENCES accounts(id) ON DELETE RESTRICT,
    created_by BIGINT REFERENCES accounts(id) ON DELETE RESTRICT,
    claim_type VARCHAR(16) NOT NULL DEFAULT 'P2P' CHECK (claim_type IN ('P2P', 'OFFICE')),
    submission_channel VARCHAR(16) NOT NULL DEFAULT 'ONLINE' CHECK (submission_channel IN ('ONLINE', 'COUNTER')),
    claimant_name VARCHAR(150),
    claimant_email VARCHAR(255),
    claimant_phone VARCHAR(40),
    item_receipt_id BIGINT REFERENCES item_receipt(item_receipt_id) ON DELETE RESTRICT,
    description TEXT NOT NULL,
    evidence_note TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'NEEDS_INFO', 'APPROVED', 'REJECTED', 'CANCELLED', 'COMPLETED', 'CLOSED')),
    close_reason VARCHAR(60),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 20. claim_answer
CREATE TABLE IF NOT EXISTS claim_answer (
    claim_answer_id BIGSERIAL PRIMARY KEY,
    claim_id BIGINT NOT NULL REFERENCES claim(claim_id) ON DELETE CASCADE,
    question_code VARCHAR(80) NOT NULL,
    section_title VARCHAR(160) NOT NULL,
    question_label VARCHAR(500) NOT NULL,
    answer_text TEXT NOT NULL,
    required BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 21. claim_evidence
CREATE TABLE IF NOT EXISTS claim_evidence (
    claim_evidence_id BIGSERIAL PRIMARY KEY,
    claim_id BIGINT NOT NULL REFERENCES claim(claim_id) ON DELETE CASCADE,
    storage_key VARCHAR(512) NOT NULL UNIQUE,
    original_name VARCHAR(255),
    mime_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    sort_order INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 22. claim_activity
CREATE TABLE IF NOT EXISTS claim_activity (
    claim_activity_id BIGSERIAL PRIMARY KEY,
    claim_id BIGINT NOT NULL REFERENCES claim(claim_id) ON DELETE CASCADE,
    actor_id BIGINT NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    action VARCHAR(32) NOT NULL,
    from_status VARCHAR(20),
    to_status VARCHAR(20) NOT NULL,
    reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 23. office_claim_supplement_image
CREATE TABLE IF NOT EXISTS office_claim_supplement_image (
    image_id BIGSERIAL PRIMARY KEY,
    activity_id BIGINT NOT NULL REFERENCES claim_activity(claim_activity_id) ON DELETE RESTRICT,
    storage_key VARCHAR(512) NOT NULL UNIQUE,
    original_name VARCHAR(255),
    content_hash VARCHAR(64) NOT NULL,
    sort_order INTEGER NOT NULL CHECK (sort_order BETWEEN 0 AND 4)
);


-- =============================================================================
-- MODULE 05: REAL-TIME P2P CHAT & LOCATION SHARING (4 TABLES)
-- Core Entity: chat_conversation
-- Tables: chat_conversation, chat_participant, chat_message, chat_attachment
-- =============================================================================

-- 24. chat_conversation
CREATE TABLE IF NOT EXISTS chat_conversation (
    conversation_id BIGSERIAL PRIMARY KEY,
    post_id BIGINT NOT NULL REFERENCES post(post_id) ON DELETE RESTRICT,
    claimant_id BIGINT NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    latest_claim_id BIGINT NOT NULL REFERENCES claim(claim_id) ON DELETE RESTRICT,
    last_message_sequence BIGINT NOT NULL DEFAULT 0,
    last_activity_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (post_id, claimant_id)
);

-- 25. chat_participant
CREATE TABLE IF NOT EXISTS chat_participant (
    conversation_id BIGINT NOT NULL REFERENCES chat_conversation(conversation_id) ON DELETE CASCADE,
    account_id BIGINT NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    participant_role VARCHAR(16) NOT NULL CHECK (participant_role IN ('FINDER', 'CLAIMANT')),
    last_read_sequence BIGINT NOT NULL DEFAULT 0,
    last_read_at TIMESTAMPTZ,
    joined_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (conversation_id, account_id)
);

-- 26. chat_message
CREATE TABLE IF NOT EXISTS chat_message (
    message_id BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT NOT NULL REFERENCES chat_conversation(conversation_id) ON DELETE CASCADE,
    sender_id BIGINT NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    sequence_no BIGINT NOT NULL,
    client_message_id UUID NOT NULL,
    message_type VARCHAR(16) NOT NULL CHECK (message_type IN ('TEXT', 'ATTACHMENT', 'MIXED', 'LOCATION')),
    body TEXT,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    accuracy_meters DOUBLE PRECISION,
    location_label VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    read_at TIMESTAMPTZ,
    FOREIGN KEY (conversation_id, sender_id) REFERENCES chat_participant(conversation_id, account_id),
    UNIQUE (conversation_id, sequence_no),
    UNIQUE (conversation_id, sender_id, client_message_id)
);

-- 27. chat_attachment
CREATE TABLE IF NOT EXISTS chat_attachment (
    attachment_id BIGSERIAL PRIMARY KEY,
    message_id BIGINT NOT NULL REFERENCES chat_message(message_id) ON DELETE CASCADE,
    storage_key VARCHAR(512) NOT NULL UNIQUE,
    original_name VARCHAR(255) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    sort_order INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);


-- =============================================================================
-- MODULE 06: REPORT SUBMISSION, REASONS & CONTEXT SNAPSHOTS (6 TABLES)
-- (Created before handover_issue link tables so foreign keys resolve cleanly)
-- Core Entity: report
-- Tables: report_reason, report_case, report, report_evidence,
--         report_snapshot, report_asset
-- =============================================================================

-- 28. report_reason
CREATE TABLE IF NOT EXISTS report_reason (
    code VARCHAR(64) PRIMARY KEY,
    target_group VARCHAR(24) NOT NULL CHECK (target_group IN ('POST', 'USER', 'CLAIM', 'MESSAGE', 'HANDOVER_ISSUE')),
    label VARCHAR(200) NOT NULL CHECK (length(btrim(label)) > 0),
    description_required BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order SMALLINT NOT NULL
);

-- 29. report_case
CREATE TABLE IF NOT EXISTS report_case (
    case_id BIGSERIAL PRIMARY KEY,
    title VARCHAR(200) NOT NULL CHECK (length(btrim(title)) > 0),
    owner_id BIGINT NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 30. report
CREATE TABLE IF NOT EXISTS report (
    report_id BIGSERIAL PRIMARY KEY,
    reporter_id BIGINT NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    kind VARCHAR(20) NOT NULL DEFAULT 'GENERAL' CHECK (kind IN ('GENERAL', 'HANDOVER_ISSUE')),
    reason_code VARCHAR(64) NOT NULL REFERENCES report_reason(code),
    reason_label_snapshot VARCHAR(200) NOT NULL,
    case_id BIGINT REFERENCES report_case(case_id) ON DELETE RESTRICT,
    post_id BIGINT REFERENCES post(post_id) ON DELETE RESTRICT,
    claim_id BIGINT REFERENCES claim(claim_id) ON DELETE RESTRICT,
    message_id BIGINT REFERENCES chat_message(message_id) ON DELETE RESTRICT,
    reported_account_id BIGINT REFERENCES accounts(id) ON DELETE RESTRICT,
    description TEXT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'IN_REVIEW', 'RESOLVED', 'REJECTED')),
    assigned_to BIGINT REFERENCES accounts(id),
    assigned_at TIMESTAMPTZ,
    resolved_by BIGINT REFERENCES accounts(id),
    resolution_note TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (num_nonnulls(post_id, claim_id, message_id, reported_account_id) = 1)
);

-- 31. report_evidence
CREATE TABLE IF NOT EXISTS report_evidence (
    evidence_id BIGSERIAL PRIMARY KEY,
    report_id BIGINT NOT NULL REFERENCES report(report_id) ON DELETE CASCADE,
    storage_key VARCHAR(512) NOT NULL UNIQUE,
    original_name VARCHAR(255),
    content_hash VARCHAR(64) NOT NULL,
    sort_order INT NOT NULL
);

-- 32. report_snapshot
CREATE TABLE IF NOT EXISTS report_snapshot (
    report_id BIGINT PRIMARY KEY REFERENCES report(report_id) ON DELETE CASCADE,
    captured_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    capture_source VARCHAR(16) NOT NULL CHECK (capture_source IN ('SUBMISSION', 'FIRST_REVIEW')),
    payload JSONB NOT NULL
);

-- 33. report_asset
CREATE TABLE IF NOT EXISTS report_asset (
    asset_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    report_id BIGINT NOT NULL REFERENCES report(report_id) ON DELETE CASCADE,
    storage_key TEXT NOT NULL,
    original_name TEXT,
    source VARCHAR(40) NOT NULL,
    UNIQUE (report_id, storage_key)
);


-- =============================================================================
-- MODULE 07: APPOINTMENTS, COUNTER VERIFICATION & ITEM HANDOVER (8 TABLES)
-- Core Entities: p2p_handover & office_handover
-- Tables: p2p_handover, p2p_handover_evidence, p2p_handover_issue,
--         appointment, office_claim_verification, office_handover,
--         office_handover_evidence, office_handover_issue
-- =============================================================================

-- 34. p2p_handover
CREATE TABLE IF NOT EXISTS p2p_handover (
    handover_id BIGSERIAL PRIMARY KEY,
    claim_id BIGINT NOT NULL UNIQUE REFERENCES claim(claim_id) ON DELETE RESTRICT,
    recorded_by BIGINT NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    note TEXT NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'WAITING_CONFIRMATION' CHECK (status IN ('WAITING_CONFIRMATION', 'COMPLETED', 'DISPUTED', 'CANCELLED')),
    confirmed_by BIGINT REFERENCES accounts(id) ON DELETE RESTRICT,
    confirmed_at TIMESTAMPTZ,
    confirmation_note TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 35. p2p_handover_evidence
CREATE TABLE IF NOT EXISTS p2p_handover_evidence (
    evidence_id BIGSERIAL PRIMARY KEY,
    handover_id BIGINT NOT NULL REFERENCES p2p_handover(handover_id) ON DELETE CASCADE,
    storage_key VARCHAR(512) NOT NULL UNIQUE,
    original_name VARCHAR(255),
    content_hash VARCHAR(64) NOT NULL,
    sort_order INT NOT NULL
);

-- 36. p2p_handover_issue
CREATE TABLE IF NOT EXISTS p2p_handover_issue (
    report_id BIGINT PRIMARY KEY REFERENCES report(report_id) ON DELETE CASCADE,
    handover_id BIGINT NOT NULL REFERENCES p2p_handover(handover_id) ON DELETE CASCADE
);

-- 37. appointment
CREATE TABLE IF NOT EXISTS appointment (
    appointment_id BIGSERIAL PRIMARY KEY,
    claim_id BIGINT NOT NULL REFERENCES claim(claim_id) ON DELETE CASCADE,
    scheduled_by BIGINT NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    scheduled_at TIMESTAMPTZ NOT NULL,
    location VARCHAR(1000) NOT NULL,
    note TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED' CHECK (status IN ('SCHEDULED', 'CANCELLED', 'COMPLETED')),
    email_status VARCHAR(20) NOT NULL DEFAULT 'UNSENT' CHECK (email_status IN ('UNSENT', 'SENT', 'FAILED')),
    response_status VARCHAR(20) NOT NULL DEFAULT 'UNSENT' CHECK (response_status IN ('UNSENT', 'PENDING', 'CONFIRMED', 'DECLINED', 'EXPIRED')),
    response_token_hash CHAR(64),
    response_deadline TIMESTAMPTZ,
    responded_at TIMESTAMPTZ,
    cancellation_reason TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 38. office_claim_verification
CREATE TABLE IF NOT EXISTS office_claim_verification (
    verification_id BIGSERIAL PRIMARY KEY,
    claim_id BIGINT NOT NULL REFERENCES claim(claim_id) ON DELETE RESTRICT,
    appointment_id BIGINT NOT NULL REFERENCES appointment(appointment_id) ON DELETE RESTRICT,
    verified_by BIGINT NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    result VARCHAR(16) NOT NULL CHECK (result IN ('PASSED', 'FAILED')),
    note TEXT NOT NULL,
    verified_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 39. office_handover
CREATE TABLE IF NOT EXISTS office_handover (
    handover_id BIGSERIAL PRIMARY KEY,
    claim_id BIGINT NOT NULL UNIQUE REFERENCES claim(claim_id) ON DELETE RESTRICT,
    item_receipt_id BIGINT NOT NULL UNIQUE REFERENCES item_receipt(item_receipt_id) ON DELETE RESTRICT,
    verification_id BIGINT NOT NULL REFERENCES office_claim_verification(verification_id) ON DELETE RESTRICT,
    recorded_by BIGINT NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    recipient_id BIGINT REFERENCES accounts(id) ON DELETE RESTRICT,
    note TEXT NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'WAITING_CONFIRMATION' CHECK (status IN ('WAITING_CONFIRMATION', 'COMPLETED', 'DISPUTED')),
    confirmed_by BIGINT REFERENCES accounts(id) ON DELETE RESTRICT,
    confirmed_at TIMESTAMPTZ,
    confirmation_note TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 40. office_handover_evidence
CREATE TABLE IF NOT EXISTS office_handover_evidence (
    evidence_id BIGSERIAL PRIMARY KEY,
    handover_id BIGINT NOT NULL REFERENCES office_handover(handover_id) ON DELETE CASCADE,
    storage_key VARCHAR(512) NOT NULL UNIQUE,
    original_name VARCHAR(255),
    content_hash VARCHAR(64) NOT NULL,
    sort_order INT NOT NULL
);

-- 41. office_handover_issue
CREATE TABLE IF NOT EXISTS office_handover_issue (
    report_id BIGINT PRIMARY KEY REFERENCES report(report_id) ON DELETE CASCADE,
    handover_id BIGINT NOT NULL REFERENCES office_handover(handover_id) ON DELETE CASCADE
);


-- =============================================================================
-- MODULE 08: REPORT INVESTIGATION, INQUIRIES & DISPUTE RESOLUTION (6 TABLES)
-- Core Entity: report (Investigation Lifecycle)
-- Tables: report_activity, report_inquiry, report_reply_evidence,
--         report_decision, report_hold, report_recipient
-- =============================================================================

-- 42. report_activity
CREATE TABLE IF NOT EXISTS report_activity (
    activity_id BIGSERIAL PRIMARY KEY,
    report_id BIGINT NOT NULL REFERENCES report(report_id) ON DELETE CASCADE,
    actor_id BIGINT NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    action VARCHAR(32) NOT NULL,
    from_status VARCHAR(16),
    to_status VARCHAR(16) NOT NULL,
    public_note TEXT,
    internal_note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 43. report_inquiry
CREATE TABLE IF NOT EXISTS report_inquiry (
    inquiry_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    report_id BIGINT NOT NULL REFERENCES report(report_id) ON DELETE CASCADE,
    recipient_id BIGINT NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    created_by BIGINT NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    question TEXT NOT NULL CHECK (length(btrim(question)) BETWEEN 1 AND 5000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    response TEXT,
    responded_at TIMESTAMPTZ,
    CHECK ((response IS NULL) = (responded_at IS NULL))
);

-- 44. report_reply_evidence
CREATE TABLE IF NOT EXISTS report_reply_evidence (
    evidence_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    inquiry_id BIGINT NOT NULL REFERENCES report_inquiry(inquiry_id) ON DELETE CASCADE,
    storage_key TEXT NOT NULL UNIQUE,
    original_name TEXT NOT NULL,
    content_hash CHAR(64) NOT NULL
);

-- 45. report_decision
CREATE TABLE IF NOT EXISTS report_decision (
    decision_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    report_id BIGINT NOT NULL REFERENCES report(report_id) ON DELETE CASCADE,
    actor_id BIGINT NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    action VARCHAR(32) NOT NULL,
    conclusion TEXT NOT NULL,
    basis TEXT NOT NULL,
    before_state JSONB NOT NULL DEFAULT '{}',
    after_state JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 46. report_hold
CREATE TABLE IF NOT EXISTS report_hold (
    report_id BIGINT PRIMARY KEY REFERENCES report(report_id) ON DELETE CASCADE,
    post_id BIGINT NOT NULL REFERENCES post(post_id) ON DELETE RESTRICT,
    actor_id BIGINT NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    reason TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    released_at TIMESTAMPTZ
);

-- 47. report_recipient
CREATE TABLE IF NOT EXISTS report_recipient (
    report_id BIGINT NOT NULL REFERENCES report(report_id) ON DELETE CASCADE,
    account_id BIGINT NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    PRIMARY KEY (report_id, account_id)
);

COMMIT;
