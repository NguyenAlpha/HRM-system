package com.htttdn.hrm.config.seed;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Cấu hình {@code rbac.seed.*}: bật/tắt seed quyền, vai trò và quyền của từng vai trò.
 * Cách Spring nạp record này: xem {@link AdminSeedProperties}.
 *
 * @param enabled có seed dữ liệu phân quyền khi khởi động hay không
 */
@ConfigurationProperties("rbac.seed")
public record RbacSeedProperties(@DefaultValue("true") boolean enabled) { }
