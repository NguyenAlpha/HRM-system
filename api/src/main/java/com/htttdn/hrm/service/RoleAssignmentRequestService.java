package com.htttdn.hrm.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

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
import com.htttdn.hrm.dto.request.roleassignment.CreateRoleRevocationRequest;
import com.htttdn.hrm.dto.request.roleassignment.RejectRoleAssignmentRequest;
import com.htttdn.hrm.dto.response.account.AccountRoleAssignmentResponse;
import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.dto.response.roleassignment.RoleAssignmentRequestResponse;
import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.AccountRoleAssignment;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.RoleAssignmentRequest;
import com.htttdn.hrm.entity.enums.RoleAssignmentRequestStatus;
import com.htttdn.hrm.entity.enums.RoleAssignmentRequestType;
import com.htttdn.hrm.entity.enums.RoleGrantPolicy;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.exception.ForbiddenException;
import com.htttdn.hrm.exception.ResourceNotFoundException;
import com.htttdn.hrm.repository.AccountRepository;
import com.htttdn.hrm.repository.AccountRoleAssignmentRepository;
import com.htttdn.hrm.repository.RoleAssignmentRequestRepository;
import com.htttdn.hrm.security.CurrentAccountProvider;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class RoleAssignmentRequestService {

    private static final String REQUEST_PERMISSION = "role.assignment.request";
    private static final String APPROVE_PERMISSION = "role.assignment.approve";
    private static final String AUTO_APPROVAL_NOTE =
        "Tự động phê duyệt theo chính sách cấp role HR_ASSIGNABLE";

    private final RoleAssignmentRequestRepository requestRepository;
    private final AccountRepository accountRepository;
    private final AccountRoleAssignmentRepository roleAssignmentRepository;
    private final AccountRoleAssignmentCommandService roleAssignmentCommandService;
    private final RoleAssignmentRequestMapper requestMapper;
    private final AccountRoleAssignmentMapper roleAssignmentMapper;
    private final EmployeeAccessScopeService employeeAccessScopeService;
    private final CurrentAccountProvider currentAccountProvider;
    private final CompanyOwnerAccountGuard companyOwnerAccountGuard;
    private final Clock clock;

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
        Account actor = accountRepository.getRequired(actorAccountId);
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
            requestRepository.saveAndFlush(roleRequest);
        }
        return requestMapper.toResponse(roleRequest);
    }

    /**
     * The assignments of an account that a revocation request may target: not revoked, not ended and of
     * a role the workflow manages. Lets a requester without account.read pick what to take back.
     */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('role.assignment.request')")
    public List<AccountRoleAssignmentResponse> listRevocableAssignments(Long accountId) {
        Employee employee = accountRepository.getRequired(accountId).getEmployee();
        if (employee == null) {
            return List.of();
        }
        employeeAccessScopeService.requireEmployeeAccess(employee.getId(), REQUEST_PERMISSION);
        LocalDate today = LocalDate.now(clock);
        return roleAssignmentRepository.findByAccountIdAndRevokedAtIsNull(accountId).stream()
            .filter(assignment -> assignment.getEffectiveTo() == null || !assignment.getEffectiveTo().isBefore(today))
            .filter(assignment -> assignment.getRole().getGrantPolicy() == RoleGrantPolicy.HR_ASSIGNABLE
                || assignment.getRole().getGrantPolicy() == RoleGrantPolicy.OWNER_APPROVAL)
            .map(roleAssignmentMapper::toResponse)
            .toList();
    }

    /**
     * Asks to take back one role assignment. Like a grant, an HR_ASSIGNABLE role is revoked at once and
     * the request is recorded as approved; an OWNER_APPROVAL role waits for the Company Owner.
     */
    @PreAuthorize("hasAuthority('role.assignment.request')")
    public RoleAssignmentRequestResponse createRevocation(CreateRoleRevocationRequest request) {
        AccountRoleAssignment target = roleAssignmentRepository.findByIdForUpdate(request.assignmentId())
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.ROLE_ASSIGNMENT_NOT_FOUND,
                "Role assignment not found: " + request.assignmentId()
            ));
        Account account = target.getAccount();
        companyOwnerAccountGuard.requireCanManage(account.getId());
        roleAssignmentCommandService.validateRevocable(target.getRole());
        requireStillHeld(target);
        Employee employee = account.getEmployee();
        if (employee == null) {
            throw new ConflictException(
                ErrorCode.ROLE_ASSIGNMENT_NOT_ALLOWED,
                "Business roles require an account linked to an employee"
            );
        }
        employeeAccessScopeService.requireEmployeeAccess(employee.getId(), REQUEST_PERMISSION);
        employeeAccessScopeService.requireDestinationAccess(
            target.getOrganizationUnit() == null ? null : target.getOrganizationUnit().getId(),
            target.getWorkLocation() == null ? null : target.getWorkLocation().getId(),
            REQUEST_PERMISSION
        );

        Long actorAccountId = currentAccountProvider.accountId();
        Account actor = accountRepository.getRequired(actorAccountId);
        Instant now = Instant.now();
        RoleAssignmentRequest roleRequest = RoleAssignmentRequest.builder()
            .requestType(RoleAssignmentRequestType.REVOKE)
            .targetAssignment(target)
            .account(account)
            .role(target.getRole())
            .scopeType(target.getScopeType())
            .organizationUnit(target.getOrganizationUnit())
            .workLocation(target.getWorkLocation())
            .effectiveFrom(target.getEffectiveFrom())
            .effectiveTo(target.getEffectiveTo())
            .reason(request.reason().trim())
            .status(RoleAssignmentRequestStatus.PENDING)
            .requestedByAccount(actor)
            .requestedAt(now)
            .build();

        savePending(roleRequest);
        if (target.getRole().getGrantPolicy() == RoleGrantPolicy.HR_ASSIGNABLE) {
            roleAssignmentCommandService.revoke(target, actor, roleRequest.getReason());
            roleRequest.setStatus(RoleAssignmentRequestStatus.APPROVED);
            roleRequest.setReviewedByAccount(actor);
            roleRequest.setReviewedAt(now);
            roleRequest.setReviewNote(AUTO_APPROVAL_NOTE);
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
        Account actor = accountRepository.getRequired(actorAccountId);
        if (roleRequest.getRequestType() == RoleAssignmentRequestType.REVOKE) {
            // Recheck the target: it may have been revoked or have expired while the request waited.
            AccountRoleAssignment target = roleAssignmentRepository
                .findByIdForUpdate(roleRequest.getTargetAssignment().getId())
                .orElseThrow();
            requireStillHeld(target);
            roleAssignmentCommandService.revoke(target, actor, roleRequest.getReason());
        } else {
            roleRequest.setAccountRoleAssignment(roleAssignmentCommandService.assignOwnerApprovedRequest(
                roleRequest,
                actorAccountId
            ));
        }
        Instant now = Instant.now();
        roleRequest.setStatus(RoleAssignmentRequestStatus.APPROVED);
        roleRequest.setReviewedByAccount(actor);
        roleRequest.setReviewedAt(now);
        roleRequest.setReviewNote(trimToNull(request.note()));
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

        Account actor = accountRepository.getRequired(currentAccountProvider.accountId());
        Instant now = Instant.now();
        roleRequest.setStatus(RoleAssignmentRequestStatus.REJECTED);
        roleRequest.setReviewedByAccount(actor);
        roleRequest.setReviewedAt(now);
        roleRequest.setReviewNote(request.note().trim());
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

        Account actor = accountRepository.getRequired(actorAccountId);
        Instant now = Instant.now();
        roleRequest.setStatus(RoleAssignmentRequestStatus.CANCELLED);
        roleRequest.setCancelledByAccount(actor);
        roleRequest.setCancelledAt(now);
        roleRequest.setCancellationReason(request.reason().trim());
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
                roleRequest.getRequestType() == RoleAssignmentRequestType.REVOKE
                    ? "A pending revocation request for this role assignment already exists"
                    : "An overlapping pending role assignment request already exists"
            );
        }
    }

    /** Only an assignment that is not revoked and has not ended can be revoked by a request. */
    private void requireStillHeld(AccountRoleAssignment assignment) {
        if (assignment.getRevokedAt() != null
            || (assignment.getEffectiveTo() != null && assignment.getEffectiveTo().isBefore(LocalDate.now(clock)))) {
            throw new ConflictException(
                ErrorCode.ROLE_ASSIGNMENT_NOT_ALLOWED,
                "Role assignment is already revoked or has ended"
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

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
