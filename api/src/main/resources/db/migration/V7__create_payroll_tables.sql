-- Kỳ lương tháng, phiếu lương và chi tiết khoản lương.

CREATE TABLE payroll_periods (
    id BIGSERIAL PRIMARY KEY,
    year SMALLINT NOT NULL,
    month SMALLINT NOT NULL CHECK (month BETWEEN 1 AND 12),
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    status VARCHAR(20) NOT NULL,
    calculated_by_account_id BIGINT REFERENCES accounts (id),
    calculated_at TIMESTAMPTZ,
    approved_by_account_id BIGINT REFERENCES accounts (id),
    approved_at TIMESTAMPTZ,
    paid_by_account_id BIGINT REFERENCES accounts (id),
    paid_at TIMESTAMPTZ,
    locked_by_account_id BIGINT REFERENCES accounts (id),
    locked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_payroll_periods_year_month UNIQUE (year, month),
    CONSTRAINT chk_payroll_periods_status
        CHECK (status IN ('DRAFT', 'CALCULATED', 'APPROVED', 'PAID', 'LOCKED', 'CANCELLED'))
);

CREATE TABLE payslips (
    id BIGSERIAL PRIMARY KEY,
    payroll_period_id BIGINT NOT NULL REFERENCES payroll_periods (id),
    employee_id BIGINT NOT NULL REFERENCES employees (id),
    employee_code_snapshot VARCHAR(30) NOT NULL,
    employee_name_snapshot VARCHAR(200) NOT NULL,
    work_location_snapshot VARCHAR(150) NOT NULL,
    organization_unit_snapshot VARCHAR(150) NOT NULL,
    contractual_basic_salary NUMERIC(15,2) NOT NULL CHECK (contractual_basic_salary >= 0),
    scheduled_work_minutes INTEGER NOT NULL CHECK (scheduled_work_minutes > 0),
    payable_work_minutes INTEGER NOT NULL CHECK (payable_work_minutes >= 0),
    approved_overtime_minutes INTEGER NOT NULL DEFAULT 0 CHECK (approved_overtime_minutes >= 0),
    basic_salary_pay NUMERIC(15,2) NOT NULL CHECK (basic_salary_pay >= 0),
    allowance_pay NUMERIC(15,2) NOT NULL DEFAULT 0 CHECK (allowance_pay >= 0),
    overtime_pay NUMERIC(15,2) NOT NULL DEFAULT 0 CHECK (overtime_pay >= 0),
    gross_pay NUMERIC(15,2) NOT NULL CHECK (gross_pay >= 0),
    net_pay NUMERIC(15,2) NOT NULL CHECK (net_pay >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_payslips_period_employee UNIQUE (payroll_period_id, employee_id),
    CONSTRAINT chk_payslips_total
        CHECK (gross_pay = basic_salary_pay + allowance_pay + overtime_pay AND net_pay = gross_pay)
);

CREATE INDEX idx_payslips_employee_period ON payslips (employee_id, payroll_period_id);

CREATE TABLE payslip_items (
    id BIGSERIAL PRIMARY KEY,
    payslip_id BIGINT NOT NULL REFERENCES payslips (id),
    component_type VARCHAR(20) NOT NULL,
    description VARCHAR(250) NOT NULL,
    quantity NUMERIC(12,4) NOT NULL DEFAULT 1,
    unit_rate NUMERIC(15,4) NOT NULL CHECK (unit_rate >= 0),
    multiplier NUMERIC(8,4) NOT NULL DEFAULT 1 CHECK (multiplier > 0),
    amount NUMERIC(15,2) NOT NULL CHECK (amount >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_payslip_items_type CHECK (component_type IN ('BASIC_SALARY', 'ALLOWANCE', 'OVERTIME'))
);

CREATE INDEX idx_payslip_items_payslip ON payslip_items (payslip_id);
