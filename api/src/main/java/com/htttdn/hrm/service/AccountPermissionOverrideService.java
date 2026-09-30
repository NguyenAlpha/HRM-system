package com.htttdn.hrm.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.request.account.CreatePermissionOverrideRequest;
import com.htttdn.hrm.dto.request.account.RevokePermissionOverrideRequest;
import com.htttdn.hrm.dto.response.account.AccountPermissionOverrideResponse;
import com.htttdn.hrm.dto.response.account.PermissionOverrideOptionResponse;
import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.AccountPermissionOverride;
import com.htttdn.hrm.entity.AccountRoleAssignment;
import com.htttdn.hrm.entity.Permission;
import com.htttdn.hrm.entity.enums.AccountStatus;
import com.htttdn.hrm.entity.enums.PermissionAssignmentPolicy;
import com.htttdn.hrm.entity.enums.PermissionOverrideEffect;
import com.htttdn.hrm.entity.enums.PermissionOverrideStatus;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.exception.ResourceNotFoundException;
import com.htttdn.hrm.repository.AccountPermissionOverrideRepository;
import com.htttdn.hrm.repository.AccountRepository;
import com.htttdn.hrm.repository.AccountRoleAssignmentRepository;
import com.htttdn.hrm.repository.PermissionRepository;
import com.htttdn.hrm.repository.RolePermissionRepository;
import com.htttdn.hrm.security.CanManagePermissionOverrides;
import com.htttdn.hrm.security.CurrentAccountProvider;

@Service
@Transactional
@CanManagePermissionOverrides
public class AccountPermissionOverrideService {

    private final AccountRepository accountRepository;
    private final AccountRoleAssignmentRepository assignmentRepository;
    private final AccountPermissionOverrideRepository overrideRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final CurrentAccountProvider currentAccountProvider;
    private final RefreshTokenService refreshTokenService;

    public AccountPermissionOverrideService(
        AccountRepository accountRepository,
        AccountRoleAssignmentRepository assignmentRepository,
        AccountPermissionOverrideRepository overrideRepository,
        PermissionRepository permissionRepository,
        RolePermissionRepository rolePermissionRepository,
        CurrentAccountProvider currentAccountProvider,
        RefreshTokenService refreshTokenService
    ) {
        this.accountRepository = accountRepository;
        this.assignmentRepository = assignmentRepository;
        this.overrideRepository = overrideRepository;
        this.permissionRepository = permissionRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.currentAccountProvider = currentAccountProvider;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional(readOnly = true)
    public List<AccountPermissionOverrideResponse> list(Long accountId, Long assignmentId) {
        findAssignment(accountId, assignmentId);
        return overrideRepository.findHistoryByAssignmentId(assignmentId).stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<PermissionOverrideOptionResponse> availablePermissions(Long accountId, Long assignmentId) {
        AccountRoleAssignment assignment = findAssignment(accountId, assignmentId);
        return permissionRepository
            .findByIsActiveTrueAndAssignmentPolicyOrderByModuleAscCodeAsc(PermissionAssignmentPolicy.DELEGABLE)
            .stream()
            .map(permission -> new PermissionOverrideOptionResponse(
                permission.getId(),
                permission.getCode(),
                permission.getName(),
                permission.getModule(),
                permission.getDescription(),
                defaultEffect(assignment, permission)
            ))
            .toList();
    }

    public AccountPermissionOverrideResponse create(
        Long accountId,
        Long assignmentId,
        CreatePermissionOverrideRequest request
    ) {
        AccountRoleAssignment assignment = findAssignmentForUpdate(accountId, assignmentId);
        validateAssignment(assignment);
        Permission permission = permissionRepository.findById(request.permissionId())
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.PERMISSION_NOT_FOUND,
                "Permission not found: " + request.permissionId()
            ));
        validatePermission(assignment, permission, request.effect());
        validatePeriod(assignment, request.effectiveFrom(), request.effectiveTo());
        if (overrideRepository.existsOverlappingActive(
            assignmentId,
            permission.getId(),
            request.effectiveFrom(),
            request.effectiveTo()
        )) {
            throw new ConflictException(
                ErrorCode.PERMISSION_OVERRIDE_EXISTS,
                "An overlapping permission override already exists"
            );
        }

        Account actor = findAccount(currentAccountProvider.accountId());
        AccountPermissionOverride permissionOverride = AccountPermissionOverride.builder()
            .accountRoleAssignment(assignment)
            .permission(permission)
            .effect(request.effect())
            .effectiveFrom(request.effectiveFrom())
            .effectiveTo(request.effectiveTo())
            .reason(request.reason().trim())
            .grantedByAccount(actor)
            .createdAt(Instant.now())
            .build();
        try {
            AccountPermissionOverride saved = overrideRepository.saveAndFlush(permissionOverride);
            refreshTokenService.revokeAll(accountId);
            return toResponse(saved);
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException(
                ErrorCode.PERMISSION_OVERRIDE_EXISTS,
                "An overlapping permission override already exists"
            );
        }
    }

