package com.htttdn.hrm.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.htttdn.hrm.entity.EmployeePayrollProfile;

import jakarta.persistence.LockModeType;

public interface EmployeePayrollProfileRepository extends JpaRepository<EmployeePayrollProfile, Long> {

    List<EmployeePayrollProfile> findByEmployeeIdOrderByEffectiveFromDesc(Long employeeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT profile
        FROM EmployeePayrollProfile profile
        WHERE profile.employee.id = :employeeId
        ORDER BY profile.effectiveFrom DESC
        """)
    List<EmployeePayrollProfile> findByEmployeeIdForUpdate(@Param("employeeId") Long employeeId);

    /** At most one row matches: excl_employee_payroll_profiles_overlap forbids overlapping periods. */
    @Query("""
        SELECT profile
        FROM EmployeePayrollProfile profile
        WHERE profile.employee.id = :employeeId
          AND profile.effectiveFrom <= :date
          AND (profile.effectiveTo IS NULL OR profile.effectiveTo >= :date)
        """)
    Optional<EmployeePayrollProfile> findEffective(
        @Param("employeeId") Long employeeId,
        @Param("date") LocalDate date
    );
}
