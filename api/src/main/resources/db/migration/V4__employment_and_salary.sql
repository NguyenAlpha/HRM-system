-- Mã nhân viên, lịch sử phân công, lương cơ bản và chính sách phụ cấp.

-- Allocate employee codes independently for each position prefix.
CREATE TABLE employee_code_counters (
    prefix VARCHAR(10) PRIMARY KEY,
    next_number BIGINT NOT NULL CHECK (next_number > 0)
);

-- Bảo vệ lịch sử phân công chính của nhân sự khỏi kỳ ngày sai hoặc chồng lấn.
CREATE TABLE employee_assignments (
    id BIGSERIAL PRIMARY KEY,
    employee_id BIGINT NOT NULL REFERENCES employees (id),
    organization_unit_id BIGINT NOT NULL REFERENCES organization_units (id),
    work_location_id BIGINT NOT NULL REFERENCES work_locations (id),
    position_id BIGINT NOT NULL REFERENCES job_positions (id),
    shift_id BIGINT REFERENCES work_shifts (id),
    manager_employee_id BIGINT REFERENCES employees (id),
    employment_type VARCHAR(20) NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE,
    is_primary BOOLEAN NOT NULL DEFAULT true,
    reason TEXT,
    created_by_account_id BIGINT NOT NULL REFERENCES accounts (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_employee_assignments_employment_type
        CHECK (employment_type IN ('FULL_TIME', 'PART_TIME', 'TEMPORARY')),
    CONSTRAINT chk_employee_assignments_period
        CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT excl_employee_primary_assignment_overlap
        EXCLUDE USING GIST (
            employee_id WITH =,
            (DATERANGE(effective_from, effective_to + 1, '[)')) WITH &&
        ) WHERE (is_primary)
);

CREATE INDEX idx_assignments_employee_period ON employee_assignments (employee_id, effective_from, effective_to);
CREATE INDEX idx_assignments_location_period ON employee_assignments (work_location_id, effective_from, effective_to);
CREATE INDEX idx_assignments_unit_period ON employee_assignments (organization_unit_id, effective_from, effective_to);

CREATE TABLE employee_salary_history (
    id BIGSERIAL PRIMARY KEY,
    employee_id BIGINT NOT NULL REFERENCES employees (id),
    base_salary NUMERIC(15,2) NOT NULL CHECK (base_salary > 0),
    effective_from DATE NOT NULL,
    effective_to DATE,
    approved_by_account_id BIGINT NOT NULL REFERENCES accounts (id),
    reason VARCHAR(250),
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_salary_period
        CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT excl_employee_salary_history_overlap
        EXCLUDE USING GIST (
            employee_id WITH =,
            DATERANGE(effective_from, effective_to + 1, '[)') WITH &&
        )
);

CREATE INDEX idx_salary_history_employee_period
    ON employee_salary_history (employee_id, effective_from, effective_to);

CREATE TABLE position_allowance_rules (
    id BIGSERIAL PRIMARY KEY,
    job_position_id BIGINT NOT NULL REFERENCES job_positions (id),
    monthly_amount NUMERIC(15,2) NOT NULL CHECK (monthly_amount >= 0),
    effective_from DATE NOT NULL,
    effective_to DATE,
    approved_by_account_id BIGINT NOT NULL REFERENCES accounts (id),
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_position_allowance_period
        CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT excl_position_allowance_rule_overlap
        EXCLUDE USING GIST (
            job_position_id WITH =,
            DATERANGE(effective_from, effective_to + 1, '[)') WITH &&
        )
);

CREATE INDEX idx_position_allowance_period
    ON position_allowance_rules (job_position_id, effective_from, effective_to);

CREATE TABLE seniority_allowance_rules (
    id BIGSERIAL PRIMARY KEY,
    min_years INTEGER NOT NULL,
    max_years INTEGER,
    percentage NUMERIC(8,4) NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE,
    approved_by_account_id BIGINT NOT NULL REFERENCES accounts (id),
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_seniority_range CHECK (
        min_years >= 0
        AND (max_years IS NULL OR max_years > min_years)
        AND percentage >= 0
    ),
    CONSTRAINT chk_seniority_allowance_period
        CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT excl_seniority_allowance_rule_overlap
        EXCLUDE USING GIST (
            INT4RANGE(min_years, max_years, '[)') WITH &&,
            DATERANGE(effective_from, effective_to + 1, '[)') WITH &&
        )
);

CREATE INDEX idx_seniority_rules_period
    ON seniority_allowance_rules (effective_from, effective_to, min_years, max_years);
