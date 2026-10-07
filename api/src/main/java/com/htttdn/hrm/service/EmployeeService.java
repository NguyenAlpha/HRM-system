package com.htttdn.hrm.service;

import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.htttdn.hrm.dto.request.employee.CreateEmployeeRequest;
import com.htttdn.hrm.dto.request.employee.SoftDeleteEmployeeRequest;
import com.htttdn.hrm.dto.request.employee.UpdateEmployeeRequest;
import com.htttdn.hrm.dto.response.employee.EmployeeCreationResponse;
import com.htttdn.hrm.dto.response.employee.EmployeeDetailResponse;
import com.htttdn.hrm.dto.response.employee.EmployeeSummaryResponse;

public interface EmployeeService {

    EmployeeCreationResponse create(CreateEmployeeRequest request);

    EmployeeDetailResponse getById(Long id);

    Page<EmployeeSummaryResponse> list(Pageable pageable);

    EmployeeDetailResponse update(Long id, UpdateEmployeeRequest request);

    EmployeeDetailResponse confirmEmployment(Long id);

    EmployeeDetailResponse completeResignation(Long employeeId, LocalDate terminationDate, String terminationReason);

    void softDelete(Long id, SoftDeleteEmployeeRequest request);
}
