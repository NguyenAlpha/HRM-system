package com.htttdn.hrm.dto.response.compensation;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.htttdn.hrm.entity.enums.CompensationType;

public record EmployeeCompensationResponse(
    Long id,
    Long employeeId,
    CompensationType componentType,
    String componentCode,
    String componentName,
    BigDecimal monthlyAmount,
    LocalDate effectiveFrom,
    LocalDate effectiveTo
) {
}
