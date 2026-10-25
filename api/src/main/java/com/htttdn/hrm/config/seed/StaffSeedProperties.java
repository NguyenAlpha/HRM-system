package com.htttdn.hrm.config.seed;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.core.io.Resource;

/**
 * Cấu hình {@code staff.seed.*} cho {@link StaffSeeder}.
 * Cách Spring nạp record này: xem {@link AdminSeedProperties}.
 *
 * @param enabled có seed nhân sự mẫu khi khởi động hay không
 * @param file    file JSON chứa nhân sự mẫu; Spring tự đổi chuỗi như {@code classpath:seed/staff.json}
 *                hoặc {@code file:/path/staff.json} thành {@link Resource}
 */
@ConfigurationProperties("staff.seed")
public record StaffSeedProperties(
    @DefaultValue("true") boolean enabled,
    @DefaultValue("classpath:seed/staff.json") Resource file
) { }
