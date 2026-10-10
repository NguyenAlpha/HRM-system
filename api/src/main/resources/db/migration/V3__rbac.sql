-- Quyền, vai trò, phân quyền theo phạm vi (RBAC) và đề xuất cấp vai trò.
-- Danh mục permission, role và mapping mặc định do PermissionSeeder, RoleSeeder và
-- RolePermissionSeeder tạo khi ứng dụng khởi động, không seed trong migration.

CREATE TABLE permissions (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(100) NOT NULL UNIQUE,
    -- Tên ngắn, dễ đọc dành cho giao diện; code vẫn là định danh kỹ thuật ổn định.
    name VARCHAR(150) NOT NULL,
    module VARCHAR(30) NOT NULL,
    description TEXT NOT NULL,
    -- Phân biệt permission có thể gán cho custom role và permission dành riêng cho system role.
    assignment_policy VARCHAR(20) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Keep the database constraint aligned with PermissionModule and the seeded catalog.
    CONSTRAINT chk_permissions_module CHECK (
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
    ),
    CONSTRAINT chk_permissions_assignment_policy
        CHECK (assignment_policy IN ('DELEGABLE', 'SYSTEM_ONLY'))
);

CREATE TABLE roles (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    description TEXT,
    is_system BOOLEAN NOT NULL DEFAULT false,
    -- Chính sách xác định workflow được phép dùng để cấp từng role cho account.
    grant_policy VARCHAR(30) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ,
    CONSTRAINT chk_roles_grant_policy
        CHECK (grant_policy IN ('AUTO', 'HR_ASSIGNABLE', 'OWNER_APPROVAL', 'SYSTEM_ONLY'))
);

CREATE TABLE role_permissions (
    role_id BIGINT NOT NULL REFERENCES roles (id),
    permission_id BIGINT NOT NULL REFERENCES permissions (id),
    created_by_account_id BIGINT REFERENCES accounts (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (role_id, permission_id)
);

-- Role assignment revocation is historical data and must not delete the original grant.
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
    revoked_by_account_id BIGINT REFERENCES accounts (id),
    revoked_at TIMESTAMPTZ,
    revocation_reason TEXT,
    CONSTRAINT chk_account_role_assignments_scope CHECK (
        (scope_type IN ('SELF', 'COMPANY') AND organization_unit_id IS NULL AND work_location_id IS NULL)
        OR (scope_type = 'ORG_UNIT' AND organization_unit_id IS NOT NULL AND work_location_id IS NULL)
        OR (scope_type = 'LOCATION' AND organization_unit_id IS NULL AND work_location_id IS NOT NULL)
    ),
    CONSTRAINT chk_account_role_assignment_period
        CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT chk_account_role_assignment_revocation CHECK (
        (revoked_at IS NULL AND revoked_by_account_id IS NULL AND revocation_reason IS NULL)
        OR
        (revoked_at IS NOT NULL AND revoked_by_account_id IS NOT NULL AND revocation_reason IS NOT NULL)
    )
);

CREATE INDEX idx_role_assignments_account_period
    ON account_role_assignments (account_id, effective_from, effective_to);

CREATE INDEX idx_role_assignments_role_active
    ON account_role_assignments (role_id)
    WHERE revoked_at IS NULL;

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
    revoked_by_account_id BIGINT REFERENCES accounts (id),
    revoked_at TIMESTAMPTZ,
    revocation_reason TEXT,
    CONSTRAINT chk_account_permission_overrides_effect CHECK (effect IN ('GRANT', 'REVOKE')),
    CONSTRAINT chk_permission_override_period
        CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT chk_permission_override_reason
        CHECK (btrim(reason) <> ''),
    CONSTRAINT chk_permission_override_revocation_audit
        CHECK (
            (revoked_by_account_id IS NULL AND revoked_at IS NULL AND revocation_reason IS NULL)
            OR
            (revoked_by_account_id IS NOT NULL AND revoked_at IS NOT NULL AND btrim(revocation_reason) <> '')
        ),
    CONSTRAINT excl_active_permission_override_overlap
        EXCLUDE USING GIST (
            account_role_assignment_id WITH =,
            permission_id WITH =,
            daterange(effective_from, effective_to, '[]') WITH &&
        )
        WHERE (revoked_at IS NULL)
);

CREATE INDEX idx_permission_overrides_assignment
    ON account_permission_overrides (account_role_assignment_id, effective_from, effective_to);

