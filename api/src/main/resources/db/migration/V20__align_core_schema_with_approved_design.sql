-- Align the deployed database with docs/Fix_database/DATABASE_SCHEMA.md.
--
-- This is an additive, data-preserving migration. The legacy employee_compensations
-- and employee_requests tables remain available until the API is fully migrated to
-- the specialized salary and leave models. Existing RBAC workflow extensions are
-- intentionally retained as well.

CREATE EXTENSION IF NOT EXISTS btree_gist;

-- -----------------------------------------------------------------------------
-- Employee lifecycle
-- -----------------------------------------------------------------------------

ALTER TABLE employees
    ADD COLUMN seniority_start_date DATE;

UPDATE employees
SET seniority_start_date = hire_date
WHERE seniority_start_date IS NULL;

CREATE FUNCTION set_employee_seniority_start_date()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.seniority_start_date IS NULL THEN
        NEW.seniority_start_date := NEW.hire_date;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_employees_seniority_start_date
BEFORE INSERT OR UPDATE OF hire_date, seniority_start_date ON employees
FOR EACH ROW
EXECUTE FUNCTION set_employee_seniority_start_date();

ALTER TABLE employees
    ALTER COLUMN seniority_start_date SET NOT NULL,
    ADD CONSTRAINT chk_employees_dates CHECK (
        seniority_start_date >= hire_date
        AND (termination_date IS NULL OR termination_date >= hire_date)
    );

ALTER TABLE roles
    ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT true;

-- -----------------------------------------------------------------------------
-- Salary history and allowance policy
-- -----------------------------------------------------------------------------

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

-- Migrate only the legacy component that has an unambiguous target meaning.
INSERT INTO employee_salary_history (
    employee_id,
    base_salary,
    effective_from,
    effective_to,
    approved_by_account_id,
    reason,
    note,
    created_at
)
SELECT
    employee_id,
    monthly_amount,
    effective_from,
    effective_to,
    approved_by_account_id,
    component_name,
    note,
    created_at
FROM employee_compensations
WHERE component_type = 'BASIC_SALARY'
  AND monthly_amount > 0;

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

-- -----------------------------------------------------------------------------
-- Specialized leave requests
-- -----------------------------------------------------------------------------

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

-- Keep historical leave data while leaving resignation workflow data untouched.
INSERT INTO leave_requests (
    employee_id,
    leave_type,
    salary_treatment,
    start_at,
    end_at,
    requested_minutes,
    reason,
    attachment_url,
    status,
    submitted_at,
    reviewed_by_account_id,
    review_comment,
    reviewed_at,
    created_at,
    updated_at
)
SELECT
    employee_id,
    leave_type,
    CASE
        WHEN leave_type = 'MATERNITY' THEN 'SOCIAL_INSURANCE'
        WHEN is_paid_leave THEN 'EMPLOYER_PAID'
        ELSE 'UNPAID'
    END,
    start_date::TIMESTAMP AT TIME ZONE 'Asia/Ho_Chi_Minh',
    (end_date + 1)::TIMESTAMP AT TIME ZONE 'Asia/Ho_Chi_Minh',
    GREATEST(1, CEIL(total_days * 480)::INTEGER),
    reason,
    attachment_url,
    CASE WHEN status = 'COMPLETED' THEN 'APPROVED' ELSE status END,
    submitted_at,
    reviewed_by_account_id,
    review_comment,
    reviewed_at,
    created_at,
    updated_at
FROM employee_requests
WHERE request_type = 'LEAVE';

-- -----------------------------------------------------------------------------
-- Attendance snapshots and leave linkage
-- -----------------------------------------------------------------------------

ALTER TABLE attendance_records
    DROP CONSTRAINT chk_attendance_records_status;

ALTER TABLE attendance_records
    ALTER COLUMN status TYPE VARCHAR(30),
    ADD COLUMN leave_request_id BIGINT REFERENCES leave_requests (id),
    ADD COLUMN scheduled_minutes INTEGER;

UPDATE attendance_records
SET status = 'UNAUTHORIZED_ABSENCE'
WHERE status = 'ABSENT';

UPDATE attendance_records attendance
SET scheduled_minutes = shift.standard_work_minutes
FROM work_shifts shift
WHERE shift.id = attendance.shift_id
  AND attendance.scheduled_minutes IS NULL;

CREATE FUNCTION set_attendance_scheduled_minutes()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.scheduled_minutes IS NULL THEN
        SELECT standard_work_minutes
        INTO NEW.scheduled_minutes
        FROM work_shifts
        WHERE id = NEW.shift_id;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_attendance_scheduled_minutes
BEFORE INSERT OR UPDATE OF shift_id, scheduled_minutes ON attendance_records
FOR EACH ROW
EXECUTE FUNCTION set_attendance_scheduled_minutes();

ALTER TABLE attendance_records
    ALTER COLUMN scheduled_minutes SET NOT NULL,
    ADD CONSTRAINT chk_attendance_scheduled_minutes CHECK (scheduled_minutes > 0),
    ADD CONSTRAINT chk_attendance_records_status CHECK (
        status IN (
            'PRESENT',
            'PAID_LEAVE',
            'UNPAID_LEAVE',
            'MATERNITY_LEAVE',
            'SICK_LEAVE',
            'UNAUTHORIZED_ABSENCE',
            'HOLIDAY',
            'MISSING_PUNCH'
        )
    );

