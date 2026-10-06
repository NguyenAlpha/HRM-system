package com.htttdn.hrm.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
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
import com.htttdn.hrm.entity.EmployeeSalaryHistory;
import com.htttdn.hrm.entity.PayrollPeriod;
import com.htttdn.hrm.entity.Payslip;
import com.htttdn.hrm.entity.PayslipItem;
import com.htttdn.hrm.entity.PositionAllowanceRule;
import com.htttdn.hrm.entity.SeniorityAllowanceRule;
import com.htttdn.hrm.entity.enums.AttendanceStatus;
import com.htttdn.hrm.entity.enums.PayrollPeriodStatus;
import com.htttdn.hrm.entity.enums.PayslipItemType;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.exception.ForbiddenException;
import com.htttdn.hrm.exception.ResourceNotFoundException;
import com.htttdn.hrm.repository.AccountRepository;
import com.htttdn.hrm.repository.AttendanceRecordRepository;
import com.htttdn.hrm.repository.EmployeeAssignmentRepository;
import com.htttdn.hrm.repository.EmployeeRepository;
import com.htttdn.hrm.repository.EmployeeSalaryHistoryRepository;
import com.htttdn.hrm.repository.PayrollPeriodRepository;
import com.htttdn.hrm.repository.PayslipItemRepository;
import com.htttdn.hrm.repository.PayslipRepository;
import com.htttdn.hrm.repository.PositionAllowanceRuleRepository;
import com.htttdn.hrm.repository.SeniorityAllowanceRuleRepository;
import com.htttdn.hrm.security.CurrentAccountProvider;
import com.htttdn.hrm.service.EmployeeAccessScopeService;
import com.htttdn.hrm.service.AttendanceCalendarService;
import com.htttdn.hrm.service.PayrollService;

@Service
@Transactional
public class PayrollServiceImpl implements PayrollService {

    private final PayrollPeriodRepository payrollPeriodRepository;
    private final PayslipRepository payslipRepository;
    private final PayslipItemRepository payslipItemRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeAssignmentRepository employeeAssignmentRepository;
    private final EmployeeSalaryHistoryRepository employeeSalaryHistoryRepository;
    private final PositionAllowanceRuleRepository positionAllowanceRuleRepository;
    private final SeniorityAllowanceRuleRepository seniorityAllowanceRuleRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final AccountRepository accountRepository;
    private final CurrentAccountProvider currentAccountProvider;
    private final EmployeeAccessScopeService employeeAccessScopeService;
    private final AttendanceCalendarService attendanceCalendarService;

    public PayrollServiceImpl(
        PayrollPeriodRepository payrollPeriodRepository,
        PayslipRepository payslipRepository,
        PayslipItemRepository payslipItemRepository,
        EmployeeRepository employeeRepository,
        EmployeeAssignmentRepository employeeAssignmentRepository,
        EmployeeSalaryHistoryRepository employeeSalaryHistoryRepository,
        PositionAllowanceRuleRepository positionAllowanceRuleRepository,
        SeniorityAllowanceRuleRepository seniorityAllowanceRuleRepository,
        AttendanceRecordRepository attendanceRecordRepository,
        AccountRepository accountRepository,
        CurrentAccountProvider currentAccountProvider,
        EmployeeAccessScopeService employeeAccessScopeService,
        AttendanceCalendarService attendanceCalendarService
    ) {
        this.payrollPeriodRepository = payrollPeriodRepository;
        this.payslipRepository = payslipRepository;
        this.payslipItemRepository = payslipItemRepository;
        this.employeeRepository = employeeRepository;
        this.employeeAssignmentRepository = employeeAssignmentRepository;
        this.employeeSalaryHistoryRepository = employeeSalaryHistoryRepository;
        this.positionAllowanceRuleRepository = positionAllowanceRuleRepository;
        this.seniorityAllowanceRuleRepository = seniorityAllowanceRuleRepository;
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.accountRepository = accountRepository;
        this.currentAccountProvider = currentAccountProvider;
        this.employeeAccessScopeService = employeeAccessScopeService;
        this.attendanceCalendarService = attendanceCalendarService;
    }

