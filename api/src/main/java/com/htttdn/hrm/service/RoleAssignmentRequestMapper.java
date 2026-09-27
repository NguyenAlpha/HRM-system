package com.htttdn.hrm.service;

import org.springframework.stereotype.Component;

import com.htttdn.hrm.dto.response.roleassignment.RoleAssignmentRequestResponse;
import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.RoleAssignmentRequest;

@Component
public class RoleAssignmentRequestMapper {

    private final AccountRoleAssignmentMapper roleAssignmentMapper;

    public RoleAssignmentRequestMapper(AccountRoleAssignmentMapper roleAssignmentMapper) {
        this.roleAssignmentMapper = roleAssignmentMapper;
    }

    public RoleAssignmentRequestResponse toResponse(RoleAssignmentRequest request) {
        Employee employee = request.getAccount().getEmployee();
        Account reviewer = request.getReviewedByAccount();
        Account canceller = request.getCancelledByAccount();
        return new RoleAssignmentRequestResponse(
            request.getId(),
            request.getAccount().getId(),
            employee == null ? null : employee.getId(),
            employee == null ? null : employee.getEmployeeCode(),
            employee == null ? null : employee.getFullName(),
            request.getRole().getId(),
            request.getRole().getCode(),
            request.getRole().getName(),
            request.getRole().getGrantPolicy(),
            request.getScopeType(),
            request.getOrganizationUnit() == null ? null : request.getOrganizationUnit().getId(),
            request.getOrganizationUnit() == null ? null : request.getOrganizationUnit().getName(),
            request.getWorkLocation() == null ? null : request.getWorkLocation().getId(),
            request.getWorkLocation() == null ? null : request.getWorkLocation().getName(),
            request.getEffectiveFrom(),
            request.getEffectiveTo(),
            request.getReason(),
            request.getStatus(),
            request.getRequestedByAccount().getId(),
            request.getRequestedByAccount().getUsername(),
            request.getRequestedAt(),
            reviewer == null ? null : reviewer.getId(),
            reviewer == null ? null : reviewer.getUsername(),
            request.getReviewedAt(),
            request.getReviewNote(),
            canceller == null ? null : canceller.getId(),
            canceller == null ? null : canceller.getUsername(),
            request.getCancelledAt(),
            request.getCancellationReason(),
            request.getUpdatedAt(),
            request.getAccountRoleAssignment() == null
                ? null
                : roleAssignmentMapper.toResponse(request.getAccountRoleAssignment())
        );
    }
}
