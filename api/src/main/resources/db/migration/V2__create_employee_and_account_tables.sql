-- Hồ sơ nhân sự và tài khoản đăng nhập.
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
        CHECK (employment_status IN ('PROBATION', 'ACTIVE', 'RESIGNED', 'TERMINATED', 'RETIRED'))
);

CREATE INDEX idx_employees_status ON employees (employment_status) WHERE deleted_at IS NULL;

CREATE TABLE accounts (
    id BIGSERIAL PRIMARY KEY,
    employee_id BIGINT UNIQUE REFERENCES employees (id),
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    failed_login_count INTEGER NOT NULL DEFAULT 0,
    locked_until TIMESTAMPTZ,
    last_login_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_accounts_status CHECK (status IN ('PENDING', 'ACTIVE', 'LOCKED', 'DISABLED'))
);

ALTER TABLE employees
    ADD CONSTRAINT fk_employees_deleted_by_account FOREIGN KEY (deleted_by_account_id) REFERENCES accounts (id);
