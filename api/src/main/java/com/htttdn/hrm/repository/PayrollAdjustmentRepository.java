package com.htttdn.hrm.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.htttdn.hrm.entity.PayrollAdjustment;

public interface PayrollAdjustmentRepository extends JpaRepository<PayrollAdjustment, Long> {

    List<PayrollAdjustment> findByPayrollPeriodIdOrderByIdAsc(Long payrollPeriodId);

    List<PayrollAdjustment> findByPayrollPeriodIdAndEmployeeIdOrderByIdAsc(Long payrollPeriodId, Long employeeId);

    Optional<PayrollAdjustment> findByIdAndPayrollPeriodId(Long id, Long payrollPeriodId);
}
