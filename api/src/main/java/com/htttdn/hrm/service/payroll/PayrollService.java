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

import jakarta.annotation.Nullable;

import lombok.RequiredArgsConstructor;

/**
 * Vòng đời kỳ lương: tạo, tính, duyệt, đánh dấu đã trả, khóa và hủy. Cách tính một phiếu lương nằm ở
 * {@link PayslipCalculator}, phần chỉ đọc nằm ở {@link PayrollQueryService}.
 *
 * <p>Trạng thái đi một chiều: {@code DRAFT} → {@code CALCULATED} → {@code APPROVED} → {@code PAID} →
 * {@code LOCKED}; chỉ {@code DRAFT}/{@code CALCULATED} được tính lại hoặc hủy ({@code CANCELLED}). Kỳ đã tính
 * mà dữ liệu đầu vào đổi thì quay về {@code DRAFT} qua {@link PayrollPeriodInvalidator}.
 *
 * <p>Mọi thao tác cần quyền phạm vi toàn công ty ({@code EmployeeAccessScopeService.requireCompanyWide},
 * thiếu → 403) và khóa ghi kỳ lương ({@code getRequiredForUpdate}, không có → 404
 * {@code PAYROLL_PERIOD_NOT_FOUND}) để hai người không cùng đổi một kỳ.
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

    /**
     * Mở kỳ lương {@code DRAFT} cho một tháng, từ ngày 1 đến ngày cuối tháng.
     *
     * @param request năm, tháng và ngày trả lương dự kiến (có thể để trống, bắt buộc khi tính)
     * @throws ConflictException tháng đó đã có kỳ chưa hủy (→ 409 {@code CONFLICT})
     */
    @PreAuthorize("hasAuthority('payroll.calculate')")
    public PayrollPeriodResponse createPeriod(CreatePayrollPeriodRequest request) {
        employeeAccessScopeService.requireCompanyWide("payroll.calculate");
        // Kiểm tra trước để báo lỗi rõ; uq_payroll_periods_year_month (bỏ qua kỳ CANCELLED) vẫn chặn khi hai
        // request tạo cùng lúc.
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

    /**
     * Tính (hoặc tính lại) phiếu lương cho mọi nhân viên có làm việc trong kỳ, rồi chuyển kỳ sang
     * {@code CALCULATED}.
     *
     * <p>Thứ tự: kiểm tra trạng thái → chốt ngày trả lương → xóa phiếu cũ → mỗi nhân viên một phiếu qua
     * {@link PayslipCalculator#calculate}. Cả kỳ chạy trong một transaction: một nhân viên thiếu dữ liệu
     * thì không phiếu nào được lưu và kỳ giữ nguyên trạng thái.
     *
     * @param request {@code taxPaymentDate} ghi đè ngày trả lương của kỳ (null → giữ ngày đã có)
     * @return kỳ đã tính, kèm tổng số phiếu, tổng thu nhập và thực nhận
     * @throws ConflictException kỳ đã duyệt/trả/khóa/hủy (→ 409 {@code PAYROLL_PERIOD_LOCKED})
     * @throws BusinessException thiếu ngày trả lương, trùng tháng nộp thuế với kỳ khác, hoặc một nhân viên
     *         thiếu dữ liệu tính lương (→ 400 {@code VALIDATION_ERROR}, thông báo kèm mã nhân viên)
     */
    @PreAuthorize("hasAuthority('payroll.calculate')")
    public PayrollPeriodResponse calculate(Long periodId, PayrollActionRequest request) {
        employeeAccessScopeService.requireCompanyWide("payroll.calculate");
        PayrollPeriod period = payrollPeriodRepository.getRequiredForUpdate(periodId);
        if (period.getStatus() != PayrollPeriodStatus.DRAFT && period.getStatus() != PayrollPeriodStatus.CALCULATED) {
            throw new ConflictException(
                ErrorCode.PAYROLL_PERIOD_LOCKED, "Period " + periodId + " can no longer be recalculated");
        }

        // Ngày trả lương quyết định luật thuế và số người phụ thuộc áp dụng (xem InsuranceTaxCalculator).
        if (request.taxPaymentDate() != null) {
            period.setTaxPaymentDate(request.taxPaymentDate());
        }
        if (period.getTaxPaymentDate() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                "taxPaymentDate is required to calculate payroll withholding");
        }
        // Thuế TNCN tính theo từng tháng trả lương: hai kỳ đã tính trả cùng một tháng thì mỗi kỳ được giảm
        // trừ gia cảnh một lần, thuế bị tính thiếu → chặn, vì hệ thống chưa gộp thu nhập nhiều kỳ.
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
        // Đẩy lệnh xóa xuống DB ngay: Hibernate chạy INSERT trước DELETE khi flush, phiếu mới sẽ vấp
        // uq_payslips_period_employee với phiếu cũ chưa xóa.
        payslipRepository.flush();

        // Nhân viên chưa xóa, vào làm trước cuối kỳ và chưa nghỉ trước đầu kỳ, theo mã nhân viên.
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

    /**
     * Duyệt bảng lương đã tính. Từ đây phiếu lương hiện cho nhân viên và dữ liệu ảnh hưởng tới kỳ bị khóa
     * (đổi lương, phụ cấp, chấm công… của kỳ sẽ bị từ chối 409 {@code PAYROLL_PERIOD_LOCKED}).
     *
     * @throws ConflictException kỳ chưa ở {@code CALCULATED}, hoặc chấm công trong kỳ đã sửa sau lúc tính
     *         (→ 409 {@code CONFLICT})
     * @throws BusinessException người duyệt cũng là người tính (→ 400 {@code VALIDATION_ERROR})
     */
    @PreAuthorize("hasAuthority('payroll.approve')")
    public PayrollPeriodResponse approve(Long periodId) {
        employeeAccessScopeService.requireCompanyWide("payroll.approve");
        PayrollPeriod period = payrollPeriodRepository.getRequiredForUpdate(periodId);
        if (period.getStatus() != PayrollPeriodStatus.CALCULATED) {
            throw new ConflictException(ErrorCode.CONFLICT, "Period must be CALCULATED before it can be approved");
        }
        // Chấm công sửa sau lúc tính (updated_at mới hơn calculatedAt) → bảng lương có thể đã lỗi thời.
        if (attendanceRecordRepository.existsByWorkDateBetweenAndUpdatedAtAfter(
            period.getPeriodStart(), period.getPeriodEnd(), period.getCalculatedAt())) {
            throw new ConflictException(ErrorCode.CONFLICT, "Attendance changed after payroll calculation; recalculate first");
        }
        Account approver = currentAccountProvider.account();
        // Tách người tính và người duyệt (four-eyes): một người không tự duyệt bảng lương mình tính.
        if (period.getCalculatedByAccount() != null && period.getCalculatedByAccount().getId().equals(approver.getId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "The approver must be different from the calculator");
        }

        period.setStatus(PayrollPeriodStatus.APPROVED);
        period.setApprovedByAccount(approver);
        period.setApprovedAt(Instant.now(clock));

        return payrollMapper.toResponse(period);
    }

    /**
     * Ghi nhận đã trả lương và chốt ngày trả thực tế.
     *
     * @param paidOn ngày trả thực tế (null → hôm nay); thành ngày trả lương của kỳ
     * @throws ConflictException kỳ chưa duyệt, hoặc ngày trả thực tế khác tháng với ngày trả đã dùng để
     *         tính thuế (→ 409 {@code CONFLICT})
     */
    @PreAuthorize("hasAuthority('payroll.mark_paid')")
    public PayrollPeriodResponse markPaid(Long periodId, @Nullable LocalDate paidOn) {
        employeeAccessScopeService.requireCompanyWide("payroll.mark_paid");
        PayrollPeriod period = payrollPeriodRepository.getRequiredForUpdate(periodId);
        if (period.getStatus() != PayrollPeriodStatus.APPROVED) {
            throw new ConflictException(ErrorCode.CONFLICT, "Period must be APPROVED before it can be marked as paid");
        }

        // Thuế đã tính theo ngày trả dự kiến (luật thuế, số người phụ thuộc, tháng nộp thuế): trả lệch sang
        // tháng khác thì số thuế có thể sai, phải tính lại trước. Cùng tháng thì chỉ cập nhật ngày.
        LocalDate actualPaymentDate = paidOn == null ? LocalDate.now(clock) : paidOn;
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

    /**
     * Khóa kỳ đã trả: trạng thái cuối, không đổi được nữa.
     *
     * @throws ConflictException kỳ chưa ở {@code PAID} (→ 409 {@code CONFLICT})
     */
    @PreAuthorize("hasAuthority('payroll.lock')")
    public PayrollPeriodResponse lock(Long periodId) {
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

    /**
     * Hủy kỳ chưa duyệt và xóa phiếu lương đã tính của nó (chưa duyệt nên chưa ai nhận). Kỳ {@code CANCELLED}
     * không còn chặn việc tạo kỳ mới cho tháng đó (uq_payroll_periods_year_month bỏ qua kỳ đã hủy).
     *
     * @throws ConflictException kỳ đã duyệt trở đi (→ 409 {@code CONFLICT})
     */
    @PreAuthorize("hasAuthority('payroll.calculate')")
    public PayrollPeriodResponse cancel(Long periodId) {
        employeeAccessScopeService.requireCompanyWide("payroll.calculate");
        PayrollPeriod period = payrollPeriodRepository.getRequiredForUpdate(periodId);
        if (period.getStatus() != PayrollPeriodStatus.DRAFT && period.getStatus() != PayrollPeriodStatus.CALCULATED) {
            throw new ConflictException(ErrorCode.CONFLICT, "Only DRAFT or CALCULATED periods can be cancelled");
        }
        payrollPeriodInvalidator.deletePayslips(periodId);
        period.setStatus(PayrollPeriodStatus.CANCELLED);
        return payrollMapper.toResponse(period);
    }
}
