-- Unique theo LOWER(...) đã bao hàm unique phân biệt hoa thường, nên bỏ bản thường.
-- Truy vấn so khớp chính xác theo các cột này chỉ còn ở seeder khi khởi động.
ALTER TABLE employees DROP CONSTRAINT employees_employee_code_key;
ALTER TABLE employees DROP CONSTRAINT employees_work_email_key;
ALTER TABLE accounts DROP CONSTRAINT accounts_email_key;

-- uq_attendance_records_employee_date (employee_id, work_date) phục vụ cả quét giảm dần theo ngày.
DROP INDEX idx_attendance_employee_date;
