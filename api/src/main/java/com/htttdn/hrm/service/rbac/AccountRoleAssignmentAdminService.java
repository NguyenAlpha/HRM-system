package com.htttdn.hrm.service.rbac;

import java.util.List;

import com.htttdn.hrm.service.account.CompanyOwnerAccountGuard;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.request.account.AssignAccountRoleRequest;
import com.htttdn.hrm.dto.request.account.RevokeAccountRoleRequest;
import com.htttdn.hrm.dto.response.account.AccountRoleAssignmentResponse;
import com.htttdn.hrm.entity.AccountRoleAssignment;
import com.htttdn.hrm.repository.AccountRepository;
import com.htttdn.hrm.repository.AccountRoleAssignmentRepository;
import com.htttdn.hrm.security.CurrentAccountProvider;

import lombok.RequiredArgsConstructor;

/**
 * Quản trị viên xem, gán và thu hồi role của một tài khoản trực tiếp, không qua yêu cầu.
 *
 * <p>Khác {@link RoleAssignmentRequestService} (người đề nghị → có thể cần Company Owner duyệt): ở đây role
 * {@code OWNER_APPROVAL} cũng được gán ngay, và không xét phạm vi nhân viên của người gán. Việc ghi thật nằm
 * ở {@link AccountRoleAssignmentCommandService}.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class AccountRoleAssignmentAdminService {

    private final AccountRepository accountRepository;
    private final AccountRoleAssignmentRepository roleAssignmentRepository;
    private final AccountRoleAssignmentCommandService roleAssignmentCommandService;
    private final AccountRoleAssignmentMapper roleAssignmentMapper;
    private final CurrentAccountProvider currentAccountProvider;
    private final CompanyOwnerAccountGuard companyOwnerAccountGuard;

    /**
     * Toàn bộ lịch sử gán role của tài khoản (kể cả đã thu hồi, đã hết hạn), mới nhất trước.
     *
     * @param accountId tài khoản cần xem
     * @return danh sách lần gán; rỗng nếu chưa từng có
     * @throws com.htttdn.hrm.exception.ResourceNotFoundException tài khoản không tồn tại (→ 404 {@code ACCOUNT_NOT_FOUND})
     */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('account.read')")
    public List<AccountRoleAssignmentResponse> list(Long accountId) {
        // Chỉ để trả 404 khi tài khoản không tồn tại, thay vì danh sách rỗng.
        accountRepository.getRequired(accountId);
        return roleAssignmentRepository.findByAccountIdOrderByCreatedAtDesc(accountId).stream()
            .map(roleAssignmentMapper::toResponse)
            .toList();
    }

    /**
     * Gán role cho tài khoản ngay lập tức.
     *
     * @param accountId tài khoản nhận role
     * @param request   role, phạm vi, thời gian hiệu lực, lý do
     * @return lần gán vừa tạo
     * @throws com.htttdn.hrm.exception.ForbiddenException tài khoản đích là Company Owner/System Admin mà người
     *         gán không cùng vai trò đó (→ 403, xem {@code CompanyOwnerAccountGuard})
     * @throws com.htttdn.hrm.exception.ConflictException các lỗi của
     *         {@link AccountRoleAssignmentCommandService#assignDirect}
     */
    @PreAuthorize("hasAuthority('account.role.assign')")
    public AccountRoleAssignmentResponse assign(Long accountId, AssignAccountRoleRequest request) {
        // Chặn chiếm quyền: chỉ Company Owner đổi quyền của Company Owner, chỉ System Admin đổi của System Admin.
        companyOwnerAccountGuard.requireCanManage(accountId);
        AccountRoleAssignment assignment = roleAssignmentCommandService.assignDirect(
            accountId,
            request,
            currentAccountProvider.account()
        );
        return roleAssignmentMapper.toResponse(assignment);
    }

    /**
     * Thu hồi một lần gán role của tài khoản; lần gán được giữ lại làm lịch sử
     * (xem {@link AccountRoleAssignmentCommandService#revoke}).
     *
     * @param accountId    tài khoản sở hữu lần gán
     * @param assignmentId lần gán cần thu hồi
     * @param request      lý do thu hồi
     * @return lần gán sau khi thu hồi
     * @throws com.htttdn.hrm.exception.ForbiddenException như {@link #assign} (→ 403)
     * @throws com.htttdn.hrm.exception.ResourceNotFoundException lần gán không tồn tại hoặc thuộc tài khoản
     *         khác (→ 404 {@code ROLE_ASSIGNMENT_NOT_FOUND})
     * @throws com.htttdn.hrm.exception.ConflictException role {@code AUTO}/{@code SYSTEM_ONLY} (→ 409)
     */
    @PreAuthorize("hasAuthority('account.role.assign')")
    public AccountRoleAssignmentResponse revoke(
        Long accountId,
        Long assignmentId,
        RevokeAccountRoleRequest request
    ) {
        companyOwnerAccountGuard.requireCanManage(accountId);
        // Khóa ghi lần gán, và chỉ tìm trong tài khoản accountId để không thu hồi nhầm lần gán của người khác.
        AccountRoleAssignment assignment = roleAssignmentRepository.getRequiredForUpdate(assignmentId, accountId);
        roleAssignmentCommandService.revoke(
            assignment,
            currentAccountProvider.account(),
            request.reason()
        );
        return roleAssignmentMapper.toResponse(assignment);
    }
}
