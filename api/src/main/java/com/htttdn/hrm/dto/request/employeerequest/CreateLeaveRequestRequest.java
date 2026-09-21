package com.htttdn.hrm.dto.request.employeerequest;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.htttdn.hrm.entity.enums.LeaveType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateLeaveRequestRequest(
    @NotNull
    Long employeeId,

    @NotNull
    LeaveType leaveType,

    @NotNull
    Boolean isPaidLeave,

    @NotNull
    LocalDate startDate,

    @NotNull
    LocalDate endDate,

    @NotNull
    @Positive
    BigDecimal totalDays,

    @NotBlank
    String reason,

    String attachmentUrl
) {
}
