package com.htttdn.hrm.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.htttdn.hrm.entity.EmployeeSalaryHistory;

import jakarta.persistence.LockModeType;

public interface EmployeeSalaryHistoryRepository extends JpaRepository<EmployeeSalaryHistory, Long> {

    List<EmployeeSalaryHistory> findByEmployeeIdOrderByEffectiveFromDesc(Long employeeId);

    @Query("""
        SELECT salary
        FROM EmployeeSalaryHistory salary
        WHERE salary.employee.id IN :employeeIds
          AND salary.effectiveFrom <= :date
          AND (salary.effectiveTo IS NULL OR salary.effectiveTo >= :date)
        """)
    List<EmployeeSalaryHistory> findEffectiveForEmployees(
        @Param("employeeIds") List<Long> employeeIds,
        @Param("date") LocalDate date
    );

    @Query("""
        SELECT salary
        FROM EmployeeSalaryHistory salary
        WHERE salary.employee.id = :employeeId
          AND salary.effectiveFrom <= :date
          AND (salary.effectiveTo IS NULL OR salary.effectiveTo >= :date)
        ORDER BY salary.effectiveFrom DESC
        """)
    List<EmployeeSalaryHistory> findEffectiveCandidates(
        @Param("employeeId") Long employeeId,
        @Param("date") LocalDate date
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT salary
        FROM EmployeeSalaryHistory salary
        WHERE salary.employee.id = :employeeId
          AND salary.effectiveTo IS NULL
        ORDER BY salary.effectiveFrom DESC
        """)
    List<EmployeeSalaryHistory> findOpenByEmployeeIdForUpdate(@Param("employeeId") Long employeeId);

    @Query("""
        SELECT CASE WHEN COUNT(salary) > 0 THEN true ELSE false END
        FROM EmployeeSalaryHistory salary
        WHERE salary.employee.id = :employeeId
          AND salary.effectiveFrom <= COALESCE(:effectiveTo, salary.effectiveFrom)
          AND (salary.effectiveTo IS NULL OR salary.effectiveTo >= :effectiveFrom)
        """)
    boolean existsOverlappingPeriod(
        @Param("employeeId") Long employeeId,
        @Param("effectiveFrom") LocalDate effectiveFrom,
        @Param("effectiveTo") LocalDate effectiveTo
    );

    boolean existsByEmployeeId(Long employeeId);

    default Optional<EmployeeSalaryHistory> findEffective(Long employeeId, LocalDate date) {
        List<EmployeeSalaryHistory> candidates = findEffectiveCandidates(employeeId, date);
        if (candidates.size() > 1) {
            throw new IllegalStateException("Multiple salary records are effective for employee " + employeeId);
        }
        return candidates.stream().findFirst();
    }
}
