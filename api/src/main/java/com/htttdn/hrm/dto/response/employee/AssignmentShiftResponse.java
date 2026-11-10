package com.htttdn.hrm.dto.response.employee;

import java.time.LocalTime;

import jakarta.annotation.Nullable;

/**
 * The working hours of an assignment's shift, carried on the assignment so whoever sees the assignment
 * (the employee on their own profile included) sees what the shift means without {@code organization.read}.
 * These are the shift's current settings, not those in force when a past assignment ran.
 */
public record AssignmentShiftResponse(
    LocalTime startTime,
    LocalTime endTime,
    Integer breakMinutes,
    @Nullable LocalTime breakStartTime,
    Integer standardWorkMinutes,
    Integer graceLateMinutes,
    Boolean crossesMidnight
) {
}
