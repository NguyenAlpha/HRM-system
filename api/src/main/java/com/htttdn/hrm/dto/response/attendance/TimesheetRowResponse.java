package com.htttdn.hrm.dto.response.attendance;

import java.util.List;

/** One employee's line of the monthly timesheet: who it is and every attendance record of the month. */
public record TimesheetRowResponse(
    Long employeeId,
    String employeeCode,
    String employeeName,
    List<AttendanceRecordResponse> records
) {
}
