-- Đề xuất cấp role được lưu riêng với assignment đã có hiệu lực.
CREATE EXTENSION IF NOT EXISTS btree_gist;

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
    updated_at TIMESTAMPTZ NOT NULL,
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
    )
);

-- Không cho hai request PENDING cùng account, role và scope có thời gian hiệu lực chồng lấn.
ALTER TABLE role_assignment_requests
    ADD CONSTRAINT excl_pending_role_assignment_request_overlap
    EXCLUDE USING GIST (
        account_id WITH =,
        role_id WITH =,
        scope_type WITH =,
        (COALESCE(organization_unit_id, 0::BIGINT)) WITH =,
        (COALESCE(work_location_id, 0::BIGINT)) WITH =,
        (DATERANGE(effective_from, effective_to + 1, '[)')) WITH &&
    ) WHERE (status = 'PENDING');

CREATE INDEX idx_role_assignment_requests_status_requested
    ON role_assignment_requests (status, requested_at DESC);

CREATE INDEX idx_role_assignment_requests_requester
    ON role_assignment_requests (requested_by_account_id, requested_at DESC);

CREATE INDEX idx_role_assignment_requests_account
    ON role_assignment_requests (account_id, requested_at DESC);
