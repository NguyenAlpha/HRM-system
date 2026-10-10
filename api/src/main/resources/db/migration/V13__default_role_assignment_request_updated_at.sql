-- Đồng bộ với các bảng có updated_at khác: giá trị mặc định là thời điểm tạo dòng.
ALTER TABLE role_assignment_requests
    ALTER COLUMN updated_at SET DEFAULT now();
