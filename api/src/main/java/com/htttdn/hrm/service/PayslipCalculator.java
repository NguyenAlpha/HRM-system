package com.htttdn.hrm.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.AttendanceRecord;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.EmployeeAssignment;
import com.htttdn.hrm.entity.PayrollAdjustment;
import com.htttdn.hrm.entity.PayrollPeriod;
import com.htttdn.hrm.entity.Payslip;
import com.htttdn.hrm.entity.PayslipItem;
import com.htttdn.hrm.entity.PositionAllowanceRule;
import com.htttdn.hrm.entity.SeniorityAllowanceRule;
import com.htttdn.hrm.entity.enums.AttendanceStatus;
import com.htttdn.hrm.entity.enums.PayrollAdjustmentType;
import com.htttdn.hrm.entity.enums.PayslipItemType;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.repository.AttendanceRecordRepository;
import com.htttdn.hrm.repository.EmployeeAssignmentRepository;
import com.htttdn.hrm.repository.EmployeeSalaryHistoryRepository;
import com.htttdn.hrm.repository.PayrollAdjustmentRepository;
import com.htttdn.hrm.repository.PayslipItemRepository;
import com.htttdn.hrm.repository.PayslipRepository;
import com.htttdn.hrm.repository.PositionAllowanceRuleRepository;
import com.htttdn.hrm.repository.SeniorityAllowanceRuleRepository;

import lombok.RequiredArgsConstructor;

/**
 * Tính phiếu lương của một nhân viên trong một kỳ, tách khỏi {@link PayrollService} để service đó chỉ
 * còn vòng đời kỳ lương (tạo, tính, duyệt, trả, khóa, hủy).
 *
 * <p>Là {@code @Component} chứ không phải service: không có {@code @PreAuthorize} hay
 * {@code @Transactional} riêng, chỉ được gọi từ {@link PayrollService#calculate}, nên chạy trong
 * transaction và quyền đã kiểm tra ở đó.
 *
 * <p>Các bước: {@link #loadInputs} kiểm tra đủ dữ liệu đầu vào, {@link #prorate} chia lương và phụ cấp
 * theo từng ngày công, rồi {@link #calculate} thêm tăng ca, điều chỉnh, khấu trừ và lưu phiếu.
 */
@Component
@RequiredArgsConstructor
public class PayslipCalculator {

    /** Dates written into payslip lines read like the rest of the app: 24/10/2026. */
    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final PayslipRepository payslipRepository;
    private final PayslipItemRepository payslipItemRepository;
    private final EmployeeAssignmentRepository employeeAssignmentRepository;
    private final EmployeeSalaryHistoryRepository employeeSalaryHistoryRepository;
    private final PositionAllowanceRuleRepository positionAllowanceRuleRepository;
    private final SeniorityAllowanceRuleRepository seniorityAllowanceRuleRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final AttendanceCalendarService attendanceCalendarService;
    private final PayrollDeductionsService payrollDeductionsService;
    private final PayrollAdjustmentRepository payrollAdjustmentRepository;

    /** Dữ liệu đầu vào đã kiểm tra đủ của một nhân viên trong kỳ. */
    private record Inputs(
        LocalDate employedTo,
        List<AttendanceCalendarService.ScheduledDay> scheduledDays,
        List<AttendanceRecord> attendanceRecords,
        Map<LocalDate, AttendanceRecord> attendanceByDate,
        LocalDate lastWorkDate,
        BigDecimal basicSalary,
        EmployeeAssignment assignment,
        long scheduledWorkMinutes,
        long companyWorkdays
    ) {
    }

    /** Lương cơ bản và hai khoản phụ cấp đã chia theo ngày công, làm tròn 2 chữ số. */
    private record ProratedPay(BigDecimal basicSalary, BigDecimal positionAllowance, BigDecimal seniorityAllowance) {
    }

    public void calculate(PayrollPeriod period, Employee employee) {
        Inputs inputs = loadInputs(period, employee);
        ProratedPay prorated = prorate(employee, inputs);
        List<AttendanceRecord> attendanceRecords = inputs.attendanceRecords();
        Map<LocalDate, AttendanceRecord> attendanceByDate = inputs.attendanceByDate();
        EmployeeAssignment assignment = inputs.assignment();
        BigDecimal basicSalary = inputs.basicSalary();
        long companyWorkdays = inputs.companyWorkdays();

        long payableWorkMinutes = inputs.scheduledDays().stream()
            .mapToLong(day -> attendanceByDate.get(day.workDate()).getPayableMinutes()).sum();
        long approvedOvertimeMinutes = attendanceRecords.stream()
            .filter(this::hasApprovedOvertime)
            .mapToLong(AttendanceRecord::getOvertimeMinutes)
            .sum();

        BigDecimal basicSalaryPay = prorated.basicSalary();
        BigDecimal positionAllowancePay = prorated.positionAllowance();
        BigDecimal seniorityAllowancePay = prorated.seniorityAllowance();
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
            .build());

