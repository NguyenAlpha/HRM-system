package com.htttdn.hrm.service;

import java.time.Instant;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.request.account.AssignAccountRoleRequest;
import com.htttdn.hrm.dto.request.roleassignment.ApproveRoleAssignmentRequest;
import com.htttdn.hrm.dto.request.roleassignment.CancelRoleAssignmentRequest;
import com.htttdn.hrm.dto.request.roleassignment.CreateRoleAssignmentRequest;
import com.htttdn.hrm.dto.request.roleassignment.RejectRoleAssignmentRequest;
import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.dto.response.roleassignment.RoleAssignmentRequestResponse;
import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.AccountRoleAssignment;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.RoleAssignmentRequest;
import com.htttdn.hrm.entity.enums.RoleAssignmentRequestStatus;
import com.htttdn.hrm.entity.enums.RoleGrantPolicy;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.exception.ForbiddenException;
import com.htttdn.hrm.exception.ResourceNotFoundException;
import com.htttdn.hrm.repository.AccountRepository;
import com.htttdn.hrm.repository.RoleAssignmentRequestRepository;
import com.htttdn.hrm.security.CurrentAccountProvider;

@Service
@Transactional
public class RoleAssignmentRequestService {

    private static final String REQUEST_PERMISSION = "role.assignment.request";
    private static final String APPROVE_PERMISSION = "role.assignment.approve";
    private static final String AUTO_APPROVAL_NOTE =
        "Tự động phê duyệt theo chính sách cấp role HR_ASSIGNABLE";

    private final RoleAssignmentRequestRepository requestRepository;
    private final AccountRepository accountRepository;
    private final AccountRoleAssignmentCommandService roleAssignmentCommandService;
    private final RoleAssignmentRequestMapper requestMapper;
    private final EmployeeAccessScopeService employeeAccessScopeService;
    private final CurrentAccountProvider currentAccountProvider;

    public RoleAssignmentRequestService(
        RoleAssignmentRequestRepository requestRepository,
        AccountRepository accountRepository,
        AccountRoleAssignmentCommandService roleAssignmentCommandService,
        RoleAssignmentRequestMapper requestMapper,
        EmployeeAccessScopeService employeeAccessScopeService,
        CurrentAccountProvider currentAccountProvider
    ) {
        this.requestRepository = requestRepository;
        this.accountRepository = accountRepository;
        this.roleAssignmentCommandService = roleAssignmentCommandService;
        this.requestMapper = requestMapper;
        this.employeeAccessScopeService = employeeAccessScopeService;
        this.currentAccountProvider = currentAccountProvider;
    }

