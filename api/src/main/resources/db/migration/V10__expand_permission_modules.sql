-- Keep the database constraint aligned with PermissionModule and the seeded catalog.

ALTER TABLE permissions
    DROP CONSTRAINT chk_permissions_module;

ALTER TABLE permissions
    ADD CONSTRAINT chk_permissions_module CHECK (
        module IN (
            'EMPLOYEE',
            'ACCOUNT',
            'ORGANIZATION',
            'REQUEST',
            'ATTENDANCE',
            'PAYROLL',
            'RBAC',
            'REPORT'
        )
    );

CREATE UNIQUE INDEX uq_accounts_email_lower
    ON accounts (LOWER(email));
