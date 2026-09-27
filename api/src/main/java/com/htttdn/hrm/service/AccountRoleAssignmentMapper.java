package com.htttdn.hrm.service;

import org.springframework.stereotype.Component;

import com.htttdn.hrm.dto.response.account.AccountRoleAssignmentResponse;
import com.htttdn.hrm.entity.AccountRoleAssignment;

@Component
public class AccountRoleAssignmentMapper {

    public AccountRoleAssignmentResponse toResponse(AccountRoleAssignment assignment) {
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
}
