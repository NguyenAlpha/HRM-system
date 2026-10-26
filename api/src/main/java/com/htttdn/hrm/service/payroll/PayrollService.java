package com.htttdn.hrm.service.payroll;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import com.htttdn.hrm.service.rbac.EmployeeAccessScopeService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.request.payroll.CreatePayrollPeriodRequest;
import com.htttdn.hrm.dto.request.payroll.PayrollActionRequest;
import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.dto.response.payroll.PayrollPeriodResponse;
import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.PayrollPeriod;
import com.htttdn.hrm.entity.enums.PayrollPeriodStatus;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.repository.AttendanceRecordRepository;
import com.htttdn.hrm.repository.EmployeeRepository;
import com.htttdn.hrm.repository.PayrollPeriodRepository;
import com.htttdn.hrm.repository.PayslipRepository;
import com.htttdn.hrm.security.CurrentAccountProvider;

import lombok.RequiredArgsConstructor;

/**
 * Vòng đời kỳ lương: tạo, tính, duyệt, đánh dấu đã trả, khóa và hủy. Cách tính một phiếu lương nằm ở
 * {@link PayslipCalculator}, phần chỉ đọc nằm ở {@link PayrollQueryService}.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class PayrollService {

    private final PayrollPeriodRepository payrollPeriodRepository;
    private final PayslipRepository payslipRepository;
    private final PayrollPeriodInvalidator payrollPeriodInvalidator;
    private final EmployeeRepository employeeRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final CurrentAccountProvider currentAccountProvider;
    private final EmployeeAccessScopeService employeeAccessScopeService;
    private final PayslipCalculator payslipCalculator;
    private final PayrollMapper payrollMapper;
    private final Clock clock;

    @PreAuthorize("hasAuthority('payroll.calculate')")
    public PayrollPeriodResponse createPeriod(CreatePayrollPeriodRequest request) {
        employeeAccessScopeService.requireCompanyWide("payroll.calculate");
        if (payrollPeriodRepository.existsByYearAndMonthAndStatusNot(request.year(), request.month(),
            PayrollPeriodStatus.CANCELLED)) {
            throw new ConflictException(ErrorCode.CONFLICT, "Payroll period already exists for " + request.year() + "-" + request.month());
        }

        LocalDate periodStart = LocalDate.of(request.year(), request.month(), 1);
        LocalDate periodEnd = periodStart.withDayOfMonth(periodStart.lengthOfMonth());
        PayrollPeriod period = PayrollPeriod.builder()
            .year(request.year())
            .month(request.month())
            .periodStart(periodStart)
            .periodEnd(periodEnd)
            .taxPaymentDate(request.taxPaymentDate())
            .status(PayrollPeriodStatus.DRAFT)
            .build();

        return payrollMapper.toResponse(payrollPeriodRepository.save(period));
    }

    @PreAuthorize("hasAuthority('payroll.calculate')")
    public PayrollPeriodResponse calculate(Long periodId, PayrollActionRequest request) {
        employeeAccessScopeService.requireCompanyWide("payroll.calculate");
        PayrollPeriod period = payrollPeriodRepository.getRequiredForUpdate(periodId);
        if (period.getStatus() != PayrollPeriodStatus.DRAFT && period.getStatus() != PayrollPeriodStatus.CALCULATED) {
            throw new ConflictException(
                ErrorCode.PAYROLL_PERIOD_LOCKED, "Period " + periodId + " can no longer be recalculated");
        }

        if (request.taxPaymentDate() != null) {
            period.setTaxPaymentDate(request.taxPaymentDate());
        }
        if (period.getTaxPaymentDate() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                "taxPaymentDate is required to calculate payroll withholding");
        }
        LocalDate paymentMonthStart = period.getTaxPaymentDate().withDayOfMonth(1);
        if (payrollPeriodRepository.existsOtherInPaymentMonth(periodId, paymentMonthStart,
            paymentMonthStart.withDayOfMonth(paymentMonthStart.lengthOfMonth()),
            List.of(PayrollPeriodStatus.CALCULATED, PayrollPeriodStatus.APPROVED,
                PayrollPeriodStatus.PAID, PayrollPeriodStatus.LOCKED))) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                "Another payroll period uses the same tax payment month; monthly tax deductions require consolidated calculation");
        }

        Account calculatedBy = currentAccountProvider.account();

        payrollPeriodInvalidator.deletePayslips(periodId);
        payslipRepository.flush();

        List<Employee> employees = employeeRepository.findEmployedDuring(
            period.getPeriodStart(), period.getPeriodEnd());

        for (Employee employee : employees) {
            payslipCalculator.calculate(period, employee);
        }

        period.setStatus(PayrollPeriodStatus.CALCULATED);
        period.setCalculatedByAccount(calculatedBy);
        period.setCalculatedAt(Instant.now(clock));

        return payrollMapper.toResponse(period);
    }

    @PreAuthorize("hasAuthority('payroll.approve')")
    public PayrollPeriodResponse approve(Long periodId, PayrollActionRequest request) {
        employeeAccessScopeService.requireCompanyWide("payroll.approve");
        PayrollPeriod period = payrollPeriodRepository.getRequiredForUpdate(periodId);
        if (period.getStatus() != PayrollPeriodStatus.CALCULATED) {
            throw new ConflictException(ErrorCode.CONFLICT, "Period must be CALCULATED before it can be approved");
        }
        if (attendanceRecordRepository.existsByWorkDateBetweenAndUpdatedAtAfter(
            period.getPeriodStart(), period.getPeriodEnd(), period.getCalculatedAt())) {
            throw new ConflictException(ErrorCode.CONFLICT, "Attendance changed after payroll calculation; recalculate first");
        }
        Account approver = currentAccountProvider.account();
        if (period.getCalculatedByAccount() != null && period.getCalculatedByAccount().getId().equals(approver.getId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "The approver must be different from the calculator");
        }

        period.setStatus(PayrollPeriodStatus.APPROVED);
        period.setApprovedByAccount(approver);
        period.setApprovedAt(Instant.now(clock));

        return payrollMapper.toResponse(period);
    }

    @PreAuthorize("hasAuthority('payroll.mark_paid')")
    public PayrollPeriodResponse markPaid(Long periodId, PayrollActionRequest request) {
        employeeAccessScopeService.requireCompanyWide("payroll.mark_paid");
        PayrollPeriod period = payrollPeriodRepository.getRequiredForUpdate(periodId);
        if (period.getStatus() != PayrollPeriodStatus.APPROVED) {
            throw new ConflictException(ErrorCode.CONFLICT, "Period must be APPROVED before it can be marked as paid");
        }

        LocalDate actualPaymentDate = request.taxPaymentDate() == null
            ? LocalDate.now(clock) : request.taxPaymentDate();
        if (period.getTaxPaymentDate() == null || !YearMonth.from(actualPaymentDate)
            .equals(YearMonth.from(period.getTaxPaymentDate()))) {
            throw new ConflictException(ErrorCode.CONFLICT,
                "Actual payment month differs from the approved tax payment month; review withholding before marking paid");
        }

        Account payer = currentAccountProvider.account();

        period.setStatus(PayrollPeriodStatus.PAID);
        period.setTaxPaymentDate(actualPaymentDate);
        period.setPaidByAccount(payer);
        period.setPaidAt(Instant.now(clock));

        return payrollMapper.toResponse(period);
    }

    @PreAuthorize("hasAuthority('payroll.lock')")
    public PayrollPeriodResponse lock(Long periodId, PayrollActionRequest request) {
        employeeAccessScopeService.requireCompanyWide("payroll.lock");
        PayrollPeriod period = payrollPeriodRepository.getRequiredForUpdate(periodId);
        if (period.getStatus() != PayrollPeriodStatus.PAID) {
            throw new ConflictException(ErrorCode.CONFLICT, "Period must be PAID before it can be locked");
        }

        Account locker = currentAccountProvider.account();

        period.setStatus(PayrollPeriodStatus.LOCKED);
        period.setLockedByAccount(locker);
        period.setLockedAt(Instant.now(clock));

        return payrollMapper.toResponse(period);
    }

    @PreAuthorize("hasAuthority('payroll.calculate')")
    public PayrollPeriodResponse cancel(Long periodId) {
        employeeAccessScopeService.requireCompanyWide("payroll.calculate");
        PayrollPeriod period = payrollPeriodRepository.getRequiredForUpdate(periodId);
        if (period.getStatus() != PayrollPeriodStatus.DRAFT && period.getStatus() != PayrollPeriodStatus.CALCULATED) {
            throw new ConflictException(ErrorCode.CONFLICT, "Only DRAFT or CALCULATED periods can be cancelled");
        }
        period.setStatus(PayrollPeriodStatus.CANCELLED);
        return payrollMapper.toResponse(period);
    }
}