-- -----------------------------------------------------------------------------
-- Payroll snapshots
-- -----------------------------------------------------------------------------

ALTER TABLE payroll_periods
    ADD CONSTRAINT chk_payroll_period_dates CHECK (period_end >= period_start),
    ADD CONSTRAINT chk_payroll_approver_separation CHECK (
        calculated_by_account_id IS NULL
        OR approved_by_account_id IS NULL
        OR calculated_by_account_id <> approved_by_account_id
    );

ALTER TABLE payslips
    RENAME COLUMN contractual_basic_salary TO contractual_base_salary;

ALTER TABLE payslips
    RENAME COLUMN basic_salary_pay TO base_salary_pay;

ALTER TABLE payslips
    ADD COLUMN position_snapshot VARCHAR(150),
    ADD COLUMN position_allowance_pay NUMERIC(15,2) NOT NULL DEFAULT 0,
    ADD COLUMN seniority_allowance_pay NUMERIC(15,2) NOT NULL DEFAULT 0;

UPDATE payslips payslip
SET position_snapshot = COALESCE(
    (
        SELECT position.title
        FROM payroll_periods period
        JOIN employee_assignments assignment
          ON assignment.employee_id = payslip.employee_id
         AND assignment.effective_from <= period.period_end
         AND (assignment.effective_to IS NULL OR assignment.effective_to >= period.period_end)
        JOIN job_positions position ON position.id = assignment.position_id
        WHERE period.id = payslip.payroll_period_id
        ORDER BY assignment.is_primary DESC, assignment.effective_from DESC
        LIMIT 1
    ),
    'Chưa xác định'
),
position_allowance_pay = allowance_pay,
seniority_allowance_pay = 0;

CREATE FUNCTION complete_payslip_snapshot()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.position_snapshot IS NULL OR BTRIM(NEW.position_snapshot) = '' THEN
        SELECT position.title
        INTO NEW.position_snapshot
        FROM payroll_periods period
        JOIN employee_assignments assignment
          ON assignment.employee_id = NEW.employee_id
         AND assignment.effective_from <= period.period_end
         AND (assignment.effective_to IS NULL OR assignment.effective_to >= period.period_end)
        JOIN job_positions position ON position.id = assignment.position_id
        WHERE period.id = NEW.payroll_period_id
        ORDER BY assignment.is_primary DESC, assignment.effective_from DESC
        LIMIT 1;

        NEW.position_snapshot := COALESCE(NEW.position_snapshot, 'Chưa xác định');
    END IF;

    -- Compatibility for the current API, which still supplies one allowance total.
    IF NEW.allowance_pay <> NEW.position_allowance_pay + NEW.seniority_allowance_pay THEN
        IF NEW.position_allowance_pay = 0 AND NEW.seniority_allowance_pay = 0 THEN
            NEW.position_allowance_pay := NEW.allowance_pay;
        ELSE
            NEW.allowance_pay := NEW.position_allowance_pay + NEW.seniority_allowance_pay;
        END IF;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_complete_payslip_snapshot
BEFORE INSERT OR UPDATE ON payslips
FOR EACH ROW
EXECUTE FUNCTION complete_payslip_snapshot();

ALTER TABLE payslips
    ALTER COLUMN position_snapshot SET NOT NULL,
    DROP CONSTRAINT chk_payslips_total,
    ADD CONSTRAINT chk_payslip_position_allowance_nonnegative
        CHECK (position_allowance_pay >= 0),
    ADD CONSTRAINT chk_payslip_seniority_allowance_nonnegative
        CHECK (seniority_allowance_pay >= 0),
    ADD CONSTRAINT chk_payslip_allowance_total CHECK (
        allowance_pay = position_allowance_pay + seniority_allowance_pay
    ),
    ADD CONSTRAINT chk_payslip_total CHECK (
        gross_pay = base_salary_pay + allowance_pay + overtime_pay
        AND net_pay = gross_pay
    );

ALTER TABLE payslip_items
    DROP CONSTRAINT chk_payslip_items_type;

ALTER TABLE payslip_items
    ALTER COLUMN component_type TYPE VARCHAR(30),
    ADD COLUMN component_code VARCHAR(50);

UPDATE payslip_items
SET component_type = CASE component_type
        WHEN 'BASIC_SALARY' THEN 'BASE_SALARY'
        WHEN 'ALLOWANCE' THEN 'POSITION_ALLOWANCE'
        ELSE component_type
    END;

UPDATE payslip_items
SET component_code = component_type
WHERE component_code IS NULL;

CREATE FUNCTION complete_payslip_item_snapshot()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.component_code IS NULL OR BTRIM(NEW.component_code) = '' THEN
        NEW.component_code := NEW.component_type;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_complete_payslip_item_snapshot
BEFORE INSERT OR UPDATE OF component_type, component_code ON payslip_items
FOR EACH ROW
EXECUTE FUNCTION complete_payslip_item_snapshot();

ALTER TABLE payslip_items
    ALTER COLUMN component_code SET NOT NULL,
    ADD CONSTRAINT chk_payslip_item_type CHECK (
        component_type IN (
            'BASE_SALARY',
            'POSITION_ALLOWANCE',
            'SENIORITY_ALLOWANCE',
            'OVERTIME'
        )
    );

-- Period checks that were documented but missing from the legacy RBAC tables.
ALTER TABLE account_permission_overrides
    ADD CONSTRAINT chk_account_permission_override_period
        CHECK (effective_to IS NULL OR effective_to >= effective_from);
