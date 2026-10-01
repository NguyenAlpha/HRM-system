-- Finalize the V20 migration from the generic compensation/request models.
--
-- Every legacy row is copied to an immutable archive before the active legacy
-- tables are removed. Records that map unambiguously to the approved salary and
-- leave models are backfilled once more. Conflicting records remain available in
-- the archive with an explicit migration status for manual audit.

CREATE TABLE legacy_employee_compensation_archive (
    source_id BIGINT PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    component_type VARCHAR(20) NOT NULL,
    component_code VARCHAR(30) NOT NULL,
    component_name VARCHAR(150) NOT NULL,
    monthly_amount NUMERIC(15,2) NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE,
    approved_by_account_id BIGINT NOT NULL,
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    migrated_salary_history_id BIGINT,
    migration_status VARCHAR(40) NOT NULL,
    archived_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_legacy_compensation_migration_status CHECK (
        migration_status IN (
            'PENDING_BACKFILL',
            'MIGRATED',
            'ARCHIVED_ALLOWANCE',
            'ARCHIVED_INVALID_AMOUNT',
            'ARCHIVED_CONFLICT'
        )
    )
);

CREATE INDEX idx_legacy_compensation_archive_employee
    ON legacy_employee_compensation_archive (employee_id, component_type, effective_from);

INSERT INTO legacy_employee_compensation_archive (
    source_id,
    employee_id,
    component_type,
    component_code,
    component_name,
    monthly_amount,
    effective_from,
    effective_to,
    approved_by_account_id,
    note,
    created_at,
    migration_status
)
SELECT
    id,
    employee_id,
    component_type,
    component_code,
    component_name,
    monthly_amount,
    effective_from,
    effective_to,
    approved_by_account_id,
    note,
    created_at,
    CASE
        WHEN component_type = 'ALLOWANCE' THEN 'ARCHIVED_ALLOWANCE'
        WHEN monthly_amount <= 0 THEN 'ARCHIVED_INVALID_AMOUNT'
        ELSE 'PENDING_BACKFILL'
    END
FROM employee_compensations;

DO $$
DECLARE
    legacy_record employee_compensations%ROWTYPE;
BEGIN
    FOR legacy_record IN
        SELECT *
        FROM employee_compensations
        WHERE component_type = 'BASIC_SALARY'
          AND monthly_amount > 0
        ORDER BY employee_id, effective_from, id
    LOOP
        IF NOT EXISTS (
            SELECT 1
            FROM employee_salary_history salary
            WHERE salary.employee_id = legacy_record.employee_id
              AND salary.base_salary = legacy_record.monthly_amount
              AND salary.effective_from = legacy_record.effective_from
              AND salary.effective_to IS NOT DISTINCT FROM legacy_record.effective_to
              AND salary.approved_by_account_id = legacy_record.approved_by_account_id
              AND salary.reason IS NOT DISTINCT FROM legacy_record.component_name
              AND salary.note IS NOT DISTINCT FROM legacy_record.note
              AND salary.created_at = legacy_record.created_at
        ) THEN
            BEGIN
                INSERT INTO employee_salary_history (
                    employee_id,
                    base_salary,
                    effective_from,
                    effective_to,
                    approved_by_account_id,
                    reason,
                    note,
                    created_at
                ) VALUES (
                    legacy_record.employee_id,
                    legacy_record.monthly_amount,
                    legacy_record.effective_from,
                    legacy_record.effective_to,
                    legacy_record.approved_by_account_id,
                    legacy_record.component_name,
                    legacy_record.note,
                    legacy_record.created_at
                );
            EXCEPTION
                WHEN exclusion_violation THEN
                    -- Preserve overlaps or records changed after V20 in the archive.
                    NULL;
            END;
        END IF;
    END LOOP;
END;
$$;

UPDATE legacy_employee_compensation_archive archive
SET migrated_salary_history_id = salary.id,
    migration_status = 'MIGRATED'
FROM employee_salary_history salary
WHERE archive.component_type = 'BASIC_SALARY'
  AND archive.monthly_amount > 0
  AND salary.employee_id = archive.employee_id
  AND salary.base_salary = archive.monthly_amount
  AND salary.effective_from = archive.effective_from
  AND salary.effective_to IS NOT DISTINCT FROM archive.effective_to
  AND salary.approved_by_account_id = archive.approved_by_account_id
  AND salary.reason IS NOT DISTINCT FROM archive.component_name
  AND salary.note IS NOT DISTINCT FROM archive.note
  AND salary.created_at = archive.created_at;

UPDATE legacy_employee_compensation_archive
SET migration_status = 'ARCHIVED_CONFLICT'
WHERE migration_status = 'PENDING_BACKFILL';

CREATE TABLE legacy_employee_request_archive (
    source_id BIGINT PRIMARY KEY,
    employee_id BIGINT NOT NULL,
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
    reviewed_by_account_id BIGINT,
    review_comment TEXT,
    reviewed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    migrated_leave_request_id BIGINT,
    migration_status VARCHAR(40) NOT NULL,
    archived_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_legacy_request_migration_status CHECK (
        migration_status IN (
            'PENDING_BACKFILL',
            'MIGRATED',
            'ARCHIVED_RESIGNATION',
            'ARCHIVED_CONFLICT'
        )
    )
);

