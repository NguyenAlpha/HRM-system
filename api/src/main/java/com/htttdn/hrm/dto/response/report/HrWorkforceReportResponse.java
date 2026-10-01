package com.htttdn.hrm.dto.response.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record HrWorkforceReportResponse(
    LocalDate asOfDate,
    long totalEmployees,
    BigDecimal averageSeniorityYears,
    List<DistributionItemResponse> educationDistribution,
    List<DistributionItemResponse> seniorityDistribution,
    long missingEducation
) {
}
