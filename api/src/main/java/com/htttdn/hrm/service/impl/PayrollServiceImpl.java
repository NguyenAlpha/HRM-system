package com.htttdn.hrm.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.request.payroll.CreatePayrollPeriodRequest;
import com.htttdn.hrm.dto.request.payroll.PayrollActionRequest;
import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.dto.response.payroll.PayrollPeriodResponse;
import com.htttdn.hrm.dto.response.payroll.PayslipItemResponse;
import com.htttdn.hrm.dto.response.payroll.PayslipResponse;
import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.AttendanceRecord;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.EmployeeAssignment;
import com.htttdn.hrm.entity.EmployeeCompensation;
import com.htttdn.hrm.entity.PayrollPeriod;
import com.htttdn.hrm.entity.Payslip;
import com.htttdn.hrm.entity.PayslipItem;
import com.htttdn.hrm.entity.enums.CompensationType;
import com.htttdn.hrm.entity.enums.EmploymentStatus;
import com.htttdn.hrm.entity.enums.PayrollPeriodStatus;
import com.htttdn.hrm.entity.enums.PayslipItemType;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.exception.ResourceNotFoundException;
import com.htttdn.hrm.repository.AccountRepository;
import com.htttdn.hrm.repository.AttendanceRecordRepository;
import com.htttdn.hrm.repository.EmployeeAssignmentRepository;
import com.htttdn.hrm.repository.EmployeeCompensationRepository;
import com.htttdn.hrm.repository.EmployeeRepository;
import com.htttdn.hrm.repository.PayrollPeriodRepository;
import com.htttdn.hrm.repository.PayslipItemRepository;
import com.htttdn.hrm.repository.PayslipRepository;
import com.htttdn.hrm.service.PayrollService;

@Service
@Transactional
public class PayrollServiceImpl implements PayrollService {

    private final PayrollPeriodRepository payrollPeriodRepository;
    private final PayslipRepository payslipRepository;
    private final PayslipItemRepository payslipItemRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeAssignmentRepository employeeAssignmentRepository;
    private final EmployeeCompensationRepository employeeCompensationRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final AccountRepository accountRepository;

    public PayrollServiceImpl(
        PayrollPeriodRepository payrollPeriodRepository,
        PayslipRepository payslipRepository,
        PayslipItemRepository payslipItemRepository,
        EmployeeRepository employeeRepository,
        EmployeeAssignmentRepository employeeAssignmentRepository,
        EmployeeCompensationRepository employeeCompensationRepository,
        AttendanceRecordRepository attendanceRecordRepository,
        AccountRepository accountRepository
    ) {
        this.payrollPeriodRepository = payrollPeriodRepository;
        this.payslipRepository = payslipRepository;
        this.payslipItemRepository = payslipItemRepository;
        this.employeeRepository = employeeRepository;
        this.employeeAssignmentRepository = employeeAssignmentRepository;
        this.employeeCompensationRepository = employeeCompensationRepository;
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.accountRepository = accountRepository;
    }

    @Override
    public PayrollPeriodResponse createPeriod(CreatePayrollPeriodRequest request) {
        if (payrollPeriodRepository.findByYearAndMonth(request.year(), request.month()).isPresent()) {
            throw new ConflictException(ErrorCode.CONFLICT, "Payroll period already exists for " + request.year() + "-" + request.month());
        }

        LocalDate periodStart = LocalDate.of(request.year(), request.month(), 1);
        LocalDate periodEnd = periodStart.withDayOfMonth(periodStart.lengthOfMonth());

        Instant now = Instant.now();
        PayrollPeriod period = PayrollPeriod.builder()
            .year(request.year())
            .month(request.month())
            .periodStart(periodStart)
            .periodEnd(periodEnd)
            .status(PayrollPeriodStatus.DRAFT)
            .createdAt(now)
            .updatedAt(now)
            .build();

        return toResponse(payrollPeriodRepository.save(period));
    }