    @Override
    @PreAuthorize("hasAuthority('payroll.calculate')")
    public PayrollPeriodResponse createPeriod(CreatePayrollPeriodRequest request) {
        employeeAccessScopeService.requireCompanyWide("payroll.calculate");
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
    @PreAuthorize("hasAuthority('payroll.calculate')")
    public PayrollPeriodResponse calculate(Long periodId, PayrollActionRequest request) {
        employeeAccessScopeService.requireCompanyWide("payroll.calculate");
        PayrollPeriod period = findPeriodForUpdateOrThrow(periodId);
        if (period.getStatus() != PayrollPeriodStatus.DRAFT && period.getStatus() != PayrollPeriodStatus.CALCULATED) {
            throw new ConflictException(
                ErrorCode.PAYROLL_PERIOD_LOCKED, "Period " + periodId + " can no longer be recalculated");
        }

        Account calculatedBy = findCurrentAccount();

        for (Payslip existing : payslipRepository.findByPayrollPeriodId(periodId)) {
            payslipItemRepository.deleteByPayslipId(existing.getId());
            payslipRepository.delete(existing);
        }
        payslipRepository.flush();

        List<Employee> employees = employeeRepository.findEmployedDuring(
            period.getPeriodStart(), period.getPeriodEnd());

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
        LocalDate employedFrom = employee.getHireDate().isAfter(period.getPeriodStart())
            ? employee.getHireDate() : period.getPeriodStart();
        LocalDate employedTo = employee.getTerminationDate() != null
            && employee.getTerminationDate().isBefore(period.getPeriodEnd())
                ? employee.getTerminationDate() : period.getPeriodEnd();
        List<LocalDate> expectedWorkDates = attendanceCalendarService.companyWorkDates(employedFrom, employedTo);
        if (expectedWorkDates.isEmpty()) {
            return;
        }
        List<AttendanceCalendarService.ScheduledDay> scheduledDays;
        try {
            scheduledDays = attendanceCalendarService.scheduleFor(employee.getId(), employedFrom, employedTo);
        } catch (BusinessException error) {
            throw missingPayrollInput(employee, error.getMessage());
        }
        Map<LocalDate, AttendanceCalendarService.ScheduledDay> scheduleByDate = scheduledDays.stream()
            .collect(Collectors.toMap(AttendanceCalendarService.ScheduledDay::workDate, Function.identity()));
        for (LocalDate date : expectedWorkDates) {
            if (!scheduleByDate.containsKey(date)) {
                throw missingPayrollInput(employee, "primary assignment and work shift on " + date);
            }
        }
        LocalDate lastWorkDate = scheduledDays.get(scheduledDays.size() - 1).workDate();
        Optional<EmployeeSalaryHistory> salaryHistory = employeeSalaryHistoryRepository
            .findEffective(employee.getId(), lastWorkDate);
        if (salaryHistory.isEmpty()) {
            throw missingPayrollInput(employee, "salary history on " + lastWorkDate);
        }

        EmployeeAssignment assignment = employeeAssignmentRepository
            .findCurrentPrimaryCandidates(employee.getId(), lastWorkDate).stream()
            .findFirst()
            .orElse(null);
        if (assignment == null) {
            throw missingPayrollInput(employee, "primary assignment");
        }

        List<AttendanceRecord> attendanceRecords = attendanceRecordRepository.findByEmployeeIdAndWorkDateBetween(
            employee.getId(), period.getPeriodStart(), period.getPeriodEnd());
        if (attendanceRecords.isEmpty()) {
            throw missingPayrollInput(employee, "attendance");
        }
        Map<LocalDate, AttendanceRecord> attendanceByDate = attendanceRecords.stream()
            .collect(Collectors.toMap(AttendanceRecord::getWorkDate, Function.identity()));
        for (AttendanceCalendarService.ScheduledDay day : scheduledDays) {
            AttendanceRecord record = attendanceByDate.get(day.workDate());
            if (record == null) {
                throw missingPayrollInput(employee, "attendance on " + day.workDate());
            }
            if (record.getStatus() == AttendanceStatus.MISSING_PUNCH) {
                throw missingPayrollInput(employee, "resolved attendance on " + day.workDate());
            }
        }
        if (attendanceRecords.stream().anyMatch(record -> record.getCheckInAt() != null
            && record.getCheckOutAt() == null)) {
            throw missingPayrollInput(employee, "completed attendance");
        }
        long scheduledWorkMinutes = scheduledDays.stream()
            .mapToLong(AttendanceCalendarService.ScheduledDay::minutes).sum();
        if (scheduledWorkMinutes <= 0) {
            throw missingPayrollInput(employee, "scheduled minutes");
        }
        long payableWorkMinutes = scheduledDays.stream()
            .mapToLong(day -> attendanceByDate.get(day.workDate()).getPayableMinutes()).sum();
        long approvedOvertimeMinutes = attendanceRecords.stream()
            .filter(this::hasApprovedOvertime)
            .mapToLong(AttendanceRecord::getOvertimeMinutes)
            .sum();

        long companyWorkdays = attendanceCalendarService.companyWorkdayCount(
            period.getPeriodStart(), period.getPeriodEnd());
        if (companyWorkdays <= 0) {
            throw missingPayrollInput(employee, "company workdays");
        }

        BigDecimal basicSalary = salaryHistory.get().getBaseSalary();

        BigDecimal basicSalaryPay = BigDecimal.ZERO;
        BigDecimal positionAllowancePay = BigDecimal.ZERO;
        BigDecimal seniorityAllowancePay = BigDecimal.ZERO;
        for (AttendanceCalendarService.ScheduledDay day : scheduledDays) {
            LocalDate date = day.workDate();
            BigDecimal effectiveSalary = employeeSalaryHistoryRepository.findEffective(employee.getId(), date)
                .orElseThrow(() -> missingPayrollInput(employee, "salary history on " + date))
                .getBaseSalary();
            int payableMinutes = attendanceByDate.get(date).getPayableMinutes();
            if (payableMinutes < 0 || payableMinutes > day.minutes()) {
                throw missingPayrollInput(employee, "valid payable minutes on " + date);
            }
            basicSalaryPay = basicSalaryPay.add(effectiveSalary
                .multiply(BigDecimal.valueOf(payableMinutes))
                .divide(BigDecimal.valueOf(companyWorkdays * day.minutes()), 8, RoundingMode.HALF_UP));

            EmployeeAssignment dailyAssignment = employeeAssignmentRepository
                .findCurrentPrimaryCandidates(employee.getId(), date).stream().findFirst()
                .orElseThrow(() -> missingPayrollInput(employee, "primary assignment on " + date));
            BigDecimal dailyPositionAllowance = positionAllowanceRuleRepository
                .findEffective(dailyAssignment.getPosition().getId(), date)
                .map(PositionAllowanceRule::getMonthlyAmount).orElse(BigDecimal.ZERO);
            positionAllowancePay = positionAllowancePay.add(dailyPositionAllowance
                .divide(BigDecimal.valueOf(companyWorkdays), 8, RoundingMode.HALF_UP));

            int dailySeniorityYears = seniorityYears(employee, date);
            BigDecimal dailySeniorityPercentage = seniorityAllowanceRuleRepository
                .findEffective(dailySeniorityYears, date)
                .map(SeniorityAllowanceRule::getPercentage).orElse(BigDecimal.ZERO);
            seniorityAllowancePay = seniorityAllowancePay.add(effectiveSalary
                .multiply(dailySeniorityPercentage)
                .divide(BigDecimal.valueOf(companyWorkdays * 100), 8, RoundingMode.HALF_UP));
        }
        basicSalaryPay = basicSalaryPay.setScale(2, RoundingMode.HALF_UP);
        positionAllowancePay = positionAllowancePay.setScale(2, RoundingMode.HALF_UP);
        seniorityAllowancePay = seniorityAllowancePay.setScale(2, RoundingMode.HALF_UP);
        BigDecimal payableHours = BigDecimal.valueOf(payableWorkMinutes)
            .divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP);
        BigDecimal effectiveHourlyRate = payableHours.signum() == 0 ? BigDecimal.ZERO
            : basicSalaryPay.divide(payableHours, 6, RoundingMode.HALF_UP);

        List<PayslipItem> items = new ArrayList<>();
        items.add(PayslipItem.builder()
            .componentType(PayslipItemType.BASE_SALARY)
            .componentCode("BASE_SALARY")
            .description("Lương cơ bản")
            .quantity(payableHours)
            .unitRate(effectiveHourlyRate)
            .multiplier(BigDecimal.ONE)
            .amount(basicSalaryPay)
            .createdAt(Instant.now())
            .build());

        BigDecimal overtimePay = BigDecimal.ZERO;
        for (AttendanceRecord record : attendanceRecords) {
            if (!hasApprovedOvertime(record)) {
                continue;
            }
            BigDecimal hours = BigDecimal.valueOf(record.getOvertimeMinutes()).divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP);
            BigDecimal overtimeSalary = employeeSalaryHistoryRepository
                .findEffective(employee.getId(), record.getWorkDate())
                .orElseThrow(() -> missingPayrollInput(employee, "salary history on " + record.getWorkDate()))
                .getBaseSalary();
            BigDecimal overtimeHourlyRate = overtimeSalary
                .divide(BigDecimal.valueOf(companyWorkdays * record.getScheduledMinutes()), 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(60));
            BigDecimal amount = overtimeHourlyRate.multiply(hours).multiply(record.getOvertimeMultiplier())
                .setScale(2, RoundingMode.HALF_UP);
            overtimePay = overtimePay.add(amount);
            items.add(PayslipItem.builder()
                .componentType(PayslipItemType.OVERTIME)
                .componentCode("OVERTIME_" + record.getWorkDate())
                .description("Tăng ca ngày " + record.getWorkDate())
                .quantity(hours)
                .unitRate(overtimeHourlyRate)
                .multiplier(record.getOvertimeMultiplier())
                .amount(amount)
                .createdAt(Instant.now())
                .build());
        }

