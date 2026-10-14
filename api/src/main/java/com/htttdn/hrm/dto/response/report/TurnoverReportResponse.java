package com.htttdn.hrm.dto.response.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.htttdn.hrm.entity.enums.EmploymentStatus;

import jakarta.annotation.Nullable;

/**
 * Biến động nhân sự trong một năm: quân số đầu/cuối kỳ, số vào làm, số nghỉ việc và tỷ lệ nghỉ việc,
 * theo từng tháng (đến tháng hiện tại nếu là năm nay) và cả năm.
 */
public record TurnoverReportResponse(
    int year,
    long headcountStart,
    long headcountEnd,
    long hires,
    long terminations,
    BigDecimal turnoverRate,
    List<Month> months,
    List<Event> events
) {

    /** Một tháng: quân số ngày cuối tháng trước, vào, nghỉ, quân số ngày cuối tháng. */
    public record Month(
        int month,
        long headcountStart,
        long hires,
        long terminations,
        long headcountEnd,
        BigDecimal turnoverRate
    ) {
    }

    /** Một lần vào làm ({@code HIRE}) hoặc nghỉ việc ({@code TERMINATION}) trong năm. */
    public record Event(
        Long employeeId,
        String employeeCode,
        String fullName,
        String type,
        LocalDate date,
        @Nullable EmploymentStatus endStatus,
        @Nullable String reason
    ) {
    }
}
