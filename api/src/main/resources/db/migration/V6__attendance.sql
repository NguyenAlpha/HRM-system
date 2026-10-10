-- Ngày lễ công ty và chấm công hằng ngày, bao gồm nghỉ phép và tăng ca đã được duyệt.

CREATE TABLE company_holidays (
    id BIGSERIAL PRIMARY KEY,
    holiday_date DATE NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE attendance_records (
    id BIGSERIAL PRIMARY KEY,
    employee_id BIGINT NOT NULL REFERENCES employees (id),
    work_date DATE NOT NULL,
    shift_id BIGINT NOT NULL REFERENCES work_shifts (id),
    leave_request_id BIGINT REFERENCES leave_requests (id),
    scheduled_start_at TIMESTAMPTZ NOT NULL,
    scheduled_end_at TIMESTAMPTZ NOT NULL,
    scheduled_minutes INTEGER NOT NULL,
    check_in_at TIMESTAMPTZ,
    check_out_at TIMESTAMPTZ,
    worked_minutes INTEGER NOT NULL DEFAULT 0 CHECK (worked_minutes >= 0),
    payable_minutes INTEGER NOT NULL DEFAULT 0 CHECK (payable_minutes >= 0),
    leave_minutes INTEGER NOT NULL DEFAULT 0 CHECK (leave_minutes >= 0),
    late_minutes INTEGER NOT NULL DEFAULT 0 CHECK (late_minutes >= 0),
    early_leave_minutes INTEGER NOT NULL DEFAULT 0 CHECK (early_leave_minutes >= 0),
    overtime_minutes INTEGER NOT NULL DEFAULT 0 CHECK (overtime_minutes >= 0),
    overtime_multiplier NUMERIC(8,4) NOT NULL DEFAULT 1 CHECK (overtime_multiplier > 0),
    overtime_tax_exempt BOOLEAN NOT NULL DEFAULT false,
    overtime_approved_by_account_id BIGINT REFERENCES accounts (id),
    overtime_approved_at TIMESTAMPTZ,
    status VARCHAR(30) NOT NULL,
    note TEXT,
    updated_by_account_id BIGINT REFERENCES accounts (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_attendance_records_employee_date UNIQUE (employee_id, work_date),
    CONSTRAINT chk_attendance_scheduled_minutes CHECK (scheduled_minutes > 0),
    CONSTRAINT chk_attendance_records_status CHECK (
        status IN (
            'PRESENT',
            'PAID_LEAVE',
            'UNPAID_LEAVE',
            'MATERNITY_LEAVE',
            'SICK_LEAVE',
            'UNAUTHORIZED_ABSENCE',
            'HOLIDAY',
            'MISSING_PUNCH'
        )
    ),
    CONSTRAINT chk_attendance_overtime_approval CHECK (
        overtime_minutes = 0
        OR (overtime_minutes > 0 AND overtime_approved_by_account_id IS NOT NULL AND overtime_approved_at IS NOT NULL)
    )
);

-- Số phút theo lịch được snapshot từ ca làm việc khi không được truyền vào.
CREATE FUNCTION set_attendance_scheduled_minutes()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.scheduled_minutes IS NULL THEN
        SELECT standard_work_minutes
        INTO NEW.scheduled_minutes
        FROM work_shifts
        WHERE id = NEW.shift_id;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_attendance_scheduled_minutes
BEFORE INSERT OR UPDATE OF shift_id, scheduled_minutes ON attendance_records
FOR EACH ROW
EXECUTE FUNCTION set_attendance_scheduled_minutes();
