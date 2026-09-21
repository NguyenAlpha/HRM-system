package com.htttdn.hrm.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.htttdn.hrm.dto.request.payroll.CreatePayrollPeriodRequest;
import com.htttdn.hrm.dto.request.payroll.PayrollActionRequest;
import com.htttdn.hrm.dto.response.payroll.PayrollPeriodResponse;
import com.htttdn.hrm.dto.response.payroll.PayslipResponse;

public interface PayrollService {

    PayrollPeriodResponse createPeriod(CreatePayrollPeriodRequest request);

    PayrollPeriodResponse calculate(Long periodId, PayrollActionRequest request);

    PayrollPeriodResponse approve(Long periodId, PayrollActionRequest request);

    PayrollPeriodResponse markPaid(Long periodId, PayrollActionRequest request);

    PayrollPeriodResponse lock(Long periodId, PayrollActionRequest request);

    PayrollPeriodResponse cancel(Long periodId);

    PayrollPeriodResponse getPeriodById(Long periodId);

    PayslipResponse getPayslip(Long payslipId);

    Page<PayslipResponse> listPayslipsForEmployee(Long employeeId, Pageable pageable);
}
