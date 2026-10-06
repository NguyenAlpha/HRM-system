package com.htttdn.hrm.repository;

import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import com.htttdn.hrm.entity.AttendanceRecord;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {

    Optional<AttendanceRecord> findByEmployeeIdAndWorkDate(Long employeeId, LocalDate workDate);

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

    boolean existsByWorkDateBetweenAndUpdatedAtAfter(LocalDate from, LocalDate to, Instant updatedAt);

    boolean existsByEmployeeId(Long employeeId);

    boolean existsByShiftId(Long shiftId);
}
