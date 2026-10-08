-- Annual leave had no quota: an approved ANNUAL request fed its minutes straight into payroll.
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

-- Bộ luật Lao động 2019: 12 ngày cơ bản, cứ đủ 5 năm làm việc được cộng thêm 1 ngày.
INSERT INTO leave_entitlement_rules (
    effective_from, effective_to, base_days, seniority_block_years, seniority_bonus_days, source_reference
)
VALUES (
    '2026-01-01', NULL, 12, 5, 1,
    'Bộ luật Lao động 2019, Điều 113 khoản 1 và Điều 114'
);
