package com.htttdn.hrm.dto.response.report;

import java.math.BigDecimal;

public record DistributionItemResponse(
    String key,
    String label,
    long count,
    BigDecimal percentage
) {
}
