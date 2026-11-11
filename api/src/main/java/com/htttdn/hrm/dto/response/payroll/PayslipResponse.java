package com.htttdn.hrm.dto.response.payroll;

import java.math.BigDecimal;
import java.util.List;

import jakarta.annotation.Nullable;

public record PayslipResponse(
    Long id,
    Long payrollPeriodId,
    Short periodYear,
    Short periodMonth,
    Long employeeId,
    String employeeCodeSnapshot,
    String employeeNameSnapshot,
    String positionSnapshot,
    String workLocationSnapshot,
    String organizationUnitSnapshot,
    /** Bank the net pay goes to, null when the employee had no account when the period was calculated. */
    @Nullable String bankName,
    /** The account number with all but its last four digits hidden, e.g. "••••5213". */
    @Nullable String bankAccountMasked,
    BigDecimal contractualBasicSalary,
    Integer scheduledWorkMinutes,
    Integer payableWorkMinutes,
    Integer approvedOvertimeMinutes,
    BigDecimal basicSalaryPay,
    BigDecimal allowancePay,
    BigDecimal overtimePay,
    BigDecimal otherEarningsPay,
    BigDecimal taxExemptOtherEarnings,
    BigDecimal grossPay,
    BigDecimal insuranceSalaryBase,
    BigDecimal unemploymentInsuranceBase,
    BigDecimal employeeSocialInsurance,
    BigDecimal employeeHealthInsurance,
    BigDecimal employeeUnemploymentInsurance,
    BigDecimal taxableIncome,
    BigDecimal personalIncomeTax,
    BigDecimal otherDeductions,
    BigDecimal netPay,
    List<PayslipItemResponse> items
) {
}
