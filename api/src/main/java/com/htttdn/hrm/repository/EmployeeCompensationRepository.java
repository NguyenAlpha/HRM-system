package com.htttdn.hrm.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.htttdn.hrm.entity.EmployeeCompensation;

public interface EmployeeCompensationRepository extends JpaRepository<EmployeeCompensation, Long> {

    List<EmployeeCompensation> findByEmployeeId(Long employeeId);

    List<EmployeeCompensation> findByEmployeeIdAndEffectiveToIsNull(Long employeeId);
}
