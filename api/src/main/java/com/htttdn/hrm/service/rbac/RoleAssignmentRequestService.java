package com.htttdn.hrm.service.rbac;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.htttdn.hrm.service.account.CompanyOwnerAccountGuard;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.request.account.AssignAccountRoleRequest;
import com.htttdn.hrm.dto.request.roleassignment.ApproveRoleAssignmentRequest;
import com.htttdn.hrm.dto.request.roleassignment.CancelRoleAssignmentRequest;
import com.htttdn.hrm.dto.request.roleassignment.CreateRoleAssignmentRequest;
import com.htttdn.hrm.dto.request.roleassignment.CreateRoleRevocationRequest;
import com.htttdn.hrm.dto.request.roleassignment.RejectRoleAssignmentRequest;
import com.htttdn.hrm.dto.response.account.AccountRoleAssignmentResponse;
import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.dto.response.roleassignment.RoleAssignmentRequestResponse;
import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.AccountRoleAssignment;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.RoleAssignmentRequest;
import com.htttdn.hrm.entity.enums.RoleAssignmentRequestStatus;
import com.htttdn.hrm.entity.enums.RoleAssignmentRequestType;
import com.htttdn.hrm.entity.enums.RoleGrantPolicy;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.exception.ForbiddenException;
import com.htttdn.hrm.repository.AccountRepository;
import com.htttdn.hrm.repository.AccountRoleAssignmentRepository;
import com.htttdn.hrm.repository.RoleAssignmentRequestRepository;
import com.htttdn.hrm.security.CurrentAccountProvider;
import com.htttdn.hrm.util.Strings;

import lombok.RequiredArgsConstructor;

