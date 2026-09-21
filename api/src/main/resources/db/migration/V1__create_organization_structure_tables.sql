-- Doanh nghiệp, cơ cấu tổ chức, vị trí công việc và danh mục ca làm việc.

CREATE TABLE company_profile (
    id SMALLINT PRIMARY KEY DEFAULT 1,
    code VARCHAR(30) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    tax_code VARCHAR(30) UNIQUE,
    phone VARCHAR(20),
    email VARCHAR(100),
    address TEXT,
    timezone VARCHAR(50) NOT NULL DEFAULT 'Asia/Ho_Chi_Minh',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_company_profile_single CHECK (id = 1)
);

CREATE TABLE work_locations (
    id BIGSERIAL PRIMARY KEY,
    parent_location_id BIGINT REFERENCES work_locations (id),
    code VARCHAR(30) NOT NULL,
    name VARCHAR(150) NOT NULL,
    location_type VARCHAR(20) NOT NULL,
    address TEXT NOT NULL,
    phone VARCHAR(20),
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ,
    CONSTRAINT chk_work_locations_type CHECK (location_type IN ('HEAD_OFFICE', 'BRANCH', 'WAREHOUSE'))
);

CREATE UNIQUE INDEX uq_work_locations_code ON work_locations (code) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX uq_work_locations_warehouse_parent ON work_locations (parent_location_id)
    WHERE location_type = 'WAREHOUSE' AND deleted_at IS NULL;
CREATE INDEX idx_locations_parent ON work_locations (parent_location_id) WHERE deleted_at IS NULL;

CREATE TABLE organization_units (
    id BIGSERIAL PRIMARY KEY,
    parent_unit_id BIGINT REFERENCES organization_units (id),
    code VARCHAR(30) NOT NULL,
    name VARCHAR(150) NOT NULL,
    unit_type VARCHAR(20) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ,
    CONSTRAINT chk_organization_units_type CHECK (unit_type IN ('BOARD', 'DEPARTMENT', 'TEAM'))
);

CREATE UNIQUE INDEX uq_organization_units_code ON organization_units (code) WHERE deleted_at IS NULL;
CREATE INDEX idx_units_parent ON organization_units (parent_unit_id) WHERE deleted_at IS NULL;

CREATE TABLE job_positions (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(30) NOT NULL,
    title VARCHAR(150) NOT NULL,
    description TEXT,
    is_managerial BOOLEAN NOT NULL DEFAULT false,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX uq_job_positions_code ON job_positions (code) WHERE deleted_at IS NULL;

CREATE TABLE work_shifts (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(30) NOT NULL,
    name VARCHAR(100) NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    break_minutes INTEGER NOT NULL DEFAULT 0 CHECK (break_minutes >= 0),
    standard_work_minutes INTEGER NOT NULL CHECK (standard_work_minutes > 0),
    grace_late_minutes INTEGER NOT NULL DEFAULT 0 CHECK (grace_late_minutes >= 0),
    crosses_midnight BOOLEAN NOT NULL DEFAULT false,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX uq_work_shifts_code ON work_shifts (code) WHERE deleted_at IS NULL;
