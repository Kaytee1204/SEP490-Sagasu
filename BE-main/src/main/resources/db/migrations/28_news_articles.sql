CREATE TABLE IF NOT EXISTS news_article (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    summary VARCHAR(500) NOT NULL,
    content TEXT NOT NULL,
    category VARCHAR(40) NOT NULL DEFAULT 'UPDATE',
    cover_image_key VARCHAR(500),
    cover_image_url VARCHAR(1000),
    published BOOLEAN NOT NULL DEFAULT TRUE,
    author_id BIGINT REFERENCES accounts(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_news_article_published_created
    ON news_article (published, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_news_article_category_created
    ON news_article (category, created_at DESC);
