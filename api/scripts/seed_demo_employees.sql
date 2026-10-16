-- Optional demo-only data for docs/DEMO_EMPLOYEE_SEED.md (snapshot: 2026-10-11).
-- Run after Flyway and the normal company/RBAC/admin seeders have completed.
-- psql -v ON_ERROR_STOP=1 -U hrm-user -d hrm -f api/scripts/seed_demo_employees.sql
-- Reruns insert missing rows only. Existing employee and business records are never updated.
BEGIN;

CREATE TEMP TABLE demo_people (
    seq integer PRIMARY KEY,
    code text UNIQUE NOT NULL,
    full_name text NOT NULL,
    birth_date date NOT NULL,
    gender text NOT NULL,
    hire_date date NOT NULL,
    status text NOT NULL,
    education text NOT NULL,
    major text NOT NULL,
    institution text NOT NULL,
    graduation_year smallint,
    unit_code text NOT NULL,
    location_code text NOT NULL,
    position_code text NOT NULL,
    manager_code text,
    opening_salary integer NOT NULL,
    current_salary integer,
    insurance_salary integer NOT NULL
) ON COMMIT DROP;

-- Generated from the profile, education, assignment and salary tables in the specification.
INSERT INTO demo_people VALUES
    (1, 'DEMO-DIR-001', 'Nguyễn Minh An', '1979-04-12', 'MALE', '2010-01-04', 'ACTIVE', 'MASTER', 'Quản trị kinh doanh', 'Đại học Demo TP.HCM', '2004', 'BOARD', 'HO', 'DIRECTOR', NULL, 25, 45, 45),
    (2, 'DEMO-HR-001', 'Trần Thu Hà', '1985-08-09', 'FEMALE', '2014-06-02', 'ACTIVE', 'MASTER', 'Quản trị nhân lực', 'Đại học Demo Hà Nội', '2010', 'HR', 'HO', 'HR_MANAGER_DEMO', 'DEMO-DIR-001', 12, 25, 25),
    (3, 'DEMO-PAY-001', 'Lê Quốc Bảo', '1989-02-16', 'MALE', '2018-04-02', 'ACTIVE', 'BACHELOR', 'Kế toán', 'Đại học Demo TP.HCM', '2011', 'ACCOUNTING', 'HO', 'PAYROLL_ACCOUNTANT', 'DEMO-DIR-001', 12, 20, 20),
    (4, 'DEMO-OPS-001', 'Phạm Hoàng Long', '1982-11-25', 'MALE', '2012-09-03', 'ACTIVE', 'BACHELOR', 'Quản trị vận hành', 'Đại học Demo TP.HCM', '2004', 'OPERATIONS', 'HO', 'OPERATIONS_MANAGER', 'DEMO-DIR-001', 15, 28, 28),
    (5, 'DEMO-BRH-001', 'Vũ Ngọc Mai', '1987-07-14', 'FEMALE', '2016-07-04', 'ACTIVE', 'BACHELOR', 'Quản trị kinh doanh', 'Đại học Demo Hà Nội', '2009', 'OPERATIONS', 'BRANCH-01', 'BRANCH_MANAGER', 'DEMO-OPS-001', 12, 22, 22),
    (6, 'DEMO-BRD-001', 'Võ Minh Khang', '1990-03-21', 'MALE', '2019-03-04', 'ACTIVE', 'BACHELOR', 'Quản trị kinh doanh', 'Đại học Demo Đà Nẵng', '2012', 'OPERATIONS', 'BRANCH-02', 'BRANCH_MANAGER', 'DEMO-OPS-001', 13, 21, 21),
    (7, 'DEMO-WH-001', 'Đặng Đức Phúc', '1988-10-03', 'MALE', '2017-08-01', 'ACTIVE', 'COLLEGE', 'Quản lý kho', 'Cao đẳng Demo TP.HCM', '2008', 'OPERATIONS', 'WAREHOUSE-01', 'WAREHOUSE_SUPERVISOR', 'DEMO-OPS-001', 10, 18, 18),
    (8, 'DEMO-TL-001', 'Nguyễn Thanh Tùng', '1991-12-17', 'MALE', '2020-02-03', 'ACTIVE', 'BACHELOR', 'Logistics', 'Đại học Demo TP.HCM', '2013', 'OPERATIONS', 'HO', 'TEAM_LEAD', 'DEMO-OPS-001', 10, 19, 19),
    (9, 'DEMO-SR-001', 'Hồ Văn Sơn', '1984-05-28', 'MALE', '2011-05-02', 'ACTIVE', 'COLLEGE', 'Vận hành', 'Cao đẳng Demo TP.HCM', '2004', 'OPERATIONS', 'HO', 'GENERAL_STAFF', 'DEMO-TL-001', 8, 17, 17),
    (10, 'DEMO-ST-001', 'Bùi Quỳnh Anh', '1997-09-05', 'FEMALE', '2023-07-03', 'ACTIVE', 'BACHELOR', 'Logistics', 'Đại học Demo Hà Nội', '2019', 'OPERATIONS', 'BRANCH-01', 'GENERAL_STAFF', 'DEMO-BRH-001', 9, 13, 13),
    (11, 'DEMO-NEW-001', 'Đỗ Gia Huy', '2002-06-18', 'MALE', '2026-09-01', 'PROBATION', 'BACHELOR', 'Quản trị kinh doanh', 'Đại học Demo TP.HCM', '2024', 'OPERATIONS', 'HO', 'GENERAL_STAFF', 'DEMO-TL-001', 10, NULL, 10),
    (12, 'DEMO-INT-001', 'Lâm Phương Linh', '2005-07-12', 'FEMALE', '2026-10-01', 'PROBATION', 'HIGH_SCHOOL', 'Công nghệ thông tin (đang học)', 'Đại học Demo TP.HCM', NULL, 'HR', 'HO', 'INTERN_DEMO', 'DEMO-HR-001', 5, NULL, 0),
    (13, 'DEMO-HR2-001', 'Phan Thị Ngọc', '1988-05-19', 'FEMALE', '2016-03-01', 'ACTIVE', 'MASTER', 'Quản trị nhân lực', 'Đại học Demo TP.HCM', '2012', 'HR', 'HO', 'HR_SPECIALIST', 'DEMO-HR-001', 8, 17, 17),
    (14, 'DEMO-HR2-002', 'Nguyễn Thảo Vy', '1994-11-08', 'FEMALE', '2020-08-03', 'ACTIVE', 'BACHELOR', 'Quản trị nhân lực', 'Đại học Demo TP.HCM', '2016', 'HR', 'HO', 'HR_SPECIALIST', 'DEMO-HR-001', 9, 15, 15),
    (15, 'DEMO-HR2-003', 'Trịnh Quốc Đạt', '1998-02-24', 'MALE', '2023-06-05', 'ACTIVE', 'BACHELOR', 'Luật lao động', 'Đại học Demo TP.HCM', '2020', 'HR', 'HO', 'HR_SPECIALIST', 'DEMO-HR-001', 10, 13, 13),
    (16, 'DEMO-ACC-001', 'Đinh Thị Hương', '1986-09-15', 'FEMALE', '2013-04-01', 'ACTIVE', 'BACHELOR', 'Kế toán', 'Đại học Demo TP.HCM', '2008', 'ACCOUNTING', 'HO', 'PAYROLL_ACCOUNTANT', 'DEMO-PAY-001', 9, 19, 19),
    (17, 'DEMO-ACC-002', 'Trần Gia Bảo', '1993-01-26', 'MALE', '2019-07-01', 'ACTIVE', 'BACHELOR', 'Tài chính', 'Đại học Demo TP.HCM', '2015', 'ACCOUNTING', 'HO', 'PAYROLL_ACCOUNTANT', 'DEMO-PAY-001', 10, 16, 16),
    (18, 'DEMO-ACC-003', 'Lê Minh Châu', '1999-12-02', 'FEMALE', '2024-02-01', 'ACTIVE', 'BACHELOR', 'Kế toán', 'Đại học Demo TP.HCM', '2021', 'ACCOUNTING', 'HO', 'PAYROLL_ACCOUNTANT', 'DEMO-PAY-001', 9, 12, 12),
    (19, 'DEMO-OPH-001', 'Nguyễn Đức Tài', '1987-06-03', 'MALE', '2011-09-05', 'ACTIVE', 'COLLEGE', 'Vận hành', 'Cao đẳng Demo TP.HCM', '2008', 'OPERATIONS', 'HO', 'GENERAL_STAFF', 'DEMO-TL-001', 7, 18, 18),
    (20, 'DEMO-OPH-002', 'Phạm Hải Yến', '1990-10-11', 'FEMALE', '2017-01-02', 'ACTIVE', 'BACHELOR', 'Logistics', 'Đại học Demo TP.HCM', '2012', 'OPERATIONS', 'HO', 'GENERAL_STAFF', 'DEMO-TL-001', 9, 16, 16),
    (21, 'DEMO-OPH-003', 'Võ Thành Nam', '1995-03-17', 'MALE', '2021-04-01', 'ACTIVE', 'BACHELOR', 'Quản trị vận hành', 'Đại học Demo TP.HCM', '2017', 'OPERATIONS', 'HO', 'GENERAL_STAFF', 'DEMO-TL-001', 9, 14, 14),
    (22, 'DEMO-OPH-004', 'Đỗ Mỹ Linh', '1997-08-29', 'FEMALE', '2022-05-02', 'ACTIVE', 'BACHELOR', 'Logistics', 'Đại học Demo TP.HCM', '2019', 'OPERATIONS', 'HO', 'GENERAL_STAFF', 'DEMO-TL-001', 9, 13, 13),
    (23, 'DEMO-OPH-005', 'Lưu Văn Phát', '1983-07-07', 'MALE', '2012-02-01', 'ACTIVE', 'COLLEGE', 'Vận hành', 'Cao đẳng Demo TP.HCM', '2003', 'OPERATIONS', 'HO', 'GENERAL_STAFF', 'DEMO-TL-001', 7, 18, 18),
    (24, 'DEMO-OPH-006', 'Bùi Khánh Ngân', '2000-04-23', 'FEMALE', '2025-01-02', 'ACTIVE', 'BACHELOR', 'Quản trị kinh doanh', 'Đại học Demo TP.HCM', '2022', 'OPERATIONS', 'HO', 'GENERAL_STAFF', 'DEMO-TL-001', 10, 11, 11),
    (25, 'DEMO-HNI-001', 'Nguyễn Thị Thu', '1989-01-30', 'FEMALE', '2015-05-04', 'ACTIVE', 'BACHELOR', 'Quản trị kinh doanh', 'Đại học Demo Hà Nội', '2011', 'OPERATIONS', 'BRANCH-01', 'GENERAL_STAFF', 'DEMO-BRH-001', 8, 17, 17),
    (26, 'DEMO-HNI-002', 'Hoàng Anh Tuấn', '1992-12-06', 'MALE', '2018-10-01', 'ACTIVE', 'COLLEGE', 'Logistics', 'Cao đẳng Demo Hà Nội', '2013', 'OPERATIONS', 'BRANCH-01', 'GENERAL_STAFF', 'DEMO-BRH-001', 9, 15, 15),
    (27, 'DEMO-HNI-003', 'Vũ Lan Anh', '1996-06-14', 'FEMALE', '2021-08-02', 'ACTIVE', 'BACHELOR', 'Quản trị kinh doanh', 'Đại học Demo Hà Nội', '2018', 'OPERATIONS', 'BRANCH-01', 'GENERAL_STAFF', 'DEMO-BRH-001', 9, 13, 13),
    (28, 'DEMO-HNI-004', 'Phạm Quang Huy', '1998-09-09', 'MALE', '2023-03-01', 'ACTIVE', 'BACHELOR', 'Logistics', 'Đại học Demo Hà Nội', '2020', 'OPERATIONS', 'BRANCH-01', 'GENERAL_STAFF', 'DEMO-BRH-001', 10, 12, 12),
    (29, 'DEMO-HNI-005', 'Cao Ngọc Hà', '1994-04-18', 'FEMALE', '2020-11-02', 'ACTIVE', 'BACHELOR', 'Quản trị kinh doanh', 'Đại học Demo Hà Nội', '2016', 'OPERATIONS', 'BRANCH-01', 'GENERAL_STAFF', 'DEMO-BRH-001', 9, 14, 14),
    (30, 'DEMO-DNG-001', 'Đặng Minh Đức', '1985-11-22', 'MALE', '2014-01-02', 'ACTIVE', 'COLLEGE', 'Vận hành', 'Cao đẳng Demo Đà Nẵng', '2006', 'OPERATIONS', 'BRANCH-02', 'GENERAL_STAFF', 'DEMO-BRD-001', 8, 17, 17),
    (31, 'DEMO-DNG-002', 'Nguyễn Phương Thảo', '1991-05-05', 'FEMALE', '2018-06-01', 'ACTIVE', 'BACHELOR', 'Logistics', 'Đại học Demo Đà Nẵng', '2013', 'OPERATIONS', 'BRANCH-02', 'GENERAL_STAFF', 'DEMO-BRD-001', 9, 15, 15),
    (32, 'DEMO-DNG-003', 'Hồ Ngọc Bích', '1997-02-12', 'FEMALE', '2022-07-01', 'ACTIVE', 'BACHELOR', 'Quản trị kinh doanh', 'Đại học Demo Đà Nẵng', '2019', 'OPERATIONS', 'BRANCH-02', 'GENERAL_STAFF', 'DEMO-BRD-001', 9, 13, 13),
    (33, 'DEMO-DNG-004', 'Trương Nhật Minh', '2000-10-20', 'MALE', '2025-02-03', 'ACTIVE', 'COLLEGE', 'Vận hành', 'Cao đẳng Demo Đà Nẵng', '2021', 'OPERATIONS', 'BRANCH-02', 'GENERAL_STAFF', 'DEMO-BRD-001', 10, 11, 11),
    (34, 'DEMO-KHO-001', 'Nguyễn Hữu Lộc', '1984-03-10', 'MALE', '2011-07-01', 'ACTIVE', 'COLLEGE', 'Quản lý kho', 'Cao đẳng Demo TP.HCM', '2005', 'OPERATIONS', 'WAREHOUSE-01', 'GENERAL_STAFF', 'DEMO-WH-001', 8, 17, 17),
    (35, 'DEMO-KHO-002', 'Lê Thanh Bình', '1992-08-31', 'MALE', '2019-09-02', 'ACTIVE', 'COLLEGE', 'Logistics', 'Cao đẳng Demo TP.HCM', '2013', 'OPERATIONS', 'WAREHOUSE-01', 'GENERAL_STAFF', 'DEMO-WH-001', 9, 14, 14),
    (36, 'DEMO-KHO-003', 'Phạm Thị Sen', '1998-01-13', 'FEMALE', '2023-10-02', 'ACTIVE', 'BACHELOR', 'Logistics', 'Đại học Demo TP.HCM', '2020', 'OPERATIONS', 'WAREHOUSE-01', 'GENERAL_STAFF', 'DEMO-WH-001', 10, 12, 12)
