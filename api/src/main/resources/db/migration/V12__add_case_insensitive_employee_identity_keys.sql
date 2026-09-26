-- Employee identity values used for account provisioning must be unique regardless of letter case.

CREATE UNIQUE INDEX uq_employees_employee_code_lower
    ON employees (LOWER(employee_code));

CREATE UNIQUE INDEX uq_employees_work_email_lower
    ON employees (LOWER(work_email))
    WHERE work_email IS NOT NULL;