CREATE INDEX idx_permission_overrides_permission
    ON account_permission_overrides (permission_id, effective_from, effective_to)
    WHERE revoked_at IS NULL;

-- Đề xuất cấp role được lưu riêng với assignment đã có hiệu lực.
CREATE TABLE role_assignment_requests (
    id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL REFERENCES accounts (id),
    role_id BIGINT NOT NULL REFERENCES roles (id),
    scope_type VARCHAR(20) NOT NULL,
    organization_unit_id BIGINT REFERENCES organization_units (id),
    work_location_id BIGINT REFERENCES work_locations (id),
    effective_from DATE NOT NULL,
    effective_to DATE,
    reason TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    requested_by_account_id BIGINT NOT NULL REFERENCES accounts (id),
    requested_at TIMESTAMPTZ NOT NULL,
    reviewed_by_account_id BIGINT REFERENCES accounts (id),
    reviewed_at TIMESTAMPTZ,
    review_note TEXT,
    cancelled_by_account_id BIGINT REFERENCES accounts (id),
    cancelled_at TIMESTAMPTZ,
    cancellation_reason TEXT,
    account_role_assignment_id BIGINT UNIQUE REFERENCES account_role_assignments (id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_role_assignment_requests_scope CHECK (
        (scope_type IN ('SELF', 'COMPANY') AND organization_unit_id IS NULL AND work_location_id IS NULL)
        OR (scope_type = 'ORG_UNIT' AND organization_unit_id IS NOT NULL AND work_location_id IS NULL)
        OR (scope_type = 'LOCATION' AND organization_unit_id IS NULL AND work_location_id IS NOT NULL)
    ),
    CONSTRAINT chk_role_assignment_requests_period
        CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT chk_role_assignment_requests_reason
        CHECK (LENGTH(BTRIM(reason)) > 0),
    CONSTRAINT chk_role_assignment_requests_status
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT chk_role_assignment_requests_resolution CHECK (
        (
            status = 'PENDING'
            AND reviewed_by_account_id IS NULL
            AND reviewed_at IS NULL
            AND review_note IS NULL
            AND cancelled_by_account_id IS NULL
            AND cancelled_at IS NULL
            AND cancellation_reason IS NULL
            AND account_role_assignment_id IS NULL
        )
        OR (
            status = 'APPROVED'
            AND reviewed_by_account_id IS NOT NULL
            AND reviewed_at IS NOT NULL
            AND cancelled_by_account_id IS NULL
            AND cancelled_at IS NULL
            AND cancellation_reason IS NULL
            AND account_role_assignment_id IS NOT NULL
        )
        OR (
            status = 'REJECTED'
            AND reviewed_by_account_id IS NOT NULL
            AND reviewed_at IS NOT NULL
            AND review_note IS NOT NULL
            AND LENGTH(BTRIM(review_note)) > 0
            AND cancelled_by_account_id IS NULL
            AND cancelled_at IS NULL
            AND cancellation_reason IS NULL
            AND account_role_assignment_id IS NULL
        )
        OR (
            status = 'CANCELLED'
            AND reviewed_by_account_id IS NULL
            AND reviewed_at IS NULL
            AND review_note IS NULL
            AND cancelled_by_account_id IS NOT NULL
            AND cancelled_at IS NOT NULL
            AND cancellation_reason IS NOT NULL
            AND LENGTH(BTRIM(cancellation_reason)) > 0
            AND account_role_assignment_id IS NULL
        )
    ),
    -- Không cho hai request PENDING cùng account, role và scope có thời gian hiệu lực chồng lấn.
    CONSTRAINT excl_pending_role_assignment_request_overlap
        EXCLUDE USING GIST (
            account_id WITH =,
            role_id WITH =,
            scope_type WITH =,
            (COALESCE(organization_unit_id, 0::BIGINT)) WITH =,
            (COALESCE(work_location_id, 0::BIGINT)) WITH =,
            (DATERANGE(effective_from, effective_to + 1, '[)')) WITH &&
        ) WHERE (status = 'PENDING')
);

CREATE INDEX idx_role_assignment_requests_status_requested
    ON role_assignment_requests (status, requested_at DESC);

CREATE INDEX idx_role_assignment_requests_requester
    ON role_assignment_requests (requested_by_account_id, requested_at DESC);

CREATE INDEX idx_role_assignment_requests_account
    ON role_assignment_requests (account_id, requested_at DESC);
