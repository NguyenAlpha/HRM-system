ALTER TABLE payslips ADD COLUMN unemployment_insurance_base NUMERIC(15,2) NOT NULL DEFAULT 0
    CHECK (unemployment_insurance_base >= 0);
