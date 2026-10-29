package com.htttdn.hrm.service.rbac;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.htttdn.hrm.service.auth.RefreshTokenService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.request.account.AssignAccountRoleRequest;
import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.AccountRoleAssignment;
import com.htttdn.hrm.entity.OrganizationUnit;
import com.htttdn.hrm.entity.Role;
import com.htttdn.hrm.entity.RoleAssignmentRequest;
import com.htttdn.hrm.entity.WorkLocation;
import com.htttdn.hrm.entity.enums.AccountStatus;
import com.htttdn.hrm.entity.enums.EmploymentStatus;
import com.htttdn.hrm.entity.enums.RoleAssignmentRequestStatus;
import com.htttdn.hrm.entity.enums.RoleGrantPolicy;
import com.htttdn.hrm.entity.enums.RoleScopeType;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.exception.ForbiddenException;
import com.htttdn.hrm.exception.ResourceNotFoundException;
import com.htttdn.hrm.repository.AccountRepository;
import com.htttdn.hrm.repository.AccountRoleAssignmentRepository;
import com.htttdn.hrm.repository.OrganizationUnitRepository;
import com.htttdn.hrm.repository.RoleAssignmentRequestRepository;
import com.htttdn.hrm.repository.RoleRepository;
import com.htttdn.hrm.repository.WorkLocationRepository;

import lombok.RequiredArgsConstructor;

