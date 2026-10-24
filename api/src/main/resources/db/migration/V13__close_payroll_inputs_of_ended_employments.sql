-- Từ nay kết thúc làm việc đóng lương cơ bản, hồ sơ bảo hiểm/thuế và người phụ thuộc còn chạy quá ngày
-- làm việc cuối tại đúng ngày đó. Đóng như vậy cho những người đã nghỉ trước thay đổi này. Lương đã
-- dừng ở ngày làm việc cuối nên không phiếu lương nào đổi; bản ghi bắt đầu sau ngày đó giữ nguyên.
UPDATE employee_salary_history salary
SET effective_to = employee.termination_date
FROM employees employee
WHERE salary.employee_id = employee.id
  AND employee.employment_status IN ('RESIGNED', 'TERMINATED', 'RETIRED')
  AND employee.termination_date IS NOT NULL
  AND salary.effective_from <= employee.termination_date
  AND (salary.effective_to IS NULL OR salary.effective_to > employee.termination_date);

UPDATE employee_payroll_profiles profile
SET effective_to = employee.termination_date
FROM employees employee
WHERE profile.employee_id = employee.id
  AND employee.employment_status IN ('RESIGNED', 'TERMINATED', 'RETIRED')
  AND employee.termination_date IS NOT NULL
  AND profile.effective_from <= employee.termination_date
  AND (profile.effective_to IS NULL OR profile.effective_to > employee.termination_date);

UPDATE employee_tax_dependents dependent
SET effective_to = employee.termination_date
FROM employees employee
WHERE dependent.employee_id = employee.id
  AND employee.employment_status IN ('RESIGNED', 'TERMINATED', 'RETIRED')
  AND employee.termination_date IS NOT NULL
  AND dependent.effective_from <= employee.termination_date
  AND (dependent.effective_to IS NULL OR dependent.effective_to > employee.termination_date);
