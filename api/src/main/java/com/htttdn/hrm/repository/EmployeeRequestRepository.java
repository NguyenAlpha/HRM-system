package com.htttdn.hrm.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.htttdn.hrm.entity.EmployeeRequest;
import com.htttdn.hrm.entity.enums.RequestStatus;
import com.htttdn.hrm.entity.enums.RequestType;

public interface EmployeeRequestRepository extends JpaRepository<EmployeeRequest, Long> {

    Page<EmployeeRequest> findByEmployeeId(Long employeeId, Pageable pageable);

    Page<EmployeeRequest> findByStatus(RequestStatus status, Pageable pageable);

    Page<EmployeeRequest> findByRequestTypeAndStatus(RequestType requestType, RequestStatus status, Pageable pageable);
}