    @Override
    public PayrollPeriodResponse calculate(Long periodId, PayrollActionRequest request) {
        PayrollPeriod period = findPeriodOrThrow(periodId);
        if (period.getStatus() != PayrollPeriodStatus.DRAFT && period.getStatus() != PayrollPeriodStatus.CALCULATED) {
            throw new ConflictException(
                ErrorCode.PAYROLL_PERIOD_LOCKED, "Period " + periodId + " can no longer be recalculated");
        }

        Account calculatedBy = accountRepository.findById(request.accountId())
            .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND, "Account not found: " + request.accountId()));

        List<Employee> employees = employeeRepository.findByEmploymentStatusInAndDeletedAtIsNull(
            List.of(EmploymentStatus.ACTIVE, EmploymentStatus.PROBATION));

        for (Employee employee : employees) {
            calculateForEmployee(period, employee);
        }

        period.setStatus(PayrollPeriodStatus.CALCULATED);
        period.setCalculatedByAccount(calculatedBy);
        period.setCalculatedAt(Instant.now());
        period.setUpdatedAt(Instant.now());

        return toResponse(period);
    }

    private void calculateForEmployee(PayrollPeriod period, Employee employee) {
        Optional<EmployeeCompensation> basicSalaryOpt = activeCompensations(employee.getId(), period.getPeriodStart())
            .stream()
            .filter(c -> c.getComponentType() == CompensationType.BASIC_SALARY)
            .findFirst();
        if (basicSalaryOpt.isEmpty()) {
            return;
        }

        EmployeeAssignment assignment = employeeAssignmentRepository
            .findFirstByEmployeeIdAndIsPrimaryTrueAndEffectiveToIsNull(employee.getId())
            .orElse(null);
        if (assignment == null) {
            return;
        }

        List<AttendanceRecord> attendanceRecords = attendanceRecordRepository.findByEmployeeIdAndWorkDateBetween(
            employee.getId(), period.getPeriodStart(), period.getPeriodEnd());
        if (attendanceRecords.isEmpty()) {
            return;
        }

        long scheduledWorkMinutes = attendanceRecords.stream()
            .mapToLong(r -> r.getShift().getStandardWorkMinutes())
            .sum();
        if (scheduledWorkMinutes <= 0) {
            return;
        }
        long payableWorkMinutes = attendanceRecords.stream().mapToLong(AttendanceRecord::getPayableMinutes).sum();
        long approvedOvertimeMinutes = attendanceRecords.stream().mapToLong(AttendanceRecord::getOvertimeMinutes).sum();

        BigDecimal basicSalary = basicSalaryOpt.get().getMonthlyAmount();
        BigDecimal hourlyRate = basicSalary
            .divide(BigDecimal.valueOf(scheduledWorkMinutes), 6, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(60));

        BigDecimal basicSalaryPay = basicSalary
            .multiply(BigDecimal.valueOf(payableWorkMinutes))
            .divide(BigDecimal.valueOf(scheduledWorkMinutes), 2, RoundingMode.HALF_UP);

        List<PayslipItem> items = new ArrayList<>();
        items.add(PayslipItem.builder()
            .componentType(PayslipItemType.BASIC_SALARY)
            .description("Lương cơ bản")
            .quantity(BigDecimal.valueOf(payableWorkMinutes).divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP))
            .unitRate(hourlyRate)
            .multiplier(BigDecimal.ONE)
            .amount(basicSalaryPay)
            .createdAt(Instant.now())
            .build());

        BigDecimal overtimePay = BigDecimal.ZERO;
        for (AttendanceRecord record : attendanceRecords) {
            if (record.getOvertimeMinutes() == null || record.getOvertimeMinutes() <= 0) {
                continue;
            }
            BigDecimal hours = BigDecimal.valueOf(record.getOvertimeMinutes()).divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP);
            BigDecimal amount = hourlyRate.multiply(hours).multiply(record.getOvertimeMultiplier()).setScale(2, RoundingMode.HALF_UP);
            overtimePay = overtimePay.add(amount);
            items.add(PayslipItem.builder()
                .componentType(PayslipItemType.OVERTIME)
                .description("Tăng ca ngày " + record.getWorkDate())
                .quantity(hours)
                .unitRate(hourlyRate)
                .multiplier(record.getOvertimeMultiplier())
                .amount(amount)
                .createdAt(Instant.now())
                .build());
        }

        BigDecimal allowancePay = BigDecimal.ZERO;
        for (EmployeeCompensation allowance : activeCompensations(employee.getId(), period.getPeriodStart())) {
            if (allowance.getComponentType() != CompensationType.ALLOWANCE) {
                continue;
            }
            allowancePay = allowancePay.add(allowance.getMonthlyAmount());
            items.add(PayslipItem.builder()
                .componentType(PayslipItemType.ALLOWANCE)
                .description(allowance.getComponentName())
                .quantity(BigDecimal.ONE)
                .unitRate(allowance.getMonthlyAmount())
                .multiplier(BigDecimal.ONE)
                .amount(allowance.getMonthlyAmount())
                .createdAt(Instant.now())
                .build());
        }

        BigDecimal grossPay = basicSalaryPay.add(allowancePay).add(overtimePay);

        payslipRepository.findByPayrollPeriodIdAndEmployeeId(period.getId(), employee.getId())
            .ifPresent(existing -> {
                payslipItemRepository.deleteByPayslipId(existing.getId());
                payslipRepository.delete(existing);
            });

        Payslip payslip = Payslip.builder()
            .payrollPeriod(period)
            .employee(employee)
            .employeeCodeSnapshot(employee.getEmployeeCode())
            .employeeNameSnapshot(employee.getFullName())
            .workLocationSnapshot(assignment.getWorkLocation().getName())
            .organizationUnitSnapshot(assignment.getOrganizationUnit().getName())
            .contractualBasicSalary(basicSalary)
            .scheduledWorkMinutes((int) scheduledWorkMinutes)
            .payableWorkMinutes((int) payableWorkMinutes)
            .approvedOvertimeMinutes((int) approvedOvertimeMinutes)
            .basicSalaryPay(basicSalaryPay)
            .allowancePay(allowancePay)
            .overtimePay(overtimePay)
            .grossPay(grossPay)
            .netPay(grossPay)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
        Payslip savedPayslip = payslipRepository.save(payslip);

        for (PayslipItem item : items) {
            item.setPayslip(savedPayslip);
        }
        payslipItemRepository.saveAll(items);
    }

    private List<EmployeeCompensation> activeCompensations(Long employeeId, LocalDate asOfDate) {
        return employeeCompensationRepository.findByEmployeeId(employeeId).stream()
            .filter(c -> !c.getEffectiveFrom().isAfter(asOfDate))
            .filter(c -> c.getEffectiveTo() == null || !c.getEffectiveTo().isBefore(asOfDate))
            .toList();
    }

    @Override
    public PayrollPeriodResponse approve(Long periodId, PayrollActionRequest request) {
        PayrollPeriod period = findPeriodOrThrow(periodId);
        if (period.getStatus() != PayrollPeriodStatus.CALCULATED) {
            throw new ConflictException(ErrorCode.CONFLICT, "Period must be CALCULATED before it can be approved");
        }
        if (period.getCalculatedByAccount() != null && period.getCalculatedByAccount().getId().equals(request.accountId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "The approver must be different from the calculator");
        }

        Account approver = accountRepository.findById(request.accountId())
            .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND, "Account not found: " + request.accountId()));

        period.setStatus(PayrollPeriodStatus.APPROVED);
        period.setApprovedByAccount(approver);
        period.setApprovedAt(Instant.now());
        period.setUpdatedAt(Instant.now());

        return toResponse(period);
    }

    @Override
    public PayrollPeriodResponse markPaid(Long periodId, PayrollActionRequest request) {
        PayrollPeriod period = findPeriodOrThrow(periodId);
        if (period.getStatus() != PayrollPeriodStatus.APPROVED) {
            throw new ConflictException(ErrorCode.CONFLICT, "Period must be APPROVED before it can be marked as paid");
        }

        Account payer = accountRepository.findById(request.accountId())
            .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND, "Account not found: " + request.accountId()));

        period.setStatus(PayrollPeriodStatus.PAID);
        period.setPaidByAccount(payer);
        period.setPaidAt(Instant.now());
        period.setUpdatedAt(Instant.now());

        return toResponse(period);
    }

    @Override
    public PayrollPeriodResponse lock(Long periodId, PayrollActionRequest request) {
        PayrollPeriod period = findPeriodOrThrow(periodId);
        if (period.getStatus() != PayrollPeriodStatus.PAID) {
            throw new ConflictException(ErrorCode.CONFLICT, "Period must be PAID before it can be locked");
        }

        Account locker = accountRepository.findById(request.accountId())
            .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND, "Account not found: " + request.accountId()));

        period.setStatus(PayrollPeriodStatus.LOCKED);
        period.setLockedByAccount(locker);
        period.setLockedAt(Instant.now());
        period.setUpdatedAt(Instant.now());

        return toResponse(period);
    }

    @Override
    public PayrollPeriodResponse cancel(Long periodId) {
        PayrollPeriod period = findPeriodOrThrow(periodId);
        if (period.getStatus() != PayrollPeriodStatus.DRAFT && period.getStatus() != PayrollPeriodStatus.CALCULATED) {
            throw new ConflictException(ErrorCode.CONFLICT, "Only DRAFT or CALCULATED periods can be cancelled");
        }
        period.setStatus(PayrollPeriodStatus.CANCELLED);
        period.setUpdatedAt(Instant.now());
        return toResponse(period);
    }

    @Override
    @Transactional(readOnly = true)
    public PayrollPeriodResponse getPeriodById(Long periodId) {
        return toResponse(findPeriodOrThrow(periodId));
    }

    @Override
    @Transactional(readOnly = true)
    public PayslipResponse getPayslip(Long payslipId) {
        Payslip payslip = payslipRepository.findById(payslipId)
            .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PAYSLIP_NOT_FOUND, "Payslip not found: " + payslipId));
        return toResponse(payslip);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PayslipResponse> listPayslipsForEmployee(Long employeeId, Pageable pageable) {
        return payslipRepository.findByEmployeeId(employeeId, pageable).map(this::toResponse);
    }

    private PayrollPeriod findPeriodOrThrow(Long id) {
        return payrollPeriodRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PAYROLL_PERIOD_NOT_FOUND, "Payroll period not found: " + id));
    }

    private PayrollPeriodResponse toResponse(PayrollPeriod period) {
        return new PayrollPeriodResponse(
            period.getId(),
            period.getYear(),
            period.getMonth(),
            period.getPeriodStart(),
            period.getPeriodEnd(),
            period.getStatus(),
            period.getCalculatedByAccount() != null ? period.getCalculatedByAccount().getId() : null,
            period.getCalculatedAt(),
            period.getApprovedByAccount() != null ? period.getApprovedByAccount().getId() : null,
            period.getApprovedAt(),
            period.getPaidByAccount() != null ? period.getPaidByAccount().getId() : null,
            period.getPaidAt(),
            period.getLockedByAccount() != null ? period.getLockedByAccount().getId() : null,
            period.getLockedAt()
        );
    }

    private PayslipResponse toResponse(Payslip payslip) {
        List<PayslipItemResponse> items = payslipItemRepository.findByPayslipId(payslip.getId()).stream()
            .map(item -> new PayslipItemResponse(
                item.getId(),
                item.getComponentType(),
                item.getDescription(),
                item.getQuantity(),
                item.getUnitRate(),
                item.getMultiplier(),
                item.getAmount()
            ))
            .toList();

        return new PayslipResponse(
            payslip.getId(),
            payslip.getPayrollPeriod().getId(),
            payslip.getEmployee().getId(),
            payslip.getEmployeeCodeSnapshot(),
            payslip.getEmployeeNameSnapshot(),
            payslip.getWorkLocationSnapshot(),
            payslip.getOrganizationUnitSnapshot(),
            payslip.getContractualBasicSalary(),
            payslip.getScheduledWorkMinutes(),
            payslip.getPayableWorkMinutes(),
            payslip.getApprovedOvertimeMinutes(),
            payslip.getBasicSalaryPay(),
            payslip.getAllowancePay(),
            payslip.getOvertimePay(),
            payslip.getGrossPay(),
            payslip.getNetPay(),
            items
        );
    }
}
