package com.htttdn.hrm.service.impl;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.request.employeerequest.CreateLeaveRequestRequest;
import com.htttdn.hrm.dto.request.employeerequest.CreateResignationRequestRequest;
import com.htttdn.hrm.dto.request.employeerequest.ReviewRequestRequest;
import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.dto.response.employeerequest.EmployeeRequestResponse;
import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.EmployeeRequest;
import com.htttdn.hrm.entity.enums.RequestStatus;
import com.htttdn.hrm.entity.enums.RequestType;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.exception.ResourceNotFoundException;
import com.htttdn.hrm.repository.AccountRepository;
import com.htttdn.hrm.repository.EmployeeRepository;
import com.htttdn.hrm.repository.EmployeeRequestRepository;
import com.htttdn.hrm.service.EmployeeRequestService;
import com.htttdn.hrm.service.EmployeeService;

@Service
@Transactional
public class EmployeeRequestServiceImpl implements EmployeeRequestService {

    private final EmployeeRequestRepository employeeRequestRepository;
    private final EmployeeRepository employeeRepository;
    private final AccountRepository accountRepository;
    private final EmployeeService employeeService;

    public EmployeeRequestServiceImpl(
        EmployeeRequestRepository employeeRequestRepository,
        EmployeeRepository employeeRepository,
        AccountRepository accountRepository,
        EmployeeService employeeService
    ) {
        this.employeeRequestRepository = employeeRequestRepository;
        this.employeeRepository = employeeRepository;
        this.accountRepository = accountRepository;
        this.employeeService = employeeService;
    }

    @Override
    public EmployeeRequestResponse createLeaveRequest(CreateLeaveRequestRequest request) {
        if (request.endDate().isBefore(request.startDate())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "endDate must not be before startDate", "endDate");
        }
        Employee employee = findEmployeeOrThrow(request.employeeId());

        Instant now = Instant.now();
        EmployeeRequest employeeRequest = EmployeeRequest.builder()
            .employee(employee)
            .requestType(RequestType.LEAVE)
            .leaveType(request.leaveType())
            .isPaidLeave(request.isPaidLeave())
            .startDate(request.startDate())
            .endDate(request.endDate())
            .totalDays(request.totalDays())
            .reason(request.reason())
            .attachmentUrl(request.attachmentUrl())
            .status(RequestStatus.DRAFT)
            .createdAt(now)
            .updatedAt(now)
            .build();

        return toResponse(employeeRequestRepository.save(employeeRequest));
    }

    @Override
    public EmployeeRequestResponse createResignationRequest(CreateResignationRequestRequest request) {
        Employee employee = findEmployeeOrThrow(request.employeeId());

        Instant now = Instant.now();
        EmployeeRequest employeeRequest = EmployeeRequest.builder()
            .employee(employee)
            .requestType(RequestType.RESIGNATION)
            .requestedLastWorkingDate(request.requestedLastWorkingDate())
            .reason(request.reason())
            .status(RequestStatus.DRAFT)
            .createdAt(now)
            .updatedAt(now)
            .build();

        return toResponse(employeeRequestRepository.save(employeeRequest));
    }

    @Override
    public EmployeeRequestResponse submit(Long id) {
        EmployeeRequest request = findRequestOrThrow(id);
        requireStatus(request, RequestStatus.DRAFT);
        request.setStatus(RequestStatus.PENDING);
        request.setSubmittedAt(Instant.now());
        request.setUpdatedAt(Instant.now());
        return toResponse(request);
    }

    @Override
    public EmployeeRequestResponse cancel(Long id) {
        EmployeeRequest request = findRequestOrThrow(id);
        if (request.getStatus() != RequestStatus.DRAFT && request.getStatus() != RequestStatus.PENDING) {
            throw new ConflictException(ErrorCode.REQUEST_ALREADY_PROCESSED, "Only DRAFT or PENDING requests can be cancelled");
        }
        request.setStatus(RequestStatus.CANCELLED);
        request.setUpdatedAt(Instant.now());
        return toResponse(request);
    }

    @Override
    public EmployeeRequestResponse approve(Long id, ReviewRequestRequest reviewRequest) {
        EmployeeRequest request = findRequestOrThrow(id);
        requireStatus(request, RequestStatus.PENDING);

        Account reviewer = accountRepository.findById(reviewRequest.reviewerAccountId())
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.RESOURCE_NOT_FOUND, "Account not found: " + reviewRequest.reviewerAccountId()));

        Instant now = Instant.now();
        request.setReviewedByAccount(reviewer);
        request.setReviewComment(reviewRequest.comment());
        request.setReviewedAt(now);
        request.setUpdatedAt(now);

        if (request.getRequestType() == RequestType.RESIGNATION) {
            employeeService.completeResignation(
                request.getEmployee().getId(),
                request.getRequestedLastWorkingDate(),
                request.getReason()
            );
            request.setStatus(RequestStatus.COMPLETED);
        } else {
            request.setStatus(RequestStatus.APPROVED);
        }

        return toResponse(request);
    }

    @Override
    public EmployeeRequestResponse reject(Long id, ReviewRequestRequest reviewRequest) {
        EmployeeRequest request = findRequestOrThrow(id);
        requireStatus(request, RequestStatus.PENDING);

        Account reviewer = accountRepository.findById(reviewRequest.reviewerAccountId())
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.RESOURCE_NOT_FOUND, "Account not found: " + reviewRequest.reviewerAccountId()));

        Instant now = Instant.now();
        request.setStatus(RequestStatus.REJECTED);
        request.setReviewedByAccount(reviewer);
        request.setReviewComment(reviewRequest.comment());
        request.setReviewedAt(now);
        request.setUpdatedAt(now);

        return toResponse(request);
    }

    @Override
    @Transactional(readOnly = true)
    public EmployeeRequestResponse getById(Long id) {
        return toResponse(findRequestOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<EmployeeRequestResponse> listByEmployee(Long employeeId, Pageable pageable) {
        return employeeRequestRepository.findByEmployeeId(employeeId, pageable).map(this::toResponse);
    }

    private void requireStatus(EmployeeRequest request, RequestStatus expected) {
        if (request.getStatus() != expected) {
            throw new ConflictException(
                ErrorCode.REQUEST_ALREADY_PROCESSED,
                "Request must be in status " + expected + " but was " + request.getStatus()
            );
        }
    }

    private Employee findEmployeeOrThrow(Long id) {
        return employeeRepository.findById(id)
            .filter(employee -> employee.getDeletedAt() == null)
            .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.EMPLOYEE_NOT_FOUND, "Employee not found: " + id));
    }

    private EmployeeRequest findRequestOrThrow(Long id) {
        return employeeRequestRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.REQUEST_NOT_FOUND, "Request not found: " + id));
    }

    private EmployeeRequestResponse toResponse(EmployeeRequest request) {
        return new EmployeeRequestResponse(
            request.getId(),
            request.getEmployee().getId(),
            request.getRequestType(),
            request.getLeaveType(),
            request.getIsPaidLeave(),
            request.getStartDate(),
            request.getEndDate(),
            request.getTotalDays(),
            request.getRequestedLastWorkingDate(),
            request.getReason(),
            request.getAttachmentUrl(),
            request.getStatus(),
            request.getSubmittedAt(),
            request.getReviewedByAccount() != null ? request.getReviewedByAccount().getId() : null,
            request.getReviewComment(),
            request.getReviewedAt()
        );
    }
}
