package com.htttdn.hrm.dto.request.attendance;

import com.htttdn.hrm.entity.enums.AttendanceStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record AdjustAttendanceRequest(
    @NotNull
    @PositiveOrZero
    Integer workedMinutes,

    @NotNull
    @PositiveOrZero
    Integer payableMinutes,

    @NotNull
    @PositiveOrZero
    Integer lateMinutes,

    @NotNull
    @PositiveOrZero
    Integer earlyLeaveMinutes,

    @NotNull
    AttendanceStatus status,

    @NotBlank
    String note,

    @NotNull
    Long updatedByAccountId
) {
}
