package com.htttdn.hrm.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.JobPosition;
import com.htttdn.hrm.exception.ResourceNotFoundException;

import jakarta.persistence.LockModeType;

public interface JobPositionRepository extends JpaRepository<JobPosition, Long> {

    Optional<JobPosition> findByCodeAndDeletedAtIsNull(String code);

    Optional<JobPosition> findByIdAndDeletedAtIsNull(Long id);

    boolean existsByCodeAndDeletedAtIsNull(String code);

    List<JobPosition> findByDeletedAtIsNullOrderByTitleAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT jobPosition FROM JobPosition jobPosition "
        + "WHERE jobPosition.id = :id AND jobPosition.deletedAt IS NULL")
    Optional<JobPosition> findByIdForUpdate(@Param("id") Long id);

    /** Chức vụ chưa xóa theo id, không có thì 404 {@code JOB_POSITION_NOT_FOUND}. Xem {@link AccountRepository#getRequired}. */
    default JobPosition getRequired(Long id) {
        return findByIdAndDeletedAtIsNull(id).orElseThrow(() -> notFound(id));
    }

    /** Như {@link #getRequired}, kèm khóa ghi ({@link #findByIdForUpdate}). */
    default JobPosition getRequiredForUpdate(Long id) {
        return findByIdForUpdate(id).orElseThrow(() -> notFound(id));
    }

    /**
     * Như {@link #getRequired}, nhưng đang ngưng hoạt động ({@code isActive = false}) cũng → 404: dùng khi gán
     * nhân viên, role hay phụ cấp mới vào.
     */
    default JobPosition getRequiredActive(Long id) {
        return findByIdAndDeletedAtIsNull(id).filter(position -> Boolean.TRUE.equals(position.getIsActive()))
            .orElseThrow(() -> notFound(id));
    }

    /** Như {@link #getRequiredActive}, kèm khóa ghi ({@link #findByIdForUpdate}). */
    default JobPosition getRequiredActiveForUpdate(Long id) {
        return findByIdForUpdate(id).filter(position -> Boolean.TRUE.equals(position.getIsActive())).orElseThrow(() -> notFound(id));
    }

    private static ResourceNotFoundException notFound(Long id) {
        return new ResourceNotFoundException(ErrorCode.JOB_POSITION_NOT_FOUND, "Job position not found: " + id);
    }
}
