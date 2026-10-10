-- Hồ sơ khấu trừ, quy tắc thuế/bảo hiểm theo thời gian, kỳ lương, phiếu lương và chi tiết khoản lương.

CREATE TABLE employee_payroll_profiles (
    id BIGSERIAL PRIMARY KEY,
    employee_id BIGINT NOT NULL REFERENCES employees(id),
    effective_from DATE NOT NULL,
    effective_to DATE,
    tax_resident BOOLEAN NOT NULL,
    social_insurance BOOLEAN NOT NULL,
    health_insurance BOOLEAN NOT NULL,
    unemployment_insurance BOOLEAN NOT NULL,
    insurance_salary NUMERIC(15,2) NOT NULL CHECK (insurance_salary >= 0),
    wage_region SMALLINT NOT NULL CHECK (wage_region BETWEEN 1 AND 4),
    created_by_account_id BIGINT NOT NULL REFERENCES accounts(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_employee_payroll_profile_dates CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT excl_employee_payroll_profiles_overlap EXCLUDE USING GIST
        (employee_id WITH =, DATERANGE(effective_from, effective_to + 1, '[)') WITH &&)
);
CREATE INDEX idx_employee_payroll_profiles_effective ON employee_payroll_profiles(employee_id, effective_from, effective_to);

CREATE TABLE employee_tax_dependents (
    id BIGSERIAL PRIMARY KEY,
    employee_id BIGINT NOT NULL REFERENCES employees(id),
    full_name VARCHAR(200) NOT NULL,
    identifier VARCHAR(50),
    effective_from DATE NOT NULL,
    effective_to DATE,
    created_by_account_id BIGINT NOT NULL REFERENCES accounts(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_employee_tax_dependent_dates CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT excl_employee_tax_dependent_overlap
        EXCLUDE USING GIST (employee_id WITH =, lower(full_name) WITH =,
            DATERANGE(effective_from, effective_to + 1, '[)') WITH &&)
);
CREATE INDEX idx_employee_tax_dependents_effective ON employee_tax_dependents(employee_id, effective_from, effective_to);

CREATE TABLE payroll_tax_rules (
    id BIGSERIAL PRIMARY KEY,
    effective_from DATE NOT NULL,
    effective_to DATE,
    personal_deduction NUMERIC(15,2) NOT NULL CHECK (personal_deduction >= 0),
    dependent_deduction NUMERIC(15,2) NOT NULL CHECK (dependent_deduction >= 0),
    source_reference TEXT NOT NULL,
    CONSTRAINT chk_payroll_tax_rule_dates CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT excl_payroll_tax_rules_overlap EXCLUDE USING GIST
        (DATERANGE(effective_from, effective_to + 1, '[)') WITH &&)
);
CREATE TABLE payroll_tax_brackets (
    tax_rule_id BIGINT NOT NULL REFERENCES payroll_tax_rules(id),
    lower_bound NUMERIC(15,2) NOT NULL CHECK (lower_bound >= 0),
    upper_bound NUMERIC(15,2),
    rate NUMERIC(6,5) NOT NULL CHECK (rate >= 0 AND rate <= 1),
    PRIMARY KEY (tax_rule_id, lower_bound),
    CONSTRAINT chk_payroll_tax_bracket_bounds CHECK (upper_bound IS NULL OR upper_bound > lower_bound)
);

CREATE TABLE payroll_insurance_rules (
    id BIGSERIAL PRIMARY KEY,
    effective_from DATE NOT NULL,
    effective_to DATE,
    social_rate NUMERIC(6,5) NOT NULL,
    health_rate NUMERIC(6,5) NOT NULL,
    unemployment_rate NUMERIC(6,5) NOT NULL,
    social_health_cap NUMERIC(15,2) NOT NULL,
    unemployment_cap_multiplier INTEGER NOT NULL,
    region_1_minimum NUMERIC(15,2) NOT NULL,
    region_2_minimum NUMERIC(15,2) NOT NULL,
    region_3_minimum NUMERIC(15,2) NOT NULL,
    region_4_minimum NUMERIC(15,2) NOT NULL,
    source_reference TEXT NOT NULL,
    CONSTRAINT chk_payroll_insurance_rule_dates CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT excl_payroll_insurance_rules_overlap EXCLUDE USING GIST
        (DATERANGE(effective_from, effective_to + 1, '[)') WITH &&)
);

CREATE TABLE payroll_periods (
    id BIGSERIAL PRIMARY KEY,
    year SMALLINT NOT NULL,
    month SMALLINT NOT NULL CHECK (month BETWEEN 1 AND 12),
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    tax_payment_date DATE,
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
        CHECK (status IN ('DRAFT', 'CALCULATED', 'APPROVED', 'PAID', 'LOCKED', 'CANCELLED')),
    CONSTRAINT chk_payroll_period_dates CHECK (period_end >= period_start),
    CONSTRAINT chk_payroll_approver_separation CHECK (
        calculated_by_account_id IS NULL
        OR approved_by_account_id IS NULL
        OR calculated_by_account_id <> approved_by_account_id
    )
);

CREATE TABLE payslips (
    id BIGSERIAL PRIMARY KEY,
    payroll_period_id BIGINT NOT NULL REFERENCES payroll_periods (id),
    employee_id BIGINT NOT NULL REFERENCES employees (id),
    employee_code_snapshot VARCHAR(30) NOT NULL,
    employee_name_snapshot VARCHAR(200) NOT NULL,
    work_location_snapshot VARCHAR(150) NOT NULL,
    organization_unit_snapshot VARCHAR(150) NOT NULL,
    position_snapshot VARCHAR(150) NOT NULL,
    contractual_base_salary NUMERIC(15,2) NOT NULL CHECK (contractual_base_salary >= 0),
    scheduled_work_minutes INTEGER NOT NULL CHECK (scheduled_work_minutes > 0),
    payable_work_minutes INTEGER NOT NULL CHECK (payable_work_minutes >= 0),
    approved_overtime_minutes INTEGER NOT NULL DEFAULT 0 CHECK (approved_overtime_minutes >= 0),
    base_salary_pay NUMERIC(15,2) NOT NULL CHECK (base_salary_pay >= 0),
    allowance_pay NUMERIC(15,2) NOT NULL DEFAULT 0 CHECK (allowance_pay >= 0),
    position_allowance_pay NUMERIC(15,2) NOT NULL DEFAULT 0,
    seniority_allowance_pay NUMERIC(15,2) NOT NULL DEFAULT 0,
    overtime_pay NUMERIC(15,2) NOT NULL DEFAULT 0 CHECK (overtime_pay >= 0),
    tax_exempt_overtime_pay NUMERIC(15,2) NOT NULL DEFAULT 0
        CHECK (tax_exempt_overtime_pay >= 0 AND tax_exempt_overtime_pay <= overtime_pay),
    gross_pay NUMERIC(15,2) NOT NULL CHECK (gross_pay >= 0),
    insurance_salary_base NUMERIC(15,2) NOT NULL DEFAULT 0,
    unemployment_insurance_base NUMERIC(15,2) NOT NULL DEFAULT 0
        CHECK (unemployment_insurance_base >= 0),
    employee_social_insurance NUMERIC(15,2) NOT NULL DEFAULT 0,
    employee_health_insurance NUMERIC(15,2) NOT NULL DEFAULT 0,
    employee_unemployment_insurance NUMERIC(15,2) NOT NULL DEFAULT 0,
    taxable_income NUMERIC(15,2) NOT NULL DEFAULT 0,
    personal_income_tax NUMERIC(15,2) NOT NULL DEFAULT 0,
    net_pay NUMERIC(15,2) NOT NULL CHECK (net_pay >= 0),
    tax_rule_id BIGINT REFERENCES payroll_tax_rules(id),
    insurance_rule_id BIGINT REFERENCES payroll_insurance_rules(id),
    payroll_profile_id BIGINT REFERENCES employee_payroll_profiles(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_payslips_period_employee UNIQUE (payroll_period_id, employee_id),
    CONSTRAINT chk_payslip_position_allowance_nonnegative
        CHECK (position_allowance_pay >= 0),
    CONSTRAINT chk_payslip_seniority_allowance_nonnegative
        CHECK (seniority_allowance_pay >= 0),
    CONSTRAINT chk_payslip_allowance_total CHECK (
        allowance_pay = position_allowance_pay + seniority_allowance_pay
    ),
    CONSTRAINT chk_payslip_total CHECK (
        gross_pay = base_salary_pay + allowance_pay + overtime_pay
        AND net_pay = gross_pay - employee_social_insurance - employee_health_insurance
            - employee_unemployment_insurance - personal_income_tax
        AND insurance_salary_base >= 0 AND taxable_income >= 0
        AND employee_social_insurance >= 0 AND employee_health_insurance >= 0
        AND employee_unemployment_insurance >= 0 AND personal_income_tax >= 0
    )
);

CREATE INDEX idx_payslips_employee_period ON payslips (employee_id, payroll_period_id);

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

CREATE TABLE payslip_items (
    id BIGSERIAL PRIMARY KEY,
    payslip_id BIGINT NOT NULL REFERENCES payslips (id),
    component_type VARCHAR(30) NOT NULL,
    component_code VARCHAR(50) NOT NULL,
    description VARCHAR(250) NOT NULL,
    quantity NUMERIC(12,4) NOT NULL DEFAULT 1,
    unit_rate NUMERIC(15,4) NOT NULL CHECK (unit_rate >= 0),
    multiplier NUMERIC(8,4) NOT NULL DEFAULT 1 CHECK (multiplier > 0),
    amount NUMERIC(15,2) NOT NULL CHECK (amount >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_payslip_item_type CHECK (
        component_type IN (
            'BASE_SALARY',
            'POSITION_ALLOWANCE',
            'SENIORITY_ALLOWANCE',
            'OVERTIME'
        )
    )
);

CREATE INDEX idx_payslip_items_payslip ON payslip_items (payslip_id);

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
