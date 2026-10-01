-- Tạm thời ngừng cung cấp system role TEAM_LEAD.
-- Giữ role và các assignment lịch sử để phục vụ audit, nhưng role bị soft-delete
-- nên không còn xuất hiện trong danh mục, không thể được cấp mới và không tham gia authorization.

DELETE FROM role_permissions
WHERE role_id IN (
    SELECT id
    FROM roles
    WHERE code = 'TEAM_LEAD'
);

UPDATE roles
SET is_active = false,
    deleted_at = COALESCE(deleted_at, now()),
    updated_at = now()
WHERE code = 'TEAM_LEAD';