        BigDecimal overtimePay = BigDecimal.ZERO;
        BigDecimal taxExemptOvertimePay = BigDecimal.ZERO;
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
            if (record.isOvertimeTaxExempt()) {
                taxExemptOvertimePay = taxExemptOvertimePay.add(amount);
            }
            items.add(PayslipItem.builder()
                .componentType(PayslipItemType.OVERTIME)
                .componentCode("OVERTIME_" + record.getWorkDate())
                .description("Tăng ca ngày " + record.getWorkDate().format(DISPLAY_DATE))
                .quantity(hours)
                .unitRate(overtimeHourlyRate)
                .multiplier(record.getOvertimeMultiplier())
                .amount(amount)
                .build());
        }

        if (positionAllowancePay.signum() > 0) {
            items.add(lumpSum(PayslipItemType.POSITION_ALLOWANCE,
                "POSITION_ALLOWANCE_" + assignment.getPosition().getCode(),
                "Phụ cấp chức vụ " + assignment.getPosition().getTitle(), positionAllowancePay));
        }

        int seniorityYears = seniorityYears(employee, inputs.lastWorkDate());
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
                .build());
        }

        BigDecimal otherEarningsPay = BigDecimal.ZERO;
        BigDecimal taxExemptOtherEarnings = BigDecimal.ZERO;
        BigDecimal otherDeductions = BigDecimal.ZERO;
        for (PayrollAdjustment adjustment : payrollAdjustmentRepository
            .findByPayrollPeriodIdAndEmployeeIdOrderByIdAsc(period.getId(), employee.getId())) {
            PayrollAdjustmentType type = adjustment.getAdjustmentType();
            if (type == PayrollAdjustmentType.DEDUCTION) {
                otherDeductions = otherDeductions.add(adjustment.getAmount());
            } else {
                otherEarningsPay = otherEarningsPay.add(adjustment.getAmount());
                if (type == PayrollAdjustmentType.NON_TAXABLE_ALLOWANCE) {
                    taxExemptOtherEarnings = taxExemptOtherEarnings.add(adjustment.getAmount());
                }
            }
            items.add(lumpSum(PayslipItemType.valueOf(type.name()), "ADJUSTMENT_" + adjustment.getId(),
                adjustment.getDescription(), adjustment.getAmount()));
        }

        BigDecimal allowancePay = positionAllowancePay.add(seniorityAllowancePay);
        BigDecimal grossPay = basicSalaryPay.add(allowancePay).add(overtimePay).add(otherEarningsPay);
        long unpaidDays = inputs.scheduledDays().stream().filter(day -> {
            AttendanceRecord record = attendanceByDate.get(day.workDate());
            return record.getPayableMinutes() == 0 && (record.getStatus() == AttendanceStatus.UNPAID_LEAVE
                || record.getStatus() == AttendanceStatus.UNAUTHORIZED_ABSENCE);
        }).count();
        PayrollDeductionsService.Deduction deduction;
        try {
            deduction = payrollDeductionsService.calculate(employee.getId(), inputs.employedTo(),
                period.getTaxPaymentDate(), grossPay.subtract(taxExemptOvertimePay).subtract(taxExemptOtherEarnings),
                unpaidDays);
        } catch (BusinessException error) {
            throw missingPayrollInput(employee, error.getMessage());
        }
        BigDecimal netPay = grossPay.subtract(deduction.social()).subtract(deduction.health())
            .subtract(deduction.unemployment()).subtract(deduction.incomeTax()).subtract(otherDeductions);
        if (netPay.signum() < 0) {
            throw missingPayrollInput(employee, "manual settlement: deductions exceed gross pay");
        }

        Payslip payslip = Payslip.builder()
            .payrollPeriod(period)
            .employee(employee)
            .employeeCodeSnapshot(employee.getEmployeeCode())
            .employeeNameSnapshot(employee.getFullName())
            .positionSnapshot(assignment.getPosition().getTitle())
            .workLocationSnapshot(assignment.getWorkLocation().getName())
            .organizationUnitSnapshot(assignment.getOrganizationUnit().getName())
            .contractualBaseSalary(basicSalary)
            .scheduledWorkMinutes((int) inputs.scheduledWorkMinutes())
            .payableWorkMinutes((int) payableWorkMinutes)
            .approvedOvertimeMinutes((int) approvedOvertimeMinutes)
            .baseSalaryPay(basicSalaryPay)
            .positionAllowancePay(positionAllowancePay)
            .seniorityAllowancePay(seniorityAllowancePay)
            .allowancePay(allowancePay)
            .overtimePay(overtimePay)
            .taxExemptOvertimePay(taxExemptOvertimePay)
            .otherEarningsPay(otherEarningsPay)
            .taxExemptOtherEarnings(taxExemptOtherEarnings)
            .grossPay(grossPay)
            .insuranceSalaryBase(deduction.insuranceSalaryBase())
            .unemploymentInsuranceBase(deduction.unemploymentInsuranceBase())
            .employeeSocialInsurance(deduction.social())
            .employeeHealthInsurance(deduction.health())
            .employeeUnemploymentInsurance(deduction.unemployment())
            .taxableIncome(deduction.taxableIncome())
            .personalIncomeTax(deduction.incomeTax())
            .otherDeductions(otherDeductions)
            .taxRuleId(deduction.taxRuleId())
            .insuranceRuleId(deduction.insuranceRuleId())
            .payrollProfileId(deduction.profileId())
            .netPay(netPay)
            .build();
        Payslip savedPayslip = payslipRepository.save(payslip);

        for (PayslipItem item : items) {
            item.setPayslip(savedPayslip);
        }
        payslipItemRepository.saveAll(items);
    }

    /** Thiếu bất kỳ dữ liệu nào (lịch làm, lương, phân công, chấm công) thì báo lỗi, không tính dở. */
    private Inputs loadInputs(PayrollPeriod period, Employee employee) {
        LocalDate employedFrom = employee.getHireDate().isAfter(period.getPeriodStart())
            ? employee.getHireDate() : period.getPeriodStart();
        LocalDate employedTo = employee.getTerminationDate() != null
            && employee.getTerminationDate().isBefore(period.getPeriodEnd())
                ? employee.getTerminationDate() : period.getPeriodEnd();
        List<LocalDate> expectedWorkDates = attendanceCalendarService.companyWorkDates(employedFrom, employedTo);
        if (expectedWorkDates.isEmpty()) {
            throw missingPayrollInput(employee, "company workdays between " + employedFrom + " and "
                + employedTo + "; review holiday or rest-day pay manually");
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
        BigDecimal basicSalary = employeeSalaryHistoryRepository.findEffective(employee.getId(), lastWorkDate)
            .orElseThrow(() -> missingPayrollInput(employee, "salary history on " + lastWorkDate))
            .getBaseSalary();

        EmployeeAssignment assignment = employeeAssignmentRepository
            .findCurrentPrimaryCandidates(employee.getId(), lastWorkDate).stream()
            .findFirst()
            .orElseThrow(() -> missingPayrollInput(employee, "primary assignment"));

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

        long companyWorkdays = attendanceCalendarService.companyWorkdayCount(
            period.getPeriodStart(), period.getPeriodEnd());
        if (companyWorkdays <= 0) {
            throw missingPayrollInput(employee, "company workdays");
        }

        return new Inputs(employedTo, scheduledDays, attendanceRecords, attendanceByDate, lastWorkDate,
            basicSalary, assignment, scheduledWorkMinutes, companyWorkdays);
    }

    /**
     * Mỗi ngày công được hưởng phần (phút được trả / phút ca) của 1/(số ngày công cả tháng) khoản tháng,
     * theo lương, chức vụ và thâm niên đang hiệu lực đúng ngày đó.
     */
    private ProratedPay prorate(Employee employee, Inputs inputs) {
        long companyWorkdays = inputs.companyWorkdays();
        BigDecimal basicSalaryPay = BigDecimal.ZERO;
        BigDecimal positionAllowancePay = BigDecimal.ZERO;
        BigDecimal seniorityAllowancePay = BigDecimal.ZERO;
        for (AttendanceCalendarService.ScheduledDay day : inputs.scheduledDays()) {
            LocalDate date = day.workDate();
            BigDecimal effectiveSalary = employeeSalaryHistoryRepository.findEffective(employee.getId(), date)
                .orElseThrow(() -> missingPayrollInput(employee, "salary history on " + date))
                .getBaseSalary();
            int payableMinutes = inputs.attendanceByDate().get(date).getPayableMinutes();
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
                .multiply(BigDecimal.valueOf(payableMinutes))
                .divide(BigDecimal.valueOf(companyWorkdays * day.minutes()), 8, RoundingMode.HALF_UP));

            int dailySeniorityYears = seniorityYears(employee, date);
            BigDecimal dailySeniorityPercentage = seniorityAllowanceRuleRepository
                .findEffective(dailySeniorityYears, date)
                .map(SeniorityAllowanceRule::getPercentage).orElse(BigDecimal.ZERO);
            seniorityAllowancePay = seniorityAllowancePay.add(effectiveSalary
                .multiply(dailySeniorityPercentage)
                .multiply(BigDecimal.valueOf(payableMinutes))
                .divide(BigDecimal.valueOf(companyWorkdays * 100 * day.minutes()), 8, RoundingMode.HALF_UP));
        }
        return new ProratedPay(
            basicSalaryPay.setScale(2, RoundingMode.HALF_UP),
            positionAllowancePay.setScale(2, RoundingMode.HALF_UP),
            seniorityAllowancePay.setScale(2, RoundingMode.HALF_UP));
    }

    /** Dòng phiếu lương trả trọn một khoản: số lượng 1, hệ số 1, đơn giá bằng số tiền. */
    private static PayslipItem lumpSum(PayslipItemType type, String code, String description, BigDecimal amount) {
        return PayslipItem.builder()
            .componentType(type)
            .componentCode(code)
            .description(description)
            .quantity(BigDecimal.ONE)
            .unitRate(amount)
            .multiplier(BigDecimal.ONE)
            .amount(amount)
            .build();
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

    private BusinessException missingPayrollInput(Employee employee, String input) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR,
            "Cannot calculate payroll for employee " + employee.getEmployeeCode()
                + " (ID " + employee.getId() + "): missing " + input);
    }
}
