package com.htttdn.hrm.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.htttdn.hrm.dto.request.employee.AssignEmployeeRequest;
import com.htttdn.hrm.dto.request.employee.CreateEmployeeRequest;
import com.htttdn.hrm.dto.request.employee.SetCompensationRequest;
import com.htttdn.hrm.dto.request.employee.SoftDeleteEmployeeRequest;
import com.htttdn.hrm.dto.request.employee.UpdateEmployeeProfileRequest;
import com.htttdn.hrm.dto.response.employee.EmployeeAssignmentResponse;
import com.htttdn.hrm.dto.response.employee.EmployeeCompensationResponse;
import com.htttdn.hrm.dto.response.employee.EmployeeResponse;

public interface EmployeeService {

    EmployeeResponse create(CreateEmployeeRequest request);

    EmployeeResponse getById(Long id);

    Page<EmployeeResponse> list(Pageable pageable);

    EmployeeResponse updateProfile(Long id, UpdateEmployeeProfileRequest request);

    EmployeeAssignmentResponse assignDepartment(Long employeeId, AssignEmployeeRequest request);

    EmployeeAssignmentResponse getCurrentAssignment(Long employeeId);

    EmployeeCompensationResponse setCompensation(Long employeeId, SetCompensationRequest request);

    List<EmployeeCompensationResponse> getActiveCompensations(Long employeeId, LocalDate asOfDate);

    void completeResignation(Long employeeId, LocalDate terminationDate, String terminationReason);

    void softDelete(Long id, SoftDeleteEmployeeRequest request);
}
