-- Preserve historical payslips. New deductions are zero on existing snapshots.
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
    CONSTRAINT chk_employee_tax_dependent_dates CHECK (effective_to IS NULL OR effective_to >= effective_from)
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

INSERT INTO payroll_tax_rules(effective_from, effective_to, personal_deduction, dependent_deduction, source_reference)
VALUES ('2026-01-01', NULL, 15500000, 6200000,
    'Law 109/2025/QH15, resident employment income, tax year 2026');
INSERT INTO payroll_tax_brackets(tax_rule_id, lower_bound, upper_bound, rate)
SELECT id, lower_bound, upper_bound, rate FROM payroll_tax_rules,
    (VALUES (0::numeric, 10000000::numeric, 0.05::numeric),
            (10000000, 30000000, 0.10),
            (30000000, 60000000, 0.20),
            (60000000, 100000000, 0.30),
            (100000000, NULL, 0.35)) AS brackets(lower_bound, upper_bound, rate);

INSERT INTO payroll_insurance_rules(effective_from, effective_to, social_rate, health_rate,
    unemployment_rate, social_health_cap, unemployment_cap_multiplier, region_1_minimum,
    region_2_minimum, region_3_minimum, region_4_minimum, source_reference)
VALUES
    ('2026-01-01', '2026-06-30', 0.08, 0.015, 0.01, 46800000, 20,
     5310000, 4730000, 4140000, 3700000,
     'BHXH reference wage 2.34m; Decree 293/2025/ND-CP; Employment Law 2025'),
    ('2026-07-01', NULL, 0.08, 0.015, 0.01, 50600000, 20,
     5310000, 4730000, 4140000, 3700000,
     'Decree 161/2026/ND-CP; Decree 293/2025/ND-CP; Employment Law 2025');

ALTER TABLE payroll_periods ADD COLUMN tax_payment_date DATE;
ALTER TABLE payslips
    ADD COLUMN insurance_salary_base NUMERIC(15,2) NOT NULL DEFAULT 0,
    ADD COLUMN employee_social_insurance NUMERIC(15,2) NOT NULL DEFAULT 0,
    ADD COLUMN employee_health_insurance NUMERIC(15,2) NOT NULL DEFAULT 0,
    ADD COLUMN employee_unemployment_insurance NUMERIC(15,2) NOT NULL DEFAULT 0,
    ADD COLUMN taxable_income NUMERIC(15,2) NOT NULL DEFAULT 0,
    ADD COLUMN personal_income_tax NUMERIC(15,2) NOT NULL DEFAULT 0,
    ADD COLUMN tax_rule_id BIGINT REFERENCES payroll_tax_rules(id),
    ADD COLUMN insurance_rule_id BIGINT REFERENCES payroll_insurance_rules(id),
    ADD COLUMN payroll_profile_id BIGINT REFERENCES employee_payroll_profiles(id);
ALTER TABLE payslips DROP CONSTRAINT chk_payslip_total;
ALTER TABLE payslips ADD CONSTRAINT chk_payslip_total CHECK (
    gross_pay = base_salary_pay + allowance_pay + overtime_pay
    AND net_pay = gross_pay - employee_social_insurance - employee_health_insurance
        - employee_unemployment_insurance - personal_income_tax
    AND insurance_salary_base >= 0 AND taxable_income >= 0
    AND employee_social_insurance >= 0 AND employee_health_insurance >= 0
    AND employee_unemployment_insurance >= 0 AND personal_income_tax >= 0
);
