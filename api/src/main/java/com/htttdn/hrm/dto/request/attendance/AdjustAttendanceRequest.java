package com.htttdn.hrm.dto.request.attendance;

import com.htttdn.hrm.entity.enums.AttendanceStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

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
    @Size(max = 100)
    String note
) {
}
