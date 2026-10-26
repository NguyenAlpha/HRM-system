package com.htttdn.hrm.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.OrganizationUnit;
import com.htttdn.hrm.exception.ResourceNotFoundException;

import jakarta.persistence.LockModeType;

public interface OrganizationUnitRepository extends JpaRepository<OrganizationUnit, Long> {

    Optional<OrganizationUnit> findByCodeAndDeletedAtIsNull(String code);

    Optional<OrganizationUnit> findByIdAndDeletedAtIsNull(Long id);

    boolean existsByCodeAndDeletedAtIsNull(String code);

    boolean existsByParentUnitIdAndDeletedAtIsNull(Long parentUnitId);

    List<OrganizationUnit> findByParentUnitIdAndDeletedAtIsNull(Long parentUnitId);

    @EntityGraph(attributePaths = "parentUnit")
    List<OrganizationUnit> findByDeletedAtIsNullOrderByNameAsc();

    Page<OrganizationUnit> findByDeletedAtIsNull(Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT unit FROM OrganizationUnit unit WHERE unit.id = :id AND unit.deletedAt IS NULL")
    Optional<OrganizationUnit> findByIdForUpdate(@Param("id") Long id);

    /** Đơn vị chưa xóa theo id, không có thì 404 {@code ORGANIZATION_UNIT_NOT_FOUND}. Xem {@link AccountRepository#getRequired}. */
    default OrganizationUnit getRequired(Long id) {
        return findByIdAndDeletedAtIsNull(id).orElseThrow(() -> notFound(id));
    }

    /** Như {@link #getRequired}, kèm khóa ghi ({@link #findByIdForUpdate}). */
    default OrganizationUnit getRequiredForUpdate(Long id) {
        return findByIdForUpdate(id).orElseThrow(() -> notFound(id));
    }

    /**
     * Như {@link #getRequired}, nhưng đang ngưng hoạt động ({@code isActive = false}) cũng → 404: dùng khi gán
     * nhân viên, role hay phụ cấp mới vào.
     */
    default OrganizationUnit getRequiredActive(Long id) {
        return findByIdAndDeletedAtIsNull(id).filter(unit -> Boolean.TRUE.equals(unit.getIsActive()))
            .orElseThrow(() -> notFound(id));
    }

    private static ResourceNotFoundException notFound(Long id) {
        return new ResourceNotFoundException(ErrorCode.ORGANIZATION_UNIT_NOT_FOUND, "Organization unit not found: " + id);
    }
}
