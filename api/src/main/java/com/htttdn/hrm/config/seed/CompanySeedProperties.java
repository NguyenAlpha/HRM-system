package com.htttdn.hrm.config.seed;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Cấu hình {@code company.seed.*}: bật/tắt dữ liệu nền của công ty (hồ sơ công ty, cơ cấu tổ chức,
 * chức danh, ca làm, ngày lễ, chính sách phụ cấp). Dùng chung cho các seeder đó.
 * Cách Spring nạp record này: xem {@link AdminSeedProperties}.
 *
 * @param enabled có seed dữ liệu nền khi khởi động hay không
 */
@ConfigurationProperties("company.seed")
public record CompanySeedProperties(@DefaultValue("true") boolean enabled) { }
