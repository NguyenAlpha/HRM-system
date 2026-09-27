package com.htttdn.hrm.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.request.account.AssignAccountRoleRequest;
import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.AccountRoleAssignment;
import com.htttdn.hrm.entity.OrganizationUnit;
import com.htttdn.hrm.entity.Role;
import com.htttdn.hrm.entity.RoleAssignmentRequest;
import com.htttdn.hrm.entity.WorkLocation;
import com.htttdn.hrm.entity.enums.AccountStatus;
import com.htttdn.hrm.entity.enums.RoleAssignmentRequestStatus;
import com.htttdn.hrm.entity.enums.RoleGrantPolicy;
import com.htttdn.hrm.entity.enums.RoleScopeType;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.exception.ResourceNotFoundException;
import com.htttdn.hrm.repository.AccountRepository;
import com.htttdn.hrm.repository.AccountRoleAssignmentRepository;
import com.htttdn.hrm.repository.OrganizationUnitRepository;
import com.htttdn.hrm.repository.RoleRepository;
import com.htttdn.hrm.repository.WorkLocationRepository;

/**
 * Internal command component for creating effective role assignments.
 * Authorization remains at the public workflow boundary; every entry point here
 * enforces the grant policy of the workflow that invoked it.
 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class AccountRoleAssignmentCommandService {

    private static final String COMPANY_OWNER_ROLE = "COMPANY_OWNER";
    private static final String DIRECTOR_ROLE = "DIRECTOR";
    private static final String HR_STAFF_ROLE = "HR_STAFF";

    private static final Set<String> PROVISIONED_COMPANY_ROLES = Set.of(
        COMPANY_OWNER_ROLE,
        DIRECTOR_ROLE,
        HR_STAFF_ROLE
    );

    private static final Map<String, Set<RoleScopeType>> SYSTEM_ROLE_SCOPES = Map.of(
        "TEAM_LEAD", Set.of(RoleScopeType.ORG_UNIT),
        "WAREHOUSE_SUPERVISOR", Set.of(RoleScopeType.LOCATION),
        "BRANCH_MANAGER", Set.of(RoleScopeType.LOCATION),
        HR_STAFF_ROLE, Set.of(RoleScopeType.COMPANY),
        "PAYROLL_ACCOUNTANT", Set.of(RoleScopeType.COMPANY),
        "PAYROLL_APPROVER", Set.of(RoleScopeType.COMPANY),
        DIRECTOR_ROLE, Set.of(RoleScopeType.COMPANY),
        COMPANY_OWNER_ROLE, Set.of(RoleScopeType.COMPANY)
    );

    private final AccountRepository accountRepository;
    private final RoleRepository roleRepository;
    private final AccountRoleAssignmentRepository roleAssignmentRepository;
    private final OrganizationUnitRepository organizationUnitRepository;
    private final WorkLocationRepository workLocationRepository;

    public AccountRoleAssignmentCommandService(
        AccountRepository accountRepository,
        RoleRepository roleRepository,
        AccountRoleAssignmentRepository roleAssignmentRepository,
        OrganizationUnitRepository organizationUnitRepository,
        WorkLocationRepository workLocationRepository
    ) {
        this.accountRepository = accountRepository;
        this.roleRepository = roleRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.organizationUnitRepository = organizationUnitRepository;
        this.workLocationRepository = workLocationRepository;
    }

    public AccountRoleAssignment assignDirect(
        Long accountId,
        AssignAccountRoleRequest request,
        Long actorAccountId
    ) {
        return assignInternal(accountId, request, actorAccountId, AssignmentFlow.DIRECT);
    }

    public AccountRoleAssignment assignProvisionedCompanyRole(
        Long accountId,
        String roleCode,
        LocalDate effectiveFrom,
        String reason,
        Long actorAccountId
    ) {
        if (!PROVISIONED_COMPANY_ROLES.contains(roleCode)) {
            throw new IllegalArgumentException("Unsupported provisioned company role: " + roleCode);
        }
        AssignAccountRoleRequest request = new AssignAccountRoleRequest(
            roleCode,
            RoleScopeType.COMPANY,
            null,
            null,
            effectiveFrom,
            null,
            reason
        );
        return assignInternal(accountId, request, actorAccountId, AssignmentFlow.BOOTSTRAP);
    }

    public AccountRoleAssignment assignHrAssignableRequest(
        RoleAssignmentRequest roleRequest,
        Long actorAccountId
    ) {
        return assignRequestedRole(roleRequest, actorAccountId, AssignmentFlow.HR_ASSIGNABLE_REQUEST);
    }

    public AccountRoleAssignment assignOwnerApprovedRequest(
        RoleAssignmentRequest roleRequest,
        Long actorAccountId
    ) {
        return assignRequestedRole(roleRequest, actorAccountId, AssignmentFlow.OWNER_APPROVED_REQUEST);
    }

    private AccountRoleAssignment assignRequestedRole(
        RoleAssignmentRequest roleRequest,
        Long actorAccountId,
        AssignmentFlow flow
    ) {
        if (roleRequest.getStatus() != RoleAssignmentRequestStatus.PENDING
            || roleRequest.getAccountRoleAssignment() != null) {
            throw new ConflictException(
                ErrorCode.CONFLICT,
                "Only a pending role assignment request without an assignment can be processed"
            );
        }
        AssignAccountRoleRequest request = new AssignAccountRoleRequest(
            roleRequest.getRole().getCode(),
            roleRequest.getScopeType(),
            roleRequest.getOrganizationUnit() == null ? null : roleRequest.getOrganizationUnit().getId(),
            roleRequest.getWorkLocation() == null ? null : roleRequest.getWorkLocation().getId(),
            roleRequest.getEffectiveFrom(),
            roleRequest.getEffectiveTo(),
            roleRequest.getReason()
        );
        return assignInternal(roleRequest.getAccount().getId(), request, actorAccountId, flow);
    }

    private AccountRoleAssignment assignInternal(
        Long accountId,
        AssignAccountRoleRequest request,
        Long actorAccountId,
        AssignmentFlow flow
    ) {
        Account account = findAccountForUpdate(accountId);
        validateTargetAccount(account);

        Role role = roleRepository.findByCodeForUpdate(request.roleCode())
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.ROLE_NOT_FOUND,
                "Role not found: " + request.roleCode()
            ));
        validateGrantPolicy(role, flow);
        validateScopePolicy(role, request.scopeType());
        validatePeriod(request.effectiveFrom(), request.effectiveTo());

        ScopeTarget scopeTarget = resolveScopeTarget(request);
        ensureNoDuplicateAssignment(account, role, request, scopeTarget);
        if (DIRECTOR_ROLE.equals(role.getCode())) {
            ensureSingleDirector(role, request.effectiveFrom(), request.effectiveTo());
        }
        if (COMPANY_OWNER_ROLE.equals(role.getCode())) {
            ensureCompanyOwnerDoesNotExist(role);
        }
        if (flow == AssignmentFlow.BOOTSTRAP && HR_STAFF_ROLE.equals(role.getCode())) {
            ensureFirstHrStaffDoesNotExist(role);
        }

        Account actor = findAccount(actorAccountId);
        return roleAssignmentRepository.save(AccountRoleAssignment.builder()
            .account(account)
            .role(role)
            .scopeType(request.scopeType())
            .organizationUnit(scopeTarget.organizationUnit())
            .workLocation(scopeTarget.workLocation())
            .effectiveFrom(request.effectiveFrom())
            .effectiveTo(request.effectiveTo())
            .grantedByAccount(actor)
            .reason(request.reason().trim())
            .createdAt(Instant.now())
            .build());
    }

    private void validateTargetAccount(Account account) {
        if (account.getStatus() == AccountStatus.DISABLED) {
            throw new ConflictException(
                ErrorCode.ROLE_ASSIGNMENT_NOT_ALLOWED,
                "Roles cannot be assigned to a disabled account"
            );
        }
        if (account.getEmployee() == null) {
            throw new ConflictException(
                ErrorCode.ROLE_ASSIGNMENT_NOT_ALLOWED,
                "Business roles require an account linked to an employee"
            );
        }
    }

    private void validateGrantPolicy(Role role, AssignmentFlow flow) {
        RoleGrantPolicy policy = role.getGrantPolicy();
        boolean allowed = switch (flow) {
            case DIRECT -> policy == RoleGrantPolicy.HR_ASSIGNABLE
                || policy == RoleGrantPolicy.OWNER_APPROVAL;
            case BOOTSTRAP -> PROVISIONED_COMPANY_ROLES.contains(role.getCode());
            case HR_ASSIGNABLE_REQUEST -> policy == RoleGrantPolicy.HR_ASSIGNABLE;
            case OWNER_APPROVED_REQUEST -> policy == RoleGrantPolicy.OWNER_APPROVAL;
        };
        if (!allowed) {
            throw new ConflictException(
                ErrorCode.ROLE_ASSIGNMENT_NOT_ALLOWED,
                "Role " + role.getCode() + " cannot be assigned through " + flow
            );
        }
    }

    private void validateScopePolicy(Role role, RoleScopeType scopeType) {
        Set<RoleScopeType> allowedScopes = SYSTEM_ROLE_SCOPES.get(role.getCode());
        if (allowedScopes != null && !allowedScopes.contains(scopeType)) {
            throw new BusinessException(
                ErrorCode.VALIDATION_ERROR,
                role.getCode() + " only supports scope " + allowedScopes,
                "scopeType"
            );
        }
    }

    private void validatePeriod(LocalDate effectiveFrom, LocalDate effectiveTo) {
        if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw new BusinessException(
                ErrorCode.VALIDATION_ERROR,
                "effectiveTo must be on or after effectiveFrom",
                "effectiveTo"
            );
        }
    }

    private ScopeTarget resolveScopeTarget(AssignAccountRoleRequest request) {
        return switch (request.scopeType()) {
            case SELF, COMPANY -> {
                if (request.organizationUnitId() != null || request.workLocationId() != null) {
                    throw invalidScope(
                        "organizationUnitId and workLocationId must be null for " + request.scopeType(),
                        "scopeType"
                    );
                }
                yield new ScopeTarget(null, null);
            }
            case ORG_UNIT -> {
                if (request.organizationUnitId() == null || request.workLocationId() != null) {
                    throw invalidScope(
                        "organizationUnitId is required and workLocationId must be null for ORG_UNIT",
                        "organizationUnitId"
                    );
                }
                OrganizationUnit unit = organizationUnitRepository
                    .findByIdAndDeletedAtIsNull(request.organizationUnitId())
                    .filter(candidate -> Boolean.TRUE.equals(candidate.getIsActive()))
                    .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.ORGANIZATION_UNIT_NOT_FOUND,
                        "Active organization unit not found: " + request.organizationUnitId()
                    ));
                yield new ScopeTarget(unit, null);
            }
            case LOCATION -> {
                if (request.workLocationId() == null || request.organizationUnitId() != null) {
                    throw invalidScope(
                        "workLocationId is required and organizationUnitId must be null for LOCATION",
                        "workLocationId"
                    );
                }
                WorkLocation location = workLocationRepository
                    .findByIdAndDeletedAtIsNull(request.workLocationId())
                    .filter(candidate -> Boolean.TRUE.equals(candidate.getIsActive()))
                    .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.LOCATION_NOT_FOUND,
                        "Active work location not found: " + request.workLocationId()
                    ));
                yield new ScopeTarget(null, location);
            }
        };
    }

    private void ensureNoDuplicateAssignment(
        Account account,
        Role role,
        AssignAccountRoleRequest request,
        ScopeTarget scopeTarget
    ) {
        boolean duplicate = roleAssignmentRepository
            .findByAccountIdAndRoleIdAndRevokedAtIsNull(account.getId(), role.getId()).stream()
            .filter(existing -> existing.getScopeType() == request.scopeType())
            .filter(existing -> sameTarget(existing, scopeTarget))
            .anyMatch(existing -> periodsOverlap(
                existing.getEffectiveFrom(),
                existing.getEffectiveTo(),
                request.effectiveFrom(),
                request.effectiveTo()
            ));
        if (duplicate) {
            throw new ConflictException(
                ErrorCode.ROLE_ASSIGNMENT_EXISTS,
                "An overlapping role assignment already exists for this account and scope"
            );
        }
    }

    private void ensureSingleDirector(Role role, LocalDate effectiveFrom, LocalDate effectiveTo) {
        boolean conflict = roleAssignmentRepository.findByRoleIdAndRevokedAtIsNull(role.getId()).stream()
            .anyMatch(existing -> periodsOverlap(
                existing.getEffectiveFrom(),
                existing.getEffectiveTo(),
                effectiveFrom,
                effectiveTo
            ));
        if (conflict) {
            throw new ConflictException(
                ErrorCode.ROLE_ASSIGNMENT_EXISTS,
                "A DIRECTOR assignment already exists for the requested period"
            );
        }
    }

    private void ensureCompanyOwnerDoesNotExist(Role role) {
        if (!roleAssignmentRepository.findByRoleIdAndRevokedAtIsNull(role.getId()).isEmpty()) {
            throw new ConflictException(
                ErrorCode.COMPANY_OWNER_ALREADY_EXISTS,
                "A COMPANY_OWNER assignment already exists"
            );
        }
    }

    private void ensureFirstHrStaffDoesNotExist(Role role) {
        if (!roleAssignmentRepository.findByRoleIdAndRevokedAtIsNull(role.getId()).isEmpty()) {
            throw new ConflictException(
                ErrorCode.HR_STAFF_ALREADY_EXISTS,
                "An HR_STAFF assignment already exists"
            );
        }
    }

    private boolean sameTarget(AccountRoleAssignment assignment, ScopeTarget target) {
        Long existingUnitId = assignment.getOrganizationUnit() == null
            ? null
            : assignment.getOrganizationUnit().getId();
        Long requestedUnitId = target.organizationUnit() == null ? null : target.organizationUnit().getId();
        Long existingLocationId = assignment.getWorkLocation() == null
            ? null
            : assignment.getWorkLocation().getId();
        Long requestedLocationId = target.workLocation() == null ? null : target.workLocation().getId();
        return Objects.equals(existingUnitId, requestedUnitId)
            && Objects.equals(existingLocationId, requestedLocationId);
    }

    private boolean periodsOverlap(
        LocalDate firstFrom,
        LocalDate firstTo,
        LocalDate secondFrom,
        LocalDate secondTo
    ) {
        return (firstTo == null || !firstTo.isBefore(secondFrom))
            && (secondTo == null || !secondTo.isBefore(firstFrom));
    }

    private BusinessException invalidScope(String message, String field) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, message, field);
    }

    private Account findAccount(Long accountId) {
        return accountRepository.findById(accountId)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.ACCOUNT_NOT_FOUND,
                "Account not found: " + accountId
            ));
    }

    private Account findAccountForUpdate(Long accountId) {
        return accountRepository.findByIdForUpdate(accountId)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.ACCOUNT_NOT_FOUND,
                "Account not found: " + accountId
            ));
    }

    private enum AssignmentFlow {
        DIRECT,
        BOOTSTRAP,
        HR_ASSIGNABLE_REQUEST,
        OWNER_APPROVED_REQUEST
    }

    private record ScopeTarget(OrganizationUnit organizationUnit, WorkLocation workLocation) {
    }
}
