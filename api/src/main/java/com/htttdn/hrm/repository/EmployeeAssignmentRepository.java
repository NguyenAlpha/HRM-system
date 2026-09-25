package com.htttdn.hrm.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.htttdn.hrm.entity.EmployeeAssignment;

public interface EmployeeAssignmentRepository extends JpaRepository<EmployeeAssignment, Long> {

    Optional<EmployeeAssignment> findFirstByEmployeeIdAndIsPrimaryTrueAndEffectiveToIsNull(Long employeeId);

    @Query("""
        SELECT assignment
        FROM EmployeeAssignment assignment
        WHERE assignment.employee.id = :employeeId
          AND assignment.isPrimary = true
          AND assignment.effectiveFrom <= :date
          AND (assignment.effectiveTo IS NULL OR assignment.effectiveTo >= :date)
        ORDER BY assignment.effectiveFrom DESC
        """)
    List<EmployeeAssignment> findCurrentPrimaryCandidates(
        @Param("employeeId") Long employeeId,
        @Param("date") LocalDate date
    );

    List<EmployeeAssignment> findByEmployeeIdOrderByEffectiveFromDesc(Long employeeId);

    boolean existsByEmployeeId(Long employeeId);
}
