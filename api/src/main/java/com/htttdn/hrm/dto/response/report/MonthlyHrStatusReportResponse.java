package com.htttdn.hrm.dto.response.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.htttdn.hrm.entity.enums.EmploymentStatus;

import jakarta.annotation.Nullable;

/**
 * Tình hình nhân sự một tháng: quân số đầu/cuối tháng, số vào làm, nghỉ việc, nghỉ phép, vắng, và
 * từng người đã làm việc ít nhất một ngày trong tháng với trạng thái của họ.
 */
public record MonthlyHrStatusReportResponse(
    int year,
    int month,
    long headcountStart,
    long hires,
    long terminations,
    long headcountEnd,
    long employeesOnLeave,
    BigDecimal leaveDays,
    long absentDays,
    List<Row> rows
) {

    /**
     * Một người. {@code status} là {@code NEW_HIRE} (vào làm trong tháng), {@code TERMINATED} (nghỉ việc
     * trong tháng, kể cả vào rồi nghỉ ngay trong tháng) hoặc {@code ACTIVE}. Ngày nghỉ tính theo đơn đã
     * duyệt (nửa ngày là 0,5), vắng là ngày vắng không phép.
     */
    public record Row(
        Long employeeId,
        String employeeCode,
        String fullName,
        @Nullable String organizationUnitName,
        @Nullable String positionTitle,
        LocalDate hireDate,
        @Nullable LocalDate terminationDate,
        String status,
        @Nullable EmploymentStatus endStatus,
        BigDecimal annualLeaveDays,
        BigDecimal sickLeaveDays,
        BigDecimal maternityLeaveDays,
        BigDecimal unpaidLeaveDays,
        BigDecimal otherLeaveDays,
        BigDecimal leaveDays,
        long absentDays
    ) {
    }
}
