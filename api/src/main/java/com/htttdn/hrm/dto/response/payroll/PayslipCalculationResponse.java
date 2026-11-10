package com.htttdn.hrm.dto.response.payroll;

import java.math.BigDecimal;
import java.util.List;

import jakarta.annotation.Nullable;

/**
 * The figures a payslip was calculated from that the payslip itself does not store, so a reader can
 * follow the calculation step by step: the month's company workdays, the insurance rates, the family
 * deductions and the tax brackets of the rules the payslip used.
 *
 * @param companyWorkdays the period's company workdays (Monday to Saturday less company holidays), the
 *        divisor of the daily salary
 * @param dependentCount dependents counted for the family deduction
 * @param taxBrackets every bracket of the tax rule with the part of the taxable income it taxed
 */
public record PayslipCalculationResponse(
    long companyWorkdays,
    BigDecimal socialRate,
    BigDecimal healthRate,
    BigDecimal unemploymentRate,
    BigDecimal personalDeduction,
    BigDecimal dependentDeduction,
    long dependentCount,
    List<TaxBracketStep> taxBrackets
) {
    /**
     * One tax bracket and what it took.
     *
     * @param upperBound null on the last, open-ended bracket
     * @param taxedIncome the part of the taxable income that falls in this bracket
     * @param tax {@code taxedIncome × rate}
     */
    public record TaxBracketStep(
        BigDecimal lowerBound,
        @Nullable BigDecimal upperBound,
        BigDecimal rate,
        BigDecimal taxedIncome,
        BigDecimal tax
    ) {
    }
}
