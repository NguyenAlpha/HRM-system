ALTER TABLE account_permission_overrides
    ADD COLUMN revoked_by_account_id BIGINT REFERENCES accounts (id),
    ADD COLUMN revoked_at TIMESTAMPTZ,
    ADD COLUMN revocation_reason TEXT;

ALTER TABLE account_permission_overrides
    ADD CONSTRAINT chk_permission_override_reason
        CHECK (btrim(reason) <> ''),
    ADD CONSTRAINT chk_permission_override_revocation_audit
        CHECK (
            (revoked_by_account_id IS NULL AND revoked_at IS NULL AND revocation_reason IS NULL)
            OR
            (revoked_by_account_id IS NOT NULL AND revoked_at IS NOT NULL AND btrim(revocation_reason) <> '')
        );

ALTER TABLE account_permission_overrides
    ADD CONSTRAINT excl_active_permission_override_overlap
    EXCLUDE USING GIST (
        account_role_assignment_id WITH =,
        permission_id WITH =,
        daterange(effective_from, effective_to, '[]') WITH &&
    )
    WHERE (revoked_at IS NULL);

CREATE INDEX idx_permission_overrides_permission
    ON account_permission_overrides (permission_id, effective_from, effective_to)
    WHERE revoked_at IS NULL;
