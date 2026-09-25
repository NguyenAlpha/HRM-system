package com.htttdn.hrm.dto.response.employee;

public record EmployeeSensitiveResponse(
    Long employeeId,
    String nationalId,
    String personalEmail,
    String address,
    String taxCode,
    String bankName,
    String bankAccountNumber,
    String bankAccountHolder
) {
}
