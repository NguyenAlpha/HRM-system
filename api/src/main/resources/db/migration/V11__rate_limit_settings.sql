-- Hạn mức rate-limit do quản trị viên chỉnh trên giao diện, ghi đè giá trị trong application.properties.
-- Mức không có dòng nào dùng giá trị trong application.properties và luôn bật.
CREATE TABLE rate_limit_policy_settings (
    policy VARCHAR(30) PRIMARY KEY,
    enabled BOOLEAN NOT NULL,
    max_requests INTEGER NOT NULL,
    window_seconds INTEGER NOT NULL,
    updated_by_account_id BIGINT NOT NULL REFERENCES accounts (id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_rate_limit_policy CHECK (policy IN (
        'login', 'refresh', 'activation', 'ip-ceiling', 'login-failure',
        'api', 'password', 'export', 'report', 'heavy-write'
    )),
    CONSTRAINT chk_rate_limit_max_requests CHECK (max_requests BETWEEN 1 AND 100000),
    CONSTRAINT chk_rate_limit_window_seconds CHECK (window_seconds BETWEEN 1 AND 86400),
    -- Chống dò mật khẩu không được tắt.
    CONSTRAINT chk_rate_limit_login_failure_enabled CHECK (policy <> 'login-failure' OR enabled)
);
