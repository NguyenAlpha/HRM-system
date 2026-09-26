-- Tên ngắn, dễ đọc dành cho giao diện; code vẫn là định danh kỹ thuật ổn định.
ALTER TABLE permissions
    ADD COLUMN name VARCHAR(150);

-- Dùng mô tả hiện có để mọi permission tùy chỉnh cũng có tên hợp lệ sau migration.
UPDATE permissions
SET name = LEFT(description, 150);

ALTER TABLE permissions
    ALTER COLUMN name SET NOT NULL;
