package com.htttdn.hrm.service;

import java.time.Instant;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.request.payroll.CreatePayrollAdjustmentRequest;
import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.dto.response.payroll.PayrollAdjustmentResponse;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.PayrollAdjustment;
import com.htttdn.hrm.entity.PayrollPeriod;
import com.htttdn.hrm.entity.enums.PayrollPeriodStatus;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.exception.ResourceNotFoundException;
import com.htttdn.hrm.repository.AccountRepository;
import com.htttdn.hrm.repository.EmployeeRepository;
import com.htttdn.hrm.repository.PayrollAdjustmentRepository;
import com.htttdn.hrm.repository.PayrollPeriodRepository;
import com.htttdn.hrm.security.CurrentAccountProvider;

import lombok.RequiredArgsConstructor;

/**
 * Bonuses, other allowances and deductions entered by the payroll accountant for one period.
 * They only reach payslips through calculation, so changing them sends a CALCULATED period back
 * to DRAFT; from APPROVED onwards they are frozen like the payslips themselves.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class PayrollAdjustmentService {
    private static final String PAYROLL_CALCULATE = "payroll.calculate";

    private final PayrollAdjustmentRepository adjustmentRepository;
    private final PayrollPeriodRepository payrollPeriodRepository;
    private final EmployeeRepository employeeRepository;
    private final AccountRepository accountRepository;
    private final CurrentAccountProvider currentAccountProvider;
    private final EmployeeAccessScopeService employeeAccessScopeService;

    @PreAuthorize("hasAuthority('payroll.calculate')")
    @Transactional(readOnly = true)
    public List<PayrollAdjustmentResponse> list(Long periodId) {
        employeeAccessScopeService.requireCompanyWide(PAYROLL_CALCULATE);
        findPeriod(periodId);
        return adjustmentRepository.findByPayrollPeriodIdOrderByIdAsc(periodId).stream()
            .map(PayrollAdjustmentService::toResponse).toList();
    }

    @PreAuthorize("hasAuthority('payroll.calculate')")
    public PayrollAdjustmentResponse create(Long periodId, CreatePayrollAdjustmentRequest request) {
        employeeAccessScopeService.requireCompanyWide(PAYROLL_CALCULATE);
        PayrollPeriod period = findEditablePeriod(periodId);
        Employee employee = employeeRepository.findByIdAndDeletedAtIsNull(request.employeeId())
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.EMPLOYEE_NOT_FOUND, "Employee not found: " + request.employeeId()));
        boolean employedInPeriod = !employee.getHireDate().isAfter(period.getPeriodEnd())
            && (employee.getTerminationDate() == null
                || !employee.getTerminationDate().isBefore(period.getPeriodStart()));
        if (!employedInPeriod) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                "Employee is not employed during this payroll period", "employeeId");
        }
        Long accountId = currentAccountProvider.accountId();
        PayrollAdjustment adjustment = adjustmentRepository.save(PayrollAdjustment.builder()
            .payrollPeriod(period)
            .employee(employee)
            .adjustmentType(request.type())
            .description(request.description().trim())
            .amount(request.amount())
            .createdByAccount(accountRepository.getReferenceById(accountId))
            .createdAt(Instant.now())
            .build());
        invalidateCalculation(period);
        return toResponse(adjustment);
    }

    @PreAuthorize("hasAuthority('payroll.calculate')")
    public void delete(Long periodId, Long adjustmentId) {
        employeeAccessScopeService.requireCompanyWide(PAYROLL_CALCULATE);
        PayrollPeriod period = findEditablePeriod(periodId);
        PayrollAdjustment adjustment = adjustmentRepository.findByIdAndPayrollPeriodId(adjustmentId, periodId)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.RESOURCE_NOT_FOUND, "Payroll adjustment not found: " + adjustmentId));
        adjustmentRepository.delete(adjustment);
        invalidateCalculation(period);
    }

    private PayrollPeriod findPeriod(Long periodId) {
        return payrollPeriodRepository.findById(periodId)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.PAYROLL_PERIOD_NOT_FOUND, "Payroll period not found: " + periodId));
    }

    private PayrollPeriod findEditablePeriod(Long periodId) {
        PayrollPeriod period = findPeriod(periodId);
        if (period.getStatus() != PayrollPeriodStatus.DRAFT && period.getStatus() != PayrollPeriodStatus.CALCULATED) {
            throw new ConflictException(ErrorCode.PAYROLL_PERIOD_LOCKED,
                "Adjustments can only change while period " + periodId + " is DRAFT or CALCULATED");
        }
        return period;
    }

    private static void invalidateCalculation(PayrollPeriod period) {
        if (period.getStatus() == PayrollPeriodStatus.CALCULATED) {
            period.setStatus(PayrollPeriodStatus.DRAFT);
            period.setCalculatedByAccount(null);
            period.setCalculatedAt(null);
        }
        period.setUpdatedAt(Instant.now());
    }

    private static PayrollAdjustmentResponse toResponse(PayrollAdjustment adjustment) {
        return new PayrollAdjustmentResponse(
            adjustment.getId(),
            adjustment.getPayrollPeriod().getId(),
            adjustment.getEmployee().getId(),
            adjustment.getEmployee().getEmployeeCode(),
            adjustment.getEmployee().getFullName(),
            adjustment.getAdjustmentType(),
            adjustment.getDescription(),
            adjustment.getAmount(),
            adjustment.getCreatedAt()
        );
    }
}
