package com.htttdn.hrm.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.htttdn.hrm.entity.EmployeeAssignment;

public interface EmployeeAssignmentRepository extends JpaRepository<EmployeeAssignment, Long> {

    Optional<EmployeeAssignment> findFirstByEmployeeIdAndIsPrimaryTrueAndEffectiveToIsNull(Long employeeId);

    List<EmployeeAssignment> findByEmployeeIdOrderByEffectiveFromDesc(Long employeeId);
}
