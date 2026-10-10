-- account_permission_overrides có hai CHECK cùng điều kiện effective_to >= effective_from.
-- Giữ chk_permission_override_period, cùng tiền tố với các ràng buộc còn lại của bảng.
ALTER TABLE account_permission_overrides
    DROP CONSTRAINT chk_account_permission_override_period;
