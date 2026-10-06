-- A later tax year needs its own reviewed policy before payroll can be calculated.
UPDATE payroll_tax_rules SET effective_to = '2026-12-31' WHERE effective_from = '2026-01-01' AND effective_to IS NULL;
UPDATE payroll_insurance_rules SET effective_to = '2026-12-31' WHERE effective_from = '2026-07-01' AND effective_to IS NULL;

ALTER TABLE employee_tax_dependents ADD CONSTRAINT excl_employee_tax_dependent_overlap
    EXCLUDE USING GIST (employee_id WITH =, lower(full_name) WITH =,
        DATERANGE(effective_from, effective_to + 1, '[)') WITH &&);
