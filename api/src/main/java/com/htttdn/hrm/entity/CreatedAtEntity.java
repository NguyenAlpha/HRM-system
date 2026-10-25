package com.htttdn.hrm.entity;

import java.time.Instant;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;

import lombok.Getter;

/**
 * Lớp cha của entity có cột {@code created_at}, do Spring Data JPA auditing tự ghi.
 *
 * <h3>Cách hoạt động</h3>
 * <ul>
 *   <li>{@code @MappedSuperclass} (chuẩn JPA): lớp này không phải bảng riêng; các field của nó trở
 *       thành cột của bảng của từng entity con.</li>
 *   <li>{@code @EntityListeners(AuditingEntityListener.class)}: Spring Data được gọi mỗi khi entity
 *       sắp được insert/update. Chỉ hoạt động khi đã bật {@code @EnableJpaAuditing}
 *       (xem {@code config.JpaAuditingConfig}).</li>
 *   <li>{@code @CreatedDate}: khi entity được lưu lần đầu ({@code repository.save(...)} một entity
 *       mới), listener ghi thời điểm hiện tại vào field này. {@code updatable = false} để những lần
 *       update sau không bao giờ đổi cột này.</li>
 * </ul>
 * Vì vậy code nghiệp vụ không gán {@code createdAt}; lớp này cố ý không có setter.
 *
 * <p>Dữ liệu mẫu cần ngày trong quá khứ thì lùi ngày sau khi lưu, qua
 * {@code config.seed.SeedTimestamps}.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
public abstract class CreatedAtEntity {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
