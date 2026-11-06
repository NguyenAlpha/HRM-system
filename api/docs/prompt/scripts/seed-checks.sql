-- Usage (from the repo root), on a freshly seeded database:
--   docker exec -i hrm-database-1 psql -U hrm-user -d <db> -q < api/docs/prompt/scripts/seed-checks.sql
-- Rules the seed data must keep that SeedDataIntegrationTest does not check. Every query must return 0 rows;
-- "today" is the business date in Vietnam, as ClockConfig defines it.

\echo '1. Pending leave or resignation waiting for someone who left or cannot sign in'
SELECT 'leave' AS kind, r.id, e.employee_code AS employee, a.employee_code AS approver, a.employment_status, acc.status
FROM leave_requests r
JOIN employees e ON e.id = r.employee_id
JOIN employees a ON a.id = r.approver_employee_id
LEFT JOIN accounts acc ON acc.employee_id = a.id AND acc.deleted_at IS NULL
WHERE r.status = 'PENDING'
  AND (a.employment_status IN ('RESIGNED', 'TERMINATED', 'RETIRED') OR acc.status IS DISTINCT FROM 'ACTIVE')
UNION ALL
SELECT 'resignation', r.id, e.employee_code, a.employee_code, a.employment_status, acc.status
FROM resignation_requests r
JOIN employees e ON e.id = r.employee_id
JOIN employees a ON a.id = r.approver_employee_id
LEFT JOIN accounts acc ON acc.employee_id = a.id AND acc.deleted_at IS NULL
WHERE r.status = 'PENDING'
  AND (a.employment_status IN ('RESIGNED', 'TERMINATED', 'RETIRED') OR acc.status IS DISTINCT FROM 'ACTIVE');

\echo '2. Leave or resignation whose approver is the employee'
SELECT 'leave' AS kind, id, employee_id FROM leave_requests WHERE approver_employee_id = employee_id
UNION ALL
SELECT 'resignation', id, employee_id FROM resignation_requests WHERE approver_employee_id = employee_id;

\echo '3. Pending leave not with the manager of the primary assignment in force today (that manager can sign in)'
SELECT r.id, e.employee_code AS employee, a.employee_code AS stored_approver, m.employee_code AS manager_today
FROM leave_requests r
JOIN employees e ON e.id = r.employee_id
JOIN employees a ON a.id = r.approver_employee_id
JOIN employee_assignments ea ON ea.employee_id = e.id AND ea.is_primary
  AND ea.effective_from <= (now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date
  AND (ea.effective_to IS NULL OR ea.effective_to >= (now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date)
JOIN employees m ON m.id = ea.manager_employee_id
JOIN accounts macc ON macc.employee_id = m.id AND macc.deleted_at IS NULL AND macc.status = 'ACTIVE'
WHERE r.status = 'PENDING' AND m.employment_status NOT IN ('RESIGNED', 'TERMINATED', 'RETIRED') AND a.id <> m.id;

\echo '4. Assignment still running (today or later) whose manager has left, for someone still employed'
SELECT e.employee_code AS employee, m.employee_code AS manager, m.employment_status
FROM employee_assignments ea
JOIN employees e ON e.id = ea.employee_id
JOIN employees m ON m.id = ea.manager_employee_id
WHERE ea.is_primary
  AND (ea.effective_to IS NULL OR ea.effective_to >= (now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date)
  AND m.employment_status IN ('RESIGNED', 'TERMINATED', 'RETIRED')
  AND e.employment_status NOT IN ('RESIGNED', 'TERMINATED', 'RETIRED');

\echo '5. Two pending or approved leave requests of one employee on the same day (Vietnam time)'
SELECT r1.employee_id, r1.id, r2.id
FROM leave_requests r1
JOIN leave_requests r2 ON r2.employee_id = r1.employee_id AND r2.id > r1.id
WHERE r1.status IN ('PENDING', 'APPROVED') AND r2.status IN ('PENDING', 'APPROVED')
  AND daterange((r1.start_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date,
                (r1.end_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date, '[]')
   && daterange((r2.start_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date,
                (r2.end_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date, '[]');

\echo '6. Open request (leave draft/pending, pending resignation) of someone whose employment has ended'
SELECT 'leave' AS kind, r.id, e.employee_code, r.status FROM leave_requests r JOIN employees e ON e.id = r.employee_id
WHERE r.status IN ('DRAFT', 'PENDING') AND e.employment_status IN ('RESIGNED', 'TERMINATED', 'RETIRED')
  AND (r.start_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date > e.termination_date
UNION ALL
SELECT 'resignation', r.id, e.employee_code, r.status FROM resignation_requests r JOIN employees e ON e.id = r.employee_id
WHERE r.status = 'PENDING' AND e.employment_status IN ('RESIGNED', 'TERMINATED', 'RETIRED');
