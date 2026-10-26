package com.htttdn.hrm.service.payroll;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Component;

import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.EmployeePayrollProfile;
import com.htttdn.hrm.entity.PayrollInsuranceRule;
import com.htttdn.hrm.entity.PayrollTaxRule;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.repository.EmployeePayrollProfileRepository;
import com.htttdn.hrm.repository.EmployeeTaxDependentRepository;
import com.htttdn.hrm.repository.PayrollInsuranceRuleRepository;
import com.htttdn.hrm.repository.PayrollTaxBracketRepository;
import com.htttdn.hrm.repository.PayrollTaxRuleRepository;

import lombok.RequiredArgsConstructor;

/**
 * Tính bảo hiểm bắt buộc và thuế TNCN người lao động phải nộp trong một tháng, từ hồ sơ bảo hiểm/thuế
 * ({@link PayrollDeductionsService} quản lý), số người phụ thuộc và luật bảo hiểm, thuế đang hiệu lực.
 *
 * <p>Là {@code @Component} như {@link PayslipCalculator}: chỉ được gọi khi tính phiếu lương, chạy trong
 * transaction và quyền của {@link PayrollService#calculate}.
 */
@Component
@RequiredArgsConstructor
public class InsuranceTaxCalculator {
    private final EmployeePayrollProfileRepository profileRepository;
    private final EmployeeTaxDependentRepository dependentRepository;
    private final PayrollTaxRuleRepository taxRules;
    private final PayrollTaxBracketRepository taxBrackets;
    private final PayrollInsuranceRuleRepository insuranceRules;

    /**
     * @param workMonthEnd ngày làm việc cuối của tháng: chọn hồ sơ bảo hiểm và luật bảo hiểm
     * @param paymentDate ngày trả lương: chọn luật thuế và số người phụ thuộc
     * @param taxableGrossPay tổng thu nhập chịu thuế (đã trừ phần miễn thuế)
     * @param unpaidDays số ngày nghỉ không lương; từ 14 ngày thì HR phải tự quyết việc đóng bảo hiểm
     * @throws BusinessException thiếu hồ sơ, luật hoặc dữ liệu không hợp lệ (→ 400)
     */
    public Deduction calculate(Long employeeId, LocalDate workMonthEnd, LocalDate paymentDate,
        BigDecimal taxableGrossPay, long unpaidDays) {
        EmployeePayrollProfile profile = profileRepository.findEffective(employeeId, workMonthEnd)
            .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR,
                "Employee " + employeeId + ": missing payroll profile on " + workMonthEnd));
        if (!profile.getTaxResident()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                "Employee " + employeeId + ": non-resident tax calculation is not configured");
        }
        boolean socialInsurance = profile.getSocialInsurance();
        boolean healthInsurance = profile.getHealthInsurance();
        boolean unemploymentInsurance = profile.getUnemploymentInsurance();
        if (unpaidDays >= 14 && (socialInsurance || healthInsurance || unemploymentInsurance)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                "Employee " + employeeId + ": 14 or more unpaid workdays; HR must set the month's insurance participation explicitly");
        }
        PayrollInsuranceRule insurance = insuranceRules.findEffective(workMonthEnd)
            .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR,
                "Missing insurance rules for " + workMonthEnd));
        if ((socialInsurance || healthInsurance || unemploymentInsurance)
            && profile.getInsuranceSalary().compareTo(insurance.regionMinimum(profile.getWageRegion())) < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                "Employee " + employeeId + ": insuranceSalary is below region " + profile.getWageRegion()
                    + " minimum on " + workMonthEnd);
        }
        BigDecimal regionMinimum = insurance.regionMinimum(profile.getWageRegion());
        BigDecimal socialBase = profile.getInsuranceSalary().min(insurance.getSocialHealthCap());
        BigDecimal unemploymentBase = profile.getInsuranceSalary().min(regionMinimum
            .multiply(BigDecimal.valueOf(insurance.getUnemploymentCapMultiplier())));
        BigDecimal social = socialInsurance ? money(socialBase.multiply(insurance.getSocialRate())) : BigDecimal.ZERO;
        BigDecimal health = healthInsurance ? money(socialBase.multiply(insurance.getHealthRate())) : BigDecimal.ZERO;
        BigDecimal unemployment = unemploymentInsurance
            ? money(unemploymentBase.multiply(insurance.getUnemploymentRate())) : BigDecimal.ZERO;
        PayrollTaxRule tax = taxRules.findEffective(paymentDate)
            .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR,
                "Missing resident income tax rules for payment date " + paymentDate));
        long dependentCount = dependentRepository.countEffective(employeeId, paymentDate);
        if (taxableGrossPay.signum() < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Taxable pay cannot be negative");
        }
        BigDecimal taxableIncome = taxableGrossPay.subtract(social).subtract(health).subtract(unemployment)
            .subtract(tax.getPersonalDeduction())
            .subtract(tax.getDependentDeduction().multiply(BigDecimal.valueOf(dependentCount)))
            .max(BigDecimal.ZERO);
        List<TaxBracket> brackets = taxBrackets.findByIdTaxRuleIdOrderByIdLowerBound(tax.getId()).stream()
            .map(bracket -> new TaxBracket(bracket.getId().getLowerBound(), bracket.getUpperBound(), bracket.getRate()))
            .toList();
        if (brackets.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tax rule has no brackets: " + tax.getId());
        }
        return new Deduction(profile.getId(), insurance.getId(), tax.getId(), socialBase, unemploymentBase,
            social, health, unemployment, taxableIncome, progressiveTax(taxableIncome, brackets));
    }

    static BigDecimal progressiveTax(BigDecimal taxableIncome, List<TaxBracket> brackets) {
        if (brackets.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tax rule has no brackets");
        }
        BigDecimal pit = BigDecimal.ZERO;
        BigDecimal expectedLower = BigDecimal.ZERO;
        for (TaxBracket bracket : brackets) {
            if (bracket.lowerBound().compareTo(expectedLower) != 0
                || bracket.rate().signum() < 0 || bracket.rate().compareTo(BigDecimal.ONE) > 0
                || bracket.upperBound() != null && bracket.upperBound().compareTo(bracket.lowerBound()) <= 0) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tax brackets have a gap or overlap");
            }
            BigDecimal upper = bracket.upperBound() == null ? taxableIncome : bracket.upperBound().min(taxableIncome);
            if (upper.compareTo(bracket.lowerBound()) > 0) {
                pit = pit.add(upper.subtract(bracket.lowerBound()).multiply(bracket.rate()));
            }
            if (bracket.upperBound() == null) {
                if (bracket != brackets.get(brackets.size() - 1)) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Open tax bracket must be last");
                }
            } else {
                expectedLower = bracket.upperBound();
            }
        }
        if (brackets.get(brackets.size() - 1).upperBound() != null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tax brackets must cover all income");
        }
        return money(pit);
    }

    private static BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }

    public record Deduction(Long profileId, Long insuranceRuleId, Long taxRuleId,
        BigDecimal insuranceSalaryBase, BigDecimal unemploymentInsuranceBase,
        BigDecimal social, BigDecimal health,
        BigDecimal unemployment, BigDecimal taxableIncome, BigDecimal incomeTax) {}
    static record TaxBracket(BigDecimal lowerBound, BigDecimal upperBound, BigDecimal rate) {}
}
