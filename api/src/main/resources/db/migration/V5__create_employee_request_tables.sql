-- Đơn nghỉ phép hoặc nghỉ việc.

CREATE TABLE employee_requests (
    id BIGSERIAL PRIMARY KEY,
    employee_id BIGINT NOT NULL REFERENCES employees (id),
    request_type VARCHAR(20) NOT NULL,
    leave_type VARCHAR(20),
    is_paid_leave BOOLEAN,
    start_date DATE,
    end_date DATE,
    total_days NUMERIC(6,2),
    requested_last_working_date DATE,
    reason TEXT NOT NULL,
    attachment_url TEXT,
    status VARCHAR(20) NOT NULL,
    submitted_at TIMESTAMPTZ,
    reviewed_by_account_id BIGINT REFERENCES accounts (id),
    review_comment TEXT,
    reviewed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_employee_requests_status
        CHECK (status IN ('DRAFT', 'PENDING', 'APPROVED', 'REJECTED', 'CANCELLED', 'COMPLETED')),
    CONSTRAINT chk_employee_requests_fields CHECK (
        (
            request_type = 'LEAVE'
            AND leave_type IS NOT NULL
            AND leave_type IN ('ANNUAL', 'SICK', 'MATERNITY', 'UNPAID', 'OTHER')
            AND is_paid_leave IS NOT NULL
            AND start_date IS NOT NULL
            AND end_date IS NOT NULL
            AND end_date >= start_date
            AND total_days IS NOT NULL
            AND total_days > 0
            AND requested_last_working_date IS NULL
        )
        OR (
            request_type = 'RESIGNATION'
            AND requested_last_working_date IS NOT NULL
            AND leave_type IS NULL
            AND is_paid_leave IS NULL
            AND start_date IS NULL
            AND end_date IS NULL
            AND total_days IS NULL
        )
    )
);

CREATE INDEX idx_requests_employee_status ON employee_requests (employee_id, status, submitted_at DESC);
CREATE INDEX idx_requests_type_status ON employee_requests (request_type, status);
