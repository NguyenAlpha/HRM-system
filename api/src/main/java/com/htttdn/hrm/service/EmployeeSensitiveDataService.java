package com.htttdn.hrm.service;

import com.htttdn.hrm.dto.request.employee.UpdateEmployeeSensitiveRequest;
import com.htttdn.hrm.dto.response.employee.EmployeeSensitiveResponse;

public interface EmployeeSensitiveDataService {

    EmployeeSensitiveResponse get(Long employeeId);

    EmployeeSensitiveResponse update(Long employeeId, UpdateEmployeeSensitiveRequest request);
}
