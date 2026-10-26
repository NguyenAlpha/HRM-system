package com.htttdn.hrm.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.WorkShift;
import com.htttdn.hrm.exception.ResourceNotFoundException;

import jakarta.persistence.LockModeType;

public interface WorkShiftRepository extends JpaRepository<WorkShift, Long> {

    Optional<WorkShift> findByIdAndDeletedAtIsNull(Long id);

    Optional<WorkShift> findByCodeAndDeletedAtIsNull(String code);

    boolean existsByCodeAndDeletedAtIsNull(String code);

    List<WorkShift> findByDeletedAtIsNullOrderByNameAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT workShift FROM WorkShift workShift "
        + "WHERE workShift.id = :id AND workShift.deletedAt IS NULL")
    Optional<WorkShift> findByIdForUpdate(@Param("id") Long id);

    /** Ca làm việc chưa xóa theo id, không có thì 404 {@code WORK_SHIFT_NOT_FOUND}. Xem {@link AccountRepository#getRequired}. */
    default WorkShift getRequired(Long id) {
        return findByIdAndDeletedAtIsNull(id).orElseThrow(() -> notFound(id));
    }

    /** Như {@link #getRequired}, kèm khóa ghi ({@link #findByIdForUpdate}). */
    default WorkShift getRequiredForUpdate(Long id) {
        return findByIdForUpdate(id).orElseThrow(() -> notFound(id));
    }

    /**
     * Như {@link #getRequired}, nhưng đang ngưng hoạt động ({@code isActive = false}) cũng → 404: dùng khi gán
     * nhân viên, role hay phụ cấp mới vào.
     */
    default WorkShift getRequiredActive(Long id) {
        return findByIdAndDeletedAtIsNull(id).filter(shift -> Boolean.TRUE.equals(shift.getIsActive()))
            .orElseThrow(() -> notFound(id));
    }

    private static ResourceNotFoundException notFound(Long id) {
        return new ResourceNotFoundException(ErrorCode.WORK_SHIFT_NOT_FOUND, "Work shift not found: " + id);
    }
}
