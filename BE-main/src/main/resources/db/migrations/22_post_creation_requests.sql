CREATE TABLE IF NOT EXISTS post_creation_request (
    account_id BIGINT NOT NULL REFERENCES accounts(id),
    request_id UUID NOT NULL,
    post_id BIGINT NOT NULL REFERENCES post(post_id),
    PRIMARY KEY (account_id, request_id)
);
