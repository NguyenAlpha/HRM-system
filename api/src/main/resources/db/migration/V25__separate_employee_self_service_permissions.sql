-- Quyền tự phục vụ chỉ đến từ role EMPLOYEE / SELF.
-- Các role nghiệp vụ được gán riêng và không lặp lại bộ permission của EMPLOYEE.

DELETE FROM role_permissions role_permission
USING roles role, permissions permission
WHERE role_permission.role_id = role.id
  AND role_permission.permission_id = permission.id
  AND role.code IN (
      'HR_MANAGER',
      'PAYROLL_ACCOUNTANT',
      'PAYROLL_APPROVER',
      'DIRECTOR'
  )
  AND permission.code IN (
      'profile.self.read',
      'profile.self.update',
      'request.self.read',
      'request.self.create',
      'request.self.cancel',
      'attendance.self.read',
      'payroll.self.read',
      'payroll.self.print'
  );
