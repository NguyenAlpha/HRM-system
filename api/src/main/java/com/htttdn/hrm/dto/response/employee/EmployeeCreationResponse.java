package com.htttdn.hrm.dto.response.employee;

public record EmployeeCreationResponse(
    EmployeeDetailResponse employee,
    EmployeeAssignmentResponse initialAssignment
) {
}
