package com.htttdn.hrm.service.rbac;

import org.springframework.stereotype.Component;

import com.htttdn.hrm.dto.response.account.AccountRoleAssignmentResponse;
import com.htttdn.hrm.entity.AccountRoleAssignment;

/** Đổi {@link AccountRoleAssignment} thành response; dùng chung cho quản trị, yêu cầu cấp role và khởi tạo công ty. */
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
            assignment.organizationUnitId(),
            assignment.workLocationId(),
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
