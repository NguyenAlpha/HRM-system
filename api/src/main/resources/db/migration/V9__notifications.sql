CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    recipient_account_id BIGINT NOT NULL REFERENCES accounts (id),
    leave_request_id BIGINT NOT NULL REFERENCES leave_requests (id),
    type VARCHAR(40) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    read_at TIMESTAMPTZ,
    CONSTRAINT chk_notification_type CHECK (type IN ('LEAVE_SUBMITTED')),
    CONSTRAINT uq_notification_recipient_leave_type UNIQUE (recipient_account_id, leave_request_id, type)
);

CREATE INDEX idx_notifications_recipient_created
    ON notifications (recipient_account_id, created_at DESC);

CREATE INDEX idx_notifications_recipient_unread
    ON notifications (recipient_account_id, created_at DESC) WHERE read_at IS NULL;
