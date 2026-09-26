package com.htttdn.hrm.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.request.account.AssignAccountRoleRequest;
import com.htttdn.hrm.dto.request.account.RevokeAccountRoleRequest;
import com.htttdn.hrm.dto.response.account.AccountRoleAssignmentResponse;
import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.AccountRoleAssignment;
import com.htttdn.hrm.entity.OrganizationUnit;
import com.htttdn.hrm.entity.Role;
import com.htttdn.hrm.entity.WorkLocation;
import com.htttdn.hrm.entity.enums.AccountStatus;
import com.htttdn.hrm.entity.enums.RoleScopeType;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.exception.ResourceNotFoundException;
import com.htttdn.hrm.repository.AccountRepository;
import com.htttdn.hrm.repository.AccountRoleAssignmentRepository;
import com.htttdn.hrm.repository.OrganizationUnitRepository;
import com.htttdn.hrm.repository.RoleRepository;
import com.htttdn.hrm.repository.WorkLocationRepository;
import com.htttdn.hrm.security.CurrentAccountProvider;

@Service
@Transactional
public class AccountRoleAssignmentAdminService {

    private static final String EMPLOYEE_ROLE = "EMPLOYEE";
    private static final String COMPANY_OWNER_ROLE = "COMPANY_OWNER";
    private static final String SYSTEM_ADMIN_ROLE = "SYSTEM_ADMIN";
    private static final String DIRECTOR_ROLE = "DIRECTOR";
    private static final String HR_STAFF_ROLE = "HR_STAFF";

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
    private final RefreshTokenService refreshTokenService;
    private final CurrentAccountProvider currentAccountProvider;

    public AccountRoleAssignmentAdminService(
        AccountRepository accountRepository,
        RoleRepository roleRepository,
        AccountRoleAssignmentRepository roleAssignmentRepository,
        OrganizationUnitRepository organizationUnitRepository,
        WorkLocationRepository workLocationRepository,
        RefreshTokenService refreshTokenService,
        CurrentAccountProvider currentAccountProvider
    ) {
        this.accountRepository = accountRepository;
        this.roleRepository = roleRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.organizationUnitRepository = organizationUnitRepository;
        this.workLocationRepository = workLocationRepository;
        this.refreshTokenService = refreshTokenService;
        this.currentAccountProvider = currentAccountProvider;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('account.read')")
    public List<AccountRoleAssignmentResponse> list(Long accountId) {
        findAccount(accountId);
        return roleAssignmentRepository.findByAccountIdOrderByCreatedAtDesc(accountId).stream()
            .map(this::toResponse)
            .toList();
    }

    @PreAuthorize("hasAuthority('account.role.assign')")
    public AccountRoleAssignmentResponse assign(Long accountId, AssignAccountRoleRequest request) {
        return assignInternal(accountId, request, currentAccountProvider.accountId(), false);
    }

    /**
     * Internal entry point for an authorized bootstrap workflow. It deliberately
     * supports only company-wide roles that have a dedicated provisioning flow.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public AccountRoleAssignmentResponse assignProvisionedCompanyRole(
        Long accountId,
        String roleCode,
        LocalDate effectiveFrom,
        String reason,
        Long actorAccountId
    ) {
        if (!DIRECTOR_ROLE.equals(roleCode)
            && !COMPANY_OWNER_ROLE.equals(roleCode)
            && !HR_STAFF_ROLE.equals(roleCode)) {
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
        return assignInternal(accountId, request, actorAccountId, true);
    }

    private AccountRoleAssignmentResponse assignInternal(
        Long accountId,
        AssignAccountRoleRequest request,
        Long actorAccountId,
        boolean allowProvisionedRole
    ) {
        Account account = findAccountForUpdate(accountId);
        validateTargetAccount(account);

        Role role = roleRepository.findByCodeForUpdate(request.roleCode())
            .filter(candidate -> Boolean.TRUE.equals(candidate.getIsActive()))
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.ROLE_NOT_FOUND,
                "Active role not found: " + request.roleCode()
            ));
        validateRoleCanBeManaged(role, allowProvisionedRole);
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
        if (allowProvisionedRole && HR_STAFF_ROLE.equals(role.getCode())) {
            ensureFirstHrStaffDoesNotExist(role);
        }

        Account actor = findAccount(actorAccountId);
        AccountRoleAssignment assignment = roleAssignmentRepository.save(AccountRoleAssignment.builder()
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
        return toResponse(assignment);
    }

    @PreAuthorize("hasAuthority('account.role.assign')")
    public AccountRoleAssignmentResponse revoke(
        Long accountId,
        Long assignmentId,
        RevokeAccountRoleRequest request
    ) {
        AccountRoleAssignment assignment = roleAssignmentRepository
            .findByIdAndAccountIdForUpdate(assignmentId, accountId)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.ROLE_ASSIGNMENT_NOT_FOUND,
                "Role assignment not found: " + assignmentId
            ));
        validateRoleCanBeManaged(assignment.getRole(), false);
        if (assignment.getRevokedAt() != null) {
            return toResponse(assignment);
        }

        Account actor = findAccount(currentAccountProvider.accountId());
        Instant now = Instant.now();
        LocalDate today = LocalDate.now();
        assignment.setRevokedByAccount(actor);
        assignment.setRevokedAt(now);
        assignment.setRevocationReason(request.reason().trim());
        if (!assignment.getEffectiveFrom().isAfter(today)
            && (assignment.getEffectiveTo() == null || assignment.getEffectiveTo().isAfter(today))) {
            assignment.setEffectiveTo(today);
        }

        refreshTokenService.revokeAll(accountId);
        return toResponse(assignment);
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

    private void validateRoleCanBeManaged(Role role, boolean allowProvisionedRole) {
        if (EMPLOYEE_ROLE.equals(role.getCode())) {
            throw new ConflictException(
                ErrorCode.ROLE_ASSIGNMENT_NOT_ALLOWED,
                "The EMPLOYEE role is managed automatically by account provisioning"
            );
        }
        if (SYSTEM_ADMIN_ROLE.equals(role.getCode())) {
            throw new ConflictException(
                ErrorCode.ROLE_ASSIGNMENT_NOT_ALLOWED,
                "The SYSTEM_ADMIN role cannot be managed through business role assignments"
            );
        }
        if (COMPANY_OWNER_ROLE.equals(role.getCode()) && !allowProvisionedRole) {
            throw new ConflictException(
                ErrorCode.ROLE_ASSIGNMENT_NOT_ALLOWED,
                "The COMPANY_OWNER role must be managed through the dedicated ownership workflow"
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

    private AccountRoleAssignmentResponse toResponse(AccountRoleAssignment assignment) {
        return new AccountRoleAssignmentResponse(
            assignment.getId(),
            assignment.getAccount().getId(),
            assignment.getRole().getId(),
            assignment.getRole().getCode(),
            assignment.getRole().getName(),
            assignment.getScopeType(),
            assignment.getOrganizationUnit() == null ? null : assignment.getOrganizationUnit().getId(),
            assignment.getWorkLocation() == null ? null : assignment.getWorkLocation().getId(),
            assignment.getEffectiveFrom(),
            assignment.getEffectiveTo(),
            assignment.getGrantedByAccount().getId(),
            assignment.getReason(),
            assignment.getCreatedAt(),
            assignment.getRevokedByAccount() == null ? null : assignment.getRevokedByAccount().getId(),
            assignment.getRevokedAt(),
            assignment.getRevocationReason()
        );
    }

    private record ScopeTarget(OrganizationUnit organizationUnit, WorkLocation workLocation) {
    }
}
