package com.htttdn.hrm.entity;

import java.time.Instant;

import org.springframework.data.annotation.LastModifiedDate;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

import lombok.Getter;

/**
 * Lớp cha của entity có cả {@code created_at} và {@code updated_at}, do Spring Data JPA auditing tự ghi.
 *
 * <p>{@code @LastModifiedDate}: listener (khai báo ở {@link CreatedAtEntity}) ghi thời điểm hiện tại
 * vào field này khi entity được insert, và mỗi lần Hibernate update một entity đã đổi. Lưu ý thời
 * điểm: việc update chỉ xảy ra khi Hibernate <em>flush</em> (thường lúc transaction commit), nên ngay
 * sau khi đổi field, {@code getUpdatedAt()} vẫn trả giá trị cũ cho tới khi flush. Method nào trả
 * {@code updatedAt} về cho client ngay sau khi sửa thì phải {@code flush()} trước.
 *
 * <p>Entity không đổi field nào thì không bị update, nên {@code updatedAt} cũng không đổi.
 */
@MappedSuperclass
@Getter
public abstract class AuditedEntity extends CreatedAtEntity {

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
