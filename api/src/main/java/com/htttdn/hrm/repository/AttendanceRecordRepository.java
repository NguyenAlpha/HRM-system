package com.htttdn.hrm.repository;

import java.time.LocalDate;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.AttendanceRecord;
import com.htttdn.hrm.exception.ResourceNotFoundException;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {

    /**
     * Completed days with time beyond the payable minutes that nobody has decided on yet, of every employee
     * not deleted, including those who have left since: their last days still wait for a decision.
     */
    @Query("""
        SELECT record
        FROM AttendanceRecord record
        JOIN FETCH record.employee employee
        WHERE employee.deletedAt IS NULL
          AND record.checkOutAt IS NOT NULL
          AND record.overtimeApprovedAt IS NULL
          AND record.workedMinutes > record.payableMinutes
        ORDER BY record.workDate DESC
        """)
    List<AttendanceRecord> findOvertimeAwaitingDecision();

    /** Prepared days from {@code from} on with nothing recorded yet: no punches, no leave, still MISSING_PUNCH. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT record
        FROM AttendanceRecord record
        WHERE record.employee.id = :employeeId
          AND record.workDate >= :from
          AND record.status = com.htttdn.hrm.entity.enums.AttendanceStatus.MISSING_PUNCH
          AND record.checkInAt IS NULL
          AND record.leaveRequest IS NULL
        ORDER BY record.workDate
        """)
    List<AttendanceRecord> findUntouchedFromForUpdate(@Param("employeeId") Long employeeId, @Param("from") LocalDate from);

    /** Whether anyone's day on {@code workDate} has something recorded: punches, leave, or an HR adjustment. */
    @Query("""
        SELECT COUNT(record) > 0
        FROM AttendanceRecord record
        WHERE record.workDate = :workDate
          AND (record.status <> com.htttdn.hrm.entity.enums.AttendanceStatus.MISSING_PUNCH
            OR record.checkInAt IS NOT NULL
            OR record.leaveRequest IS NOT NULL)
        """)
    boolean existsRecordedOn(@Param("workDate") LocalDate workDate);

    /** Deletes everyone's prepared day on {@code workDate} that has nothing recorded yet. */
    @Modifying
    @Query("""
        DELETE FROM AttendanceRecord record
        WHERE record.workDate = :workDate
          AND record.status = com.htttdn.hrm.entity.enums.AttendanceStatus.MISSING_PUNCH
          AND record.checkInAt IS NULL
          AND record.leaveRequest IS NULL
        """)
    void deleteUntouchedOn(@Param("workDate") LocalDate workDate);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT record FROM AttendanceRecord record WHERE record.employee.id = :employeeId AND record.workDate = :workDate")
    Optional<AttendanceRecord> findByEmployeeIdAndWorkDateForUpdate(
        @Param("employeeId") Long employeeId, @Param("workDate") LocalDate workDate);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT record FROM AttendanceRecord record WHERE record.id = :id")
    Optional<AttendanceRecord> findByIdForUpdate(@Param("id") Long id);

    Page<AttendanceRecord> findByEmployeeIdAndWorkDateBetween(
        Long employeeId, LocalDate from, LocalDate to, Pageable pageable);

    List<AttendanceRecord> findByEmployeeIdAndWorkDateBetween(Long employeeId, LocalDate from, LocalDate to);

    List<AttendanceRecord> findByEmployeeIdInAndWorkDateBetweenOrderByWorkDateAsc(
        Collection<Long> employeeIds, LocalDate from, LocalDate to);

    boolean existsByWorkDateBetweenAndUpdatedAtAfter(LocalDate from, LocalDate to, Instant updatedAt);

    boolean existsByEmployeeId(Long employeeId);

    boolean existsByShiftId(Long shiftId);

    /** Ngày công theo id, không có thì 404 {@code ATTENDANCE_NOT_FOUND}. Xem {@link AccountRepository#getRequired}. */
    default AttendanceRecord getRequired(Long id) {
        return findById(id).orElseThrow(() -> notFound(id));
    }

    /** Như {@link #getRequired}, kèm khóa ghi ({@link #findByIdForUpdate}). */
    default AttendanceRecord getRequiredForUpdate(Long id) {
        return findByIdForUpdate(id).orElseThrow(() -> notFound(id));
    }

    private static ResourceNotFoundException notFound(Long id) {
        return new ResourceNotFoundException(ErrorCode.ATTENDANCE_NOT_FOUND, "Attendance record not found: " + id);
    }
}
