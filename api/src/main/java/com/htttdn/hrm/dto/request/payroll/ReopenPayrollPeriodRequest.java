package com.htttdn.hrm.dto.request.payroll;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Lý do hủy duyệt kỳ lương, ghi lại trên kỳ. */
public record ReopenPayrollPeriodRequest(@NotBlank @Size(max = 500) String reason) {
}
