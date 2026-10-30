package com.htttdn.hrm.service.payroll;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Component;

import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.PayrollPeriod;
import com.htttdn.hrm.entity.Payslip;
import com.htttdn.hrm.entity.enums.PayrollPeriodStatus;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.repository.PayrollPeriodRepository;
import com.htttdn.hrm.repository.PayslipItemRepository;
import com.htttdn.hrm.repository.PayslipRepository;

import lombok.RequiredArgsConstructor;

/**
 * Khi một dữ liệu đầu vào của lương (phân công, lương, phụ cấp, hồ sơ thuế, ngày lễ, khoản điều chỉnh…) đổi, các kỳ
 * lương bị ảnh hưởng phải được xử lý trước: kỳ đã duyệt/trả/khóa → từ chối (409
 * {@code PAYROLL_PERIOD_LOCKED}); kỳ đã tính → xóa phiếu lương và đưa về {@code DRAFT} để tính lại.
 *
 * <p>Là {@code @Component} không có {@code @Transactional}: chạy trong transaction của service gọi nó,
 * nên nếu thay đổi sau đó lỗi thì việc xóa phiếu lương cũng được rollback.
 */
@Component
@RequiredArgsConstructor
public class PayrollPeriodInvalidator {

    private static final List<PayrollPeriodStatus> CALCULATED_OR_RELEASED = List.of(
        PayrollPeriodStatus.CALCULATED, PayrollPeriodStatus.APPROVED,
        PayrollPeriodStatus.PAID, PayrollPeriodStatus.LOCKED);

    private final PayrollPeriodRepository payrollPeriodRepository;
    private final PayslipRepository payslipRepository;
    private final PayslipItemRepository payslipItemRepository;

    /** Các kỳ kết thúc từ {@code effectiveFrom} trở đi (khóa ghi). */
    public void invalidateFrom(LocalDate effectiveFrom) {
        invalidate(payrollPeriodRepository.findAffectedPeriodsForUpdate(effectiveFrom, CALCULATED_OR_RELEASED));
    }

    /** Các kỳ kết thúc từ {@code effectiveFrom} trở đi có phiếu lương của nhân viên này (khóa ghi). */
    public void invalidateEmployeeFrom(Long employeeId, LocalDate effectiveFrom) {
        invalidate(payrollPeriodRepository.findAffectedPeriodsOfEmployeeForUpdate(
            employeeId, effectiveFrom, CALCULATED_OR_RELEASED));
    }

    /** Các kỳ có ngày nộp thuế trong [{@code from}, {@code to}]; {@code to} null → không giới hạn. */
    public void invalidateTaxPaymentsBetween(LocalDate from, LocalDate to) {
        invalidate(payrollPeriodRepository.findAffectedPaymentPeriodsForUpdate(from, to, CALCULATED_OR_RELEASED));
    }

    /** Một kỳ cụ thể mà bên gọi đã tải (nên khóa ghi); kỳ chưa tính hoặc đã hủy → không làm gì. */
    public void invalidatePeriod(PayrollPeriod period) {
        if (CALCULATED_OR_RELEASED.contains(period.getStatus())) {
            invalidate(List.of(period));
        }
    }

    /** Xóa mọi phiếu lương của kỳ cùng các dòng của chúng: khi kỳ bị vô hiệu, trước khi tính lại, hoặc khi hủy kỳ. */
    public void deletePayslips(Long periodId) {
        for (Payslip payslip : payslipRepository.findByPayrollPeriodId(periodId)) {
            payslipItemRepository.deleteByPayslipId(payslip.getId());
            payslipRepository.delete(payslip);
        }
    }

    private void invalidate(List<PayrollPeriod> affected) {
        for (PayrollPeriod period : affected) {
            if (period.getStatus() != PayrollPeriodStatus.CALCULATED) {
                throw new ConflictException(ErrorCode.PAYROLL_PERIOD_LOCKED,
                    "Cannot change payroll data: period " + period.getYear() + "-" + period.getMonth()
                        + " is " + period.getStatus());
            }
            deletePayslips(period.getId());
            period.setStatus(PayrollPeriodStatus.DRAFT);
            period.setCalculatedByAccount(null);
            period.setCalculatedAt(null);
        }
    }
}
