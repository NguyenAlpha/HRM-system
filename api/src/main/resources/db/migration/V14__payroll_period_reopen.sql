-- Kỳ đã duyệt nhưng chưa trả có thể được hủy duyệt (VD trả lương lệch sang tháng khác nên phải tính lại
-- thuế): kỳ về DRAFT, phiếu lương bị xóa. Ba cột ghi lần hủy duyệt gần nhất; cả ba cùng có hoặc cùng trống.
ALTER TABLE payroll_periods
    ADD COLUMN reopened_by_account_id BIGINT REFERENCES accounts (id),
    ADD COLUMN reopened_at TIMESTAMPTZ,
    ADD COLUMN reopen_reason VARCHAR(500),
    ADD CONSTRAINT chk_payroll_periods_reopen CHECK (
        (reopened_by_account_id IS NULL AND reopened_at IS NULL AND reopen_reason IS NULL)
        OR (reopened_by_account_id IS NOT NULL AND reopened_at IS NOT NULL AND reopen_reason IS NOT NULL)
    );
