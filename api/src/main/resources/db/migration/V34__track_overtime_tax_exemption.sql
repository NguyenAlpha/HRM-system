ALTER TABLE attendance_records ADD COLUMN overtime_tax_exempt BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE payslips ADD COLUMN tax_exempt_overtime_pay NUMERIC(15,2) NOT NULL DEFAULT 0
    CHECK (tax_exempt_overtime_pay >= 0 AND tax_exempt_overtime_pay <= overtime_pay);
