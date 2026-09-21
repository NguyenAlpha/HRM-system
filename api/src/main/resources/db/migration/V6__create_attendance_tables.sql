-- Chấm công hằng ngày, bao gồm tăng ca đã được duyệt.

CREATE TABLE attendance_records (
    id BIGSERIAL PRIMARY KEY,
    employee_id BIGINT NOT NULL REFERENCES employees (id),
    work_date DATE NOT NULL,
    shift_id BIGINT NOT NULL REFERENCES work_shifts (id),
    scheduled_start_at TIMESTAMPTZ NOT NULL,
    scheduled_end_at TIMESTAMPTZ NOT NULL,
    check_in_at TIMESTAMPTZ,
    check_out_at TIMESTAMPTZ,
    worked_minutes INTEGER NOT NULL DEFAULT 0 CHECK (worked_minutes >= 0),
    payable_minutes INTEGER NOT NULL DEFAULT 0 CHECK (payable_minutes >= 0),
    late_minutes INTEGER NOT NULL DEFAULT 0 CHECK (late_minutes >= 0),
    early_leave_minutes INTEGER NOT NULL DEFAULT 0 CHECK (early_leave_minutes >= 0),
    overtime_minutes INTEGER NOT NULL DEFAULT 0 CHECK (overtime_minutes >= 0),
    overtime_multiplier NUMERIC(8,4) NOT NULL DEFAULT 1 CHECK (overtime_multiplier > 0),
    overtime_approved_by_account_id BIGINT REFERENCES accounts (id),
    overtime_approved_at TIMESTAMPTZ,
    status VARCHAR(20) NOT NULL,
    note TEXT,
    updated_by_account_id BIGINT REFERENCES accounts (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_attendance_records_employee_date UNIQUE (employee_id, work_date),
    CONSTRAINT chk_attendance_records_status
        CHECK (status IN ('PRESENT', 'ABSENT', 'PAID_LEAVE', 'UNPAID_LEAVE', 'HOLIDAY', 'MISSING_PUNCH')),
    CONSTRAINT chk_attendance_overtime_approval CHECK (
        overtime_minutes = 0
        OR (overtime_minutes > 0 AND overtime_approved_by_account_id IS NOT NULL AND overtime_approved_at IS NOT NULL)
    )
);

CREATE INDEX idx_attendance_employee_date ON attendance_records (employee_id, work_date DESC);