;

CREATE TEMP VIEW demo_audit_account AS
SELECT a.id
FROM accounts a
JOIN account_role_assignments ara ON ara.account_id = a.id
JOIN roles r ON r.id = ara.role_id
WHERE a.status = 'ACTIVE' AND r.code = 'SYSTEM_ADMIN'
  AND ara.scope_type = 'COMPANY' AND ara.revoked_at IS NULL
  AND ara.effective_from <= CURRENT_DATE
  AND (ara.effective_to IS NULL OR ara.effective_to >= CURRENT_DATE)
ORDER BY a.id LIMIT 1;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM demo_audit_account) THEN
        RAISE EXCEPTION 'Demo seed requires an active SYSTEM_ADMIN account (run the normal admin seeder first)';
    END IF;
    IF (SELECT count(*) FROM demo_people) <> 36 THEN
        RAISE EXCEPTION 'Demo seed definition must contain 36 employees';
    END IF;
    IF EXISTS (
        SELECT 1 FROM demo_people p JOIN employees e ON lower(e.employee_code) = lower(p.code)
        WHERE e.deleted_at IS NOT NULL OR e.work_email IS DISTINCT FROM lower(p.code) || '@hrm-demo.test'
    ) THEN
        RAISE EXCEPTION 'Existing DEMO employee code has a conflicting identity';
    END IF;
    IF EXISTS (
        SELECT 1 FROM demo_people p JOIN accounts a ON a.username = lower(p.code)
        LEFT JOIN employees e ON e.id = a.employee_id
        WHERE e.employee_code IS DISTINCT FROM p.code OR a.email IS DISTINCT FROM lower(p.code) || '@hrm-demo.test'
    ) THEN
        RAISE EXCEPTION 'Existing DEMO account has a conflicting identity';
    END IF;
    IF EXISTS (
        SELECT 1 FROM demo_people p
        WHERE NOT EXISTS (SELECT 1 FROM organization_units u WHERE u.code = p.unit_code AND u.deleted_at IS NULL)
           OR NOT EXISTS (SELECT 1 FROM work_locations l WHERE l.code = p.location_code AND l.deleted_at IS NULL)
           OR (p.position_code NOT IN ('HR_MANAGER_DEMO', 'INTERN_DEMO') AND NOT EXISTS
               (SELECT 1 FROM job_positions j WHERE j.code = p.position_code AND j.deleted_at IS NULL))
    ) OR NOT EXISTS (
        -- Some existing databases store the Java LocalTime values with a UTC JDBC offset.
        -- Both triplets represent the OFFICE_DAY definition used by the demo calendar.
        SELECT 1 FROM work_shifts WHERE code = 'OFFICE_DAY' AND deleted_at IS NULL
          AND (
              (start_time = TIME '08:00' AND end_time = TIME '17:00' AND break_start_time = TIME '12:00')
              OR (start_time = TIME '01:00' AND end_time = TIME '10:00' AND break_start_time = TIME '05:00')
          )
          AND break_minutes = 60
          AND standard_work_minutes = 480 AND grace_late_minutes = 15
    )
      OR NOT EXISTS (SELECT 1 FROM roles WHERE code = 'EMPLOYEE' AND deleted_at IS NULL) THEN
        RAISE EXCEPTION 'Demo seed requires the normal company, shift and RBAC catalogs';
    END IF;
