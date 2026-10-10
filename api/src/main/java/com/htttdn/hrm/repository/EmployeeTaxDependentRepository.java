package com.htttdn.hrm.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.htttdn.hrm.entity.EmployeeTaxDependent;

public interface EmployeeTaxDependentRepository extends JpaRepository<EmployeeTaxDependent, Long> {

    List<EmployeeTaxDependent> findByEmployeeIdOrderByEffectiveFromDescIdDesc(Long employeeId);

    @Query("""
        SELECT CASE WHEN COUNT(dependent) > 0 THEN true ELSE false END
        FROM EmployeeTaxDependent dependent
        WHERE dependent.employee.id = :employeeId
          AND LOWER(dependent.fullName) = LOWER(:fullName)
          AND dependent.effectiveFrom <= COALESCE(:effectiveTo, dependent.effectiveFrom)
          AND (dependent.effectiveTo IS NULL OR dependent.effectiveTo >= :effectiveFrom)
        """)
    boolean existsOverlappingPeriod(
        @Param("employeeId") Long employeeId,
        @Param("fullName") String fullName,
        @Param("effectiveFrom") LocalDate effectiveFrom,
        @Param("effectiveTo") LocalDate effectiveTo
    );

    @Query("""
        SELECT COUNT(dependent)
        FROM EmployeeTaxDependent dependent
        WHERE dependent.employee.id = :employeeId
          AND dependent.effectiveFrom <= :date
          AND (dependent.effectiveTo IS NULL OR dependent.effectiveTo >= :date)
        """)
    long countEffective(@Param("employeeId") Long employeeId, @Param("date") LocalDate date);
}