    public AccountPermissionOverrideResponse revoke(
        Long accountId,
        Long assignmentId,
        Long overrideId,
        RevokePermissionOverrideRequest request
    ) {
        findAssignmentForUpdate(accountId, assignmentId);
        AccountPermissionOverride permissionOverride = overrideRepository
            .findByIdAndAssignmentIdForUpdate(overrideId, assignmentId)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.PERMISSION_OVERRIDE_NOT_FOUND,
                "Permission override not found: " + overrideId
            ));
        if (permissionOverride.getRevokedAt() != null) {
            return toResponse(permissionOverride);
        }

        Account actor = findAccount(currentAccountProvider.accountId());
        permissionOverride.setRevokedByAccount(actor);
        permissionOverride.setRevokedAt(Instant.now());
        permissionOverride.setRevocationReason(request.reason().trim());
        refreshTokenService.revokeAll(accountId);
        return toResponse(permissionOverride);
    }

    private AccountRoleAssignment findAssignment(Long accountId, Long assignmentId) {
        return assignmentRepository.findByIdAndAccountId(assignmentId, accountId)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.ROLE_ASSIGNMENT_NOT_FOUND,
                "Role assignment not found: " + assignmentId
            ));
    }

    private AccountRoleAssignment findAssignmentForUpdate(Long accountId, Long assignmentId) {
        return assignmentRepository.findByIdAndAccountIdForUpdate(assignmentId, accountId)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.ROLE_ASSIGNMENT_NOT_FOUND,
                "Role assignment not found: " + assignmentId
            ));
    }

    private void validateAssignment(AccountRoleAssignment assignment) {
        if (assignment.getRevokedAt() != null || assignment.getRole().getDeletedAt() != null) {
            throw new ConflictException(
                ErrorCode.PERMISSION_OVERRIDE_NOT_ALLOWED,
                "Permission overrides require a non-revoked role assignment with an active role"
            );
        }
        if (assignment.getAccount().getStatus() == AccountStatus.DISABLED) {
            throw new ConflictException(
                ErrorCode.PERMISSION_OVERRIDE_NOT_ALLOWED,
                "Permission overrides cannot be added to a disabled account"
            );
        }
    }

    private void validatePermission(
        AccountRoleAssignment assignment,
        Permission permission,
        PermissionOverrideEffect requestedEffect
    ) {
        if (!Boolean.TRUE.equals(permission.getIsActive())
            || permission.getAssignmentPolicy() != PermissionAssignmentPolicy.DELEGABLE) {
            throw new ConflictException(
                ErrorCode.PERMISSION_OVERRIDE_NOT_ALLOWED,
                "Only active DELEGABLE permissions can be overridden"
            );
        }
        PermissionOverrideEffect expectedEffect = defaultEffect(assignment, permission);
        if (requestedEffect != expectedEffect) {
            throw new ConflictException(
                ErrorCode.PERMISSION_OVERRIDE_NOT_ALLOWED,
                "The requested effect does not change the role's default permission"
            );
        }
    }

    private PermissionOverrideEffect defaultEffect(AccountRoleAssignment assignment, Permission permission) {
        boolean grantedByRole = rolePermissionRepository.existsByIdRoleIdAndIdPermissionId(
            assignment.getRole().getId(),
            permission.getId()
        );
        return grantedByRole ? PermissionOverrideEffect.REVOKE : PermissionOverrideEffect.GRANT;
    }

    private void validatePeriod(
        AccountRoleAssignment assignment,
        LocalDate effectiveFrom,
        LocalDate effectiveTo
    ) {
        if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw new BusinessException(
                ErrorCode.VALIDATION_ERROR,
                "effectiveTo must be on or after effectiveFrom",
                "effectiveTo"
            );
        }
        if (effectiveFrom.isBefore(assignment.getEffectiveFrom())) {
            throw new BusinessException(
                ErrorCode.VALIDATION_ERROR,
                "Override cannot start before the role assignment",
                "effectiveFrom"
            );
        }
        if (assignment.getEffectiveTo() != null
            && (effectiveTo == null || effectiveTo.isAfter(assignment.getEffectiveTo()))) {
            throw new BusinessException(
                ErrorCode.VALIDATION_ERROR,
                "Override must end within the role assignment period",
                "effectiveTo"
            );
        }
    }

    private Account findAccount(Long accountId) {
        return accountRepository.findById(accountId)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.ACCOUNT_NOT_FOUND,
                "Account not found: " + accountId
            ));
    }

    private AccountPermissionOverrideResponse toResponse(AccountPermissionOverride permissionOverride) {
        LocalDate today = LocalDate.now();
        PermissionOverrideStatus status;
        if (permissionOverride.getRevokedAt() != null) {
            status = PermissionOverrideStatus.REVOKED;
        } else if (permissionOverride.getEffectiveFrom().isAfter(today)) {
            status = PermissionOverrideStatus.SCHEDULED;
        } else if (permissionOverride.getEffectiveTo() != null
            && permissionOverride.getEffectiveTo().isBefore(today)) {
            status = PermissionOverrideStatus.EXPIRED;
        } else {
            status = PermissionOverrideStatus.ACTIVE;
        }

        Permission permission = permissionOverride.getPermission();
        Account grantedBy = permissionOverride.getGrantedByAccount();
        Account revokedBy = permissionOverride.getRevokedByAccount();
        return new AccountPermissionOverrideResponse(
            permissionOverride.getId(),
            permissionOverride.getAccountRoleAssignment().getId(),
            permissionOverride.getAccountRoleAssignment().getAccount().getId(),
            permission.getId(),
            permission.getCode(),
            permission.getName(),
            permission.getModule(),
            permissionOverride.getEffect(),
            permissionOverride.getEffectiveFrom(),
            permissionOverride.getEffectiveTo(),
            permissionOverride.getReason(),
            grantedBy.getId(),
            grantedBy.getUsername(),
            permissionOverride.getCreatedAt(),
            revokedBy == null ? null : revokedBy.getId(),
            revokedBy == null ? null : revokedBy.getUsername(),
            permissionOverride.getRevokedAt(),
            permissionOverride.getRevocationReason(),
            status
        );
    }
}