END $$;

INSERT INTO job_positions (code, title, is_managerial)
SELECT v.code, v.title, v.is_managerial
FROM (VALUES
    ('HR_MANAGER_DEMO', 'Trưởng phòng Nhân sự', true),
    ('INTERN_DEMO', 'Thực tập sinh', false)
) v(code, title, is_managerial)
WHERE NOT EXISTS (SELECT 1 FROM job_positions j WHERE j.code = v.code AND j.deleted_at IS NULL);

INSERT INTO employees (
    employee_code, full_name, date_of_birth, gender, highest_education_level,
    major, institution, graduation_year, national_id, personal_email, work_email,
    phone, address, tax_code, bank_name, bank_account_number, bank_account_holder,
    hire_date, seniority_start_date, employment_status
)
SELECT p.code, p.full_name, p.birth_date, p.gender, p.education,
       p.major, p.institution, p.graduation_year, 'TEST-ID-' || p.code,
       lower(p.code) || '@example.test', lower(p.code) || '@hrm-demo.test',
       '00000000' || lpad(p.seq::text, 2, '0'),
       'Địa chỉ giả lập - ' || CASE p.location_code
           WHEN 'BRANCH-01' THEN 'Thành phố Hà Nội'
           WHEN 'BRANCH-02' THEN 'Thành phố Đà Nẵng'
           ELSE 'Thành phố Hồ Chí Minh' END,
       'TEST-TAX-' || p.code, 'NGÂN HÀNG DEMO', 'TEST-ACC-' || p.code,
       p.full_name, p.hire_date, p.hire_date, p.status
