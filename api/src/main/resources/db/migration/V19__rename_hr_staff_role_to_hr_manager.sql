-- Đổi tên system role nhân sự, giữ nguyên role ID để bảo toàn permission và assignment hiện có.
UPDATE roles
SET code = 'HR_MANAGER',
    name = 'Quản lý nhân sự',
    description = 'Quản lý nghiệp vụ nhân sự trên toàn công ty',
    updated_at = now()
WHERE code = 'HR_STAFF';
