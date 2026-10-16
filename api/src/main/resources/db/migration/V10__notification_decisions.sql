ALTER TABLE notifications DROP CONSTRAINT chk_notification_type;

ALTER TABLE notifications ADD CONSTRAINT chk_notification_type
    CHECK (type IN ('LEAVE_SUBMITTED', 'LEAVE_APPROVED', 'LEAVE_REJECTED'));
