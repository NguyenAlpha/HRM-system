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

import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.enums.EmploymentStatus;
import com.htttdn.hrm.exception.ResourceNotFoundException;

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

    /** Employees whose scheduled end is due: their last working day is before {@code today}. */
    @Query("""
        SELECT employee.id FROM Employee employee
        WHERE employee.deletedAt IS NULL
          AND employee.scheduledEndStatus IS NOT NULL
          AND employee.terminationDate < :today
        """)
    List<Long> findScheduledEndsDue(@Param("today") LocalDate today);

    Optional<Employee> findByWorkEmail(String workEmail);

    boolean existsByEmployeeCode(String employeeCode);

    boolean existsByEmployeeCodeIgnoreCase(String employeeCode);

    boolean existsByWorkEmailIgnoreCase(String workEmail);

    boolean existsByWorkEmailIgnoreCaseAndIdNot(String workEmail, Long id);

    boolean existsByNationalIdAndIdNot(String nationalId, Long id);

    Page<Employee> findByDeletedAtIsNull(Pageable pageable);

    Page<Employee> findByEmploymentStatusAndDeletedAtIsNull(EmploymentStatus employmentStatus, Pageable pageable);

    /**
     * Nhân viên chưa bị xóa mềm, không có thì 404 {@code EMPLOYEE_NOT_FOUND}. Cách hoạt động: xem
     * {@link AccountRepository#getRequired}.
     */
    default Employee getRequired(Long id) {
        return findByIdAndDeletedAtIsNull(id).orElseThrow(() -> notFound(id));
    }

    /** Như {@link #getRequired}, kèm khóa ghi ({@link #findByIdForUpdate}). */
    default Employee getRequiredForUpdate(Long id) {
        return findByIdForUpdate(id).orElseThrow(() -> notFound(id));
    }

    private static ResourceNotFoundException notFound(Long id) {
        return new ResourceNotFoundException(ErrorCode.EMPLOYEE_NOT_FOUND, "Employee not found: " + id);
    }
}
