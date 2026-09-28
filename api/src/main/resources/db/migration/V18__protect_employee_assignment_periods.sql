-- Bảo vệ lịch sử phân công chính của nhân sự khỏi kỳ ngày sai hoặc chồng lấn.

ALTER TABLE employee_assignments
    ADD CONSTRAINT chk_employee_assignments_period
        CHECK (effective_to IS NULL OR effective_to >= effective_from);

ALTER TABLE employee_assignments
    ADD CONSTRAINT excl_employee_primary_assignment_overlap
    EXCLUDE USING GIST (
        employee_id WITH =,
        (DATERANGE(effective_from, effective_to + 1, '[)')) WITH &&
    ) WHERE (is_primary);
