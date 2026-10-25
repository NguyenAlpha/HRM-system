package com.htttdn.hrm.config.seed;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Cấu hình {@code owner.seed.*} cho {@link CompanyOwnerSeeder}.
 * Cách Spring nạp record này: xem {@link AdminSeedProperties}.
 *
 * @param enabled có tạo chủ công ty mẫu khi khởi động hay không
 */
@ConfigurationProperties("owner.seed")
public record OwnerSeedProperties(@DefaultValue("true") boolean enabled) { }
