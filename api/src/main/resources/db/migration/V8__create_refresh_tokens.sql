-- Refresh tokens are opaque credentials. Only their SHA-256 hashes are stored.

CREATE TABLE refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    token_hash CHAR(64) NOT NULL UNIQUE,
    account_id BIGINT NOT NULL REFERENCES accounts (id),
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_refresh_tokens_account_id ON refresh_tokens (account_id);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens (expires_at);
