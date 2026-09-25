package com.htttdn.hrm.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.enums.EmploymentStatus;

public interface EmployeeRepository extends JpaRepository<Employee, Long>, JpaSpecificationExecutor<Employee> {

    List<Employee> findByEmploymentStatusInAndDeletedAtIsNull(List<EmploymentStatus> employmentStatuses);

    Optional<Employee> findByEmployeeCode(String employeeCode);

    Optional<Employee> findByWorkEmail(String workEmail);

    boolean existsByEmployeeCode(String employeeCode);

    boolean existsByWorkEmail(String workEmail);

    boolean existsByWorkEmailAndIdNot(String workEmail, Long id);

    boolean existsByNationalId(String nationalId);

    boolean existsByNationalIdAndIdNot(String nationalId, Long id);

    Page<Employee> findByDeletedAtIsNull(Pageable pageable);

    Page<Employee> findByEmploymentStatusAndDeletedAtIsNull(EmploymentStatus employmentStatus, Pageable pageable);
}
