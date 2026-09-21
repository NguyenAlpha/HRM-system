-- Quyền, vai trò và phân quyền theo phạm vi (RBAC).

CREATE TABLE permissions (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(100) NOT NULL UNIQUE,
    module VARCHAR(30) NOT NULL,
    description TEXT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_permissions_module
        CHECK (module IN ('EMPLOYEE', 'REQUEST', 'ATTENDANCE', 'PAYROLL', 'RBAC', 'REPORT'))
);

CREATE TABLE roles (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    description TEXT,
    is_system BOOLEAN NOT NULL DEFAULT false,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);

CREATE TABLE role_permissions (
    role_id BIGINT NOT NULL REFERENCES roles (id),
    permission_id BIGINT NOT NULL REFERENCES permissions (id),
    created_by_account_id BIGINT REFERENCES accounts (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE account_role_assignments (
    id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL REFERENCES accounts (id),
    role_id BIGINT NOT NULL REFERENCES roles (id),
    scope_type VARCHAR(20) NOT NULL,
    organization_unit_id BIGINT REFERENCES organization_units (id),
    work_location_id BIGINT REFERENCES work_locations (id),
    effective_from DATE NOT NULL,
    effective_to DATE,
    granted_by_account_id BIGINT NOT NULL REFERENCES accounts (id),
    reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_account_role_assignments_scope CHECK (
        (scope_type IN ('SELF', 'COMPANY') AND organization_unit_id IS NULL AND work_location_id IS NULL)
        OR (scope_type = 'ORG_UNIT' AND organization_unit_id IS NOT NULL AND work_location_id IS NULL)
        OR (scope_type = 'LOCATION' AND organization_unit_id IS NULL AND work_location_id IS NOT NULL)
    )
);

CREATE INDEX idx_role_assignments_account_period
    ON account_role_assignments (account_id, effective_from, effective_to);

CREATE TABLE account_permission_overrides (
    id BIGSERIAL PRIMARY KEY,
    account_role_assignment_id BIGINT NOT NULL REFERENCES account_role_assignments (id),
    permission_id BIGINT NOT NULL REFERENCES permissions (id),
    effect VARCHAR(10) NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE,
    reason TEXT NOT NULL,
    granted_by_account_id BIGINT NOT NULL REFERENCES accounts (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_account_permission_overrides_effect CHECK (effect IN ('GRANT', 'REVOKE'))
);

CREATE INDEX idx_permission_overrides_assignment
    ON account_permission_overrides (account_role_assignment_id, effective_from, effective_to);
