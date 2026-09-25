package com.htttdn.hrm.service;

import java.time.LocalDate;
import java.util.List;

import com.htttdn.hrm.dto.request.compensation.SetCompensationRequest;
import com.htttdn.hrm.dto.response.compensation.EmployeeCompensationResponse;

public interface EmployeeCompensationService {

    EmployeeCompensationResponse set(Long employeeId, SetCompensationRequest request);

    List<EmployeeCompensationResponse> getActive(Long employeeId, LocalDate asOfDate);
}
