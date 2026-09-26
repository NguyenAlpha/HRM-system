-- Workflow Director độc lập đã bị loại bỏ; Company Owner đầu tiên nhận DIRECTOR
-- ngay trong workflow bootstrap và các lần chuyển giao dùng API role assignment.
DELETE FROM account_permission_overrides override_entry
USING permissions permission
WHERE override_entry.permission_id = permission.id
  AND permission.code = 'organization.director.provision';

DELETE FROM role_permissions role_permission
USING permissions permission
WHERE role_permission.permission_id = permission.id
  AND permission.code = 'organization.director.provision';

DELETE FROM permissions
WHERE code = 'organization.director.provision';
