package com.htttdn.hrm.config.seed;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Cấu hình {@code payroll.seed.*} cho {@link PayrollSeeder}.
 * Cách Spring nạp record này: xem {@link AdminSeedProperties}.
 *
 * @param enabled có seed các kỳ lương mẫu khi khởi động hay không
 */
@ConfigurationProperties("payroll.seed")
public record PayrollSeedProperties(@DefaultValue("true") boolean enabled) { }
