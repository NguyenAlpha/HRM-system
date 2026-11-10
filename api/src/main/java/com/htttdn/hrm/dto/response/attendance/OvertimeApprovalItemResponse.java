package com.htttdn.hrm.dto.response.attendance;

import java.math.BigDecimal;

/**
 * A day of extra time waiting for the approver's overtime decision, with whose day it is, the most
 * minutes that can be paid as overtime and the multiplier they would be paid at.
 */
public record OvertimeApprovalItemResponse(
    AttendanceRecordResponse record,
    String employeeCode,
    String employeeName,
    int extraMinutes,
    BigDecimal overtimeMultiplier
) {
}
