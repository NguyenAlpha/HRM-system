-- Hồ sơ nhân sự, tài khoản đăng nhập và các token xác thực.
-- employees.deleted_by_account_id và scheduled_end_by_account_id tham chiếu accounts nên FK được thêm
-- sau khi accounts tồn tại.

CREATE TABLE employees (
    id BIGSERIAL PRIMARY KEY,
    employee_code VARCHAR(30) NOT NULL,
    full_name VARCHAR(200) NOT NULL,
    date_of_birth DATE,
    gender VARCHAR(20),
    highest_education_level VARCHAR(20),
    major VARCHAR(200),
    institution VARCHAR(200),
    graduation_year SMALLINT,
    national_id VARCHAR(30) UNIQUE,
    personal_email VARCHAR(100),
    work_email VARCHAR(100),
    phone VARCHAR(20),
    address TEXT,
    tax_code VARCHAR(30),
    bank_name VARCHAR(150),
    bank_account_number VARCHAR(50),
    bank_account_holder VARCHAR(200),
    hire_date DATE NOT NULL,
    seniority_start_date DATE NOT NULL,
    employment_status VARCHAR(20) NOT NULL,
    termination_date DATE,
    termination_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ,
    deleted_by_account_id BIGINT,
    deletion_reason TEXT,
    -- HR kết thúc làm việc với ngày làm việc cuối trong tương lai: termination_date và termination_reason
    -- được ghi ngay, còn trạng thái, phân công, tài khoản và role chỉ đóng sau ngày đó (job 00:10 mỗi đêm).
    -- scheduled_end_status là trạng thái sẽ đặt; scheduled_end_by_account_id là người lên lịch, dùng làm
    -- người thu hồi role khi job chạy.
    scheduled_end_status VARCHAR(20),
    scheduled_end_by_account_id BIGINT,
    CONSTRAINT chk_employees_gender CHECK (gender IN ('MALE', 'FEMALE', 'OTHER', 'UNDISCLOSED')),
    CONSTRAINT chk_employees_education_level
        CHECK (highest_education_level IN ('HIGH_SCHOOL', 'COLLEGE', 'BACHELOR', 'MASTER', 'DOCTORATE')),
    CONSTRAINT chk_employees_status
        CHECK (employment_status IN ('PROBATION', 'ACTIVE', 'RESIGNED', 'TERMINATED', 'RETIRED')),
    CONSTRAINT chk_employees_dates CHECK (
        seniority_start_date >= hire_date
        AND (termination_date IS NULL OR termination_date >= hire_date)
    ),
    CONSTRAINT chk_employees_scheduled_end CHECK (
        (scheduled_end_status IS NULL AND scheduled_end_by_account_id IS NULL)
        OR (scheduled_end_status IN ('RESIGNED', 'TERMINATED', 'RETIRED')
            AND scheduled_end_by_account_id IS NOT NULL
            AND employment_status IN ('PROBATION', 'ACTIVE')
            AND termination_date IS NOT NULL
            AND termination_reason IS NOT NULL)
    )
);

CREATE INDEX idx_employees_status ON employees (employment_status) WHERE deleted_at IS NULL;
CREATE INDEX idx_employees_scheduled_end ON employees (termination_date) WHERE scheduled_end_status IS NOT NULL;

-- Employee identity values used for account provisioning must be unique regardless of letter case.
-- Unique theo LOWER(...) đã bao hàm unique phân biệt hoa thường nên không khai báo UNIQUE trên cột.
CREATE UNIQUE INDEX uq_employees_employee_code_lower
    ON employees (LOWER(employee_code));

CREATE UNIQUE INDEX uq_employees_work_email_lower
    ON employees (LOWER(work_email))
    WHERE work_email IS NOT NULL;

-- Thâm niên mặc định tính từ ngày vào làm khi không được nhập riêng.
CREATE FUNCTION set_employee_seniority_start_date()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.seniority_start_date IS NULL THEN
        NEW.seniority_start_date := NEW.hire_date;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_employees_seniority_start_date
BEFORE INSERT OR UPDATE OF hire_date, seniority_start_date ON employees
FOR EACH ROW
EXECUTE FUNCTION set_employee_seniority_start_date();

