package com.htttdn.hrm.dto.response.auth;

public record AuthEmployeeResponse(
    Long id,
    String employeeCode,
    String fullName
) {
}
