package com.htttdn.hrm.dto.response.payroll;

import java.math.BigDecimal;
import java.util.List;

public record PayslipResponse(
    Long id,
    Long payrollPeriodId,
    Long employeeId,
    String employeeCodeSnapshot,
    String employeeNameSnapshot,
    String workLocationSnapshot,
    String organizationUnitSnapshot,
    BigDecimal contractualBasicSalary,
    Integer scheduledWorkMinutes,
    Integer payableWorkMinutes,
    Integer approvedOvertimeMinutes,
    BigDecimal basicSalaryPay,
    BigDecimal allowancePay,
    BigDecimal overtimePay,
    BigDecimal grossPay,
    BigDecimal netPay,
    List<PayslipItemResponse> items
) {
}
