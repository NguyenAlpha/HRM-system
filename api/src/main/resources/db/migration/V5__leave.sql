-- Đơn nghỉ phép và hạn mức phép năm.

CREATE TABLE leave_requests (
    id BIGSERIAL PRIMARY KEY,
    employee_id BIGINT NOT NULL REFERENCES employees (id),
    leave_type VARCHAR(30) NOT NULL,
    salary_treatment VARCHAR(30) NOT NULL,
    start_at TIMESTAMPTZ NOT NULL,
    end_at TIMESTAMPTZ NOT NULL,
    requested_minutes INTEGER NOT NULL,
    reason TEXT NOT NULL,
    attachment_url TEXT,
    status VARCHAR(20) NOT NULL,
    submitted_at TIMESTAMPTZ,
    reviewed_by_account_id BIGINT REFERENCES accounts (id),
    review_comment TEXT,
    reviewed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_leave_type
        CHECK (leave_type IN ('ANNUAL', 'SICK', 'MATERNITY', 'UNPAID', 'OTHER')),
    CONSTRAINT chk_leave_salary_treatment
        CHECK (salary_treatment IN ('EMPLOYER_PAID', 'SOCIAL_INSURANCE', 'UNPAID')),
    CONSTRAINT chk_leave_status
        CHECK (status IN ('DRAFT', 'PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT chk_leave_period
        CHECK (end_at > start_at AND requested_minutes > 0),
    CONSTRAINT excl_approved_leave_request_overlap
        EXCLUDE USING GIST (
            employee_id WITH =,
            TSTZRANGE(start_at, end_at, '[)') WITH &&
        ) WHERE (status = 'APPROVED')
);

CREATE INDEX idx_leave_requests_employee_status
    ON leave_requests (employee_id, status, start_at);

CREATE TABLE leave_entitlement_rules (
    id BIGSERIAL PRIMARY KEY,
    effective_from DATE NOT NULL,
    effective_to DATE,
    base_days INTEGER NOT NULL CHECK (base_days > 0),
    seniority_block_years INTEGER NOT NULL CHECK (seniority_block_years > 0),
    seniority_bonus_days INTEGER NOT NULL CHECK (seniority_bonus_days >= 0),
    source_reference TEXT NOT NULL,
    CONSTRAINT chk_leave_entitlement_rule_dates CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT excl_leave_entitlement_rules_overlap EXCLUDE USING GIST
        (DATERANGE(effective_from, effective_to + 1, '[)') WITH &&)
);

-- base_minutes and standard_day_minutes are snapshots: changing the rule or the employee's
-- shift later must not silently rewrite a year that has already been granted.
CREATE TABLE employee_leave_entitlements (
    id BIGSERIAL PRIMARY KEY,
    employee_id BIGINT NOT NULL REFERENCES employees (id),
    year SMALLINT NOT NULL,
    base_minutes INTEGER NOT NULL CHECK (base_minutes >= 0),
    carried_over_minutes INTEGER NOT NULL DEFAULT 0 CHECK (carried_over_minutes >= 0),
    adjustment_minutes INTEGER NOT NULL DEFAULT 0,
    adjustment_reason TEXT,
    standard_day_minutes INTEGER NOT NULL CHECK (standard_day_minutes > 0),
    rule_id BIGINT REFERENCES leave_entitlement_rules (id),
    created_by_account_id BIGINT NOT NULL REFERENCES accounts (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_employee_leave_entitlement UNIQUE (employee_id, year),
    CONSTRAINT chk_leave_entitlement_total CHECK (
        base_minutes + carried_over_minutes + adjustment_minutes >= 0
    )
);

CREATE INDEX idx_employee_leave_entitlements_year
    ON employee_leave_entitlements (year, employee_id);
