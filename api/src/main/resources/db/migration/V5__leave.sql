-- Đơn nghỉ phép. Hạn mức phép năm không lưu bảng riêng mà tính từ ngày vào làm (12 ngày/năm).

CREATE TABLE leave_requests (
    id BIGSERIAL PRIMARY KEY,
    employee_id BIGINT NOT NULL REFERENCES employees (id),
    leave_type VARCHAR(30) NOT NULL,
    salary_treatment VARCHAR(30) NOT NULL,
    start_at TIMESTAMPTZ NOT NULL,
    end_at TIMESTAMPTZ NOT NULL,
    requested_minutes INTEGER NOT NULL,
    reason TEXT NOT NULL,
    attachment_url TEXT,
    status VARCHAR(20) NOT NULL,
    submitted_at TIMESTAMPTZ,
    -- Người duyệt được chốt khi gửi đơn: quản lý trực tiếp, nếu không có thì Giám đốc, rồi Chủ sở hữu.
    approver_employee_id BIGINT REFERENCES employees (id),
    reviewed_by_account_id BIGINT REFERENCES accounts (id),
    review_comment TEXT,
    reviewed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_leave_type
        CHECK (leave_type IN ('ANNUAL', 'SICK', 'MATERNITY', 'UNPAID', 'OTHER')),
    CONSTRAINT chk_leave_salary_treatment
        CHECK (salary_treatment IN ('EMPLOYER_PAID', 'SOCIAL_INSURANCE', 'UNPAID')),
    CONSTRAINT chk_leave_status
        CHECK (status IN ('DRAFT', 'PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT chk_leave_period
        CHECK (end_at > start_at AND requested_minutes > 0),
    CONSTRAINT excl_approved_leave_request_overlap
        EXCLUDE USING GIST (
            employee_id WITH =,
            TSTZRANGE(start_at, end_at, '[)') WITH &&
        ) WHERE (status = 'APPROVED')
);

CREATE INDEX idx_leave_requests_employee_status
    ON leave_requests (employee_id, status, start_at);

CREATE INDEX idx_leave_requests_approver_status
    ON leave_requests (approver_employee_id, status);

-- Đơn nghỉ việc: nhân viên đề nghị ngày làm việc cuối, người duyệt (như đơn nghỉ) có thể đổi ngày đó
-- khi duyệt. Hợp đồng chỉ kết thúc sau ngày làm việc cuối (completed_at), nên nhân viên vẫn đăng nhập
-- và chấm công bình thường trong thời gian báo trước.
CREATE TABLE resignation_requests (
    id BIGSERIAL PRIMARY KEY,
    employee_id BIGINT NOT NULL REFERENCES employees (id),
    last_working_date DATE NOT NULL,
    reason TEXT NOT NULL CHECK (btrim(reason) <> ''),
    status VARCHAR(20) NOT NULL,
    approver_employee_id BIGINT NOT NULL REFERENCES employees (id),
    reviewed_by_account_id BIGINT REFERENCES accounts (id),
    review_comment TEXT,
    reviewed_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_resignation_status
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT chk_resignation_completed
        CHECK (completed_at IS NULL OR status = 'APPROVED')
);

-- Mỗi nhân viên chỉ có một đơn nghỉ việc còn mở: đang chờ, hoặc đã duyệt mà chưa tới ngày nghỉ.
CREATE UNIQUE INDEX uq_resignation_requests_open
    ON resignation_requests (employee_id)
    WHERE status = 'PENDING' OR (status = 'APPROVED' AND completed_at IS NULL);

CREATE INDEX idx_resignation_requests_approver_status
    ON resignation_requests (approver_employee_id, status);
