-- Phân biệt permission có thể gán cho custom role và permission dành riêng cho system role.
ALTER TABLE permissions
    ADD COLUMN assignment_policy VARCHAR(20);

UPDATE permissions
SET assignment_policy = 'DELEGABLE';

UPDATE permissions
SET assignment_policy = 'SYSTEM_ONLY'
WHERE code = 'organization.company_owner.bootstrap';

-- Thu hồi mapping không hợp lệ có thể đã được tạo trước khi chính sách này tồn tại.
DELETE FROM role_permissions rp
USING roles r, permissions p
WHERE rp.role_id = r.id
  AND rp.permission_id = p.id
  AND r.is_system = false
  AND p.assignment_policy = 'SYSTEM_ONLY';

ALTER TABLE permissions
    ALTER COLUMN assignment_policy SET NOT NULL,
    ADD CONSTRAINT chk_permissions_assignment_policy
        CHECK (assignment_policy IN ('DELEGABLE', 'SYSTEM_ONLY'));