        if (positionAllowancePay.signum() > 0) {
            items.add(PayslipItem.builder()
                .componentType(PayslipItemType.POSITION_ALLOWANCE)
                .componentCode("POSITION_ALLOWANCE_" + assignment.getPosition().getCode())
                .description("Phụ cấp chức vụ " + assignment.getPosition().getTitle())
                .quantity(BigDecimal.ONE)
                .unitRate(positionAllowancePay)
                .multiplier(BigDecimal.ONE)
                .amount(positionAllowancePay)
                .createdAt(Instant.now())
                .build());
        }

        int seniorityYears = seniorityYears(employee, lastWorkDate);
        BigDecimal seniorityPercentage = basicSalary.signum() == 0 ? BigDecimal.ZERO
            : seniorityAllowancePay.multiply(BigDecimal.valueOf(100))
                .divide(basicSalary, 4, RoundingMode.HALF_UP);
        if (seniorityAllowancePay.signum() > 0) {
            items.add(PayslipItem.builder()
                .componentType(PayslipItemType.SENIORITY_ALLOWANCE)
                .componentCode("SENIORITY_ALLOWANCE_" + seniorityYears + "Y")
                .description("Phụ cấp thâm niên " + seniorityYears + " năm")
                .quantity(BigDecimal.ONE)
                .unitRate(basicSalary)
                .multiplier(seniorityPercentage.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP))
                .amount(seniorityAllowancePay)
                .createdAt(Instant.now())
                .build());
        }

        BigDecimal allowancePay = positionAllowancePay.add(seniorityAllowancePay);
        BigDecimal grossPay = basicSalaryPay.add(allowancePay).add(overtimePay);

        Payslip payslip = Payslip.builder()
            .payrollPeriod(period)
            .employee(employee)
            .employeeCodeSnapshot(employee.getEmployeeCode())
            .employeeNameSnapshot(employee.getFullName())
            .positionSnapshot(assignment.getPosition().getTitle())
            .workLocationSnapshot(assignment.getWorkLocation().getName())
            .organizationUnitSnapshot(assignment.getOrganizationUnit().getName())
            .contractualBaseSalary(basicSalary)
            .scheduledWorkMinutes((int) scheduledWorkMinutes)
            .payableWorkMinutes((int) payableWorkMinutes)
            .approvedOvertimeMinutes((int) approvedOvertimeMinutes)
            .baseSalaryPay(basicSalaryPay)
            .positionAllowancePay(positionAllowancePay)
            .seniorityAllowancePay(seniorityAllowancePay)
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

    private boolean hasApprovedOvertime(AttendanceRecord record) {
        return record.getOvertimeMinutes() != null
            && record.getOvertimeMinutes() > 0
            && record.getOvertimeApprovedByAccount() != null
            && record.getOvertimeApprovedAt() != null;
    }

    private int seniorityYears(Employee employee, LocalDate asOfDate) {
        return (int) Math.max(
            0,
            ChronoUnit.YEARS.between(employee.getSeniorityStartDate(), asOfDate)
        );
    }

    @Override
    @PreAuthorize("hasAuthority('payroll.approve')")
    public PayrollPeriodResponse approve(Long periodId, PayrollActionRequest request) {
        employeeAccessScopeService.requireCompanyWide("payroll.approve");
        PayrollPeriod period = findPeriodForUpdateOrThrow(periodId);
        if (period.getStatus() != PayrollPeriodStatus.CALCULATED) {
            throw new ConflictException(ErrorCode.CONFLICT, "Period must be CALCULATED before it can be approved");
        }
        if (attendanceRecordRepository.existsByWorkDateBetweenAndUpdatedAtAfter(
            period.getPeriodStart(), period.getPeriodEnd(), period.getCalculatedAt())) {
            throw new ConflictException(ErrorCode.CONFLICT, "Attendance changed after payroll calculation; recalculate first");
        }
        Account approver = findCurrentAccount();
        if (period.getCalculatedByAccount() != null && period.getCalculatedByAccount().getId().equals(approver.getId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "The approver must be different from the calculator");
        }

        period.setStatus(PayrollPeriodStatus.APPROVED);
        period.setApprovedByAccount(approver);
        period.setApprovedAt(Instant.now());
        period.setUpdatedAt(Instant.now());

        return toResponse(period);
    }

    @Override
    @PreAuthorize("hasAuthority('payroll.mark_paid')")
    public PayrollPeriodResponse markPaid(Long periodId, PayrollActionRequest request) {
        employeeAccessScopeService.requireCompanyWide("payroll.mark_paid");
        PayrollPeriod period = findPeriodForUpdateOrThrow(periodId);
        if (period.getStatus() != PayrollPeriodStatus.APPROVED) {
            throw new ConflictException(ErrorCode.CONFLICT, "Period must be APPROVED before it can be marked as paid");
        }

        Account payer = findCurrentAccount();

        period.setStatus(PayrollPeriodStatus.PAID);
        period.setPaidByAccount(payer);
        period.setPaidAt(Instant.now());
        period.setUpdatedAt(Instant.now());

        return toResponse(period);
    }

    @Override
    @PreAuthorize("hasAuthority('payroll.lock')")
    public PayrollPeriodResponse lock(Long periodId, PayrollActionRequest request) {
        employeeAccessScopeService.requireCompanyWide("payroll.lock");
        PayrollPeriod period = findPeriodForUpdateOrThrow(periodId);
        if (period.getStatus() != PayrollPeriodStatus.PAID) {
            throw new ConflictException(ErrorCode.CONFLICT, "Period must be PAID before it can be locked");
        }

        Account locker = findCurrentAccount();

        period.setStatus(PayrollPeriodStatus.LOCKED);
        period.setLockedByAccount(locker);
        period.setLockedAt(Instant.now());
        period.setUpdatedAt(Instant.now());

        return toResponse(period);
    }

    @Override
    @PreAuthorize("hasAuthority('payroll.calculate')")
    public PayrollPeriodResponse cancel(Long periodId) {
        employeeAccessScopeService.requireCompanyWide("payroll.calculate");
        PayrollPeriod period = findPeriodForUpdateOrThrow(periodId);
        if (period.getStatus() != PayrollPeriodStatus.DRAFT && period.getStatus() != PayrollPeriodStatus.CALCULATED) {
            throw new ConflictException(ErrorCode.CONFLICT, "Only DRAFT or CALCULATED periods can be cancelled");
        }
        period.setStatus(PayrollPeriodStatus.CANCELLED);
        period.setUpdatedAt(Instant.now());
        return toResponse(period);
    }

    @Override
    @PreAuthorize("hasAuthority('report.payroll.read')")
    @Transactional(readOnly = true)
    public PayrollPeriodResponse getPeriodById(Long periodId) {
        employeeAccessScopeService.requireCompanyWide("report.payroll.read");
        return toResponse(findPeriodOrThrow(periodId));
    }

    @Override
    @PreAuthorize("hasAuthority('report.payroll.read')")
    @Transactional(readOnly = true)
    public Page<PayrollPeriodResponse> listPeriods(Pageable pageable) {
        employeeAccessScopeService.requireCompanyWide("report.payroll.read");
        return payrollPeriodRepository.findAll(pageable).map(this::toResponse);
    }

    @Override
    @PreAuthorize("hasAnyAuthority('payroll.self.read', 'report.payroll.read')")
    @Transactional(readOnly = true)
    public PayslipResponse getPayslip(Long payslipId) {
        Payslip payslip = payslipRepository.findById(payslipId)
            .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PAYSLIP_NOT_FOUND, "Payslip not found: " + payslipId));
        requirePayslipAccess(payslip.getEmployee().getId());
        if (!currentAccountProvider.hasAuthority("report.payroll.read")
            && !isReleased(payslip.getPayrollPeriod().getStatus())) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN, "Payslip has not been approved");
        }
        return toResponse(payslip);
    }

    @Override
    @PreAuthorize("hasAnyAuthority('payroll.self.read', 'report.payroll.read')")
    @Transactional(readOnly = true)
    public Page<PayslipResponse> listPayslipsForEmployee(Long employeeId, Pageable pageable) {
        requirePayslipAccess(employeeId);
        if (currentAccountProvider.hasAuthority("report.payroll.read")) {
            return payslipRepository.findByEmployeeId(employeeId, pageable).map(this::toResponse);
        }
        return payslipRepository.findByEmployeeIdAndPayrollPeriodStatusIn(employeeId,
            List.of(PayrollPeriodStatus.APPROVED, PayrollPeriodStatus.PAID, PayrollPeriodStatus.LOCKED),
            pageable).map(this::toResponse);
    }

    private boolean isReleased(PayrollPeriodStatus status) {
        return status == PayrollPeriodStatus.APPROVED || status == PayrollPeriodStatus.PAID
            || status == PayrollPeriodStatus.LOCKED;
    }

    private void requirePayslipAccess(Long employeeId) {
        if (currentAccountProvider.hasAuthority("payroll.self.read")) {
            Account actor = findCurrentAccount();
            if (actor.getEmployee() != null && employeeId.equals(actor.getEmployee().getId())) {
                return;
            }
        }
        if (!currentAccountProvider.hasAuthority("report.payroll.read")) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN, "Payslip is outside the assigned scope");
        }
        employeeAccessScopeService.requireEmployeeAccess(employeeId, "report.payroll.read");
    }

    private BusinessException missingPayrollInput(Employee employee, String input) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR,
            "Cannot calculate payroll for employee " + employee.getEmployeeCode()
                + " (ID " + employee.getId() + "): missing " + input);
    }

    private Account findCurrentAccount() {
        Long accountId = currentAccountProvider.accountId();
        return accountRepository.findById(accountId)
            .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND, "Account not found: " + accountId));
    }

    private PayrollPeriod findPeriodForUpdateOrThrow(Long id) {
        return payrollPeriodRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PAYROLL_PERIOD_NOT_FOUND, "Payroll period not found: " + id));
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
            payslip.getContractualBaseSalary(),
            payslip.getScheduledWorkMinutes(),
            payslip.getPayableWorkMinutes(),
            payslip.getApprovedOvertimeMinutes(),
            payslip.getBaseSalaryPay(),
            payslip.getAllowancePay(),
            payslip.getOvertimePay(),
            payslip.getGrossPay(),
            payslip.getNetPay(),
            items
        );
    }
}
