package com.htttdn.hrm.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.Role;
import com.htttdn.hrm.exception.ResourceNotFoundException;

import jakarta.persistence.LockModeType;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByCodeAndDeletedAtIsNull(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT role FROM Role role WHERE role.code = :code AND role.deletedAt IS NULL")
    Optional<Role> findByCodeForUpdate(@Param("code") String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT role FROM Role role WHERE role.id = :id AND role.deletedAt IS NULL")
    Optional<Role> findByIdForUpdate(@Param("id") Long id);

    boolean existsByCodeAndDeletedAtIsNull(String code);

    boolean existsByCode(String code);

    Page<Role> findByDeletedAtIsNull(Pageable pageable);

    List<Role> findByDeletedAtIsNullOrderByIdAsc();

    Optional<Role> findByIdAndDeletedAtIsNull(Long id);

    /** Role chưa bị xóa mềm, không có thì 404 {@code ROLE_NOT_FOUND}. Xem {@link AccountRepository#getRequired}. */
    default Role getRequired(Long id) {
        return findByIdAndDeletedAtIsNull(id).orElseThrow(() -> notFound(id));
    }

    /** Như {@link #getRequired}, kèm khóa ghi ({@link #findByIdForUpdate}). */
    default Role getRequiredForUpdate(Long id) {
        return findByIdForUpdate(id).orElseThrow(() -> notFound(id));
    }

    private static ResourceNotFoundException notFound(Long id) {
        return new ResourceNotFoundException(ErrorCode.ROLE_NOT_FOUND, "Role not found: " + id);
    }
}
