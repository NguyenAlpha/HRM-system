package com.htttdn.hrm.service.account;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import com.htttdn.hrm.config.seed.AdminSeedProperties;

/**
 * Cấu hình {@code account.*} cho {@link AccountActivationService}.
 * Cách Spring nạp record này: xem {@link AdminSeedProperties}.
 *
 * @param activationTokenExpirationHours thời hạn link kích hoạt tài khoản, tính bằng giờ; phải dương
 */
@ConfigurationProperties("account")
public record AccountActivationProperties(@DefaultValue("24") long activationTokenExpirationHours) {

    /**
     * Compact constructor của record: chạy khi Spring tạo record, nên giá trị sai làm ứng dụng
     * dừng ngay lúc khởi động thay vì lúc người dùng kích hoạt tài khoản.
     */
    public AccountActivationProperties {
        if (activationTokenExpirationHours <= 0) {
            throw new IllegalArgumentException("Account activation token expiration must be positive");
        }
    }
}
