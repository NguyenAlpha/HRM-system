package com.htttdn.hrm.service.organization;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.request.organization.TransferCompanyOwnerRequest;
import com.htttdn.hrm.dto.response.account.AccountRoleAssignmentResponse;
import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.repository.AccountRepository;
import com.htttdn.hrm.repository.ResignationRequestRepository;
import com.htttdn.hrm.security.CurrentAccountProvider;
import com.htttdn.hrm.service.rbac.AccountRoleAssignmentCommandService;
import com.htttdn.hrm.service.rbac.AccountRoleAssignmentMapper;

import lombok.RequiredArgsConstructor;

/**
 * Chuyển giao Company Owner: Owner hiện tại trao {@code COMPANY_OWNER} cho một nhân viên khác, có hiệu lực
 * ngay. Chỉ role này chuyển đi; Owner cũ giữ nguyên các role khác (VD {@code DIRECTOR}, đổi riêng qua thu hồi
 * và gán). Người duyệt đơn và người duyệt đề xuất role được tính lại theo người giữ role, nên đổi theo ngay.
 *
 * <p>Owner xác nhận bằng mật khẩu của chính mình, vì thao tác không hoàn tác được nếu người nhận không trả lại.
 * Lịch sử nằm ở hai lần gán: lần cũ bị thu hồi và lần mới được cấp, cùng người thực hiện và lý do.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class CompanyOwnerTransferService {

    private final AccountRoleAssignmentCommandService roleAssignmentCommandService;
    private final AccountRoleAssignmentMapper roleAssignmentMapper;
    private final CurrentAccountProvider currentAccountProvider;
    private final PasswordEncoder passwordEncoder;
    private final AccountRepository accountRepository;
    private final ResignationRequestRepository resignationRequestRepository;

    /**
     * @param request tài khoản nhận, mật khẩu của Owner hiện tại, lý do
     * @return lần gán {@code COMPANY_OWNER} mới của người nhận
     * @throws BusinessException chuyển cho chính mình (→ 400 {@code VALIDATION_ERROR}, field {@code accountId})
     *         hoặc sai mật khẩu (→ 400 {@code INVALID_CREDENTIALS}, field {@code password})
     * @throws com.htttdn.hrm.exception.ForbiddenException người gọi không còn giữ {@code COMPANY_OWNER} (→ 403)
     * @throws com.htttdn.hrm.exception.ConflictException người nhận không dùng được (→ 409, xem
     *         {@link AccountRoleAssignmentCommandService#transferCompanyOwner}), hoặc đang có đơn nghỉ việc chờ
     *         duyệt / đã duyệt chưa tới ngày nghỉ (→ 409 {@code ROLE_ASSIGNMENT_NOT_ALLOWED})
     */
    @PreAuthorize("hasRole('COMPANY_OWNER')")
    public AccountRoleAssignmentResponse transfer(TransferCompanyOwnerRequest request) {
        Account owner = currentAccountProvider.account();
        if (request.accountId().equals(owner.getId())) {
            throw new BusinessException(
                ErrorCode.VALIDATION_ERROR,
                "Company ownership must go to another account",
                "accountId"
            );
        }
        // 400 chứ không phải 401: phiên vẫn hợp lệ, chỉ mật khẩu xác nhận sai.
        if (owner.getPasswordHash() == null || !passwordEncoder.matches(request.password(), owner.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "Password is incorrect", "password");
        }
        // Đơn còn mở sẽ kết thúc việc làm của Owner mới, mà việc đó bị chặn khi họ giữ COMPANY_OWNER.
        Employee recipient = accountRepository.findById(request.accountId()).map(Account::getEmployee).orElse(null);
        if (recipient != null && resignationRequestRepository.existsOpen(recipient.getId())) {
            throw new ConflictException(
                ErrorCode.ROLE_ASSIGNMENT_NOT_ALLOWED,
                "Company ownership cannot go to an employee with an open resignation request"
            );
        }
        return roleAssignmentMapper.toResponse(
            roleAssignmentCommandService.transferCompanyOwner(owner, request.accountId(), request.reason()));
    }
}