FROM demo_people p
WHERE NOT EXISTS (SELECT 1 FROM employees e WHERE lower(e.employee_code) = lower(p.code));

-- Accounts remain pending, with no password or activation token. The existing account
-- activation flow can provision a token when a demo user needs to log in.
INSERT INTO accounts (employee_id, username, email, status)
SELECT e.id, lower(p.code), lower(p.code) || '@hrm-demo.test', 'PENDING'
FROM demo_people p JOIN employees e ON e.employee_code = p.code
WHERE NOT EXISTS (SELECT 1 FROM accounts a WHERE a.employee_id = e.id);

INSERT INTO account_role_assignments (
    account_id, role_id, scope_type, effective_from, granted_by_account_id, reason
)
SELECT a.id, r.id, 'SELF', p.hire_date, admin.id, 'Khởi tạo dữ liệu demo'
FROM demo_people p JOIN accounts a ON a.username = lower(p.code)
CROSS JOIN roles r CROSS JOIN demo_audit_account admin
WHERE r.code = 'EMPLOYEE'
  AND NOT EXISTS (SELECT 1 FROM account_role_assignments ara
                  WHERE ara.account_id = a.id AND ara.role_id = r.id
                    AND ara.scope_type = 'SELF' AND ara.effective_to IS NULL AND ara.revoked_at IS NULL);

-- The HR account is intentionally pending. The admin is the audit actor for direct
-- historical inserts; no business role is granted outside the normal grant workflow.
INSERT INTO employee_assignments (
    employee_id, organization_unit_id, work_location_id, position_id, shift_id,
    manager_employee_id, employment_type, effective_from, effective_to,
    is_primary, reason, created_by_account_id
)
SELECT e.id, u.id, l.id, j.id, s.id, m.id,
       CASE WHEN p.code = 'DEMO-INT-001' THEN 'TEMPORARY' ELSE 'FULL_TIME' END,
       CASE WHEN p.code = 'DEMO-SR-001' THEN DATE '2011-05-02' ELSE p.hire_date END,
       CASE WHEN p.code = 'DEMO-SR-001' THEN DATE '2020-02-02' ELSE NULL END,
       true, 'Khởi tạo dữ liệu demo', admin.id
FROM demo_people p
JOIN employees e ON e.employee_code = p.code
JOIN organization_units u ON u.code = p.unit_code AND u.deleted_at IS NULL
JOIN work_locations l ON l.code = p.location_code AND l.deleted_at IS NULL
JOIN job_positions j ON j.code = p.position_code AND j.deleted_at IS NULL
JOIN work_shifts s ON s.code = 'OFFICE_DAY' AND s.deleted_at IS NULL
LEFT JOIN employees m ON m.employee_code = CASE WHEN p.code = 'DEMO-SR-001' THEN 'DEMO-DIR-001' ELSE p.manager_code END
CROSS JOIN demo_audit_account admin
WHERE NOT EXISTS (SELECT 1 FROM employee_assignments x WHERE x.employee_id = e.id);

