package com.htttdn.hrm.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Bật Spring Data JPA auditing: các field {@code @CreatedDate} / {@code @LastModifiedDate}
 * (xem {@code entity.CreatedAtEntity}) được tự ghi khi lưu entity.
 *
 * <p>Đặt ở class cấu hình riêng thay vì trên {@code HrmApplication}, để các test chỉ dựng một phần
 * ứng dụng (ví dụ {@code @WebMvcTest}, không có JPA) không bị bật auditing theo.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
