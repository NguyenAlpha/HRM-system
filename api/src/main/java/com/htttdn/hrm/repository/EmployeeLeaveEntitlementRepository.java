package com.htttdn.hrm.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.htttdn.hrm.entity.EmployeeLeaveEntitlement;

public interface EmployeeLeaveEntitlementRepository extends JpaRepository<EmployeeLeaveEntitlement, Long> {

    Optional<EmployeeLeaveEntitlement> findByEmployeeIdAndYear(Long employeeId, Short year);

    boolean existsByEmployeeId(Long employeeId);
}
