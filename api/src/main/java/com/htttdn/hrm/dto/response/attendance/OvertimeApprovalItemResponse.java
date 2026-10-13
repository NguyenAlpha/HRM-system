package com.htttdn.hrm.dto.response.attendance;

/** A day of extra time waiting for the approver's overtime decision, with whose day it is. */
public record OvertimeApprovalItemResponse(
    AttendanceRecordResponse record,
    String employeeCode,
    String employeeName
) {
}