INSERT INTO employee_assignments (
    employee_id, organization_unit_id, work_location_id, position_id, shift_id,
    manager_employee_id, employment_type, effective_from, is_primary, reason, created_by_account_id
)
SELECT e.id, u.id, l.id, j.id, s.id, m.id, 'FULL_TIME', DATE '2020-02-03', true,
       'Đổi người quản lý', admin.id
FROM employees e
JOIN employees m ON m.employee_code = 'DEMO-TL-001'
JOIN organization_units u ON u.code = 'OPERATIONS' AND u.deleted_at IS NULL
JOIN work_locations l ON l.code = 'HO' AND l.deleted_at IS NULL
JOIN job_positions j ON j.code = 'GENERAL_STAFF' AND j.deleted_at IS NULL
JOIN work_shifts s ON s.code = 'OFFICE_DAY' AND s.deleted_at IS NULL
CROSS JOIN demo_audit_account admin
WHERE e.employee_code = 'DEMO-SR-001'
  AND EXISTS (SELECT 1 FROM employee_assignments x WHERE x.employee_id = e.id
              AND x.effective_from = DATE '2011-05-02' AND x.effective_to = DATE '2020-02-02')
  AND NOT EXISTS (SELECT 1 FROM employee_assignments x WHERE x.employee_id = e.id
                  AND x.is_primary AND daterange(x.effective_from, x.effective_to + 1, '[)')
                      && daterange(DATE '2020-02-03', NULL, '[)'));

INSERT INTO employee_salary_history (
    employee_id, base_salary, effective_from, effective_to,
    approved_by_account_id, reason, note
)
SELECT e.id, p.opening_salary * 1000000, p.hire_date,
       CASE WHEN p.current_salary IS NOT NULL THEN DATE '2025-12-31' ELSE NULL END,
       admin.id, 'Khởi tạo dữ liệu demo', 'Chỉ dùng kiểm thử'
FROM demo_people p JOIN employees e ON e.employee_code = p.code CROSS JOIN demo_audit_account admin
WHERE NOT EXISTS (SELECT 1 FROM employee_salary_history h WHERE h.employee_id = e.id
                  AND daterange(h.effective_from, h.effective_to + 1, '[)')
                      && daterange(p.hire_date,
                          CASE WHEN p.current_salary IS NOT NULL THEN DATE '2026-01-01' ELSE NULL END, '[)'));

INSERT INTO employee_salary_history (
    employee_id, base_salary, effective_from, approved_by_account_id, reason, note
)
SELECT e.id, p.current_salary * 1000000, DATE '2026-01-01', admin.id,
       'Điều chỉnh lương demo 2026', 'Chỉ dùng kiểm thử'
FROM demo_people p JOIN employees e ON e.employee_code = p.code CROSS JOIN demo_audit_account admin
WHERE p.current_salary IS NOT NULL
  AND EXISTS (SELECT 1 FROM employee_salary_history h WHERE h.employee_id = e.id
              AND h.effective_from = p.hire_date AND h.effective_to = DATE '2025-12-31')
  AND NOT EXISTS (SELECT 1 FROM employee_salary_history h WHERE h.employee_id = e.id
                  AND daterange(h.effective_from, h.effective_to + 1, '[)')
                      && daterange(DATE '2026-01-01', NULL, '[)'));

INSERT INTO employee_payroll_profiles (
    employee_id, effective_from, tax_resident, social_insurance, health_insurance,
    unemployment_insurance, insurance_salary, wage_region, created_by_account_id
)
SELECT e.id, greatest(p.hire_date, DATE '2026-01-01'), true,
       p.code <> 'DEMO-INT-001', p.code <> 'DEMO-INT-001', p.code <> 'DEMO-INT-001',
       p.insurance_salary * 1000000, 1, admin.id
FROM demo_people p JOIN employees e ON e.employee_code = p.code CROSS JOIN demo_audit_account admin
WHERE NOT EXISTS (SELECT 1 FROM employee_payroll_profiles x WHERE x.employee_id = e.id);

CREATE TEMP TABLE demo_dependents (code text, full_name text, identifier text) ON COMMIT DROP;
INSERT INTO demo_dependents VALUES
    ('DEMO-DIR-001', 'Nguyễn Gia Hân', 'TEST-DEP-DIR-01'),
    ('DEMO-DIR-001', 'Nguyễn Gia Minh', 'TEST-DEP-DIR-02'),
    ('DEMO-HR-001', 'Trần An Nhiên', 'TEST-DEP-HR-01'),
    ('DEMO-SR-001', 'Hồ Anh Khoa', 'TEST-DEP-SR-01'),
    ('DEMO-HR2-001', 'Phan Gia Hân', 'TEST-DEP-HR2-001-01'),
    ('DEMO-ACC-001', 'Đinh Minh Khoa', 'TEST-DEP-ACC-001-01'),
    ('DEMO-OPH-001', 'Nguyễn Anh Khang', 'TEST-DEP-OPH-001-01'),
    ('DEMO-OPH-001', 'Nguyễn Gia Linh', 'TEST-DEP-OPH-001-02'),
    ('DEMO-HNI-001', 'Nguyễn An Nhiên', 'TEST-DEP-HNI-001-01'),
    ('DEMO-KHO-001', 'Nguyễn Minh Khôi', 'TEST-DEP-KHO-001-01');

