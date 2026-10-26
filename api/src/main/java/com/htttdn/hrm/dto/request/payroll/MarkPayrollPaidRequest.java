package com.htttdn.hrm.dto.request.payroll;

import java.time.LocalDate;

import jakarta.annotation.Nullable;

/** Ngày trả lương thực tế khi ghi nhận kỳ đã trả; bỏ trống → hôm nay. */
public record MarkPayrollPaidRequest(@Nullable LocalDate paidOn) {
}
