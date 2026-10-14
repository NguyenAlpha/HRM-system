package com.htttdn.hrm.dto.response.report;

import java.math.BigDecimal;
import java.util.List;

import com.htttdn.hrm.entity.enums.PayrollPeriodStatus;

import jakarta.annotation.Nullable;

/**
 * Quỹ lương của một năm, cộng từ phiếu lương của các kỳ chưa hủy: theo tháng và theo đơn vị
 * (đơn vị ghi trên phiếu lương lúc tính). Bảo hiểm là phần người lao động đóng, bị khấu trừ vào lương.
 */
public record PayrollSummaryReportResponse(
    int year,
    Amounts total,
    List<Month> months,
    List<Unit> units
) {

    public record Amounts(
        long payslipCount,
        BigDecimal grossPay,
        BigDecimal allowancePay,
        BigDecimal overtimePay,
        BigDecimal socialInsurance,
        BigDecimal healthInsurance,
        BigDecimal unemploymentInsurance,
        BigDecimal personalIncomeTax,
        BigDecimal otherDeductions,
        BigDecimal netPay
    ) {
    }

    /** Một tháng; {@code periodId} và {@code status} là null khi tháng đó chưa có kỳ lương. */
    public record Month(
        int month,
        @Nullable Long periodId,
        @Nullable PayrollPeriodStatus status,
        Amounts amounts
    ) {
    }

    public record Unit(
        String organizationUnit,
        Amounts amounts
    ) {
    }
}
