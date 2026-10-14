package com.htttdn.hrm.dto.response.payroll;

import java.math.BigDecimal;
import java.util.List;

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
    BigDecimal contractualBasicSalary,
    Integer scheduledWorkMinutes,
    Integer payableWorkMinutes,
    Integer approvedOvertimeMinutes,
    BigDecimal basicSalaryPay,
    BigDecimal allowancePay,
    BigDecimal overtimePay,
    BigDecimal taxExemptOvertimePay,
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
