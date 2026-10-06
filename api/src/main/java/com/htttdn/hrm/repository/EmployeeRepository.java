package com.htttdn.hrm.repository;

import java.util.List;
import java.util.Optional;
import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.enums.EmploymentStatus;

import jakarta.persistence.LockModeType;

public interface EmployeeRepository extends JpaRepository<Employee, Long>, JpaSpecificationExecutor<Employee> {

    @Query("""
        SELECT employee FROM Employee employee
        WHERE employee.deletedAt IS NULL
          AND employee.hireDate <= :to
          AND (employee.terminationDate IS NULL OR employee.terminationDate >= :from)
        ORDER BY employee.employeeCode
        """)
    List<Employee> findEmployedDuring(@Param("from") LocalDate from, @Param("to") LocalDate to);

    List<Employee> findByEmploymentStatusInAndDeletedAtIsNull(List<EmploymentStatus> employmentStatuses);

    Optional<Employee> findByEmployeeCode(String employeeCode);

    Optional<Employee> findByIdAndDeletedAtIsNull(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT employee FROM Employee employee WHERE employee.id = :id AND employee.deletedAt IS NULL")
    Optional<Employee> findByIdForUpdate(@Param("id") Long id);

    Optional<Employee> findByWorkEmail(String workEmail);

    boolean existsByEmployeeCode(String employeeCode);

    boolean existsByEmployeeCodeIgnoreCase(String employeeCode);

    boolean existsByWorkEmailIgnoreCase(String workEmail);

    boolean existsByWorkEmailIgnoreCaseAndIdNot(String workEmail, Long id);

    boolean existsByNationalIdAndIdNot(String nationalId, Long id);

    Page<Employee> findByDeletedAtIsNull(Pageable pageable);

    Page<Employee> findByEmploymentStatusAndDeletedAtIsNull(EmploymentStatus employmentStatus, Pageable pageable);
}