INSERT INTO employee_tax_dependents (
    employee_id, full_name, identifier, effective_from, created_by_account_id
)
SELECT e.id, d.full_name, d.identifier, DATE '2026-01-01', admin.id
FROM demo_dependents d JOIN employees e ON e.employee_code = d.code CROSS JOIN demo_audit_account admin
WHERE NOT EXISTS (SELECT 1 FROM employee_tax_dependents x
                  WHERE x.employee_id = e.id AND x.identifier = d.identifier);

INSERT INTO company_holidays (holiday_date, name, created_at)
VALUES (DATE '2026-09-02', 'Quốc khánh 2026', TIMESTAMPTZ '2026-08-28 09:00+07'),
       (DATE '2026-09-03', 'Nghỉ Quốc khánh liền kề (lịch demo)', TIMESTAMPTZ '2026-08-28 09:00+07')
ON CONFLICT (holiday_date) DO NOTHING;

-- Materialize 2026 leave from the effective rule using the same seniority blocks
-- and start-month proration as LeaveEntitlementService. Preserve existing balances.
INSERT INTO employee_leave_entitlements (
    employee_id, year, base_minutes, carried_over_minutes, adjustment_minutes,
    standard_day_minutes, rule_id, created_by_account_id
)
SELECT e.id, 2026,
       CASE WHEN rule.id IS NULL THEN 0 ELSE round(
           (rule.base_days + CASE WHEN e.seniority_start_date <= DATE '2026-01-01'
               THEN floor(extract(year FROM age(DATE '2026-01-01', e.seniority_start_date))
                          / rule.seniority_block_years) * rule.seniority_bonus_days
               ELSE 0 END) * s.standard_work_minutes
           * CASE WHEN e.hire_date >= DATE '2026-01-01'
               THEN (13 - extract(month FROM e.hire_date)) / 12.0 ELSE 1 END
       )::integer END,
       0, 0, s.standard_work_minutes, rule.id, admin.id
FROM demo_people p JOIN employees e ON e.employee_code = p.code
JOIN work_shifts s ON s.code = 'OFFICE_DAY' AND s.deleted_at IS NULL
CROSS JOIN demo_audit_account admin
LEFT JOIN LATERAL (
    SELECT r.id, r.base_days, r.seniority_block_years, r.seniority_bonus_days
    FROM leave_entitlement_rules r
    WHERE r.effective_from <= greatest(e.hire_date, DATE '2026-01-01')
      AND (r.effective_to IS NULL OR r.effective_to >= greatest(e.hire_date, DATE '2026-01-01'))
    ORDER BY r.effective_from DESC LIMIT 1
) rule ON true
WHERE NOT EXISTS (SELECT 1 FROM employee_leave_entitlements x
                  WHERE x.employee_id = e.id AND x.year = 2026);

CREATE TEMP TABLE demo_leave (
    code text, day date, start_time time, end_time time,
    leave_type text, treatment text, minutes integer, reason text
) ON COMMIT DROP;
INSERT INTO demo_leave VALUES
    ('DEMO-SR-001', '2026-09-10', '08:00', '17:00', 'ANNUAL', 'EMPLOYER_PAID', 480, 'Nghỉ phép năm'),
    ('DEMO-TL-001', '2026-09-18', '13:00', '17:00', 'ANNUAL', 'EMPLOYER_PAID', 240, 'Nghỉ phép nửa ngày'),
    ('DEMO-NEW-001', '2026-09-25', '08:00', '17:00', 'UNPAID', 'UNPAID', 480, 'Nghỉ việc riêng không lương'),
    ('DEMO-ST-001', '2026-10-06', '08:00', '17:00', 'SICK', 'SOCIAL_INSURANCE', 480, 'Nghỉ ốm'),
    ('DEMO-HR2-002', '2026-09-16', '08:00', '17:00', 'ANNUAL', 'EMPLOYER_PAID', 480, 'Nghỉ phép năm'),
    ('DEMO-OPH-003', '2026-09-23', '13:00', '17:00', 'ANNUAL', 'EMPLOYER_PAID', 240, 'Nghỉ phép nửa ngày'),
    ('DEMO-HNI-002', '2026-10-05', '08:00', '17:00', 'SICK', 'SOCIAL_INSURANCE', 480, 'Nghỉ ốm'),
    ('DEMO-DNG-002', '2026-10-08', '08:00', '17:00', 'UNPAID', 'UNPAID', 480, 'Nghỉ việc riêng không lương');

INSERT INTO leave_requests (
    employee_id, leave_type, salary_treatment, start_at, end_at,
    requested_minutes, reason, status, submitted_at, reviewed_by_account_id,
    review_comment, reviewed_at, created_at, updated_at
)
SELECT e.id, d.leave_type, d.treatment,
       (d.day::text || ' ' || d.start_time::text || '+07')::timestamptz,
       (d.day::text || ' ' || d.end_time::text || '+07')::timestamptz,
       d.minutes, d.reason, 'APPROVED',
       ((d.day - 2)::text || ' 09:05+07')::timestamptz,
       admin.id, 'Đã duyệt dữ liệu demo',
       ((d.day - 1)::text || ' 10:00+07')::timestamptz,
       ((d.day - 2)::text || ' 09:00+07')::timestamptz,
       ((d.day - 1)::text || ' 10:00+07')::timestamptz
FROM demo_leave d JOIN employees e ON e.employee_code = d.code CROSS JOIN demo_audit_account admin
WHERE NOT EXISTS (SELECT 1 FROM leave_requests x WHERE x.employee_id = e.id
                  AND x.start_at = (d.day::text || ' ' || d.start_time::text || '+07')::timestamptz
                  AND x.end_at = (d.day::text || ' ' || d.end_time::text || '+07')::timestamptz);