/**
 * Luồng đề nghị cấp hoặc thu hồi role, dành cho người có {@code role.assignment.request} (thường là HR).
 *
 * <p>Cách xử lý theo chính sách cấp của role:
 * <ul>
 *   <li>{@code HR_ASSIGNABLE}: yêu cầu được thực hiện ngay và ghi {@code APPROVED} với ghi chú tự duyệt —
 *       yêu cầu chỉ để lưu vết ai cấp/thu hồi, khi nào, vì sao.</li>
 *   <li>{@code OWNER_APPROVAL}: yêu cầu ở {@code PENDING} chờ người có {@code role.assignment.approve}
 *       (Company Owner) duyệt hoặc từ chối; người tạo có thể hủy khi còn chờ.</li>
 * </ul>
 * Người đề nghị chỉ được đề nghị cho nhân viên và đơn vị/địa điểm trong phạm vi của mình
 * ({@link EmployeeAccessScopeService}); việc gán/thu hồi thật do {@link AccountRoleAssignmentCommandService}
 * làm, trong cùng transaction.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class RoleAssignmentRequestService {

    private static final String REQUEST_PERMISSION = "role.assignment.request";
    private static final String APPROVE_PERMISSION = "role.assignment.approve";
    private static final String AUTO_APPROVAL_NOTE =
        "Tự động phê duyệt theo chính sách cấp role HR_ASSIGNABLE";

    private final RoleAssignmentRequestRepository requestRepository;
    private final AccountRepository accountRepository;
    private final AccountRoleAssignmentRepository roleAssignmentRepository;
    private final AccountRoleAssignmentCommandService roleAssignmentCommandService;
    private final RoleAssignmentRequestMapper requestMapper;
    private final AccountRoleAssignmentMapper roleAssignmentMapper;
    private final EmployeeAccessScopeService employeeAccessScopeService;
    private final CurrentAccountProvider currentAccountProvider;
    private final CompanyOwnerAccountGuard companyOwnerAccountGuard;
    private final Clock clock;

    /**
     * Đề nghị cấp role cho một tài khoản.
     *
     * <p>Thứ tự: kiểm tra như khi gán thật, gồm cả ngày vào làm ({@link AccountRoleAssignmentCommandService#prepareRequest})
     * → nhân viên và đơn vị/địa điểm đích trong phạm vi người đề nghị → lưu {@code PENDING} → nếu role
     * {@code HR_ASSIGNABLE} thì gán luôn và chuyển {@code APPROVED}.
     *
     * @param request tài khoản, role, phạm vi, thời hạn, lý do
     * @return yêu cầu vừa tạo ({@code PENDING} hoặc đã {@code APPROVED} kèm lần gán)
     * @throws com.htttdn.hrm.exception.ForbiddenException đề nghị cho chính tài khoản mình, bị
     *         {@code CompanyOwnerAccountGuard} chặn, hoặc nhân viên / đích ngoài phạm vi (→ 403)
     * @throws BusinessException bắt đầu trước ngày vào làm, phạm vi/thời hạn sai (→ 400 {@code VALIDATION_ERROR})
     * @throws ConflictException role không cấp được qua yêu cầu, trùng lần gán, hoặc đã có yêu cầu chờ chồng thời
     *         gian (→ 409 {@code ROLE_ASSIGNMENT_REQUEST_EXISTS}…)
     */
    @PreAuthorize("hasAuthority('role.assignment.request')")
    public RoleAssignmentRequestResponse create(CreateRoleAssignmentRequest request) {
        // Role HR_ASSIGNABLE được duyệt ngay, nên tự đề nghị cho mình là tự cấp quyền; ngoại lệ quyền cũng cấm như vậy.
        if (request.accountId().equals(currentAccountProvider.accountId())) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN, "Roles cannot be requested for your own account");
        }
        // Như createRevocation: không đổi role của Company Owner/System Admin nếu người đề nghị không cùng vai trò đó.
        companyOwnerAccountGuard.requireCanManage(request.accountId());
        AssignAccountRoleRequest assignmentRequest = toAssignmentRequest(request);
        AccountRoleAssignmentCommandService.RequestCandidate candidate =
            roleAssignmentCommandService.prepareRequest(request.accountId(), assignmentRequest);

        // prepareRequest đã bảo đảm tài khoản gắn nhân viên, nên employee không null.
        Employee employee = candidate.account().getEmployee();
        employeeAccessScopeService.requireEmployeeAccess(employee.getId(), REQUEST_PERMISSION);
        employeeAccessScopeService.requireDestinationAccess(
            request.organizationUnitId(),
            request.workLocationId(),
            REQUEST_PERMISSION
        );

        Account actor = currentAccountProvider.account();
        Instant now = Instant.now(clock);
        RoleAssignmentRequest roleRequest = RoleAssignmentRequest.builder()
            .account(candidate.account())
            .role(candidate.role())
            .scopeType(request.scopeType())
            .organizationUnit(candidate.organizationUnit())
            .workLocation(candidate.workLocation())
            .effectiveFrom(request.effectiveFrom())
            .effectiveTo(request.effectiveTo())
            .reason(request.reason().trim())
            .status(RoleAssignmentRequestStatus.PENDING)
            .requestedByAccount(actor)
            .requestedAt(now)
            .build();

        // Lưu PENDING trước rồi mới gán: assignHrAssignableRequest yêu cầu một yêu cầu PENDING đã lưu, và
        // constraint chống yêu cầu trùng được kiểm tra ngay ở bước lưu này.
        savePending(roleRequest);
        if (candidate.role().getGrantPolicy() == RoleGrantPolicy.HR_ASSIGNABLE) {
            roleRequest.setAccountRoleAssignment(
                roleAssignmentCommandService.assignHrAssignableRequest(roleRequest, actor));
            markReviewed(roleRequest, RoleAssignmentRequestStatus.APPROVED, actor, now, AUTO_APPROVAL_NOTE);
        }
        return requestMapper.toResponse(roleRequest);
    }

    /**
     * The assignments of an account that a revocation request may target: not revoked, not ended and of
     * a role the workflow manages. Lets a requester without account.read pick what to take back.
     *
     * @param accountId tài khoản cần xem
     * @return lần gán có thể đề nghị thu hồi; rỗng nếu tài khoản không gắn nhân viên
     * @throws com.htttdn.hrm.exception.ResourceNotFoundException tài khoản không có (→ 404 {@code ACCOUNT_NOT_FOUND})
     * @throws com.htttdn.hrm.exception.ForbiddenException nhân viên ngoài phạm vi (→ 403)
     */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('role.assignment.request')")
    public List<AccountRoleAssignmentResponse> listRevocableAssignments(Long accountId) {
        Employee employee = accountRepository.getRequired(accountId).getEmployee();
        if (employee == null) {
            return List.of();
        }
        employeeAccessScopeService.requireEmployeeAccess(employee.getId(), REQUEST_PERMISSION);
        LocalDate today = LocalDate.now(clock);
        return roleAssignmentRepository.findByAccountIdAndRevokedAtIsNull(accountId).stream()
            .filter(assignment -> assignment.getEffectiveTo() == null || !assignment.getEffectiveTo().isBefore(today))
            .filter(assignment -> assignment.getRole().getGrantPolicy() == RoleGrantPolicy.HR_ASSIGNABLE
                || assignment.getRole().getGrantPolicy() == RoleGrantPolicy.OWNER_APPROVAL)
            .map(roleAssignmentMapper::toResponse)
            .toList();
    }

    /**
     * Asks to take back one role assignment. Like a grant, an HR_ASSIGNABLE role is revoked at once and
     * the request is recorded as approved; an OWNER_APPROVAL role waits for the Company Owner.
     *
     * @param request lần gán cần thu hồi và lý do
     * @return yêu cầu thu hồi ({@code PENDING}, hoặc {@code APPROVED} nếu đã thu hồi ngay)
     * @throws com.htttdn.hrm.exception.ResourceNotFoundException lần gán không có (→ 404 {@code ROLE_ASSIGNMENT_NOT_FOUND})
     * @throws com.htttdn.hrm.exception.ForbiddenException bị {@code CompanyOwnerAccountGuard} chặn, hoặc nhân viên /
     *         đơn vị / địa điểm của lần gán ngoài phạm vi (→ 403)
     * @throws ConflictException role {@code AUTO}/{@code SYSTEM_ONLY}, lần gán đã thu hồi/hết hạn, tài khoản không
     *         gắn nhân viên (→ 409), hoặc đã có yêu cầu thu hồi đang chờ (→ 409 {@code ROLE_ASSIGNMENT_REQUEST_EXISTS})
     */
    @PreAuthorize("hasAuthority('role.assignment.request')")
    public RoleAssignmentRequestResponse createRevocation(CreateRoleRevocationRequest request) {
        AccountRoleAssignment target = roleAssignmentRepository.getRequiredForUpdate(request.assignmentId());
        Account account = target.getAccount();
        // Không cho đề nghị lấy role của Company Owner/System Admin nếu người đề nghị không cùng vai trò đó.
        companyOwnerAccountGuard.requireCanManage(account.getId());
        roleAssignmentCommandService.validateRevocable(target.getRole());
        requireStillHeld(target);
        Employee employee = account.getEmployee();
        if (employee == null) {
            throw new ConflictException(
                ErrorCode.ROLE_ASSIGNMENT_NOT_ALLOWED,
                "Business roles require an account linked to an employee"
            );
        }
        employeeAccessScopeService.requireEmployeeAccess(employee.getId(), REQUEST_PERMISSION);
        // Phạm vi của lần gán bị thu hồi cũng phải nằm trong phạm vi người đề nghị: người đề nghị chỉ có phạm vi
        // một chi nhánh không thu hồi được lần gán MANAGER ở chi nhánh khác của cùng nhân viên.
        employeeAccessScopeService.requireDestinationAccess(
            target.getOrganizationUnit() == null ? null : target.getOrganizationUnit().getId(),
            target.getWorkLocation() == null ? null : target.getWorkLocation().getId(),
            REQUEST_PERMISSION
        );

        Account actor = currentAccountProvider.account();
        Instant now = Instant.now(clock);
        RoleAssignmentRequest roleRequest = RoleAssignmentRequest.builder()
            .requestType(RoleAssignmentRequestType.REVOKE)
            .targetAssignment(target)
            .account(account)
            .role(target.getRole())
            .scopeType(target.getScopeType())
            .organizationUnit(target.getOrganizationUnit())
            .workLocation(target.getWorkLocation())
            .effectiveFrom(target.getEffectiveFrom())
            .effectiveTo(target.getEffectiveTo())
            .reason(request.reason().trim())
            .status(RoleAssignmentRequestStatus.PENDING)
            .requestedByAccount(actor)
            .requestedAt(now)
            .build();

        savePending(roleRequest);
        if (target.getRole().getGrantPolicy() == RoleGrantPolicy.HR_ASSIGNABLE) {
            roleAssignmentCommandService.revoke(target, actor, roleRequest.getReason());
            markReviewed(roleRequest, RoleAssignmentRequestStatus.APPROVED, actor, now, AUTO_APPROVAL_NOTE);
        }
        return requestMapper.toResponse(roleRequest);
    }

    /**
     * Danh sách yêu cầu: người duyệt thấy mọi yêu cầu, người đề nghị chỉ thấy yêu cầu do mình tạo.
     *
     * @param status lọc theo trạng thái; {@code null} = mọi trạng thái
     * @param pageable phân trang và sắp xếp
     * @return một trang yêu cầu
     */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyAuthority('role.assignment.request', 'role.assignment.approve')")
    public Page<RoleAssignmentRequestResponse> list(
        RoleAssignmentRequestStatus status,
        Pageable pageable
    ) {
        Long actorAccountId = currentAccountProvider.accountId();
        Page<RoleAssignmentRequest> requests;
        // hasAuthority đọc authority từ JWT hiện tại (không truy vấn database). Không lọc theo phạm vi: người
        // duyệt (Company Owner) có quyền trên toàn công ty.
        if (currentAccountProvider.hasAuthority(APPROVE_PERMISSION)) {
            requests = status == null
                ? requestRepository.findAll(pageable)
                : requestRepository.findByStatus(status, pageable);
        } else {
            requests = status == null
                ? requestRepository.findByRequestedByAccountId(actorAccountId, pageable)
                : requestRepository.findByRequestedByAccountIdAndStatus(
                    actorAccountId,
                    status,
                    pageable
                );
        }
        return requests.map(requestMapper::toResponse);
    }

    /**
     * Chi tiết một yêu cầu, cùng quy tắc xem như {@link #list}.
     *
     * @throws com.htttdn.hrm.exception.ResourceNotFoundException không có yêu cầu
     *         (→ 404 {@code ROLE_ASSIGNMENT_REQUEST_NOT_FOUND})
     * @throws ForbiddenException không phải người tạo và không có quyền duyệt (→ 403)
     */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyAuthority('role.assignment.request', 'role.assignment.approve')")
    public RoleAssignmentRequestResponse getById(Long requestId) {
        Long actorAccountId = currentAccountProvider.accountId();
        RoleAssignmentRequest roleRequest = requestRepository.getRequired(requestId);
        requireCanView(roleRequest, actorAccountId);
        return requestMapper.toResponse(roleRequest);
    }

    /**
     * Duyệt một yêu cầu đang chờ: thực hiện cấp hoặc thu hồi role rồi ghi {@code APPROVED}.
     *
     * <p>Với yêu cầu cấp, mọi điều kiện gán được kiểm tra lại tại lúc duyệt
     * ({@link AccountRoleAssignmentCommandService#assignOwnerApprovedRequest}).
     *
     * @param requestId yêu cầu cần duyệt
     * @param request   ghi chú duyệt (không bắt buộc)
     * @return yêu cầu sau khi duyệt
     * @throws com.htttdn.hrm.exception.ResourceNotFoundException không có yêu cầu (→ 404)
     * @throws ConflictException yêu cầu đã xử lý (→ 409 {@code ROLE_ASSIGNMENT_REQUEST_ALREADY_PROCESSED}),
     *         lần gán cần thu hồi không còn, hoặc điều kiện gán không còn thỏa (→ 409)
     */
    @PreAuthorize("hasAuthority('role.assignment.approve')")
    public RoleAssignmentRequestResponse approve(
        Long requestId,
        ApproveRoleAssignmentRequest request
    ) {
        // Khóa ghi yêu cầu: hai người duyệt/từ chối cùng lúc → người sau thấy không còn PENDING.
        RoleAssignmentRequest roleRequest = requestRepository.getRequiredForUpdate(requestId);
        requirePending(roleRequest);

        Account actor = currentAccountProvider.account();
        if (roleRequest.getRequestType() == RoleAssignmentRequestType.REVOKE) {
            // Recheck the target: it may have been revoked or have expired while the request waited.
            AccountRoleAssignment target =
                roleAssignmentRepository.getRequiredForUpdate(roleRequest.getTargetAssignment().getId());
            requireStillHeld(target);
            roleAssignmentCommandService.revoke(target, actor, roleRequest.getReason());
        } else {
            roleRequest.setAccountRoleAssignment(
                roleAssignmentCommandService.assignOwnerApprovedRequest(roleRequest, actor));
        }
        markReviewed(roleRequest, RoleAssignmentRequestStatus.APPROVED, actor, Instant.now(clock),
            Strings.trimToNull(request.note()));
        return requestMapper.toResponse(roleRequest);
    }

    /**
     * Từ chối một yêu cầu đang chờ; không gán hay thu hồi gì.
     *
     * @throws com.htttdn.hrm.exception.ResourceNotFoundException không có yêu cầu (→ 404)
     * @throws ConflictException yêu cầu đã xử lý (→ 409 {@code ROLE_ASSIGNMENT_REQUEST_ALREADY_PROCESSED})
     */
    @PreAuthorize("hasAuthority('role.assignment.approve')")
    public RoleAssignmentRequestResponse reject(
        Long requestId,
        RejectRoleAssignmentRequest request
    ) {
        RoleAssignmentRequest roleRequest = requestRepository.getRequiredForUpdate(requestId);
        requirePending(roleRequest);

        markReviewed(roleRequest, RoleAssignmentRequestStatus.REJECTED, currentAccountProvider.account(),
            Instant.now(clock), request.note().trim());
        return requestMapper.toResponse(roleRequest);
    }

    /**
     * Người tạo hủy yêu cầu của mình khi còn chờ duyệt.
     *
     * @throws com.htttdn.hrm.exception.ResourceNotFoundException không có yêu cầu (→ 404)
     * @throws ConflictException yêu cầu đã xử lý (→ 409 {@code ROLE_ASSIGNMENT_REQUEST_ALREADY_PROCESSED})
     * @throws ForbiddenException không phải người tạo (→ 403)
     */
    @PreAuthorize("hasAuthority('role.assignment.request')")
    public RoleAssignmentRequestResponse cancel(
        Long requestId,
        CancelRoleAssignmentRequest request
    ) {
        Long actorAccountId = currentAccountProvider.accountId();
        RoleAssignmentRequest roleRequest = requestRepository.getRequiredForUpdate(requestId);
        requirePending(roleRequest);
        if (!roleRequest.getRequestedByAccount().getId().equals(actorAccountId)) {
            throw new ForbiddenException(
                ErrorCode.FORBIDDEN,
                "Only the account that created the request can cancel it"
            );
        }

        Account actor = currentAccountProvider.account();
        Instant now = Instant.now(clock);
        roleRequest.setStatus(RoleAssignmentRequestStatus.CANCELLED);
        roleRequest.setCancelledByAccount(actor);
        roleRequest.setCancelledAt(now);
        roleRequest.setCancellationReason(request.reason().trim());
        requestRepository.saveAndFlush(roleRequest);
        return requestMapper.toResponse(roleRequest);
    }

    private AssignAccountRoleRequest toAssignmentRequest(CreateRoleAssignmentRequest request) {
        return new AssignAccountRoleRequest(
            request.roleCode(),
            request.scopeType(),
            request.organizationUnitId(),
            request.workLocationId(),
            request.effectiveFrom(),
            request.effectiveTo(),
            request.reason()
        );
    }

    /**
     * Lưu yêu cầu {@code PENDING}, để database chặn trùng thay vì kiểm tra trước (tránh race giữa hai request):
     * {@code excl_pending_role_assignment_request_overlap} cho yêu cầu cấp chồng thời gian,
     * {@code uq_role_assignment_requests_pending_revoke} cho hai yêu cầu thu hồi cùng một lần gán.
     *
     * @throws ConflictException vi phạm một trong hai ràng buộc (→ 409 {@code ROLE_ASSIGNMENT_REQUEST_EXISTS})
     */
    private void savePending(RoleAssignmentRequest roleRequest) {
        try {
            requestRepository.saveAndFlush(roleRequest);
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException(
                ErrorCode.ROLE_ASSIGNMENT_REQUEST_EXISTS,
                roleRequest.getRequestType() == RoleAssignmentRequestType.REVOKE
                    ? "A pending revocation request for this role assignment already exists"
                    : "An overlapping pending role assignment request already exists"
            );
        }
    }

    /** Ghi kết quả xét duyệt (duyệt hoặc từ chối) của một yêu cầu đang chờ và lưu ngay. */
    private void markReviewed(
        RoleAssignmentRequest roleRequest,
        RoleAssignmentRequestStatus status,
        Account reviewer,
        Instant reviewedAt,
        String note
    ) {
        roleRequest.setStatus(status);
        roleRequest.setReviewedByAccount(reviewer);
        roleRequest.setReviewedAt(reviewedAt);
        roleRequest.setReviewNote(note);
        requestRepository.saveAndFlush(roleRequest);
    }

    /** Only an assignment that is not revoked and has not ended can be revoked by a request. */
    private void requireStillHeld(AccountRoleAssignment assignment) {
        if (assignment.getRevokedAt() != null
            || (assignment.getEffectiveTo() != null && assignment.getEffectiveTo().isBefore(LocalDate.now(clock)))) {
            throw new ConflictException(
                ErrorCode.ROLE_ASSIGNMENT_NOT_ALLOWED,
                "Role assignment is already revoked or has ended"
            );
        }
    }

    /** Người tạo yêu cầu hoặc người có quyền duyệt mới xem được (→ 403 nếu không). */
    private void requireCanView(RoleAssignmentRequest roleRequest, Long actorAccountId) {
        if (roleRequest.getRequestedByAccount().getId().equals(actorAccountId)
            || currentAccountProvider.hasAuthority(APPROVE_PERMISSION)) {
            return;
        }
        throw new ForbiddenException(ErrorCode.FORBIDDEN, "Role assignment request is outside your access");
    }

    private void requirePending(RoleAssignmentRequest request) {
        if (request.getStatus() != RoleAssignmentRequestStatus.PENDING) {
            throw new ConflictException(
                ErrorCode.ROLE_ASSIGNMENT_REQUEST_ALREADY_PROCESSED,
                "Role assignment request has already been processed"
            );
        }
    }
}
