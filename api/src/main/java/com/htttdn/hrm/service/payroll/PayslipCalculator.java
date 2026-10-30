package com.htttdn.hrm.service.payroll;

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

import com.htttdn.hrm.service.attendance.AttendanceCalendarService;
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
 * theo từng ngày công, {@link #overtime} và {@link #adjustments} tính tăng ca và điều chỉnh,
 * {@link #deductions} tính bảo hiểm và thuế, rồi {@link #calculate} ghép lại và lưu phiếu.
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
    private final InsuranceTaxCalculator insuranceTaxCalculator;
    private final PayrollAdjustmentRepository payrollAdjustmentRepository;

    /**
     * Dữ liệu đầu vào đã kiểm tra đủ của một nhân viên trong kỳ.
     *
     * <p>{@code employedTo}: ngày cuối nhân viên còn làm trong kỳ (ngày nghỉ việc hoặc cuối kỳ);
     * {@code lastWorkDate}: ngày có lịch làm cuối cùng, dùng chọn lương và phân công ghi lên phiếu;
     * {@code companyWorkdays}: số ngày công cả tháng của công ty (mẫu số khi chia lương theo ngày);
     * {@code daysNotEmployed}: ngày công của kỳ trước khi vào làm hoặc sau khi nghỉ việc.
     */
    private record Inputs(
        LocalDate employedTo,
        List<AttendanceCalendarService.ScheduledDay> scheduledDays,
        List<AttendanceRecord> attendanceRecords,
        Map<LocalDate, AttendanceRecord> attendanceByDate,
        LocalDate lastWorkDate,
        BigDecimal basicSalary,
        EmployeeAssignment assignment,
        long scheduledWorkMinutes,
        long companyWorkdays,
        long daysNotEmployed
    ) {
    }

    /** Lương cơ bản và hai khoản phụ cấp đã chia theo ngày công, làm tròn 2 chữ số. */
    private record ProratedPay(BigDecimal basicSalary, BigDecimal positionAllowance, BigDecimal seniorityAllowance) {
    }

    /** Tăng ca đã duyệt trong kỳ: tổng phút, tổng tiền, phần miễn thuế và mỗi ngày một dòng phiếu lương. */
    private record OvertimePay(long minutes, BigDecimal amount, BigDecimal taxExempt, List<PayslipItem> items) {
    }

    /** Các khoản điều chỉnh của kỳ: khoản cộng, phần cộng miễn thuế, khoản trừ và các dòng phiếu lương. */
    private record AdjustmentTotals(BigDecimal earnings, BigDecimal taxExempt, BigDecimal deductions,
        List<PayslipItem> items) {
    }

    /**
     * Tính và lưu phiếu lương (kèm các dòng chi tiết) của một nhân viên trong kỳ.
     *
     * <p>Thu nhập = lương cơ bản + phụ cấp + tăng ca + khoản cộng điều chỉnh; thực nhận = thu nhập − bảo
     * hiểm − thuế TNCN − khoản trừ điều chỉnh. Phiếu lưu kèm ảnh chụp tên, chức vụ, đơn vị và id luật
     * thuế/bảo hiểm lúc tính, để phiếu cũ không đổi khi dữ liệu gốc đổi sau này.
     *
     * @throws BusinessException thiếu dữ liệu đầu vào hoặc khoản trừ lớn hơn thu nhập (→ 400
     *         {@code PAYROLL_INPUT_MISSING}, thông báo tiếng Việt kèm mã nhân viên)
     */
    public void calculate(PayrollPeriod period, Employee employee) {
        Inputs inputs = loadInputs(period, employee);
        ProratedPay prorated = prorate(employee, inputs);
        long payableWorkMinutes = inputs.scheduledDays().stream()
            .mapToLong(day -> inputs.attendanceByDate().get(day.workDate()).getPayableMinutes()).sum();
        OvertimePay overtime = overtime(employee, inputs);
        AdjustmentTotals adjustments = adjustments(period, employee);

        // Thứ tự dòng trên phiếu: lương cơ bản, tăng ca, phụ cấp, điều chỉnh.
        List<PayslipItem> items = new ArrayList<>();
        items.add(baseSalaryItem(prorated.basicSalary(), payableWorkMinutes));
        items.addAll(overtime.items());
        items.addAll(allowanceItems(employee, inputs, prorated));
        items.addAll(adjustments.items());

        BigDecimal allowancePay = prorated.positionAllowance().add(prorated.seniorityAllowance());
        BigDecimal grossPay = prorated.basicSalary().add(allowancePay).add(overtime.amount()).add(adjustments.earnings());
        // Thu nhập chịu thuế: bỏ phần tăng ca và phụ cấp được miễn thuế.
        InsuranceTaxCalculator.Deduction deduction = deductions(period, employee, inputs,
            grossPay.subtract(overtime.taxExempt()).subtract(adjustments.taxExempt()));
        BigDecimal netPay = grossPay.subtract(deduction.social()).subtract(deduction.health())
            .subtract(deduction.unemployment()).subtract(deduction.incomeTax()).subtract(adjustments.deductions());
        // Thực nhận âm (khoản trừ điều chỉnh hoặc bảo hiểm lớn hơn thu nhập) → kế toán phải xử lý tay, không
        // lưu phiếu âm; lỗi này làm hỏng cả lần tính kỳ (xem PayrollService.calculate).
        if (netPay.signum() < 0) {
            throw missingPayrollInput(employee, "khoản trừ lớn hơn thu nhập, cần kế toán xử lý tay");
        }

        EmployeeAssignment assignment = inputs.assignment();
        Payslip payslip = Payslip.builder()
            .payrollPeriod(period)
            .employee(employee)
            .employeeCodeSnapshot(employee.getEmployeeCode())
            .employeeNameSnapshot(employee.getFullName())
            .positionSnapshot(assignment.getPosition().getTitle())
            .workLocationSnapshot(assignment.getWorkLocation().getName())
            .organizationUnitSnapshot(assignment.getOrganizationUnit().getName())
            .contractualBaseSalary(inputs.basicSalary())
            .scheduledWorkMinutes((int) inputs.scheduledWorkMinutes())
            .payableWorkMinutes((int) payableWorkMinutes)
            .approvedOvertimeMinutes((int) overtime.minutes())
            .baseSalaryPay(prorated.basicSalary())
            .positionAllowancePay(prorated.positionAllowance())
            .seniorityAllowancePay(prorated.seniorityAllowance())
            .allowancePay(allowancePay)
            .overtimePay(overtime.amount())
            .taxExemptOvertimePay(overtime.taxExempt())
            .otherEarningsPay(adjustments.earnings())
            .taxExemptOtherEarnings(adjustments.taxExempt())
            .grossPay(grossPay)
            .insuranceSalaryBase(deduction.insuranceSalaryBase())
            .unemploymentInsuranceBase(deduction.unemploymentInsuranceBase())
            .employeeSocialInsurance(deduction.social())
            .employeeHealthInsurance(deduction.health())
            .employeeUnemploymentInsurance(deduction.unemployment())
            .taxableIncome(deduction.taxableIncome())
            .personalIncomeTax(deduction.incomeTax())
            .otherDeductions(adjustments.deductions())
            .taxRuleId(deduction.taxRuleId())
            .insuranceRuleId(deduction.insuranceRuleId())
            .payrollProfileId(deduction.profileId())
            .netPay(netPay)
            .build();
        Payslip savedPayslip = payslipRepository.save(payslip);

        // Dòng phiếu được dựng trước khi có phiếu; gắn phiếu đã lưu (có id) rồi mới lưu các dòng.
        for (PayslipItem item : items) {
            item.setPayslip(savedPayslip);
        }
        payslipItemRepository.saveAll(items);
    }

    /** Dòng lương cơ bản: số giờ được trả, đơn giá = lương đã chia / số giờ đó. */
    private static PayslipItem baseSalaryItem(BigDecimal basicSalaryPay, long payableWorkMinutes) {
        BigDecimal payableHours = BigDecimal.valueOf(payableWorkMinutes)
            .divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP);
        BigDecimal effectiveHourlyRate = payableHours.signum() == 0 ? BigDecimal.ZERO
            : basicSalaryPay.divide(payableHours, 6, RoundingMode.HALF_UP);
        return PayslipItem.builder()
            .componentType(PayslipItemType.BASE_SALARY)
            .componentCode("BASE_SALARY")
            .description("Lương cơ bản")
            .quantity(payableHours)
            .unitRate(effectiveHourlyRate)
            .multiplier(BigDecimal.ONE)
            .amount(basicSalaryPay)
            .build();
    }

    /**
     * Mỗi ngày có tăng ca đã duyệt: đơn giá giờ = lương hiệu lực ngày đó / (số ngày công cả tháng × giờ ca),
     * nhân số giờ và hệ số tăng ca.
     *
     * <p>Số phút, hệ số và việc miễn thuế do người duyệt chọn khi duyệt tăng ca
     * ({@code OvertimeService}). Một query lương cho mỗi ngày tăng ca: chấp nhận được vì mỗi người chỉ có
     * vài ngày tăng ca một tháng.
     *
     * @throws BusinessException không có lương hiệu lực vào ngày tăng ca (→ 400 {@code PAYROLL_INPUT_MISSING})
     */
    private OvertimePay overtime(Employee employee, Inputs inputs) {
        long minutes = 0;
        BigDecimal overtimePay = BigDecimal.ZERO;
        BigDecimal taxExemptOvertimePay = BigDecimal.ZERO;
        List<PayslipItem> items = new ArrayList<>();
        for (AttendanceRecord record : inputs.attendanceRecords()) {
            if (!hasApprovedOvertime(record)) {
                continue;
            }
            minutes += record.getOvertimeMinutes();
            BigDecimal hours = BigDecimal.valueOf(record.getOvertimeMinutes()).divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP);
            BigDecimal overtimeSalary = employeeSalaryHistoryRepository
                .findEffective(employee.getId(), record.getWorkDate())
                .orElseThrow(() -> missingPayrollInput(employee, "thiếu lương cơ bản hiệu lực ngày " + display(record.getWorkDate())))
                .getBaseSalary();
            BigDecimal overtimeHourlyRate = overtimeSalary
                .divide(BigDecimal.valueOf(inputs.companyWorkdays() * record.getScheduledMinutes()), 6, RoundingMode.HALF_UP)
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
        return new OvertimePay(minutes, overtimePay, taxExemptOvertimePay, items);
    }

    /**
     * Dòng phụ cấp chức vụ và thâm niên (khoản bằng 0 thì không có dòng); số tiền đã chia ở {@link #prorate}.
     *
     * <p>Dòng thâm niên ghi số năm và tỉ lệ thực nhận (tiền phụ cấp / lương cơ bản của hợp đồng), nên khi tỉ
     * lệ đổi giữa tháng, tỉ lệ trên phiếu là tỉ lệ bình quân của tháng.
     */
    private List<PayslipItem> allowanceItems(Employee employee, Inputs inputs, ProratedPay prorated) {
        List<PayslipItem> items = new ArrayList<>();
        EmployeeAssignment assignment = inputs.assignment();
        if (prorated.positionAllowance().signum() > 0) {
            items.add(lumpSum(PayslipItemType.POSITION_ALLOWANCE,
                "POSITION_ALLOWANCE_" + assignment.getPosition().getCode(),
                "Phụ cấp chức vụ " + assignment.getPosition().getTitle(), prorated.positionAllowance()));
        }

        BigDecimal basicSalary = inputs.basicSalary();
        BigDecimal seniorityAllowancePay = prorated.seniorityAllowance();
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
        return items;
    }

    /** Thưởng, phụ cấp khác và khoản trừ kế toán nhập cho nhân viên trong kỳ ({@link PayrollAdjustmentService}). */
    private AdjustmentTotals adjustments(PayrollPeriod period, Employee employee) {
        BigDecimal otherEarningsPay = BigDecimal.ZERO;
        BigDecimal taxExemptOtherEarnings = BigDecimal.ZERO;
        BigDecimal otherDeductions = BigDecimal.ZERO;
        List<PayslipItem> items = new ArrayList<>();
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
        return new AdjustmentTotals(otherEarningsPay, taxExemptOtherEarnings, otherDeductions, items);
    }

    /**
     * Bảo hiểm và thuế TNCN của tháng ({@link InsuranceTaxCalculator#calculate}). Ngày không làm việc gồm
     * ngày nghỉ không lương, vắng không phép và ngày công của tháng trước khi vào làm hoặc sau khi nghỉ
     * việc; đủ 14 ngày thì tháng đó không trừ bảo hiểm. Lỗi thiếu hồ sơ được báo kèm mã nhân viên.
     */
    private InsuranceTaxCalculator.Deduction deductions(PayrollPeriod period, Employee employee, Inputs inputs,
        BigDecimal taxableGrossPay) {
        // Chỉ đếm ngày nghỉ trọn ngày (0 phút được trả); nghỉ nửa ngày vẫn tính là có làm việc.
        long unpaidDays = inputs.scheduledDays().stream().filter(day -> {
            AttendanceRecord record = inputs.attendanceByDate().get(day.workDate());
            return record.getPayableMinutes() == 0 && (record.getStatus() == AttendanceStatus.UNPAID_LEAVE
                || record.getStatus() == AttendanceStatus.UNAUTHORIZED_ABSENCE);
        }).count();
        // Nghỉ việc đóng người phụ thuộc ở ngày làm cuối; lương tháng cuối thường trả sau ngày đó, nên đếm
        // người phụ thuộc tại ngày làm cuối để phiếu lương cuối vẫn được giảm trừ gia cảnh.
        LocalDate paymentDate = period.getTaxPaymentDate();
        LocalDate lastWorkingDay = employee.getTerminationDate();
        LocalDate dependentsOn = lastWorkingDay != null && lastWorkingDay.isBefore(paymentDate) ? lastWorkingDay : paymentDate;
        try {
            return insuranceTaxCalculator.calculate(employee.getId(), inputs.employedTo(),
                paymentDate, dependentsOn, taxableGrossPay, unpaidDays + inputs.daysNotEmployed());
        } catch (BusinessException error) {
            // Bọc lại để thông báo có mã nhân viên; thành 400 PAYROLL_INPUT_MISSING. Câu của
            // InsuranceTaxCalculator đã là tiếng Việt, viết để nối vào sau tên nhân viên.
            throw missingPayrollInput(employee, error.getMessage());
        }
    }

    /**
     * Thiếu bất kỳ dữ liệu nào (lịch làm, lương, phân công, chấm công) thì báo lỗi, không tính dở.
     *
     * <p>Chỉ xét phần kỳ nhân viên còn làm ([{@code employedFrom}, {@code employedTo}]). Ngày công của công ty
     * là mọi ngày trừ Chủ nhật và ngày lễ ({@code AttendanceCalendarService.companyWorkDates}); mỗi ngày đó
     * phải có phân công chính kèm ca, và có ngày công đã chốt.
     *
     * @throws BusinessException dữ liệu nào thiếu thì thông báo nêu đúng dữ liệu đó (→ 400 {@code PAYROLL_INPUT_MISSING})
     */
    private Inputs loadInputs(PayrollPeriod period, Employee employee) {
        LocalDate employedFrom = employee.employedFrom(period.getPeriodStart());
        LocalDate employedTo = employee.employedTo(period.getPeriodEnd());
        // Không có ngày công nào (VD vào làm rồi nghỉ trong mấy ngày lễ) → không có gì để chia lương theo ngày.
        List<LocalDate> expectedWorkDates = attendanceCalendarService.companyWorkDates(employedFrom, employedTo);
        if (expectedWorkDates.isEmpty()) {
            throw missingPayrollInput(employee, "không có ngày công nào từ " + display(employedFrom) + " đến "
                + display(employedTo) + ", cần xem xét tay lương ngày lễ, ngày nghỉ");
        }

        // scheduleFor: ngày công kèm ca của phân công chính hiệu lực ngày đó; bỏ qua ngày không có phân công,
        // ném lỗi khi phân công thiếu ca. Ngày bị bỏ qua được bắt ở vòng lặp ngay sau.
        List<AttendanceCalendarService.ScheduledDay> scheduledDays;
        try {
            scheduledDays = attendanceCalendarService.scheduleFor(employee.getId(), employedFrom, employedTo);
        } catch (BusinessException error) {
            throw missingPayrollInput(employee, "phân công chính trong kỳ chưa có ca làm việc");
        }
        Map<LocalDate, AttendanceCalendarService.ScheduledDay> scheduleByDate = scheduledDays.stream()
            .collect(Collectors.toMap(AttendanceCalendarService.ScheduledDay::workDate, Function.identity()));
        for (LocalDate date : expectedWorkDates) {
            if (!scheduleByDate.containsKey(date)) {
                throw missingPayrollInput(employee, "thiếu phân công chính kèm ca làm việc ngày " + display(date));
            }
        }
        // Lương và phân công ghi lên phiếu là của ngày làm cuối cùng; tiền thì vẫn chia theo từng ngày ở prorate.
        LocalDate lastWorkDate = scheduledDays.get(scheduledDays.size() - 1).workDate();
        BigDecimal basicSalary = employeeSalaryHistoryRepository.findEffective(employee.getId(), lastWorkDate)
            .orElseThrow(() -> missingPayrollInput(employee, "thiếu lương cơ bản hiệu lực ngày " + display(lastWorkDate)))
            .getBaseSalary();

        EmployeeAssignment assignment = employeeAssignmentRepository
            .findCurrentPrimary(employee.getId(), lastWorkDate)
            .orElseThrow(() -> missingPayrollInput(employee, "thiếu phân công chính ngày " + display(lastWorkDate)));

        List<AttendanceRecord> attendanceRecords = attendanceRecordRepository.findByEmployeeIdAndWorkDateBetween(
            employee.getId(), period.getPeriodStart(), period.getPeriodEnd());
        if (attendanceRecords.isEmpty()) {
            throw missingPayrollInput(employee, "chưa có dữ liệu chấm công trong kỳ");
        }
        Map<LocalDate, AttendanceRecord> attendanceByDate = attendanceRecords.stream()
            .collect(Collectors.toMap(AttendanceRecord::getWorkDate, Function.identity()));
        for (AttendanceCalendarService.ScheduledDay day : scheduledDays) {
            AttendanceRecord record = attendanceByDate.get(day.workDate());
            if (record == null) {
                throw missingPayrollInput(employee, "thiếu chấm công ngày " + display(day.workDate()));
            }
            if (record.getStatus() == AttendanceStatus.MISSING_PUNCH) {
                throw missingPayrollInput(employee, "chấm công ngày " + display(day.workDate()) + " còn thiếu giờ vào hoặc giờ ra chưa xử lý");
            }
        }
        // Có giờ vào mà chưa có giờ ra (kể cả ngày ngoài lịch, VD tăng ca Chủ nhật) → ngày công chưa xong.
        if (attendanceRecords.stream().anyMatch(record -> record.getCheckInAt() != null
            && record.getCheckOutAt() == null)) {
            throw missingPayrollInput(employee, "còn ngày chấm công có giờ vào mà chưa có giờ ra");
        }
        long scheduledWorkMinutes = scheduledDays.stream()
            .mapToLong(AttendanceCalendarService.ScheduledDay::minutes).sum();
        if (scheduledWorkMinutes <= 0) {
            throw missingPayrollInput(employee, "ca làm việc trong kỳ không có số phút theo lịch");
        }

        long companyWorkdays = attendanceCalendarService.companyWorkdayCount(
            period.getPeriodStart(), period.getPeriodEnd());
        if (companyWorkdays <= 0) {
            throw missingPayrollInput(employee, "kỳ lương không có ngày công nào của công ty");
        }
        // Ngày công của kỳ trước ngày vào làm và sau ngày nghỉ việc: tính là không làm việc (xem deductions).
        long daysNotEmployed = 0;
        if (employedFrom.isAfter(period.getPeriodStart())) {
            daysNotEmployed += attendanceCalendarService.companyWorkdayCount(period.getPeriodStart(), employedFrom.minusDays(1));
        }
        if (employedTo.isBefore(period.getPeriodEnd())) {
            daysNotEmployed += attendanceCalendarService.companyWorkdayCount(employedTo.plusDays(1), period.getPeriodEnd());
        }

        return new Inputs(employedTo, scheduledDays, attendanceRecords, attendanceByDate, lastWorkDate,
            basicSalary, assignment, scheduledWorkMinutes, companyWorkdays, daysNotEmployed);
    }

    /**
     * Mỗi ngày công được hưởng phần (phút được trả / phút ca) của 1/(số ngày công cả tháng) khoản tháng,
     * theo lương, chức vụ và thâm niên đang hiệu lực đúng ngày đó.
     *
     * <p>Mẫu số là số ngày công cả tháng của công ty, không phải số ngày của nhân viên: làm đủ mọi ngày công
     * thì nhận đủ lương tháng, vào làm giữa tháng thì nhận theo số ngày đã làm. Ngày lễ không phải ngày
     * công nên không làm giảm lương. Cộng dồn với 8 chữ số thập phân, làm tròn 2 chữ số một lần ở cuối.
     *
     * <p>Mỗi ngày vài query (lương, phân công, phụ cấp): khoảng 26 ngày × vài query mỗi nhân viên, chấp nhận
     * được với quy mô công ty hiện tại.
     *
     * @throws BusinessException thiếu lương/phân công một ngày, hoặc phút được trả vượt phút ca (→ 400
     *         {@code PAYROLL_INPUT_MISSING})
     */
    private ProratedPay prorate(Employee employee, Inputs inputs) {
        long companyWorkdays = inputs.companyWorkdays();
        BigDecimal basicSalaryPay = BigDecimal.ZERO;
        BigDecimal positionAllowancePay = BigDecimal.ZERO;
        BigDecimal seniorityAllowancePay = BigDecimal.ZERO;
        for (AttendanceCalendarService.ScheduledDay day : inputs.scheduledDays()) {
            LocalDate date = day.workDate();
            BigDecimal effectiveSalary = employeeSalaryHistoryRepository.findEffective(employee.getId(), date)
                .orElseThrow(() -> missingPayrollInput(employee, "thiếu lương cơ bản hiệu lực ngày " + display(date)))
                .getBaseSalary();
            int payableMinutes = inputs.attendanceByDate().get(date).getPayableMinutes();
            if (payableMinutes < 0 || payableMinutes > day.minutes()) {
                throw missingPayrollInput(employee, "số phút được trả ngày " + display(date) + " không hợp lệ");
            }
            basicSalaryPay = basicSalaryPay.add(effectiveSalary
                .multiply(BigDecimal.valueOf(payableMinutes))
                .divide(BigDecimal.valueOf(companyWorkdays * day.minutes()), 8, RoundingMode.HALF_UP));

            EmployeeAssignment dailyAssignment = employeeAssignmentRepository
                .findCurrentPrimary(employee.getId(), date)
                .orElseThrow(() -> missingPayrollInput(employee, "thiếu phân công chính ngày " + display(date)));
            BigDecimal dailyPositionAllowance = positionAllowanceRuleRepository
                .findEffective(dailyAssignment.getPosition().getId(), date)
                .map(PositionAllowanceRule::getMonthlyAmount).orElse(BigDecimal.ZERO);
            positionAllowancePay = positionAllowancePay.add(dailyPositionAllowance
                .multiply(BigDecimal.valueOf(payableMinutes))
                .divide(BigDecimal.valueOf(companyWorkdays * day.minutes()), 8, RoundingMode.HALF_UP));

            // Phụ cấp thâm niên là % lương cơ bản (percentage tính theo trăm, nên chia thêm 100).
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

    /** Ngày có tăng ca được trả: người duyệt đã duyệt (có người và thời điểm duyệt) với số phút lớn hơn 0. */
    private boolean hasApprovedOvertime(AttendanceRecord record) {
        return record.getOvertimeMinutes() != null
            && record.getOvertimeMinutes() > 0
            && record.getOvertimeApprovedByAccount() != null
            && record.getOvertimeApprovedAt() != null;
    }

    /** Số năm thâm niên tròn tính từ {@code seniorityStartDate} đến {@code asOfDate}; chưa tới ngày bắt đầu → 0. */
    private int seniorityYears(Employee employee, LocalDate asOfDate) {
        return (int) Math.max(
            0,
            ChronoUnit.YEARS.between(employee.getSeniorityStartDate(), asOfDate)
        );
    }

    /**
     * Lỗi 400 {@code PAYROLL_INPUT_MISSING} nêu nhân viên (mã, tên) và dữ liệu còn thiếu, để HR biết sửa ở đâu.
     * Viết bằng tiếng Việt vì web hiện nguyên câu này cho HR.
     */
    private BusinessException missingPayrollInput(Employee employee, String problem) {
        String who = employee.getFullName() == null ? employee.getEmployeeCode()
            : employee.getEmployeeCode() + " - " + employee.getFullName();
        return new BusinessException(ErrorCode.PAYROLL_INPUT_MISSING,
            "Chưa tính được lương cho nhân viên " + who + ": " + problem + ".");
    }

    private static String display(LocalDate date) {
        return date.format(DISPLAY_DATE);
    }
}
