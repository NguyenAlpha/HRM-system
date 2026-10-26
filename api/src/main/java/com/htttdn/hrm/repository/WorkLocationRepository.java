package com.htttdn.hrm.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.WorkLocation;
import com.htttdn.hrm.exception.ResourceNotFoundException;

import jakarta.persistence.LockModeType;

public interface WorkLocationRepository extends JpaRepository<WorkLocation, Long> {

    Optional<WorkLocation> findByCodeAndDeletedAtIsNull(String code);

    Optional<WorkLocation> findByIdAndDeletedAtIsNull(Long id);

    boolean existsByCodeAndDeletedAtIsNull(String code);

    boolean existsByParentLocationIdAndDeletedAtIsNull(Long parentLocationId);

    List<WorkLocation> findByParentLocationIdAndDeletedAtIsNull(Long parentLocationId);

    @EntityGraph(attributePaths = "parentLocation")
    List<WorkLocation> findByDeletedAtIsNullOrderByNameAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT location FROM WorkLocation location WHERE location.id = :id AND location.deletedAt IS NULL")
    Optional<WorkLocation> findByIdForUpdate(@Param("id") Long id);

    /** Địa điểm làm việc chưa xóa theo id, không có thì 404 {@code LOCATION_NOT_FOUND}. Xem {@link AccountRepository#getRequired}. */
    default WorkLocation getRequired(Long id) {
        return findByIdAndDeletedAtIsNull(id).orElseThrow(() -> notFound(id));
    }

    /** Như {@link #getRequired}, kèm khóa ghi ({@link #findByIdForUpdate}). */
    default WorkLocation getRequiredForUpdate(Long id) {
        return findByIdForUpdate(id).orElseThrow(() -> notFound(id));
    }

    /**
     * Như {@link #getRequired}, nhưng đang ngưng hoạt động ({@code isActive = false}) cũng → 404: dùng khi gán
     * nhân viên, role hay phụ cấp mới vào.
     */
    default WorkLocation getRequiredActive(Long id) {
        return findByIdAndDeletedAtIsNull(id).filter(location -> Boolean.TRUE.equals(location.getIsActive()))
            .orElseThrow(() -> notFound(id));
    }

    private static ResourceNotFoundException notFound(Long id) {
        return new ResourceNotFoundException(ErrorCode.LOCATION_NOT_FOUND, "Work location not found: " + id);
    }
}