CREATE TEMP TABLE demo_attendance_override (
    code text, day date, marker text, check_in time, check_out time,
    worked integer, payable integer, leave_minutes integer, late integer, early integer,
    overtime integer, multiplier numeric(8,4), status text, note text
) ON COMMIT DROP;
INSERT INTO demo_attendance_override VALUES
    ('DEMO-WH-001', '2026-09-02', 'H+', '13:00', '17:00', 240, 0, 0, 0, 0, 240, '3.0000', 'HOLIDAY', 'Trực kho ngày lễ demo'),
    ('DEMO-OPS-001', '2026-09-06', 'CN+', '08:00', '12:00', 240, 0, 0, 0, 0, 240, '2.0000', 'PRESENT', 'Trực vận hành Chủ nhật demo'),
    ('DEMO-BRD-001', '2026-09-08', 'T', '08:25', '17:00', 455, 455, 0, 10, 0, 0, '1.0000', 'PRESENT', 'Đi muộn 25 phút, trừ 15 phút cho phép'),
    ('DEMO-SR-001', '2026-09-10', 'L', NULL, NULL, 0, 480, 480, 0, 0, 0, '1.0000', 'PAID_LEAVE', 'Nghỉ phép năm'),
    ('DEMO-TL-001', '2026-09-12', 'OT', '08:00', '19:00', 600, 480, 0, 0, 0, 120, '1.5000', 'PRESENT', 'Tăng ca dự án demo'),
    ('DEMO-TL-001', '2026-09-18', 'LP', '08:00', '12:00', 240, 480, 240, 0, 0, 0, '1.0000', 'PRESENT', 'Làm sáng, nghỉ phép chiều'),
    ('DEMO-ST-001', '2026-09-22', 'E', '08:00', '16:30', 450, 450, 0, 0, 30, 0, '1.0000', 'PRESENT', 'Về sớm 30 phút'),
    ('DEMO-NEW-001', '2026-09-25', 'U', NULL, NULL, 0, 0, 480, 0, 0, 0, '1.0000', 'UNPAID_LEAVE', 'Nghỉ không lương'),
    ('DEMO-ST-001', '2026-10-06', 'S', NULL, NULL, 0, 0, 480, 0, 0, 0, '1.0000', 'SICK_LEAVE', 'Nghỉ ốm'),
    ('DEMO-PAY-001', '2026-10-07', 'A', NULL, NULL, 0, 0, 0, 0, 0, 0, '1.0000', 'UNAUTHORIZED_ABSENCE', 'Vắng không phép, HR đã xác nhận'),
    ('DEMO-INT-001', '2026-10-10', 'M', '08:05', NULL, 0, 0, 0, 0, 0, 0, '1.0000', 'MISSING_PUNCH', 'Thiếu chấm ra, chờ xử lý'),
    ('DEMO-KHO-001', '2026-09-02', 'H+', '13:00', '17:00', 240, 0, 0, 0, 0, 240, '3.0000', 'HOLIDAY', 'Trực kho ngày lễ demo'),
    ('DEMO-OPH-001', '2026-09-06', 'CN+', '08:00', '12:00', 240, 0, 0, 0, 0, 240, '2.0000', 'PRESENT', 'Trực vận hành Chủ nhật demo'),
    ('DEMO-DNG-001', '2026-09-08', 'T', '08:25', '17:00', 455, 455, 0, 10, 0, 0, '1.0000', 'PRESENT', 'Đi muộn 25 phút, trừ 15 phút cho phép'),
    ('DEMO-OPH-002', '2026-09-12', 'OT', '08:00', '19:00', 600, 480, 0, 0, 0, 120, '1.5000', 'PRESENT', 'Tăng ca dự án demo'),
    ('DEMO-HR2-002', '2026-09-16', 'L', NULL, NULL, 0, 480, 480, 0, 0, 0, '1.0000', 'PAID_LEAVE', 'Nghỉ phép năm'),
    ('DEMO-OPH-003', '2026-09-23', 'LP', '08:00', '12:00', 240, 480, 240, 0, 0, 0, '1.0000', 'PRESENT', 'Làm sáng, nghỉ phép chiều'),
    ('DEMO-HNI-003', '2026-09-25', 'E', '08:00', '16:30', 450, 450, 0, 0, 30, 0, '1.0000', 'PRESENT', 'Về sớm 30 phút'),
    ('DEMO-HNI-002', '2026-10-05', 'S', NULL, NULL, 0, 0, 480, 0, 0, 0, '1.0000', 'SICK_LEAVE', 'Nghỉ ốm'),
    ('DEMO-ACC-002', '2026-10-07', 'A', NULL, NULL, 0, 0, 0, 0, 0, 0, '1.0000', 'UNAUTHORIZED_ABSENCE', 'Vắng không phép, HR đã xác nhận'),
    ('DEMO-DNG-002', '2026-10-08', 'U', NULL, NULL, 0, 0, 480, 0, 0, 0, '1.0000', 'UNPAID_LEAVE', 'Nghỉ không lương'),
    ('DEMO-KHO-002', '2026-10-09', 'T', '08:25', '17:00', 455, 455, 0, 10, 0, 0, '1.0000', 'PRESENT', 'Đi muộn 25 phút, trừ 15 phút cho phép')
;

