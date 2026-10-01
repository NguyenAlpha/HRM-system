package com.htttdn.hrm.dto.response.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PayrollSalaryReportResponse(
    LocalDate asOfDate,
    String currency,
    long totalEmployees,
    long employeesWithBasicSalary,
    BigDecimal averageBasicSalary,
    BigDecimal minimumBasicSalary,
    BigDecimal maximumBasicSalary,
    List<DistributionItemResponse> salaryDistribution,
    long missingBasicSalary
) {
}
