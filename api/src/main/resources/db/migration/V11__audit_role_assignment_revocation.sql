-- Role assignment revocation is historical data and must not delete the original grant.

ALTER TABLE account_role_assignments
    ADD COLUMN revoked_by_account_id BIGINT REFERENCES accounts (id),
    ADD COLUMN revoked_at TIMESTAMPTZ,
    ADD COLUMN revocation_reason TEXT,
    ADD CONSTRAINT chk_account_role_assignment_period
        CHECK (effective_to IS NULL OR effective_to >= effective_from),
    ADD CONSTRAINT chk_account_role_assignment_revocation CHECK (
        (revoked_at IS NULL AND revoked_by_account_id IS NULL AND revocation_reason IS NULL)
        OR
        (revoked_at IS NOT NULL AND revoked_by_account_id IS NOT NULL AND revocation_reason IS NOT NULL)
    );

CREATE INDEX idx_role_assignments_role_active
    ON account_role_assignments (role_id)
    WHERE revoked_at IS NULL;