CREATE INDEX idx_legacy_request_archive_employee
    ON legacy_employee_request_archive (employee_id, request_type, status, created_at);

INSERT INTO legacy_employee_request_archive (
    source_id,
    employee_id,
    request_type,
    leave_type,
    is_paid_leave,
    start_date,
    end_date,
    total_days,
    requested_last_working_date,
    reason,
    attachment_url,
    status,
    submitted_at,
    reviewed_by_account_id,
    review_comment,
    reviewed_at,
    created_at,
    updated_at,
    migration_status
)
SELECT
    id,
    employee_id,
    request_type,
    leave_type,
    is_paid_leave,
    start_date,
    end_date,
    total_days,
    requested_last_working_date,
    reason,
    attachment_url,
    status,
    submitted_at,
    reviewed_by_account_id,
    review_comment,
    reviewed_at,
    created_at,
    updated_at,
    CASE
        WHEN request_type = 'RESIGNATION' THEN 'ARCHIVED_RESIGNATION'
        ELSE 'PENDING_BACKFILL'
    END
FROM employee_requests;

DO $$
DECLARE
    legacy_record employee_requests%ROWTYPE;
BEGIN
    FOR legacy_record IN
        SELECT *
        FROM employee_requests
        WHERE request_type = 'LEAVE'
        ORDER BY employee_id, start_date, id
    LOOP
        IF NOT EXISTS (
            SELECT 1
            FROM leave_requests leave_request
            WHERE leave_request.employee_id = legacy_record.employee_id
              AND leave_request.leave_type = legacy_record.leave_type
              AND leave_request.start_at = (legacy_record.start_date::TIMESTAMP AT TIME ZONE 'Asia/Ho_Chi_Minh')
              AND leave_request.end_at = ((legacy_record.end_date + 1)::TIMESTAMP AT TIME ZONE 'Asia/Ho_Chi_Minh')
              AND leave_request.reason = legacy_record.reason
              AND leave_request.created_at = legacy_record.created_at
        ) THEN
            BEGIN
                INSERT INTO leave_requests (
                    employee_id,
                    leave_type,
                    salary_treatment,
                    start_at,
                    end_at,
                    requested_minutes,
                    reason,
                    attachment_url,
                    status,
                    submitted_at,
                    reviewed_by_account_id,
                    review_comment,
                    reviewed_at,
                    created_at,
                    updated_at
                ) VALUES (
                    legacy_record.employee_id,
                    legacy_record.leave_type,
                    CASE
                        WHEN legacy_record.leave_type = 'MATERNITY' THEN 'SOCIAL_INSURANCE'
                        WHEN legacy_record.is_paid_leave THEN 'EMPLOYER_PAID'
                        ELSE 'UNPAID'
                    END,
                    legacy_record.start_date::TIMESTAMP AT TIME ZONE 'Asia/Ho_Chi_Minh',
                    (legacy_record.end_date + 1)::TIMESTAMP AT TIME ZONE 'Asia/Ho_Chi_Minh',
                    GREATEST(1, CEIL(legacy_record.total_days * 480)::INTEGER),
                    legacy_record.reason,
                    legacy_record.attachment_url,
                    CASE
                        WHEN legacy_record.status = 'COMPLETED' THEN 'APPROVED'
                        ELSE legacy_record.status
                    END,
                    legacy_record.submitted_at,
                    legacy_record.reviewed_by_account_id,
                    legacy_record.review_comment,
                    legacy_record.reviewed_at,
                    legacy_record.created_at,
                    legacy_record.updated_at
                );
            EXCEPTION
                WHEN exclusion_violation THEN
                    -- Preserve overlapping approved leave in the archive for audit.
                    NULL;
            END;
        END IF;
    END LOOP;
END;
$$;

UPDATE legacy_employee_request_archive archive
SET migrated_leave_request_id = leave_request.id,
    migration_status = 'MIGRATED'
FROM leave_requests leave_request
WHERE archive.request_type = 'LEAVE'
  AND leave_request.employee_id = archive.employee_id
  AND leave_request.leave_type = archive.leave_type
  AND leave_request.start_at = (archive.start_date::TIMESTAMP AT TIME ZONE 'Asia/Ho_Chi_Minh')
  AND leave_request.end_at = ((archive.end_date + 1)::TIMESTAMP AT TIME ZONE 'Asia/Ho_Chi_Minh')
  AND leave_request.reason = archive.reason
  AND leave_request.created_at = archive.created_at;

UPDATE legacy_employee_request_archive
SET migration_status = 'ARCHIVED_CONFLICT'
WHERE migration_status = 'PENDING_BACKFILL';

COMMENT ON TABLE legacy_employee_compensation_archive IS
    'Read-only snapshot of employee_compensations removed by V27';
COMMENT ON TABLE legacy_employee_request_archive IS
    'Read-only snapshot of employee_requests removed by V27';

DROP TABLE employee_compensations;
DROP TABLE employee_requests;
