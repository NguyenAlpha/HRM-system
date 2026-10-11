package com.htttdn.hrm.dto.response.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.annotation.Nullable;

public record PayrollSalaryReportResponse(
    LocalDate asOfDate,
    String currency,
    long totalEmployees,
    long employeesWithBasicSalary,
    @Nullable BigDecimal averageBasicSalary,
    @Nullable BigDecimal minimumBasicSalary,
    @Nullable BigDecimal maximumBasicSalary,
    List<DistributionItemResponse> salaryDistribution,
    long missingBasicSalary
) {
}