-- Pending accounts do not have a password until the invited user activates the account.
-- Tài khoản xóa mềm (deleted_at) luôn ở trạng thái DISABLED và giữ nguyên username/email; nhân viên của
-- nó được cấp tài khoản mới (unique employee_id chỉ tính tài khoản chưa xóa).
CREATE TABLE accounts (
    id BIGSERIAL PRIMARY KEY,
    employee_id BIGINT REFERENCES employees (id),
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255),
    status VARCHAR(20) NOT NULL,
    failed_login_count INTEGER NOT NULL DEFAULT 0,
    last_login_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ,
    deleted_by_account_id BIGINT REFERENCES accounts (id),
    deletion_reason TEXT,
    CONSTRAINT chk_accounts_status CHECK (status IN ('PENDING', 'ACTIVE', 'DISABLED')),
    CONSTRAINT chk_accounts_password_after_activation
        CHECK (status = 'PENDING' OR password_hash IS NOT NULL OR deleted_at IS NOT NULL),
    CONSTRAINT chk_accounts_deleted
        CHECK (deleted_at IS NULL OR (status = 'DISABLED' AND deleted_by_account_id IS NOT NULL AND deletion_reason IS NOT NULL))
);

CREATE UNIQUE INDEX uq_accounts_employee_active
    ON accounts (employee_id) WHERE deleted_at IS NULL;

-- Unique theo LOWER(email), tương tự employee_code và work_email.
CREATE UNIQUE INDEX uq_accounts_email_lower
    ON accounts (LOWER(email));

ALTER TABLE employees
    ADD CONSTRAINT fk_employees_deleted_by_account FOREIGN KEY (deleted_by_account_id) REFERENCES accounts (id);

ALTER TABLE employees
    ADD CONSTRAINT fk_employees_scheduled_end_by_account FOREIGN KEY (scheduled_end_by_account_id) REFERENCES accounts (id);

-- Refresh tokens are opaque credentials. Only their SHA-256 hashes are stored.
CREATE TABLE refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    token_hash CHAR(64) NOT NULL UNIQUE,
    account_id BIGINT NOT NULL REFERENCES accounts (id),
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_refresh_tokens_account_id ON refresh_tokens (account_id);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens (expires_at);

-- Activation tokens are opaque credentials. Only their SHA-256 hashes are stored.
CREATE TABLE account_activation_tokens (
    id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL REFERENCES accounts (id),
    token_hash CHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    created_by_account_id BIGINT NOT NULL REFERENCES accounts (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_account_activation_token_state
        CHECK (used_at IS NULL OR revoked_at IS NULL)
);

CREATE INDEX idx_account_activation_tokens_account_id
    ON account_activation_tokens (account_id);

CREATE INDEX idx_account_activation_tokens_expires_at
    ON account_activation_tokens (expires_at);

CREATE UNIQUE INDEX uq_account_activation_tokens_active_account
    ON account_activation_tokens (account_id)
    WHERE used_at IS NULL AND revoked_at IS NULL;

-- Hạn mức rate-limit do quản trị viên chỉnh trên giao diện, ghi đè giá trị trong application.properties.
-- Mức không có dòng nào dùng giá trị trong application.properties và luôn bật.
CREATE TABLE rate_limit_policy_settings (
    policy VARCHAR(30) PRIMARY KEY,
    enabled BOOLEAN NOT NULL,
    max_requests INTEGER NOT NULL,
    window_seconds INTEGER NOT NULL,
    updated_by_account_id BIGINT NOT NULL REFERENCES accounts (id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_rate_limit_policy CHECK (policy IN (
        'login', 'refresh', 'activation', 'ip-ceiling', 'login-failure',
        'api', 'password', 'export', 'report', 'heavy-write'
    )),
    CONSTRAINT chk_rate_limit_max_requests CHECK (max_requests BETWEEN 1 AND 100000),
    CONSTRAINT chk_rate_limit_window_seconds CHECK (window_seconds BETWEEN 1 AND 86400),
    -- Chống dò mật khẩu không được tắt.
    CONSTRAINT chk_rate_limit_login_failure_enabled CHECK (policy <> 'login-failure' OR enabled)
);
