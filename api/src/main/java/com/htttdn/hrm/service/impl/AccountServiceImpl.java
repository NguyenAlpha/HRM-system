package com.htttdn.hrm.service.impl;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.request.account.AssignRoleRequest;
import com.htttdn.hrm.dto.response.account.AccountRoleAssignmentResponse;
import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.AccountRoleAssignment;
import com.htttdn.hrm.entity.OrganizationUnit;
import com.htttdn.hrm.entity.Role;
import com.htttdn.hrm.entity.WorkLocation;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.exception.ResourceNotFoundException;
import com.htttdn.hrm.repository.AccountRepository;
import com.htttdn.hrm.repository.AccountRoleAssignmentRepository;
import com.htttdn.hrm.repository.OrganizationUnitRepository;
import com.htttdn.hrm.repository.RoleRepository;
import com.htttdn.hrm.repository.WorkLocationRepository;
import com.htttdn.hrm.service.AccountService;

@Service
@Transactional
public class AccountServiceImpl implements AccountService {

    private final AccountRepository accountRepository;
    private final RoleRepository roleRepository;
    private final AccountRoleAssignmentRepository accountRoleAssignmentRepository;
    private final OrganizationUnitRepository organizationUnitRepository;
    private final WorkLocationRepository workLocationRepository;

    public AccountServiceImpl(
        AccountRepository accountRepository,
        RoleRepository roleRepository,
        AccountRoleAssignmentRepository accountRoleAssignmentRepository,
        OrganizationUnitRepository organizationUnitRepository,
        WorkLocationRepository workLocationRepository
    ) {
        this.accountRepository = accountRepository;
        this.roleRepository = roleRepository;
        this.accountRoleAssignmentRepository = accountRoleAssignmentRepository;
        this.organizationUnitRepository = organizationUnitRepository;
        this.workLocationRepository = workLocationRepository;
    }

    @Override
    public AccountRoleAssignmentResponse assignRole(Long accountId, AssignRoleRequest request) {
        Account account = findAccountOrThrow(accountId);
        Role role = roleRepository.findById(request.roleId())
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.ROLE_NOT_FOUND, "Role not found: " + request.roleId()));

        OrganizationUnit organizationUnit = null;
        WorkLocation workLocation = null;

        switch (request.scopeType()) {
            case SELF, COMPANY -> {
                if (request.organizationUnitId() != null || request.workLocationId() != null) {
                    throw new BusinessException(
                        ErrorCode.VALIDATION_ERROR,
                        "organizationUnitId/workLocationId must be null for scope " + request.scopeType(),
                        "scopeType"
                    );
                }
            }
            case ORG_UNIT -> {
                if (request.organizationUnitId() == null || request.workLocationId() != null) {
                    throw new BusinessException(
                        ErrorCode.VALIDATION_ERROR,
                        "organizationUnitId is required and workLocationId must be null for scope ORG_UNIT",
                        "organizationUnitId"
                    );
                }
                organizationUnit = organizationUnitRepository.findById(request.organizationUnitId())
                    .filter(unit -> unit.getDeletedAt() == null)
                    .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.ORGANIZATION_UNIT_NOT_FOUND,
                        "Organization unit not found: " + request.organizationUnitId()));
            }
            case LOCATION -> {
                if (request.workLocationId() == null || request.organizationUnitId() != null) {
                    throw new BusinessException(
                        ErrorCode.VALIDATION_ERROR,
                        "workLocationId is required and organizationUnitId must be null for scope LOCATION",
                        "workLocationId"
                    );
                }
                workLocation = workLocationRepository.findById(request.workLocationId())
                    .filter(location -> location.getDeletedAt() == null)
                    .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.LOCATION_NOT_FOUND, "Work location not found: " + request.workLocationId()));
            }
        }

        Account grantedBy = findAccountOrThrow(request.grantedByAccountId());

        AccountRoleAssignment assignment = AccountRoleAssignment.builder()
            .account(account)
            .role(role)
            .scopeType(request.scopeType())
            .organizationUnit(organizationUnit)
            .workLocation(workLocation)
            .effectiveFrom(request.effectiveFrom())
            .effectiveTo(request.effectiveTo())
            .grantedByAccount(grantedBy)
            .reason(request.reason())
            .createdAt(Instant.now())
            .build();

        return toResponse(accountRoleAssignmentRepository.save(assignment));
    }

    @Override
    public void revokeRole(Long assignmentId) {
        AccountRoleAssignment assignment = accountRoleAssignmentRepository.findById(assignmentId)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.RESOURCE_NOT_FOUND, "Role assignment not found: " + assignmentId));
        assignment.setEffectiveTo(java.time.LocalDate.now());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountRoleAssignmentResponse> listRoleAssignments(Long accountId) {
        findAccountOrThrow(accountId);
        return accountRoleAssignmentRepository.findByAccountId(accountId).stream()
            .map(this::toResponse)
            .toList();
    }

    private Account findAccountOrThrow(Long id) {
        return accountRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.RESOURCE_NOT_FOUND, "Account not found: " + id));
    }

    private AccountRoleAssignmentResponse toResponse(AccountRoleAssignment assignment) {
        return new AccountRoleAssignmentResponse(
            assignment.getId(),
            assignment.getAccount().getId(),
            assignment.getRole().getId(),
            assignment.getRole().getCode(),
            assignment.getScopeType(),
            assignment.getOrganizationUnit() != null ? assignment.getOrganizationUnit().getId() : null,
            assignment.getWorkLocation() != null ? assignment.getWorkLocation().getId() : null,
            assignment.getEffectiveFrom(),
            assignment.getEffectiveTo()
        );
    }
}
