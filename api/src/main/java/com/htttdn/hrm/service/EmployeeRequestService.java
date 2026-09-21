package com.htttdn.hrm.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.htttdn.hrm.dto.request.employeerequest.CreateLeaveRequestRequest;
import com.htttdn.hrm.dto.request.employeerequest.CreateResignationRequestRequest;
import com.htttdn.hrm.dto.request.employeerequest.ReviewRequestRequest;
import com.htttdn.hrm.dto.response.employeerequest.EmployeeRequestResponse;

public interface EmployeeRequestService {

    EmployeeRequestResponse createLeaveRequest(CreateLeaveRequestRequest request);

    EmployeeRequestResponse createResignationRequest(CreateResignationRequestRequest request);

    EmployeeRequestResponse submit(Long id);

    EmployeeRequestResponse cancel(Long id);

    EmployeeRequestResponse approve(Long id, ReviewRequestRequest request);

    EmployeeRequestResponse reject(Long id, ReviewRequestRequest request);

    EmployeeRequestResponse getById(Long id);

    Page<EmployeeRequestResponse> listByEmployee(Long employeeId, Pageable pageable);
}
