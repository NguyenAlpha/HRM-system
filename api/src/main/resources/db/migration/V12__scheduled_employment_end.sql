-- HR kết thúc làm việc với ngày làm việc cuối trong tương lai: termination_date và termination_reason
-- được ghi ngay, còn trạng thái, phân công, tài khoản và role chỉ đóng sau ngày đó (job 00:10 mỗi đêm).
-- scheduled_end_status là trạng thái sẽ đặt; scheduled_end_by_account_id là người lên lịch, dùng làm
-- người thu hồi role khi job chạy.
ALTER TABLE employees
    ADD COLUMN scheduled_end_status VARCHAR(20),
    ADD COLUMN scheduled_end_by_account_id BIGINT REFERENCES accounts (id),
    ADD CONSTRAINT chk_employees_scheduled_end CHECK (
        (scheduled_end_status IS NULL AND scheduled_end_by_account_id IS NULL)
        OR (scheduled_end_status IN ('RESIGNED', 'TERMINATED', 'RETIRED')
            AND scheduled_end_by_account_id IS NOT NULL
            AND employment_status IN ('PROBATION', 'ACTIVE')
            AND termination_date IS NOT NULL
            AND termination_reason IS NOT NULL)
    );

CREATE INDEX idx_employees_scheduled_end ON employees (termination_date) WHERE scheduled_end_status IS NOT NULL;
