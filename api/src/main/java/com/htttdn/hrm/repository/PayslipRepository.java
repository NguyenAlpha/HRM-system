package com.htttdn.hrm.repository;

import java.util.List;
import java.util.Collection;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.htttdn.hrm.entity.Payslip;
import com.htttdn.hrm.entity.enums.PayrollPeriodStatus;

public interface PayslipRepository extends JpaRepository<Payslip, Long> {

    Optional<Payslip> findByPayrollPeriodIdAndEmployeeId(Long payrollPeriodId, Long employeeId);

    List<Payslip> findByPayrollPeriodId(Long payrollPeriodId);

    Page<Payslip> findByEmployeeId(Long employeeId, Pageable pageable);

    Page<Payslip> findByEmployeeIdAndPayrollPeriodStatusIn(Long employeeId,
        Collection<PayrollPeriodStatus> statuses, Pageable pageable);

    boolean existsByEmployeeId(Long employeeId);
}
