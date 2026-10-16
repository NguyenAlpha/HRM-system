-- Optional extension of seed_demo_employees.sql for historical demo payroll.
-- July and August are intentionally simple full-attendance months; September retains
-- the detailed exceptions from docs/DEMO_EMPLOYEE_SEED.md. October stays DRAFT.
-- Calculate the first three periods through PayrollService, not by inserting payslips.
BEGIN;

DO $$
BEGIN
    IF (SELECT count(*) FROM employees WHERE employee_code LIKE 'DEMO-%' AND deleted_at IS NULL) <> 36
       OR NOT EXISTS (SELECT 1 FROM work_shifts WHERE code = 'OFFICE_DAY'
                      AND deleted_at IS NULL AND standard_work_minutes = 480)
       OR (SELECT count(*) FROM attendance_records a JOIN employees e ON e.id = a.employee_id
           WHERE e.employee_code LIKE 'DEMO-%'
             AND a.work_date BETWEEN DATE '2026-09-01' AND DATE '2026-09-30') < 912 THEN
        RAISE EXCEPTION 'Run seed_demo_employees.sql before historical payroll seed';
    END IF;
END $$;

WITH calendar AS (
    SELECT d::date AS work_date
    FROM generate_series(DATE '2026-07-01', DATE '2026-08-31', INTERVAL '1 day') d
)
INSERT INTO attendance_records (
    employee_id, work_date, shift_id, scheduled_start_at, scheduled_end_at,
    scheduled_minutes, check_in_at, check_out_at, worked_minutes, payable_minutes,
    status, note, created_at, updated_at
)
SELECT e.id, c.work_date, s.id,
       (c.work_date::text || ' 08:00+07')::timestamptz,
       (c.work_date::text || ' 17:00+07')::timestamptz,
       480,
       (c.work_date::text || ' 08:00+07')::timestamptz,
       (c.work_date::text || ' 17:00+07')::timestamptz,
       480, 480, 'PRESENT', 'Ngày công chuẩn demo cho lịch sử lương',
       (c.work_date::text || ' 07:00+07')::timestamptz,
       (c.work_date::text || ' 20:00+07')::timestamptz
FROM calendar c
JOIN employees e ON e.employee_code LIKE 'DEMO-%' AND e.deleted_at IS NULL
    AND e.hire_date <= c.work_date
JOIN work_shifts s ON s.code = 'OFFICE_DAY' AND s.deleted_at IS NULL
LEFT JOIN company_holidays h ON h.holiday_date = c.work_date
WHERE extract(isodow FROM c.work_date) <= 6 AND h.id IS NULL
ON CONFLICT (employee_id, work_date) DO NOTHING;

INSERT INTO payroll_periods (year, month, period_start, period_end, tax_payment_date, status)
VALUES
    (2026, 7, DATE '2026-07-01', DATE '2026-07-31', DATE '2026-08-05', 'DRAFT'),
    (2026, 8, DATE '2026-08-01', DATE '2026-08-31', DATE '2026-09-05', 'DRAFT'),
    (2026, 9, DATE '2026-09-01', DATE '2026-09-30', DATE '2026-10-05', 'DRAFT'),
    (2026, 10, DATE '2026-10-01', DATE '2026-10-31', DATE '2026-11-05', 'DRAFT')
ON CONFLICT (year, month) DO NOTHING;

DO $$
DECLARE
    expected_july integer;
    expected_august integer;
BEGIN
    SELECT count(*) * 34 INTO expected_july
    FROM generate_series(DATE '2026-07-01', DATE '2026-07-31', INTERVAL '1 day') d
    LEFT JOIN company_holidays h ON h.holiday_date = d::date
    WHERE extract(isodow FROM d) <= 6 AND h.id IS NULL;
    SELECT count(*) * 34 INTO expected_august
    FROM generate_series(DATE '2026-08-01', DATE '2026-08-31', INTERVAL '1 day') d
    LEFT JOIN company_holidays h ON h.holiday_date = d::date
    WHERE extract(isodow FROM d) <= 6 AND h.id IS NULL;
    IF (SELECT count(*) FROM attendance_records a JOIN employees e ON e.id = a.employee_id
        WHERE e.employee_code LIKE 'DEMO-%' AND a.work_date BETWEEN DATE '2026-07-01' AND DATE '2026-07-31') < expected_july
       OR (SELECT count(*) FROM attendance_records a JOIN employees e ON e.id = a.employee_id
           WHERE e.employee_code LIKE 'DEMO-%' AND a.work_date BETWEEN DATE '2026-08-01' AND DATE '2026-08-31') < expected_august THEN
        RAISE EXCEPTION 'Historical demo attendance count check failed';
    END IF;
END $$;

SELECT p.year, p.month, p.id AS period_id, p.status,
       (SELECT count(*) FROM attendance_records a JOIN employees e ON e.id = a.employee_id
        WHERE e.employee_code LIKE 'DEMO-%'
          AND a.work_date BETWEEN p.period_start AND p.period_end) AS demo_attendance_rows
FROM payroll_periods p WHERE p.year = 2026 AND p.month BETWEEN 7 AND 10
ORDER BY p.month;

COMMIT;
