package com.htttdn.hrm.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.request.account.AssignAccountRoleRequest;
import com.htttdn.hrm.dto.request.account.RevokeAccountRoleRequest;
import com.htttdn.hrm.dto.response.account.AccountRoleAssignmentResponse;
import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.AccountRoleAssignment;
import com.htttdn.hrm.entity.Role;
import com.htttdn.hrm.entity.enums.RoleGrantPolicy;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.exception.ResourceNotFoundException;
import com.htttdn.hrm.repository.AccountRepository;
import com.htttdn.hrm.repository.AccountRoleAssignmentRepository;
import com.htttdn.hrm.security.CurrentAccountProvider;

@Service
@Transactional
public class AccountRoleAssignmentAdminService {

    private final AccountRepository accountRepository;
    private final AccountRoleAssignmentRepository roleAssignmentRepository;
    private final AccountRoleAssignmentCommandService roleAssignmentCommandService;
    private final AccountRoleAssignmentMapper roleAssignmentMapper;
    private final RefreshTokenService refreshTokenService;
    private final CurrentAccountProvider currentAccountProvider;

    public AccountRoleAssignmentAdminService(
        AccountRepository accountRepository,
        AccountRoleAssignmentRepository roleAssignmentRepository,
        AccountRoleAssignmentCommandService roleAssignmentCommandService,
        AccountRoleAssignmentMapper roleAssignmentMapper,
        RefreshTokenService refreshTokenService,
        CurrentAccountProvider currentAccountProvider
    ) {
        this.accountRepository = accountRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.roleAssignmentCommandService = roleAssignmentCommandService;
        this.roleAssignmentMapper = roleAssignmentMapper;
        this.refreshTokenService = refreshTokenService;
        this.currentAccountProvider = currentAccountProvider;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('account.read')")
    public List<AccountRoleAssignmentResponse> list(Long accountId) {
        findAccount(accountId);
        return roleAssignmentRepository.findByAccountIdOrderByCreatedAtDesc(accountId).stream()
            .map(roleAssignmentMapper::toResponse)
            .toList();
    }

    @PreAuthorize("hasAuthority('account.role.assign')")
    public AccountRoleAssignmentResponse assign(Long accountId, AssignAccountRoleRequest request) {
        AccountRoleAssignment assignment = roleAssignmentCommandService.assignDirect(
            accountId,
            request,
            currentAccountProvider.accountId()
        );
        return roleAssignmentMapper.toResponse(assignment);
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
        validateRoleCanBeRevoked(assignment.getRole());
        if (assignment.getRevokedAt() != null) {
            return roleAssignmentMapper.toResponse(assignment);
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
        return roleAssignmentMapper.toResponse(assignment);
    }

    private void validateRoleCanBeRevoked(Role role) {
        if (role.getGrantPolicy() == RoleGrantPolicy.AUTO) {
            throw new ConflictException(
                ErrorCode.ROLE_ASSIGNMENT_NOT_ALLOWED,
                "Automatically assigned roles cannot be revoked through the general role assignment API"
            );
        }
        if (role.getGrantPolicy() == RoleGrantPolicy.SYSTEM_ONLY) {
            throw new ConflictException(
                ErrorCode.ROLE_ASSIGNMENT_NOT_ALLOWED,
                "System-only roles must be managed through their dedicated workflow"
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
}
