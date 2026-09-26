-- Chính sách xác định workflow được phép dùng để cấp từng role cho account.
ALTER TABLE roles
    ADD COLUMN grant_policy VARCHAR(30);

-- Custom role và các system role cần phê duyệt mặc định đi qua Company Owner.
UPDATE roles
SET grant_policy = 'OWNER_APPROVAL';

UPDATE roles
SET grant_policy = 'AUTO'
WHERE code = 'EMPLOYEE';

UPDATE roles
SET grant_policy = 'HR_ASSIGNABLE'
WHERE code IN ('TEAM_LEAD', 'WAREHOUSE_SUPERVISOR');

UPDATE roles
SET grant_policy = 'SYSTEM_ONLY'
WHERE code IN ('COMPANY_OWNER', 'SYSTEM_ADMIN');

ALTER TABLE roles
    ALTER COLUMN grant_policy SET NOT NULL,
    ADD CONSTRAINT chk_roles_grant_policy
        CHECK (grant_policy IN ('AUTO', 'HR_ASSIGNABLE', 'OWNER_APPROVAL', 'SYSTEM_ONLY'));
