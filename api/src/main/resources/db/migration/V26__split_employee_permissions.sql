-- Tách employee.read/employee.manage thành các quyền nguyên tử theo từng use case.
-- employee.read tiếp tục đại diện cho quyền xem chi tiết; employee.manage được ngừng sử dụng.

INSERT INTO permissions (code, name, module, description, assignment_policy, is_active, created_at)
VALUES
    ('employee.list.read', 'Xem danh sách nhân viên', 'EMPLOYEE', 'Xem danh sách nhân viên trong phạm vi được phân công', 'DELEGABLE', true, now()),
    ('employee.create', 'Tạo hồ sơ nhân viên', 'EMPLOYEE', 'Tạo hồ sơ nhân viên và phân công ban đầu', 'DELEGABLE', true, now()),
    ('employee.update', 'Cập nhật hồ sơ nhân viên', 'EMPLOYEE', 'Cập nhật thông tin hồ sơ nhân viên trong phạm vi được phân công', 'DELEGABLE', true, now()),
    ('employee.probation.confirm', 'Xác nhận hoàn thành thử việc', 'EMPLOYEE', 'Xác nhận nhân viên thử việc trở thành nhân viên chính thức', 'DELEGABLE', true, now()),
    ('employee.assignment.read', 'Xem phân công nhân viên', 'EMPLOYEE', 'Xem phân công hiện tại và lịch sử phân công của nhân viên', 'DELEGABLE', true, now()),
    ('employee.assignment.manage', 'Quản lý phân công nhân viên', 'EMPLOYEE', 'Điều chuyển, bổ nhiệm và thay đổi phân công của nhân viên', 'DELEGABLE', true, now()),
    ('employee.lifecycle.manage', 'Quản lý vòng đời nhân viên', 'EMPLOYEE', 'Thực hiện nghiệp vụ vòng đời nhân viên đã được phê duyệt', 'DELEGABLE', true, now()),
    ('employee.delete', 'Xóa hồ sơ nhân viên', 'EMPLOYEE', 'Xóa mềm hồ sơ nhân viên chưa phát sinh dữ liệu nghiệp vụ', 'DELEGABLE', true, now())
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    module = EXCLUDED.module,
    description = EXCLUDED.description,
    assignment_policy = EXCLUDED.assignment_policy,
    is_active = true;

UPDATE permissions
SET name = 'Xem chi tiết nhân viên',
    description = 'Xem chi tiết hồ sơ nhân viên trong phạm vi được phân công'
WHERE code = 'employee.read';

-- Role từng có quyền đọc tổng quát vẫn xem được danh sách, chi tiết và phân công.
INSERT INTO role_permissions (role_id, permission_id, created_by_account_id, created_at)
SELECT legacy_mapping.role_id, new_permission.id, legacy_mapping.created_by_account_id, now()
FROM role_permissions legacy_mapping
JOIN permissions legacy_permission
  ON legacy_permission.id = legacy_mapping.permission_id
JOIN permissions new_permission
  ON new_permission.code IN ('employee.list.read', 'employee.assignment.read')
WHERE legacy_permission.code = 'employee.read'
ON CONFLICT DO NOTHING;

-- Role từng có quyền manage nhận đủ các quyền ghi tương đương để không mất chức năng.
INSERT INTO role_permissions (role_id, permission_id, created_by_account_id, created_at)
SELECT legacy_mapping.role_id, new_permission.id, legacy_mapping.created_by_account_id, now()
FROM role_permissions legacy_mapping
JOIN permissions legacy_permission
  ON legacy_permission.id = legacy_mapping.permission_id
JOIN permissions new_permission
  ON new_permission.code IN (
      'employee.create',
      'employee.update',
      'employee.probation.confirm',
      'employee.assignment.manage',
      'employee.lifecycle.manage',
      'employee.delete'
  )
WHERE legacy_permission.code = 'employee.manage'
ON CONFLICT DO NOTHING;

-- Sao chép các override còn hiệu lực khi chưa tồn tại override mới bị chồng khoảng ngày.
INSERT INTO account_permission_overrides (
    account_role_assignment_id,
    permission_id,
    effect,
    effective_from,
    effective_to,
    reason,
    granted_by_account_id,
    created_at
)
SELECT
    legacy_override.account_role_assignment_id,
    new_permission.id,
    legacy_override.effect,
    legacy_override.effective_from,
    legacy_override.effective_to,
    legacy_override.reason,
    legacy_override.granted_by_account_id,
    legacy_override.created_at
FROM account_permission_overrides legacy_override
JOIN permissions legacy_permission
  ON legacy_permission.id = legacy_override.permission_id
JOIN permissions new_permission
  ON (
      legacy_permission.code = 'employee.read'
      AND new_permission.code IN ('employee.list.read', 'employee.assignment.read')
  ) OR (
      legacy_permission.code = 'employee.manage'
      AND new_permission.code IN (
          'employee.create',
          'employee.update',
          'employee.probation.confirm',
          'employee.assignment.manage',
          'employee.lifecycle.manage',
          'employee.delete'
      )
  )
WHERE legacy_override.revoked_at IS NULL
  AND NOT EXISTS (
      SELECT 1
      FROM account_permission_overrides existing_override
      WHERE existing_override.account_role_assignment_id = legacy_override.account_role_assignment_id
        AND existing_override.permission_id = new_permission.id
        AND existing_override.revoked_at IS NULL
        AND daterange(existing_override.effective_from, existing_override.effective_to, '[]')
            && daterange(legacy_override.effective_from, legacy_override.effective_to, '[]')
  );

DELETE FROM role_permissions role_permission
USING permissions permission
WHERE role_permission.permission_id = permission.id
  AND permission.code = 'employee.manage';

UPDATE permissions
SET is_active = false
WHERE code = 'employee.manage';