/**
 * Internal command component for creating and revoking effective role assignments.
 * Authorization remains at the public workflow boundary; every entry point here
 * enforces the grant policy of the workflow that invoked it.
 *
 * <p>Nơi duy nhất ghi vào {@code account_role_assignments}. Ba luồng gọi vào đây, mỗi luồng ứng với một
 * {@link AssignmentFlow} và chỉ được cấp role có {@link RoleGrantPolicy} tương ứng
 * (xem {@link #validateGrantPolicy}):
 * <ul>
 *   <li>Gán trực tiếp bởi quản trị: {@link AccountRoleAssignmentAdminService} → {@link #assignDirect}</li>
 *   <li>Yêu cầu cấp role: {@link RoleAssignmentRequestService} → {@link #prepareRequest}, rồi
 *       {@link #assignHrAssignableRequest} (tự duyệt) hoặc {@link #assignOwnerApprovedRequest} (Owner duyệt)</li>
 *   <li>Khởi tạo công ty: {@code OrganizationCompanyOwnerProvisioningService} →
 *       {@link #assignProvisionedCompanyRole} cho {@code COMPANY_OWNER} và {@code DIRECTOR}</li>
 *   <li>Chuyển giao Company Owner: {@code CompanyOwnerTransferService} → {@link #transferCompanyOwner}</li>
 *   <li>Bổ nhiệm Giám đốc mới: {@link AccountRoleAssignmentAdminService} → {@link #appointDirector}</li>
 * </ul>
 *
 * <p>{@code Propagation.MANDATORY}: mọi method phải được gọi bên trong transaction của service gọi tới
 * (nếu không sẽ ném {@code IllegalTransactionStateException}), để việc gán role và việc ghi yêu cầu/hồ sơ
 * đi kèm cùng commit hoặc cùng rollback.
 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
@RequiredArgsConstructor
public class AccountRoleAssignmentCommandService {

    private static final Set<String> PROVISIONED_COMPANY_ROLES = Set.of(
        SystemRoles.COMPANY_OWNER,
        SystemRoles.DIRECTOR
    );

    private final AccountRepository accountRepository;
    private final RoleRepository roleRepository;
    private final AccountRoleAssignmentRepository roleAssignmentRepository;
    private final RoleAssignmentRequestRepository requestRepository;
    private final OrganizationUnitRepository organizationUnitRepository;
    private final WorkLocationRepository workLocationRepository;
    private final RoleAssignmentPolicy roleAssignmentPolicy;
    private final RefreshTokenService refreshTokenService;
    private final Clock clock;

    /**
     * Revokes one assignment and keeps it as history: it stops counting at once, a period that has
     * started closes today and the account's refresh tokens are revoked. An assignment that is already
     * revoked is left as it is.
     *
     * <p>Lần gán chưa bắt đầu (effectiveFrom sau hôm nay) giữ nguyên khoảng hiệu lực; chỉ revokedAt khiến nó
     * không bao giờ có hiệu lực. Access token đã cấp vẫn mang role/permission cũ tới khi hết hạn, nhưng phạm vi
     * ({@link EmployeeAccessScopeService}) đọc database nên mất ngay.
     *
     * @param assignment lần gán cần thu hồi (nên đã khóa ghi bởi nơi gọi)
     * @param actor      người thu hồi, ghi vào revokedByAccount
     * @param reason     lý do thu hồi
     * @throws ConflictException role {@code AUTO}/{@code SYSTEM_ONLY} (→ 409 {@code ROLE_ASSIGNMENT_NOT_ALLOWED})
     */
    public void revoke(AccountRoleAssignment assignment, Account actor, String reason) {
        validateRevocable(assignment.getRole());
        // Thu hồi hai lần không lỗi: lần sau không đổi gì (idempotent).
        if (assignment.getRevokedAt() != null) {
            return;
        }
        close(assignment, LocalDate.now(clock), actor, reason);
        // Thu hồi mọi refresh token → người bị thu hồi phải đăng nhập lại, token mới không còn role này.
        refreshTokenService.revokeAll(assignment.getAccount().getId());
    }

    /**
     * Revokes the business roles an account still holds on {@code endDate}, when the employee leaves or the
     * account is deleted, so they no longer count as held (a DIRECTOR assignment would otherwise block
     * appointing the next director). Roles that already ended before that day stay as history. AUTO and
     * SYSTEM_ONLY roles are left to their own workflows, as {@link #revoke} refuses them. Requests still
     * waiting for the account are cancelled with the same reason: approving them would fail on the disabled
     * account or the revoked assignment.
     *
     * <p>Không thu hồi refresh token: nơi gọi đã khóa cả tài khoản nên tự làm việc đó.
     *
     * @param accountId tài khoản bị thu hồi role
     * @param endDate   ngày cuối còn giữ role (ngày nghỉ việc / ngày xóa tài khoản)
     * @param actor     người thực hiện, ghi vào revokedByAccount
     * @param reason    lý do thu hồi
     */
    public void revokeHeldRoles(Long accountId, LocalDate endDate, Account actor, String reason) {
        revokeStillRunning(roleAssignmentRepository.findByAccountIdAndRevokedAtIsNull(accountId), endDate, actor, reason);
        cancelPendingRequests(
            requestRepository.findByAccountIdAndStatusForUpdate(accountId, RoleAssignmentRequestStatus.PENDING),
            actor,
            reason
        );
    }

    /**
     * Revokes today the assignments of a custom role being deleted and cancels the requests waiting for it,
     * so the deleted role neither shows as held nor blocks deleting its unit or location. Assignments that
     * already ended stay as history, as in {@link #revokeHeldRoles}.
     *
     * <p>Không thu hồi refresh token: như khi đổi permission của role, người giữ mất quyền ở access token kế tiếp.
     *
     * @param role   role tự tạo đang bị xóa mềm
     * @param actor  người xóa, ghi vào revokedByAccount / cancelledByAccount
     * @param reason lý do thu hồi và hủy
     */
    public void revokeDeletedRole(Role role, Account actor, String reason) {
        revokeStillRunning(
            roleAssignmentRepository.findByRoleIdAndRevokedAtIsNull(role.getId()), LocalDate.now(clock), actor, reason);
        cancelPendingRequests(
            requestRepository.findByRoleIdAndStatusForUpdate(role.getId(), RoleAssignmentRequestStatus.PENDING),
            actor,
            reason
        );
    }

    /** Thu hồi các lần gán còn chạy tới {@code endDate}, bỏ qua role {@code AUTO}/{@code SYSTEM_ONLY} và lần gán đã kết thúc. */
    private void revokeStillRunning(
        List<AccountRoleAssignment> assignments,
        LocalDate endDate,
        Account actor,
        String reason
    ) {
        for (AccountRoleAssignment assignment : assignments) {
            RoleGrantPolicy policy = assignment.getRole().getGrantPolicy();
            if (policy == RoleGrantPolicy.AUTO || policy == RoleGrantPolicy.SYSTEM_ONLY) {
                continue;
            }
            if (assignment.getEffectiveTo() != null && assignment.getEffectiveTo().isBefore(endDate)) {
                continue;
            }
            close(assignment, endDate, actor, reason);
        }
    }

    /** Hủy các yêu cầu đang chờ không còn duyệt được; người hủy và lý do như khi người tạo tự hủy. */
    private void cancelPendingRequests(List<RoleAssignmentRequest> requests, Account actor, String reason) {
        Instant now = Instant.now(clock);
        for (RoleAssignmentRequest request : requests) {
            request.setStatus(RoleAssignmentRequestStatus.CANCELLED);
            request.setCancelledByAccount(actor);
            request.setCancelledAt(now);
            request.setCancellationReason(reason.trim());
        }
    }

    /** Đánh dấu thu hồi; khoảng hiệu lực đã bắt đầu và còn chạy qua {@code endDate} thì kết thúc tại {@code endDate}. */
    private void close(AccountRoleAssignment assignment, LocalDate endDate, Account actor, String reason) {
        assignment.setRevokedByAccount(actor);
        assignment.setRevokedAt(Instant.now(clock));
        assignment.setRevocationReason(reason.trim());
        if (!assignment.getEffectiveFrom().isAfter(endDate)
            && (assignment.getEffectiveTo() == null || assignment.getEffectiveTo().isAfter(endDate))) {
            assignment.setEffectiveTo(endDate);
        }
    }

    /**
     * AUTO and SYSTEM_ONLY roles are managed by their own workflows, never by a revocation.
     *
     * @throws ConflictException role không thu hồi được (→ 409 {@code ROLE_ASSIGNMENT_NOT_ALLOWED})
     */
    public void validateRevocable(Role role) {
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

    /**
     * Gán role ngay, không qua yêu cầu: cho role {@code HR_ASSIGNABLE} hoặc {@code OWNER_APPROVAL}.
     *
     * @param accountId tài khoản nhận role
     * @param request   role, phạm vi, thời gian hiệu lực và lý do
     * @param actor     người gán, ghi vào grantedByAccount
     * @return lần gán vừa lưu
     * @throws ConflictException / BusinessException / ResourceNotFoundException xem {@link #validateCandidate}
     */
    public AccountRoleAssignment assignDirect(
        Long accountId,
        AssignAccountRoleRequest request,
        Account actor
    ) {
        return assignInternal(accountId, request, actor, AssignmentFlow.DIRECT);
    }

    /**
     * Kiểm tra trước một yêu cầu cấp role (cùng bộ kiểm tra như khi gán thật) nhưng chưa ghi gì, để từ chối
     * yêu cầu sai ngay lúc tạo thay vì lúc duyệt.
     *
     * @param accountId tài khoản được đề nghị cấp role
     * @param request   role, phạm vi, thời gian hiệu lực và lý do
     * @return tài khoản, role và đơn vị/địa điểm đã nạp, để {@link RoleAssignmentRequestService} dựng yêu cầu
     * @throws ConflictException / BusinessException / ResourceNotFoundException xem {@link #validateCandidate}
     */
    public RequestCandidate prepareRequest(Long accountId, AssignAccountRoleRequest request) {
        AssignmentCandidate candidate = validateCandidate(
            accountId,
            request,
            AssignmentFlow.REQUEST_SUBMISSION
        );
        return new RequestCandidate(
            candidate.account(),
            candidate.role(),
            candidate.scopeTarget().organizationUnit(),
            candidate.scopeTarget().workLocation()
        );
    }

    /**
     * Gán {@code COMPANY_OWNER} hoặc {@code DIRECTOR} phạm vi {@code COMPANY}, không thời hạn, lúc khởi tạo
     * công ty ({@code OrganizationCompanyOwnerProvisioningService}). Hai role này không đi qua luồng gán/yêu cầu
     * thông thường.
     *
     * @param accountId     tài khoản vừa cấp cho chủ doanh nghiệp
     * @param roleCode      {@code COMPANY_OWNER} hoặc {@code DIRECTOR}
     * @param effectiveFrom ngày bắt đầu
     * @param reason        lý do (quyết định sở hữu / bổ nhiệm)
     * @param actor         người khởi tạo
     * @return lần gán vừa lưu
     * @throws IllegalArgumentException roleCode khác hai role trên — lỗi lập trình, không đến từ người dùng
     * @throws ConflictException đã có Company Owner (→ 409 {@code COMPANY_OWNER_ALREADY_EXISTS}) hoặc đã có
     *         Director trùng thời gian (→ 409 {@code DIRECTOR_ALREADY_ASSIGNED})
     */
    public AccountRoleAssignment assignProvisionedCompanyRole(
        Long accountId,
        String roleCode,
        LocalDate effectiveFrom,
        String reason,
        Account actor
    ) {
        if (!PROVISIONED_COMPANY_ROLES.contains(roleCode)) {
            throw new IllegalArgumentException("Unsupported provisioned company role: " + roleCode);
        }
        AssignAccountRoleRequest request = new AssignAccountRoleRequest(
            roleCode,
            RoleScopeType.COMPANY,
            null,
            null,
            effectiveFrom,
            null,
            reason
        );
        return assignInternal(accountId, request, actor, AssignmentFlow.BOOTSTRAP);
    }

    /**
     * Moves {@code COMPANY_OWNER} from the current owner to another account, effective today: the owner's
     * assignment is revoked and closed today, a new one starts today for the recipient, and the owner's
     * refresh tokens are revoked so their next token no longer carries the role. Other roles stay as they are.
     *
     * <p>Người nhận phải là tài khoản {@code ACTIVE} (đăng nhập được) của nhân viên đang làm việc và chưa có lịch
     * nghỉ, vì công ty không được rơi vào cảnh có Owner mà không ai dùng được. Khóa role {@code COMPANY_OWNER}
     * trước, nên hai lần chuyển giao cùng lúc chạy lần lượt và lần sau thấy người gọi không còn là Owner.
     *
     * @param owner              Owner hiện tại, người thực hiện
     * @param recipientAccountId tài khoản nhận
     * @param reason             lý do, ghi vào cả lần thu hồi và lần gán mới
     * @return lần gán {@code COMPANY_OWNER} mới
     * @throws ForbiddenException người gọi không còn giữ {@code COMPANY_OWNER} (→ 403)
     * @throws ResourceNotFoundException tài khoản nhận không có (→ 404 {@code ACCOUNT_NOT_FOUND})
     * @throws ConflictException người nhận không {@code ACTIVE}, không gắn nhân viên hoặc nhân viên không còn
     *         làm việc / sắp nghỉ (→ 409 {@code ROLE_ASSIGNMENT_NOT_ALLOWED})
     */
    public AccountRoleAssignment transferCompanyOwner(Account owner, Long recipientAccountId, String reason) {
        Role ownerRole = roleRepository.findByCodeForUpdate(SystemRoles.COMPANY_OWNER)
            .orElseThrow(() -> new IllegalStateException("COMPANY_OWNER role is missing"));
        AccountRoleAssignment current = roleAssignmentRepository.findByRoleIdAndRevokedAtIsNull(ownerRole.getId()).stream()
            .filter(assignment -> assignment.getAccount().getId().equals(owner.getId()))
            .findFirst()
            // Token vẫn mang ROLE_COMPANY_OWNER tới khi hết hạn dù đã chuyển giao, nên kiểm tra lại ở database.
            .orElseThrow(() -> new ForbiddenException(
                ErrorCode.FORBIDDEN,
                "Only the current Company Owner can transfer company ownership"
            ));
        validateTransferRecipient(accountRepository.getRequired(recipientAccountId));

        LocalDate today = LocalDate.now(clock);
        close(current, today, owner, reason);
        refreshTokenService.revokeAll(owner.getId());
        AssignAccountRoleRequest request = new AssignAccountRoleRequest(
            SystemRoles.COMPANY_OWNER,
            RoleScopeType.COMPANY,
            null,
            null,
            today,
            null,
            reason
        );
        return assignInternal(recipientAccountId, request, owner, AssignmentFlow.OWNER_TRANSFER);
    }

    /**
     * Appoints the next Director from {@code handoverDate} in one step: the current Director's assignment
     * ends the day before and a new one starts on that day, so the one-Director rule holds throughout.
     * The current assignment is shortened, not revoked, because revoking would end it at once and the
     * handover may lie ahead. A handover today revokes the current Director's refresh tokens.
     *
     * <p>Lần gán mới đi qua cùng bộ kiểm tra như gán trực tiếp ({@link #validateCandidate}), nên người nhận
     * phải có tài khoản dùng được và ngày bàn giao không trước ngày vào làm.
     *
     * @param accountId    tài khoản được bổ nhiệm
     * @param handoverDate ngày Giám đốc mới bắt đầu, không trước hôm nay
     * @param actor        người bổ nhiệm, ghi vào grantedByAccount
     * @param reason       lý do bổ nhiệm
     * @return lần gán {@code DIRECTOR} mới
     * @throws BusinessException ngày bàn giao đã qua (field {@code handoverDate}) hoặc người nhận đã là Giám đốc
     *         lúc đó (field {@code accountId}) (→ 400 {@code VALIDATION_ERROR}), hoặc các lỗi của {@link #validateCandidate}
     * @throws ConflictException đã có lần bổ nhiệm khác bắt đầu từ ngày bàn giao trở đi
     *         (→ 409 {@code DIRECTOR_ALREADY_ASSIGNED})
     */
    public AccountRoleAssignment appointDirector(Long accountId, LocalDate handoverDate, Account actor, String reason) {
        LocalDate today = LocalDate.now(clock);
        if (handoverDate.isBefore(today)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "handoverDate must not be in the past", "handoverDate");
        }
        // Khóa role trước khi đọc các lần gán: hai lần bổ nhiệm cùng lúc chạy lần lượt.
        Role directorRole = roleRepository.findByCodeForUpdate(SystemRoles.DIRECTOR)
            .orElseThrow(() -> new IllegalStateException("DIRECTOR role is missing"));
        for (AccountRoleAssignment current : roleAssignmentRepository.findByRoleIdAndRevokedAtIsNull(directorRole.getId())) {
            if (current.getEffectiveTo() != null && current.getEffectiveTo().isBefore(handoverDate)) {
                continue;
            }
            if (current.getAccount().getId().equals(accountId)) {
                throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR, "The account is already the Director on the handover date", "accountId");
            }
            // Một lần bổ nhiệm đã lên lịch từ ngày đó trở đi không bị thay âm thầm; thu hồi nó trước.
            if (!current.getEffectiveFrom().isBefore(handoverDate)) {
                throw new ConflictException(
                    ErrorCode.DIRECTOR_ALREADY_ASSIGNED,
                    "Another Director appointment already starts on or after the handover date"
                );
            }
            current.setEffectiveTo(handoverDate.minusDays(1));
            if (!handoverDate.isAfter(today)) {
                refreshTokenService.revokeAll(current.getAccount().getId());
            }
        }
        AssignAccountRoleRequest request = new AssignAccountRoleRequest(
            SystemRoles.DIRECTOR,
            RoleScopeType.COMPANY,
            null,
            null,
            handoverDate,
            null,
            reason
        );
        return assignInternal(accountId, request, actor, AssignmentFlow.DIRECT);
    }

    /** Người nhận Company Owner phải đăng nhập được và là nhân viên đang làm, không có lịch nghỉ (→ 409). */
    private void validateTransferRecipient(Account recipient) {
        Employee employee = recipient.getEmployee();
        boolean working = employee != null
            && employee.getDeletedAt() == null
            && (employee.getEmploymentStatus() == EmploymentStatus.ACTIVE
                || employee.getEmploymentStatus() == EmploymentStatus.PROBATION)
            && employee.getScheduledEndStatus() == null;
        if (recipient.getStatus() != AccountStatus.ACTIVE || !working) {
            throw new ConflictException(
                ErrorCode.ROLE_ASSIGNMENT_NOT_ALLOWED,
                "Company ownership goes only to an active account of an employee who is still working"
            );
        }
    }

    /**
     * Thực hiện yêu cầu cấp role {@code HR_ASSIGNABLE}: được gọi ngay khi tạo yêu cầu (tự duyệt).
     *
     * @param roleRequest yêu cầu đang {@code PENDING}, đã lưu
     * @param actor       người tạo yêu cầu, cũng là người gán
     * @return lần gán vừa lưu
     * @throws ConflictException yêu cầu không còn PENDING hoặc đã có lần gán (→ 409 {@code CONFLICT}), role không
     *         phải HR_ASSIGNABLE, hoặc các lỗi của {@link #validateCandidate}
     */
    public AccountRoleAssignment assignHrAssignableRequest(
        RoleAssignmentRequest roleRequest,
        Account actor
    ) {
        return assignRequestedRole(roleRequest, actor, AssignmentFlow.HR_ASSIGNABLE_REQUEST);
    }

    /**
     * Thực hiện yêu cầu cấp role {@code OWNER_APPROVAL} khi Company Owner duyệt. Mọi điều kiện được kiểm tra
     * lại tại thời điểm duyệt, vì dữ liệu có thể đã đổi từ lúc tạo yêu cầu.
     *
     * @param roleRequest yêu cầu đang {@code PENDING}
     * @param actor       người duyệt, ghi vào grantedByAccount
     * @return lần gán vừa lưu
     * @throws ConflictException như {@link #assignHrAssignableRequest}
     */
    public AccountRoleAssignment assignOwnerApprovedRequest(
        RoleAssignmentRequest roleRequest,
        Account actor
    ) {
        return assignRequestedRole(roleRequest, actor, AssignmentFlow.OWNER_APPROVED_REQUEST);
    }

    private AccountRoleAssignment assignRequestedRole(
        RoleAssignmentRequest roleRequest,
        Account actor,
        AssignmentFlow flow
    ) {
        if (roleRequest.getStatus() != RoleAssignmentRequestStatus.PENDING
            || roleRequest.getAccountRoleAssignment() != null) {
            throw new ConflictException(
                ErrorCode.CONFLICT,
                "Only a pending role assignment request without an assignment can be processed"
            );
        }
        // Dựng lại request từ yêu cầu đã lưu để đi chung đường kiểm tra với gán trực tiếp.
        AssignAccountRoleRequest request = new AssignAccountRoleRequest(
            roleRequest.getRole().getCode(),
            roleRequest.getScopeType(),
            roleRequest.organizationUnitId(),
            roleRequest.workLocationId(),
            roleRequest.getEffectiveFrom(),
            roleRequest.getEffectiveTo(),
            roleRequest.getReason()
        );
        return assignInternal(roleRequest.getAccount().getId(), request, actor, flow);
    }

    private AccountRoleAssignment assignInternal(
        Long accountId,
        AssignAccountRoleRequest request,
        Account actor,
        AssignmentFlow flow
    ) {
        AssignmentCandidate candidate = validateCandidate(accountId, request, flow);

        return roleAssignmentRepository.save(AccountRoleAssignment.builder()
            .account(candidate.account())
            .role(candidate.role())
            .scopeType(request.scopeType())
            .organizationUnit(candidate.scopeTarget().organizationUnit())
            .workLocation(candidate.scopeTarget().workLocation())
            .effectiveFrom(request.effectiveFrom())
            .effectiveTo(request.effectiveTo())
            .grantedByAccount(actor)
            .reason(request.reason().trim())
            .build());
    }

    /**
     * Mọi kiểm tra trước khi gán một role, dùng chung cho mọi luồng.
     *
     * <p>Thứ tự: khóa tài khoản → tài khoản còn dùng được và gắn nhân viên → khóa role → role được phép
     * cấp qua luồng này → phạm vi hợp lệ với role ({@link RoleAssignmentPolicy#validateScope}) → khoảng hiệu
     * lực (không trước ngày vào làm) → đơn vị/địa điểm khớp loại phạm vi và đang hoạt động → không trùng lần gán cũ → ràng buộc riêng của
     * {@code DIRECTOR} (một người tại một thời điểm) và {@code COMPANY_OWNER} (chỉ một).
     *
     * @throws ResourceNotFoundException tài khoản / role / đơn vị / địa điểm không tồn tại (→ 404)
     * @throws ConflictException tài khoản bị khóa hoặc chưa gắn nhân viên, role không cấp được qua luồng này,
     *         trùng lần gán, trùng Director/Owner (→ 409)
     * @throws BusinessException phạm vi hoặc khoảng hiệu lực sai, bắt đầu trước ngày vào làm
     *         (→ 400 {@code VALIDATION_ERROR})
     */
    private AssignmentCandidate validateCandidate(
        Long accountId,
        AssignAccountRoleRequest request,
        AssignmentFlow flow
    ) {
        // Khóa ghi (SELECT … FOR UPDATE) tài khoản và role: hai request gán đồng thời cho cùng tài khoản hoặc
        // cùng role (VD hai Director) phải chờ nhau, nên các kiểm tra trùng bên dưới không bị race. Bảng
        // account_role_assignments không có unique/exclusion constraint chống trùng, chỉ dựa vào khóa này.
        Account account = accountRepository.getRequiredForUpdate(accountId);
        validateTargetAccount(account);

        // findByCodeForUpdate chỉ lấy role chưa xóa mềm.
        Role role = roleRepository.findByCodeForUpdate(request.roleCode())
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.ROLE_NOT_FOUND,
                "Role not found: " + request.roleCode()
            ));
        validateGrantPolicy(role, flow);
        roleAssignmentPolicy.validateScope(role, request.scopeType());
        validatePeriod(account.getEmployee().getHireDate(), request.effectiveFrom(), request.effectiveTo());

        ScopeTarget scopeTarget = resolveScopeTarget(request);
        ensureNoDuplicateAssignment(account, role, request, scopeTarget);
        if (SystemRoles.DIRECTOR.equals(role.getCode())) {
            ensureSingleDirector(role, request.effectiveFrom(), request.effectiveTo());
        }
        if (SystemRoles.COMPANY_OWNER.equals(role.getCode())) {
            ensureCompanyOwnerDoesNotExist(role);
        }
        return new AssignmentCandidate(account, role, scopeTarget);
    }

    /** Tài khoản bị vô hiệu hóa hoặc chưa gắn nhân viên không nhận role (→ 409 {@code ROLE_ASSIGNMENT_NOT_ALLOWED}). */
    private void validateTargetAccount(Account account) {
        if (account.getStatus() == AccountStatus.DISABLED) {
            throw new ConflictException(
                ErrorCode.ROLE_ASSIGNMENT_NOT_ALLOWED,
                "Roles cannot be assigned to a disabled account"
            );
        }
        if (account.getEmployee() == null) {
            throw new ConflictException(
                ErrorCode.ROLE_ASSIGNMENT_NOT_ALLOWED,
                "Business roles require an account linked to an employee"
            );
        }
    }

    /**
     * Luồng gọi có được cấp role với chính sách này không. {@code AUTO} (role nền tự gán khi cấp tài khoản)
     * và {@code SYSTEM_ONLY} không luồng nào ở đây cấp được, trừ {@code COMPANY_OWNER}/{@code DIRECTOR} qua
     * {@code BOOTSTRAP}, và {@code COMPANY_OWNER} qua {@code OWNER_TRANSFER}.
     *
     * @throws ConflictException không được phép (→ 409 {@code ROLE_ASSIGNMENT_NOT_ALLOWED})
     */
    private void validateGrantPolicy(Role role, AssignmentFlow flow) {
        RoleGrantPolicy policy = role.getGrantPolicy();
        boolean allowed = switch (flow) {
            case DIRECT -> policy == RoleGrantPolicy.HR_ASSIGNABLE
                || policy == RoleGrantPolicy.OWNER_APPROVAL;
            case BOOTSTRAP -> PROVISIONED_COMPANY_ROLES.contains(role.getCode());
            case OWNER_TRANSFER -> SystemRoles.COMPANY_OWNER.equals(role.getCode());
            case REQUEST_SUBMISSION -> policy == RoleGrantPolicy.HR_ASSIGNABLE
                || policy == RoleGrantPolicy.OWNER_APPROVAL;
            case HR_ASSIGNABLE_REQUEST -> policy == RoleGrantPolicy.HR_ASSIGNABLE;
            case OWNER_APPROVED_REQUEST -> policy == RoleGrantPolicy.OWNER_APPROVAL;
        };
        if (!allowed) {
            throw new ConflictException(
                ErrorCode.ROLE_ASSIGNMENT_NOT_ALLOWED,
                "Role " + role.getCode() + " cannot be assigned through " + flow
            );
        }
    }

    /**
     * Lần gán không bắt đầu trước ngày nhân viên vào làm, và không kết thúc trước khi bắt đầu
     * (→ 400 {@code VALIDATION_ERROR}).
     */
    private void validatePeriod(LocalDate hireDate, LocalDate effectiveFrom, LocalDate effectiveTo) {
        if (effectiveFrom.isBefore(hireDate)) {
            throw new BusinessException(
                ErrorCode.VALIDATION_ERROR,
                "effectiveFrom must not be before employee hireDate",
                "effectiveFrom"
            );
        }
        if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw new BusinessException(
                ErrorCode.VALIDATION_ERROR,
                "effectiveTo must be on or after effectiveFrom",
                "effectiveTo"
            );
        }
    }

    /**
     * Nạp đơn vị/địa điểm theo loại phạm vi, đúng quy tắc của ràng buộc {@code chk_account_role_assignments_scope}:
     * {@code SELF}/{@code COMPANY} không có đích, {@code ORG_UNIT} chỉ có đơn vị, {@code LOCATION} chỉ có địa điểm.
     * Kiểm tra trước ở đây để trả 400 có tên field thay vì lỗi constraint.
     *
     * @throws BusinessException thiếu hoặc thừa id (→ 400 {@code VALIDATION_ERROR})
     * @throws ResourceNotFoundException đơn vị/địa điểm không tồn tại, đã xóa hoặc đã tắt (→ 404)
     */
    private ScopeTarget resolveScopeTarget(AssignAccountRoleRequest request) {
        return switch (request.scopeType()) {
            case SELF, COMPANY -> {
                if (request.organizationUnitId() != null || request.workLocationId() != null) {
                    throw invalidScope(
                        "organizationUnitId and workLocationId must be null for " + request.scopeType(),
                        "scopeType"
                    );
                }
                yield new ScopeTarget(null, null);
            }
            case ORG_UNIT -> {
                if (request.organizationUnitId() == null || request.workLocationId() != null) {
                    throw invalidScope(
                        "organizationUnitId is required and workLocationId must be null for ORG_UNIT",
                        "organizationUnitId"
                    );
                }
                OrganizationUnit unit = organizationUnitRepository.getRequiredActive(request.organizationUnitId());
                yield new ScopeTarget(unit, null);
            }
            case LOCATION -> {
                if (request.workLocationId() == null || request.organizationUnitId() != null) {
                    throw invalidScope(
                        "workLocationId is required and organizationUnitId must be null for LOCATION",
                        "workLocationId"
                    );
                }
                WorkLocation location = workLocationRepository.getRequiredActive(request.workLocationId());
                yield new ScopeTarget(null, location);
            }
        };
    }

    /**
     * Không cho hai lần gán chưa thu hồi cùng tài khoản, role, loại phạm vi và đích mà thời gian chồng nhau.
     * Cùng role nhưng khác phạm vi (VD {@code MANAGER} ở hai đơn vị) thì được.
     *
     * @throws ConflictException đã có lần gán chồng (→ 409 {@code ROLE_ASSIGNMENT_EXISTS})
     */
    private void ensureNoDuplicateAssignment(
        Account account,
        Role role,
        AssignAccountRoleRequest request,
        ScopeTarget scopeTarget
    ) {
        boolean duplicate = roleAssignmentRepository
            .findByAccountIdAndRoleIdAndRevokedAtIsNull(account.getId(), role.getId()).stream()
            .filter(existing -> existing.getScopeType() == request.scopeType())
            .filter(existing -> sameTarget(existing, scopeTarget))
            .anyMatch(existing -> periodsOverlap(
                existing.getEffectiveFrom(),
                existing.getEffectiveTo(),
                request.effectiveFrom(),
                request.effectiveTo()
            ));
        if (duplicate) {
            throw new ConflictException(
                ErrorCode.ROLE_ASSIGNMENT_EXISTS,
                "An overlapping role assignment already exists for this account and scope"
            );
        }
    }

    /**
     * Công ty chỉ có một Director tại mỗi thời điểm: chặn lần gán chồng thời gian với Director hiện có
     * (→ 409 {@code DIRECTOR_ALREADY_ASSIGNED}, mã riêng vì người đang giữ thường là tài khoản khác).
     */
    private void ensureSingleDirector(Role role, LocalDate effectiveFrom, LocalDate effectiveTo) {
        boolean conflict = roleAssignmentRepository.findByRoleIdAndRevokedAtIsNull(role.getId()).stream()
            .anyMatch(existing -> periodsOverlap(
                existing.getEffectiveFrom(),
                existing.getEffectiveTo(),
                effectiveFrom,
                effectiveTo
            ));
        if (conflict) {
            throw new ConflictException(
                ErrorCode.DIRECTOR_ALREADY_ASSIGNED,
                "A DIRECTOR assignment already exists for the requested period"
            );
        }
    }

    /** Chỉ một Company Owner: còn lần gán chưa thu hồi nào (kể cả đã hết hạn) là chặn (→ 409). */
    private void ensureCompanyOwnerDoesNotExist(Role role) {
        if (!roleAssignmentRepository.findByRoleIdAndRevokedAtIsNull(role.getId()).isEmpty()) {
            throw new ConflictException(
                ErrorCode.COMPANY_OWNER_ALREADY_EXISTS,
                "A COMPANY_OWNER assignment already exists"
            );
        }
    }

    private boolean sameTarget(AccountRoleAssignment assignment, ScopeTarget target) {
        Long requestedUnitId = target.organizationUnit() == null ? null : target.organizationUnit().getId();
        Long requestedLocationId = target.workLocation() == null ? null : target.workLocation().getId();
        return Objects.equals(assignment.organizationUnitId(), requestedUnitId)
            && Objects.equals(assignment.workLocationId(), requestedLocationId);
    }

    /** Hai khoảng ngày (bao cả hai đầu, {@code null} ở cuối = không thời hạn) có giao nhau không. */
    private boolean periodsOverlap(
        LocalDate firstFrom,
        LocalDate firstTo,
        LocalDate secondFrom,
        LocalDate secondTo
    ) {
        return (firstTo == null || !firstTo.isBefore(secondFrom))
            && (secondTo == null || !secondTo.isBefore(firstFrom));
    }

    private BusinessException invalidScope(String message, String field) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, message, field);
    }

    /**
     * Luồng đang gán role, quyết định chính sách cấp nào được chấp nhận ({@link #validateGrantPolicy}).
     * {@code REQUEST_SUBMISSION} là lúc tạo yêu cầu (chỉ kiểm tra, chưa gán).
     */
    private enum AssignmentFlow {
        DIRECT,
        BOOTSTRAP,
        OWNER_TRANSFER,
        REQUEST_SUBMISSION,
        HR_ASSIGNABLE_REQUEST,
        OWNER_APPROVED_REQUEST
    }

    public record RequestCandidate(
        Account account,
        Role role,
        OrganizationUnit organizationUnit,
        WorkLocation workLocation
    ) {
    }

    private record AssignmentCandidate(Account account, Role role, ScopeTarget scopeTarget) {
    }

    private record ScopeTarget(OrganizationUnit organizationUnit, WorkLocation workLocation) {
    }
}