WITH calendar AS (
    SELECT d::date AS day
    FROM generate_series(DATE '2026-09-01', DATE '2026-10-10', INTERVAL '1 day') AS d
), rows AS (
    SELECT p.code, c.day, o.marker, o.check_in, o.check_out,
           o.worked, o.payable, o.leave_minutes, o.late, o.early,
           o.overtime, o.multiplier, o.status, o.note,
           h.name AS holiday_name
    FROM demo_people p CROSS JOIN calendar c
    LEFT JOIN demo_attendance_override o ON o.code = p.code AND o.day = c.day
    LEFT JOIN company_holidays h ON h.holiday_date = c.day
    WHERE c.day >= p.hire_date
      AND (extract(isodow FROM c.day) <= 6 OR o.marker = 'CN+')
)
INSERT INTO attendance_records (
    employee_id, work_date, shift_id, leave_request_id,
    scheduled_start_at, scheduled_end_at, scheduled_minutes,
    check_in_at, check_out_at, worked_minutes, payable_minutes, leave_minutes,
    late_minutes, early_leave_minutes, overtime_minutes, overtime_multiplier,
    overtime_tax_exempt, overtime_approved_by_account_id, overtime_approved_at,
    status, note, updated_by_account_id, created_at, updated_at
)
SELECT e.id, x.day, s.id,
       lr.id,
       (x.day::text || ' 08:00+07')::timestamptz,
       (x.day::text || ' 17:00+07')::timestamptz,
       480,
       CASE WHEN x.marker IS NOT NULL THEN
           CASE WHEN x.check_in IS NULL THEN NULL ELSE (x.day::text || ' ' || x.check_in::text || '+07')::timestamptz END
           WHEN x.holiday_name IS NULL THEN (x.day::text || ' 08:00+07')::timestamptz ELSE NULL END,
       CASE WHEN x.marker IS NOT NULL THEN
           CASE WHEN x.check_out IS NULL THEN NULL ELSE (x.day::text || ' ' || x.check_out::text || '+07')::timestamptz END
           WHEN x.holiday_name IS NULL THEN (x.day::text || ' 17:00+07')::timestamptz ELSE NULL END,
       coalesce(x.worked, CASE WHEN x.holiday_name IS NULL THEN 480 ELSE 0 END),
       coalesce(x.payable, CASE WHEN x.holiday_name IS NULL THEN 480 ELSE 0 END),
       coalesce(x.leave_minutes, 0), coalesce(x.late, 0), coalesce(x.early, 0),
       coalesce(x.overtime, 0), coalesce(x.multiplier, 1), false,
       CASE WHEN x.overtime > 0 THEN admin.id ELSE NULL END,
       CASE WHEN x.overtime > 0 THEN ((x.day + 1)::text || ' 09:00+07')::timestamptz ELSE NULL END,
       coalesce(x.status, CASE WHEN x.holiday_name IS NULL THEN 'PRESENT' ELSE 'HOLIDAY' END),
       coalesce(x.note, x.holiday_name),
       CASE WHEN x.marker = 'A' THEN admin.id ELSE NULL END,
       (x.day::text || ' 07:00+07')::timestamptz,
       CASE WHEN x.overtime > 0 OR x.marker = 'A' THEN
           ((x.day + 1)::text || ' 09:00+07')::timestamptz
           ELSE (x.day::text || ' 20:00+07')::timestamptz END
FROM rows x JOIN employees e ON e.employee_code = x.code
JOIN work_shifts s ON s.code = 'OFFICE_DAY' AND s.deleted_at IS NULL
CROSS JOIN demo_audit_account admin
LEFT JOIN demo_leave d ON d.code = x.code AND d.day = x.day
LEFT JOIN leave_requests lr ON lr.employee_id = e.id
    AND lr.start_at = (d.day::text || ' ' || d.start_time::text || '+07')::timestamptz
    AND lr.end_at = (d.day::text || ' ' || d.end_time::text || '+07')::timestamptz
ON CONFLICT (employee_id, work_date) DO NOTHING;

DO $$
BEGIN
    IF (SELECT count(*) FROM employees WHERE employee_code IN (SELECT code FROM demo_people)) <> 36
       OR (SELECT count(*) FROM attendance_records a JOIN employees e ON e.id = a.employee_id
           WHERE e.employee_code IN (SELECT code FROM demo_people)
             AND a.work_date BETWEEN DATE '2026-09-01' AND DATE '2026-10-10') < 1236 THEN
        RAISE EXCEPTION 'Demo seed count check failed';
    END IF;
END $$;

SELECT
    (SELECT count(*) FROM employees e JOIN demo_people p ON p.code = e.employee_code) AS employees,
    (SELECT count(*) FROM employee_assignments a JOIN employees e ON e.id = a.employee_id
     JOIN demo_people p ON p.code = e.employee_code) AS assignments,
    (SELECT count(*) FROM employee_salary_history h JOIN employees e ON e.id = h.employee_id
     JOIN demo_people p ON p.code = e.employee_code) AS salary_rows,
    (SELECT count(*) FROM employee_payroll_profiles h JOIN employees e ON e.id = h.employee_id
     JOIN demo_people p ON p.code = e.employee_code) AS payroll_profiles,
    (SELECT count(*) FROM employee_tax_dependents d JOIN employees e ON e.id = d.employee_id
     JOIN demo_people p ON p.code = e.employee_code) AS dependents,
    (SELECT count(*) FROM employee_leave_entitlements x JOIN employees e ON e.id = x.employee_id
     JOIN demo_people p ON p.code = e.employee_code WHERE x.year = 2026) AS leave_entitlements,
    (SELECT count(*) FROM leave_requests l JOIN employees e ON e.id = l.employee_id
     JOIN demo_people p ON p.code = e.employee_code
     WHERE l.start_at >= TIMESTAMPTZ '2026-09-01 00:00+07'
       AND l.start_at < TIMESTAMPTZ '2026-10-11 00:00+07') AS leave_requests,
    (SELECT count(*) FROM attendance_records a JOIN employees e ON e.id = a.employee_id
     JOIN demo_people p ON p.code = e.employee_code
     WHERE a.work_date BETWEEN DATE '2026-09-01' AND DATE '2026-10-10') AS attendance_records;

COMMIT;
