package com.htttdn.hrm.dto.response.employeerequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.htttdn.hrm.entity.enums.LeaveType;
import com.htttdn.hrm.entity.enums.RequestStatus;
import com.htttdn.hrm.entity.enums.RequestType;

public record EmployeeRequestResponse(
    Long id,
    Long employeeId,
    RequestType requestType,
    LeaveType leaveType,
    Boolean isPaidLeave,
    LocalDate startDate,
    LocalDate endDate,
    BigDecimal totalDays,
    LocalDate requestedLastWorkingDate,
    String reason,
    String attachmentUrl,
    RequestStatus status,
    Instant submittedAt,
    Long reviewedByAccountId,
    String reviewComment,
    Instant reviewedAt
) {
}
