CREATE TABLE IF NOT EXISTS saved_post (
    account_id BIGINT NOT NULL,
    post_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (account_id, post_id),
    CONSTRAINT saved_post_account_fk FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE CASCADE,
    CONSTRAINT saved_post_post_fk FOREIGN KEY (post_id) REFERENCES post(post_id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_saved_post_account_created ON saved_post (account_id, created_at DESC, post_id DESC);
