ALTER TABLE attendance_records
    ADD COLUMN leave_minutes INTEGER NOT NULL DEFAULT 0 CHECK (leave_minutes >= 0);
