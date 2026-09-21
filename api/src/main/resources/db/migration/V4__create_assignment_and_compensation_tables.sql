-- Lịch sử phân công và lương cơ bản/phụ cấp của nhân viên.

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
        CHECK (employment_type IN ('FULL_TIME', 'PART_TIME', 'TEMPORARY'))
);

CREATE INDEX idx_assignments_employee_period ON employee_assignments (employee_id, effective_from, effective_to);
CREATE INDEX idx_assignments_location_period ON employee_assignments (work_location_id, effective_from, effective_to);
CREATE INDEX idx_assignments_unit_period ON employee_assignments (organization_unit_id, effective_from, effective_to);

CREATE TABLE employee_compensations (
    id BIGSERIAL PRIMARY KEY,
    employee_id BIGINT NOT NULL REFERENCES employees (id),
    component_type VARCHAR(20) NOT NULL,
    component_code VARCHAR(30) NOT NULL,
    component_name VARCHAR(150) NOT NULL,
    monthly_amount NUMERIC(15,2) NOT NULL CHECK (monthly_amount >= 0),
    effective_from DATE NOT NULL,
    effective_to DATE,
    approved_by_account_id BIGINT NOT NULL REFERENCES accounts (id),
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_employee_compensations_type CHECK (component_type IN ('BASIC_SALARY', 'ALLOWANCE'))
);

CREATE INDEX idx_compensations_employee_period ON employee_compensations (employee_id, effective_from, effective_to);