    @PreAuthorize("hasAuthority('role.assignment.request')")
    public RoleAssignmentRequestResponse create(CreateRoleAssignmentRequest request) {
        AssignAccountRoleRequest assignmentRequest = toAssignmentRequest(request);
        AccountRoleAssignmentCommandService.RequestCandidate candidate =
            roleAssignmentCommandService.prepareRequest(request.accountId(), assignmentRequest);

        Employee employee = candidate.account().getEmployee();
        employeeAccessScopeService.requireEmployeeAccess(employee.getId(), REQUEST_PERMISSION);
        employeeAccessScopeService.requireDestinationAccess(
            request.organizationUnitId(),
            request.workLocationId(),
            REQUEST_PERMISSION
        );
        if (request.effectiveFrom().isBefore(employee.getHireDate())) {
            throw new BusinessException(
                ErrorCode.VALIDATION_ERROR,
                "effectiveFrom must not be before employee hireDate",
                "effectiveFrom"
            );
        }

        Long actorAccountId = currentAccountProvider.accountId();
        Account actor = findAccount(actorAccountId);
        Instant now = Instant.now();
        RoleAssignmentRequest roleRequest = RoleAssignmentRequest.builder()
            .account(candidate.account())
            .role(candidate.role())
            .scopeType(request.scopeType())
            .organizationUnit(candidate.organizationUnit())
            .workLocation(candidate.workLocation())
            .effectiveFrom(request.effectiveFrom())
            .effectiveTo(request.effectiveTo())
            .reason(request.reason().trim())
            .status(RoleAssignmentRequestStatus.PENDING)
            .requestedByAccount(actor)
            .requestedAt(now)
            .updatedAt(now)
            .build();

        savePending(roleRequest);
        if (candidate.role().getGrantPolicy() == RoleGrantPolicy.HR_ASSIGNABLE) {
            AccountRoleAssignment assignment = roleAssignmentCommandService.assignHrAssignableRequest(
                roleRequest,
                actorAccountId
            );
            roleRequest.setStatus(RoleAssignmentRequestStatus.APPROVED);
            roleRequest.setReviewedByAccount(actor);
            roleRequest.setReviewedAt(now);
            roleRequest.setReviewNote(AUTO_APPROVAL_NOTE);
            roleRequest.setAccountRoleAssignment(assignment);
            roleRequest.setUpdatedAt(now);
            requestRepository.saveAndFlush(roleRequest);
        }
        return requestMapper.toResponse(roleRequest);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyAuthority('role.assignment.request', 'role.assignment.approve')")
    public Page<RoleAssignmentRequestResponse> list(
        RoleAssignmentRequestStatus status,
        Pageable pageable
    ) {
        Long actorAccountId = currentAccountProvider.accountId();
        Page<RoleAssignmentRequest> requests;
        if (currentAccountProvider.hasAuthority(APPROVE_PERMISSION)) {
            requests = status == null
                ? requestRepository.findAll(pageable)
                : requestRepository.findByStatus(status, pageable);
        } else {
            requests = status == null
                ? requestRepository.findByRequestedByAccountId(actorAccountId, pageable)
                : requestRepository.findByRequestedByAccountIdAndStatus(
                    actorAccountId,
                    status,
                    pageable
                );
        }
        return requests.map(requestMapper::toResponse);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyAuthority('role.assignment.request', 'role.assignment.approve')")
    public RoleAssignmentRequestResponse getById(Long requestId) {
        Long actorAccountId = currentAccountProvider.accountId();
        RoleAssignmentRequest roleRequest = findRequest(requestId);
        requireCanView(roleRequest, actorAccountId);
        return requestMapper.toResponse(roleRequest);
    }

    @PreAuthorize("hasAuthority('role.assignment.approve')")
    public RoleAssignmentRequestResponse approve(
        Long requestId,
        ApproveRoleAssignmentRequest request
    ) {
        RoleAssignmentRequest roleRequest = findRequestForUpdate(requestId);
        requirePending(roleRequest);

        Long actorAccountId = currentAccountProvider.accountId();
        Account actor = findAccount(actorAccountId);
        AccountRoleAssignment assignment = roleAssignmentCommandService.assignOwnerApprovedRequest(
            roleRequest,
            actorAccountId
        );
        Instant now = Instant.now();
        roleRequest.setStatus(RoleAssignmentRequestStatus.APPROVED);
        roleRequest.setReviewedByAccount(actor);
        roleRequest.setReviewedAt(now);
        roleRequest.setReviewNote(trimToNull(request.note()));
        roleRequest.setAccountRoleAssignment(assignment);
        roleRequest.setUpdatedAt(now);
        requestRepository.saveAndFlush(roleRequest);
        return requestMapper.toResponse(roleRequest);
    }

    @PreAuthorize("hasAuthority('role.assignment.approve')")
    public RoleAssignmentRequestResponse reject(
        Long requestId,
        RejectRoleAssignmentRequest request
    ) {
        RoleAssignmentRequest roleRequest = findRequestForUpdate(requestId);
        requirePending(roleRequest);

        Account actor = findAccount(currentAccountProvider.accountId());
        Instant now = Instant.now();
        roleRequest.setStatus(RoleAssignmentRequestStatus.REJECTED);
        roleRequest.setReviewedByAccount(actor);
        roleRequest.setReviewedAt(now);
        roleRequest.setReviewNote(request.note().trim());
        roleRequest.setUpdatedAt(now);
        requestRepository.saveAndFlush(roleRequest);
        return requestMapper.toResponse(roleRequest);
    }

    @PreAuthorize("hasAuthority('role.assignment.request')")
    public RoleAssignmentRequestResponse cancel(
        Long requestId,
        CancelRoleAssignmentRequest request
    ) {
        Long actorAccountId = currentAccountProvider.accountId();
        RoleAssignmentRequest roleRequest = findRequestForUpdate(requestId);
        requirePending(roleRequest);
        if (!roleRequest.getRequestedByAccount().getId().equals(actorAccountId)) {
            throw new ForbiddenException(
                ErrorCode.FORBIDDEN,
                "Only the account that created the request can cancel it"
            );
        }

        Account actor = findAccount(actorAccountId);
        Instant now = Instant.now();
        roleRequest.setStatus(RoleAssignmentRequestStatus.CANCELLED);
        roleRequest.setCancelledByAccount(actor);
        roleRequest.setCancelledAt(now);
        roleRequest.setCancellationReason(request.reason().trim());
        roleRequest.setUpdatedAt(now);
        requestRepository.saveAndFlush(roleRequest);
        return requestMapper.toResponse(roleRequest);
    }

    private AssignAccountRoleRequest toAssignmentRequest(CreateRoleAssignmentRequest request) {
        return new AssignAccountRoleRequest(
            request.roleCode(),
            request.scopeType(),
            request.organizationUnitId(),
            request.workLocationId(),
            request.effectiveFrom(),
            request.effectiveTo(),
            request.reason()
        );
    }

    private void savePending(RoleAssignmentRequest roleRequest) {
        try {
            requestRepository.saveAndFlush(roleRequest);
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException(
                ErrorCode.ROLE_ASSIGNMENT_REQUEST_EXISTS,
                "An overlapping pending role assignment request already exists"
            );
        }
    }

    private void requireCanView(RoleAssignmentRequest roleRequest, Long actorAccountId) {
        if (roleRequest.getRequestedByAccount().getId().equals(actorAccountId)
            || currentAccountProvider.hasAuthority(APPROVE_PERMISSION)) {
            return;
        }
        throw new ForbiddenException(ErrorCode.FORBIDDEN, "Role assignment request is outside your access");
    }

    private void requirePending(RoleAssignmentRequest request) {
        if (request.getStatus() != RoleAssignmentRequestStatus.PENDING) {
            throw new ConflictException(
                ErrorCode.ROLE_ASSIGNMENT_REQUEST_ALREADY_PROCESSED,
                "Role assignment request has already been processed"
            );
        }
    }

    private RoleAssignmentRequest findRequest(Long requestId) {
        return requestRepository.findById(requestId)
            .orElseThrow(() -> requestNotFound(requestId));
    }

    private RoleAssignmentRequest findRequestForUpdate(Long requestId) {
        return requestRepository.findByIdForUpdate(requestId)
            .orElseThrow(() -> requestNotFound(requestId));
    }

    private ResourceNotFoundException requestNotFound(Long requestId) {
        return new ResourceNotFoundException(
            ErrorCode.ROLE_ASSIGNMENT_REQUEST_NOT_FOUND,
            "Role assignment request not found: " + requestId
        );
    }

    private Account findAccount(Long accountId) {
        return accountRepository.findById(accountId)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.ACCOUNT_NOT_FOUND,
                "Account not found: " + accountId
            ));
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
