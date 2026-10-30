package com.htttdn.hrm.service.payroll;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
 * <p>Tháng người lao động không làm việc từ {@value #MIN_DAYS_NOT_WORKED_WITHOUT_INSURANCE} ngày làm việc
 * trở lên (nghỉ không lương, vắng, hoặc chưa vào làm/đã nghỉ việc) thì không đóng BHXH, BHYT, BHTN tháng
 * đó: các khoản bảo hiểm bằng 0 như người không tham gia, thuế vẫn tính bình thường.
 *
 * <p>Là {@code @Component} như {@link PayslipCalculator}: chỉ được gọi khi tính phiếu lương, chạy trong
 * transaction và quyền của {@link PayrollService#calculate}.
 *
 * <p>Lỗi ở đây là câu tiếng Việt viết thường, không có tên nhân viên: {@link PayslipCalculator} nối nó vào sau
 * mã và tên nhân viên rồi trả cho HR.
 */
@Component
@RequiredArgsConstructor
public class InsuranceTaxCalculator {
    static final int MIN_DAYS_NOT_WORKED_WITHOUT_INSURANCE = 14;

    private final EmployeePayrollProfileRepository profileRepository;
    private final EmployeeTaxDependentRepository dependentRepository;
    private final PayrollTaxRuleRepository taxRules;
    private final PayrollTaxBracketRepository taxBrackets;
    private final PayrollInsuranceRuleRepository insuranceRules;

    /**
     * Bảo hiểm (BHXH, BHYT, BHTN) và thuế TNCN người lao động nộp trong tháng.
     *
     * <p>Thứ tự: chọn hồ sơ bảo hiểm/thuế → tính bảo hiểm theo lương đóng bảo hiểm (có trần) → thu nhập
     * tính thuế = thu nhập chịu thuế − bảo hiểm − giảm trừ bản thân − giảm trừ người phụ thuộc (không âm) →
     * thuế lũy tiến ({@link #progressiveTax}).
     *
     * @param workMonthEnd ngày cuối nhân viên còn làm trong tháng: chọn hồ sơ bảo hiểm và luật bảo hiểm
     * @param paymentDate ngày trả lương: chọn luật thuế
     * @param dependentsOn ngày đếm người phụ thuộc: ngày trả lương, hoặc ngày làm cuối nếu nhân viên nghỉ trước đó
     * @param taxableGrossPay tổng thu nhập chịu thuế (đã trừ phần miễn thuế)
     * @param daysNotWorked số ngày làm việc của tháng mà người lao động không làm việc; từ 14 ngày thì
     *        tháng đó không trừ bảo hiểm
     * @return các khoản bảo hiểm, căn cứ đóng (0 khi tháng không đóng), thu nhập tính thuế, thuế, và id
     *         hồ sơ/luật đã dùng (lưu lên phiếu lương để đối chiếu sau)
     * @throws BusinessException thiếu hồ sơ, luật hoặc dữ liệu không hợp lệ (→ 400)
     */
    public Deduction calculate(Long employeeId, LocalDate workMonthEnd, LocalDate paymentDate,
        LocalDate dependentsOn, BigDecimal taxableGrossPay, long daysNotWorked) {
        EmployeePayrollProfile profile = profileRepository.findEffective(employeeId, workMonthEnd)
            .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR,
                "thiếu hồ sơ bảo hiểm/thuế hiệu lực ngày " + display(workMonthEnd)));
        if (!profile.getTaxResident()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                "chưa hỗ trợ tính thuế cho người không cư trú");
        }
        // Tháng không đóng bảo hiểm: coi như hồ sơ tắt cả ba loại, nên cũng bỏ qua kiểm tra lương tối thiểu vùng.
        boolean contributes = daysNotWorked < MIN_DAYS_NOT_WORKED_WITHOUT_INSURANCE;
        boolean socialInsurance = contributes && profile.getSocialInsurance();
        boolean healthInsurance = contributes && profile.getHealthInsurance();
        boolean unemploymentInsurance = contributes && profile.getUnemploymentInsurance();
        PayrollInsuranceRule insurance = insuranceRules.findEffective(workMonthEnd)
            .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR,
                "thiếu quy định bảo hiểm hiệu lực ngày " + display(workMonthEnd)));
        if ((socialInsurance || healthInsurance || unemploymentInsurance)
            && profile.getInsuranceSalary().compareTo(insurance.regionMinimum(profile.getWageRegion())) < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                "lương đóng bảo hiểm thấp hơn lương tối thiểu vùng " + profile.getWageRegion()
                    + " ngày " + display(workMonthEnd));
        }
        // Hai mức trần khác nhau: BHXH/BHYT theo socialHealthCap, BHTN theo unemploymentCapMultiplier × lương
        // tối thiểu vùng. Tháng không đóng thì căn cứ ghi lên phiếu là 0, khớp với các khoản bảo hiểm bằng 0.
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
                "thiếu biểu thuế TNCN cho ngày trả lương " + display(paymentDate)));
        // Người phụ thuộc đếm theo ngày trả lương, cùng mốc với luật thuế (xem dependentsOn).
        long dependentCount = dependentRepository.countEffective(employeeId, dependentsOn);
        if (taxableGrossPay.signum() < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "thu nhập chịu thuế bị âm");
        }
        BigDecimal taxableIncome = taxableGrossPay.subtract(social).subtract(health).subtract(unemployment)
            .subtract(tax.getPersonalDeduction())
            .subtract(tax.getDependentDeduction().multiply(BigDecimal.valueOf(dependentCount)))
            .max(BigDecimal.ZERO);
        List<TaxBracket> brackets = taxBrackets.findByIdTaxRuleIdOrderByIdLowerBound(tax.getId()).stream()
            .map(bracket -> new TaxBracket(bracket.getId().getLowerBound(), bracket.getUpperBound(), bracket.getRate()))
            .toList();
        return new Deduction(profile.getId(), insurance.getId(), tax.getId(),
            contributes ? socialBase : BigDecimal.ZERO, contributes ? unemploymentBase : BigDecimal.ZERO,
            social, health, unemployment, taxableIncome, progressiveTax(taxableIncome, brackets));
    }

    /**
     * Thuế lũy tiến từng phần: mỗi bậc đánh thuế phần thu nhập vượt cận dưới, tối đa tới cận trên, theo
     * thuế suất của bậc, rồi cộng lại, làm tròn 2 chữ số.
     *
     * <p>Kiểm tra luôn bảng bậc lấy từ DB: bậc đầu từ 0, các bậc nối liền không hở/chồng, thuế suất trong
     * [0, 1], chỉ bậc cuối không có cận trên. Package-private để unit test gọi thẳng.
     *
     * @param brackets các bậc theo cận dưới tăng dần
     * @throws BusinessException bảng bậc rỗng hoặc sai cấu trúc (→ 400 {@code VALIDATION_ERROR})
     */
    static BigDecimal progressiveTax(BigDecimal taxableIncome, List<TaxBracket> brackets) {
        if (brackets.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "biểu thuế TNCN chưa có bậc thuế");
        }
        BigDecimal pit = BigDecimal.ZERO;
        BigDecimal expectedLower = BigDecimal.ZERO;
        for (TaxBracket bracket : brackets) {
            if (bracket.lowerBound().compareTo(expectedLower) != 0
                || bracket.rate().signum() < 0 || bracket.rate().compareTo(BigDecimal.ONE) > 0
                || bracket.upperBound() != null && bracket.upperBound().compareTo(bracket.lowerBound()) <= 0) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "biểu thuế TNCN có bậc bị hở hoặc chồng nhau");
            }
            BigDecimal upper = bracket.upperBound() == null ? taxableIncome : bracket.upperBound().min(taxableIncome);
            if (upper.compareTo(bracket.lowerBound()) > 0) {
                pit = pit.add(upper.subtract(bracket.lowerBound()).multiply(bracket.rate()));
            }
            if (bracket.upperBound() == null) {
                if (bracket != brackets.get(brackets.size() - 1)) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR, "biểu thuế TNCN có bậc không giới hạn trên không nằm cuối");
                }
            } else {
                expectedLower = bracket.upperBound();
            }
        }
        if (brackets.get(brackets.size() - 1).upperBound() != null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "biểu thuế TNCN chưa phủ hết mọi mức thu nhập");
        }
        return money(pit);
    }

    private static BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }

    private static String display(LocalDate date) { return date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")); }

    public record Deduction(Long profileId, Long insuranceRuleId, Long taxRuleId,
        BigDecimal insuranceSalaryBase, BigDecimal unemploymentInsuranceBase,
        BigDecimal social, BigDecimal health,
        BigDecimal unemployment, BigDecimal taxableIncome, BigDecimal incomeTax) {}
    static record TaxBracket(BigDecimal lowerBound, BigDecimal upperBound, BigDecimal rate) {}
}
