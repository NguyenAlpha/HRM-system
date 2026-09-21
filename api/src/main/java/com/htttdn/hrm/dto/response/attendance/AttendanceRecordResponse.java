package com.htttdn.hrm.dto.response.attendance;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.htttdn.hrm.entity.enums.AttendanceStatus;

public record AttendanceRecordResponse(
    Long id,
    Long employeeId,
    LocalDate workDate,
    Long shiftId,
    Instant scheduledStartAt,
    Instant scheduledEndAt,
    Instant checkInAt,
    Instant checkOutAt,
    Integer workedMinutes,
    Integer payableMinutes,
    Integer lateMinutes,
    Integer earlyLeaveMinutes,
    Integer overtimeMinutes,
    BigDecimal overtimeMultiplier,
    AttendanceStatus status
) {
}
