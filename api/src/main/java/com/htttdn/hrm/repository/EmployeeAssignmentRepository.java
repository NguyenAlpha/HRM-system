package com.htttdn.hrm.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.htttdn.hrm.entity.EmployeeAssignment;

import jakarta.persistence.LockModeType;

public interface EmployeeAssignmentRepository extends JpaRepository<EmployeeAssignment, Long> {

    Optional<EmployeeAssignment> findFirstByEmployeeIdAndIsPrimaryTrueAndEffectiveToIsNull(Long employeeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT assignment
        FROM EmployeeAssignment assignment
        WHERE assignment.employee.id = :employeeId
          AND assignment.isPrimary = true
          AND assignment.effectiveTo IS NULL
        ORDER BY assignment.effectiveFrom DESC
        """)
    List<EmployeeAssignment> findOpenPrimaryForUpdate(@Param("employeeId") Long employeeId);

    @Query("""
        SELECT assignment
        FROM EmployeeAssignment assignment
        WHERE assignment.employee.id = :employeeId
          AND assignment.isPrimary = true
          AND assignment.effectiveFrom <= :date
          AND (assignment.effectiveTo IS NULL OR assignment.effectiveTo >= :date)
        ORDER BY assignment.effectiveFrom DESC
        """)
    @EntityGraph(attributePaths = {
        "organizationUnit", "workLocation", "position", "shift", "managerEmployee"
    })
    List<EmployeeAssignment> findCurrentPrimaryCandidates(
        @Param("employeeId") Long employeeId,
        @Param("date") LocalDate date
    );

    @EntityGraph(attributePaths = {
        "organizationUnit", "workLocation", "position", "shift", "managerEmployee"
    })
    List<EmployeeAssignment> findByEmployeeIdOrderByEffectiveFromDesc(Long employeeId);

    boolean existsByEmployeeId(Long employeeId);

    @Query("""
        SELECT CASE WHEN COUNT(assignment) > 0 THEN true ELSE false END
        FROM EmployeeAssignment assignment
        WHERE assignment.organizationUnit.id = :organizationUnitId
          AND (assignment.effectiveTo IS NULL OR assignment.effectiveTo >= :date)
        """)
    boolean existsCurrentOrFutureByOrganizationUnitId(
        @Param("organizationUnitId") Long organizationUnitId,
        @Param("date") LocalDate date
    );

    @Query("""
        SELECT CASE WHEN COUNT(assignment) > 0 THEN true ELSE false END
        FROM EmployeeAssignment assignment
        WHERE assignment.workLocation.id = :workLocationId
          AND (assignment.effectiveTo IS NULL OR assignment.effectiveTo >= :date)
        """)
    boolean existsCurrentOrFutureByWorkLocationId(
        @Param("workLocationId") Long workLocationId,
        @Param("date") LocalDate date
    );

    @Query("""
        SELECT CASE WHEN COUNT(assignment) > 0 THEN true ELSE false END
        FROM EmployeeAssignment assignment
        WHERE assignment.position.id = :positionId
          AND (assignment.effectiveTo IS NULL OR assignment.effectiveTo >= :date)
        """)
    boolean existsCurrentOrFutureByPositionId(
        @Param("positionId") Long positionId,
        @Param("date") LocalDate date
    );

    @Query("""
        SELECT CASE WHEN COUNT(assignment) > 0 THEN true ELSE false END
        FROM EmployeeAssignment assignment
        WHERE assignment.shift.id = :shiftId
          AND (assignment.effectiveTo IS NULL OR assignment.effectiveTo >= :date)
        """)
    boolean existsCurrentOrFutureByShiftId(
        @Param("shiftId") Long shiftId,
        @Param("date") LocalDate date
    );
}
