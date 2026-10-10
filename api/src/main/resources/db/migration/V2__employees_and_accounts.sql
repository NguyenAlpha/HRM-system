-- Hồ sơ nhân sự, tài khoản đăng nhập và các token xác thực.
-- employees.deleted_by_account_id tham chiếu accounts nên FK được thêm sau khi accounts tồn tại.

CREATE TABLE employees (
    id BIGSERIAL PRIMARY KEY,
    employee_code VARCHAR(30) NOT NULL UNIQUE,
    full_name VARCHAR(200) NOT NULL,
    date_of_birth DATE,
    gender VARCHAR(20),
    highest_education_level VARCHAR(20),
    major VARCHAR(200),
    institution VARCHAR(200),
    graduation_year SMALLINT,
    national_id VARCHAR(30) UNIQUE,
    personal_email VARCHAR(100),
    work_email VARCHAR(100) UNIQUE,
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
    CONSTRAINT chk_employees_gender CHECK (gender IN ('MALE', 'FEMALE', 'OTHER', 'UNDISCLOSED')),
    CONSTRAINT chk_employees_education_level
        CHECK (highest_education_level IN ('HIGH_SCHOOL', 'COLLEGE', 'BACHELOR', 'MASTER', 'DOCTORATE')),
    CONSTRAINT chk_employees_status
        CHECK (employment_status IN ('PROBATION', 'ACTIVE', 'RESIGNED', 'TERMINATED', 'RETIRED')),
    CONSTRAINT chk_employees_dates CHECK (
        seniority_start_date >= hire_date
        AND (termination_date IS NULL OR termination_date >= hire_date)
    )
);

CREATE INDEX idx_employees_status ON employees (employment_status) WHERE deleted_at IS NULL;

-- Employee identity values used for account provisioning must be unique regardless of letter case.
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
CREATE TABLE accounts (
    id BIGSERIAL PRIMARY KEY,
    employee_id BIGINT UNIQUE REFERENCES employees (id),
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255),
    status VARCHAR(20) NOT NULL,
    failed_login_count INTEGER NOT NULL DEFAULT 0,
    locked_until TIMESTAMPTZ,
    last_login_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_accounts_status CHECK (status IN ('PENDING', 'ACTIVE', 'LOCKED', 'DISABLED')),
    CONSTRAINT chk_accounts_password_after_activation
        CHECK (status = 'PENDING' OR password_hash IS NOT NULL)
);

CREATE UNIQUE INDEX uq_accounts_email_lower
    ON accounts (LOWER(email));

ALTER TABLE employees
    ADD CONSTRAINT fk_employees_deleted_by_account FOREIGN KEY (deleted_by_account_id) REFERENCES accounts (id);

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
