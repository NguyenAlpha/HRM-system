package com.htttdn.hrm.service.payroll;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.response.payroll.PayslipCalculationResponse;
import com.htttdn.hrm.dto.response.payroll.PayslipCalculationResponse.TaxBracketStep;
import com.htttdn.hrm.entity.PayrollInsuranceRule;
import com.htttdn.hrm.entity.PayrollPeriod;
import com.htttdn.hrm.entity.PayrollTaxRule;
import com.htttdn.hrm.entity.Payslip;
import com.htttdn.hrm.repository.EmployeeTaxDependentRepository;
import com.htttdn.hrm.repository.PayrollInsuranceRuleRepository;
import com.htttdn.hrm.repository.PayrollTaxBracketRepository;
import com.htttdn.hrm.repository.PayrollTaxRuleRepository;
import com.htttdn.hrm.repository.PayslipRepository;
import com.htttdn.hrm.service.attendance.AttendanceCalendarService;

import lombok.RequiredArgsConstructor;

/**
 * Các số liệu dùng để giải thích cách tính một phiếu lương mà phiếu không lưu: số ngày công chuẩn của
 * tháng, tỉ lệ bảo hiểm, giảm trừ gia cảnh và biểu thuế, theo đúng bộ quy tắc phiếu đã dùng
 * ({@code taxRuleId}, {@code insuranceRuleId}).
 *
 * <p>Số người phụ thuộc được đếm lại theo cùng mốc ngày {@link PayslipCalculator} dùng (ngày trả lương, hoặc
 * ngày làm cuối nếu nhân viên nghỉ trước đó). Người phụ thuộc của kỳ đã duyệt không đổi được
 * ({@code PayrollDeductionsService}), nên số đếm lại khớp với lúc tính.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PayslipCalculationService {

    private final PayrollQueryService payrollQueryService;
    private final PayslipRepository payslipRepository;
    private final AttendanceCalendarService attendanceCalendarService;
    private final PayrollTaxRuleRepository taxRuleRepository;
    private final PayrollTaxBracketRepository taxBracketRepository;
    private final PayrollInsuranceRuleRepository insuranceRuleRepository;
    private final EmployeeTaxDependentRepository dependentRepository;

    /**
     * Số liệu giải thích cách tính phiếu lương; cùng quyền với xem phiếu lương.
     *
     * @throws com.htttdn.hrm.exception.ResourceNotFoundException không có phiếu lương (→ 404)
     * @throws com.htttdn.hrm.exception.ForbiddenException không được xem phiếu này, hoặc phiếu của chính
     *         mình nhưng kỳ chưa duyệt (→ 403)
     */
    @PreAuthorize("hasAnyAuthority('payroll.self.read', 'report.payroll.read')")
    public PayslipCalculationResponse getCalculation(Long payslipId) {
        // Kiểm tra quyền và trạng thái kỳ giống hệt khi xem phiếu lương.
        payrollQueryService.getPayslip(payslipId);
        Payslip payslip = payslipRepository.getRequired(payslipId);
        PayrollPeriod period = payslip.getPayrollPeriod();

        PayrollInsuranceRule insurance = insuranceRuleRepository.findById(payslip.getInsuranceRuleId()).orElseThrow();
        PayrollTaxRule tax = taxRuleRepository.findById(payslip.getTaxRuleId()).orElseThrow();
        LocalDate lastWorkingDay = payslip.getEmployee().getTerminationDate();
        LocalDate dependentsOn = lastWorkingDay != null && lastWorkingDay.isBefore(period.getTaxPaymentDate())
            ? lastWorkingDay : period.getTaxPaymentDate();

        BigDecimal taxableIncome = payslip.getTaxableIncome();
        List<TaxBracketStep> brackets = taxBracketRepository.findByIdTaxRuleIdOrderByIdLowerBound(tax.getId()).stream()
            .map(bracket -> {
                BigDecimal lower = bracket.getId().getLowerBound();
                BigDecimal upper = bracket.getUpperBound();
                BigDecimal top = upper == null ? taxableIncome : taxableIncome.min(upper);
                BigDecimal taxed = top.subtract(lower).max(BigDecimal.ZERO);
                return new TaxBracketStep(lower, upper, bracket.getRate(), taxed,
                    taxed.multiply(bracket.getRate()).setScale(2, RoundingMode.HALF_UP));
            })
            .toList();

        return new PayslipCalculationResponse(
            attendanceCalendarService.companyWorkdayCount(period.getPeriodStart(), period.getPeriodEnd()),
            insurance.getSocialRate(),
            insurance.getHealthRate(),
            insurance.getUnemploymentRate(),
            tax.getPersonalDeduction(),
            tax.getDependentDeduction(),
            dependentRepository.countEffective(payslip.getEmployee().getId(), dependentsOn),
            brackets);
    }
}
