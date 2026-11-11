package com.htttdn.hrm.dto.request.payroll;

import java.time.LocalDate;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Ghi nhận kỳ đã chuyển lương: ngày trả thực tế (bỏ trống → hôm nay) và mã giao dịch / số ủy nhiệm chi
 * mà ngân hàng trả về khi chi lương theo bảng chi lương.
 */
public record MarkPayrollPaidRequest(
    @Nullable LocalDate paidOn,
    @NotBlank
    @Size(max = 100)
    String paymentReference
) {
}
