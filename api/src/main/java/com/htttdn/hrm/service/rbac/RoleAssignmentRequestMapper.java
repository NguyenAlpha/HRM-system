package com.htttdn.hrm.service.rbac;

import org.springframework.stereotype.Component;

import com.htttdn.hrm.dto.response.roleassignment.RoleAssignmentRequestResponse;
import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.RoleAssignmentRequest;

import lombok.RequiredArgsConstructor;

/** Đổi {@link RoleAssignmentRequest} thành response, kèm thông tin nhân viên và người xử lý cho màn danh sách. */
@Component
@RequiredArgsConstructor
public class RoleAssignmentRequestMapper {

    private final AccountRoleAssignmentMapper roleAssignmentMapper;

    /**
     * Các quan hệ ({@code account.employee}, role, người duyệt…) được đọc lazy; các query danh sách của
     * {@code RoleAssignmentRequestRepository} đã {@code @EntityGraph} chúng để tránh N+1.
     *
     * @return response; {@code accountRoleAssignment} chỉ có khi yêu cầu cấp đã được duyệt,
     *         {@code targetAssignment} chỉ có ở yêu cầu thu hồi
     */
    public RoleAssignmentRequestResponse toResponse(RoleAssignmentRequest request) {
        Employee employee = request.getAccount().getEmployee();
        Account reviewer = request.getReviewedByAccount();
        Account canceller = request.getCancelledByAccount();
        return new RoleAssignmentRequestResponse(
            request.getId(),
            request.getRequestType(),
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
                : roleAssignmentMapper.toResponse(request.getAccountRoleAssignment()),
            request.getTargetAssignment() == null
                ? null
                : roleAssignmentMapper.toResponse(request.getTargetAssignment())
        );
    }
}
