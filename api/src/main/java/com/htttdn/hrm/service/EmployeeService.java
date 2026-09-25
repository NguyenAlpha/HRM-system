package com.htttdn.hrm.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.htttdn.hrm.dto.request.employee.AssignEmployeeRequest;
import com.htttdn.hrm.dto.request.employee.CreateEmployeeRequest;
import com.htttdn.hrm.dto.request.employee.SoftDeleteEmployeeRequest;
import com.htttdn.hrm.dto.request.employee.UpdateEmployeeRequest;
import com.htttdn.hrm.dto.response.employee.EmployeeAssignmentResponse;
import com.htttdn.hrm.dto.response.employee.EmployeeDetailResponse;
import com.htttdn.hrm.dto.response.employee.EmployeeSummaryResponse;

public interface EmployeeService {

    EmployeeDetailResponse create(CreateEmployeeRequest request);

    EmployeeDetailResponse getById(Long id);

    Page<EmployeeSummaryResponse> list(Pageable pageable);

    EmployeeDetailResponse update(Long id, UpdateEmployeeRequest request);

    EmployeeAssignmentResponse assign(Long employeeId, AssignEmployeeRequest request);

    EmployeeAssignmentResponse getCurrentAssignment(Long employeeId);

    List<EmployeeAssignmentResponse> listAssignments(Long employeeId);

    void completeResignation(Long employeeId, LocalDate terminationDate, String terminationReason);

    void softDelete(Long id, SoftDeleteEmployeeRequest request);
}
