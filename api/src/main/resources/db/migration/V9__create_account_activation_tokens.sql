-- Pending accounts do not have a password until the invited user activates the account.

ALTER TABLE accounts
    ALTER COLUMN password_hash DROP NOT NULL;

ALTER TABLE accounts
    ADD CONSTRAINT chk_accounts_password_after_activation
        CHECK (status = 'PENDING' OR password_hash IS NOT NULL);

-- Activation tokens are opaque credentials. Only their SHA-256 hashes are stored.

CREATE TABLE account_activation_tokens (
    id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL REFERENCES accounts (id),
    token_hash CHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    created_by_account_id BIGINT NOT NULL REFERENCES accounts (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_account_activation_token_state
        CHECK (used_at IS NULL OR revoked_at IS NULL)
);

CREATE INDEX idx_account_activation_tokens_account_id
    ON account_activation_tokens (account_id);

CREATE INDEX idx_account_activation_tokens_expires_at
    ON account_activation_tokens (expires_at);

CREATE UNIQUE INDEX uq_account_activation_tokens_active_account
    ON account_activation_tokens (account_id)
    WHERE used_at IS NULL AND revoked_at IS NULL;
