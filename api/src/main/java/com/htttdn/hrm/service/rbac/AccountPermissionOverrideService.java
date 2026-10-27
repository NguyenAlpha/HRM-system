package com.htttdn.hrm.service.rbac;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.htttdn.hrm.service.account.CompanyOwnerAccountGuard;
import com.htttdn.hrm.service.auth.RefreshTokenService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.request.account.CreatePermissionOverrideRequest;
import com.htttdn.hrm.dto.request.account.RevokePermissionOverrideRequest;
import com.htttdn.hrm.dto.response.account.AccountPermissionOverrideResponse;
import com.htttdn.hrm.dto.response.account.PermissionOverrideOptionResponse;
import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.AccountPermissionOverride;
import com.htttdn.hrm.entity.AccountRoleAssignment;
import com.htttdn.hrm.entity.Permission;
import com.htttdn.hrm.entity.enums.AccountStatus;
import com.htttdn.hrm.entity.enums.PermissionAssignmentPolicy;
import com.htttdn.hrm.entity.enums.PermissionOverrideEffect;
import com.htttdn.hrm.entity.enums.PermissionOverrideStatus;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.exception.ForbiddenException;
import com.htttdn.hrm.exception.ResourceNotFoundException;
import com.htttdn.hrm.repository.AccountPermissionOverrideRepository;
import com.htttdn.hrm.repository.AccountRoleAssignmentRepository;
import com.htttdn.hrm.repository.PermissionRepository;
import com.htttdn.hrm.repository.RolePermissionRepository;
import com.htttdn.hrm.security.CanManagePermissionOverrides;
import com.htttdn.hrm.security.CurrentAccountProvider;

import lombok.RequiredArgsConstructor;

/**
 * Ngoại lệ quyền: thêm hoặc bớt một permission trên <i>một lần gán role</i> của tài khoản, có thời hạn,
 * mà không phải tạo role mới.
 *
 * <p>{@code GRANT} thêm permission role không có; {@code REVOKE} bớt permission role đang có. Ngoại lệ chỉ
 * tác động lên lần gán chứa nó, nên phạm vi đi theo lần gán đó, và REVOKE không lấy đi permission mà lần gán
 * khác của cùng tài khoản vẫn cho (cách tính: {@link AccountAuthorizationService}). Chỉ permission
 * {@code DELEGABLE} được dùng. Ngoại lệ không bị xóa, chỉ thu hồi để giữ lịch sử.
 *
 * <p>{@link CanManagePermissionOverrides} trên class → mọi method cần {@code account.permission.override.manage}.
 */
@Service
@Transactional
@CanManagePermissionOverrides
@RequiredArgsConstructor
public class AccountPermissionOverrideService {

    private final AccountRoleAssignmentRepository assignmentRepository;
    private final AccountPermissionOverrideRepository overrideRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final CurrentAccountProvider currentAccountProvider;
    private final RefreshTokenService refreshTokenService;
    private final CompanyOwnerAccountGuard companyOwnerAccountGuard;
    private final Clock clock;

    /**
     * Lịch sử ngoại lệ của một lần gán (kể cả đã thu hồi, đã hết hạn), mới nhất trước.
     *
     * @param accountId    tài khoản sở hữu lần gán
     * @param assignmentId lần gán cần xem
     * @return danh sách ngoại lệ kèm trạng thái tính theo hôm nay
     * @throws com.htttdn.hrm.exception.ResourceNotFoundException lần gán không có hoặc thuộc tài khoản khác
     *         (→ 404 {@code ROLE_ASSIGNMENT_NOT_FOUND})
     */
    @Transactional(readOnly = true)
    public List<AccountPermissionOverrideResponse> list(Long accountId, Long assignmentId) {
        // Chỉ để kiểm tra lần gán thuộc đúng tài khoản trên URL; khác tài khoản cũng 404.
        assignmentRepository.getRequired(assignmentId, accountId);
        return overrideRepository.findHistoryByAssignmentId(assignmentId).stream()
            .map(this::toResponse)
            .toList();
    }

    /**
     * Các permission có thể tạo ngoại lệ trên lần gán, kèm loại ngoại lệ duy nhất hợp lệ cho từng cái.
     *
     * <p>Mỗi permission được hỏi riêng xem role có nó không (1 query/permission). Chấp nhận được vì chỉ dùng
     * khi mở form ngoại lệ và số permission ít.
     *
     * @param accountId    tài khoản sở hữu lần gán
     * @param assignmentId lần gán
     * @return permission {@code DELEGABLE} đang active, theo module rồi mã; {@code REVOKE} nếu role đã có,
     *         {@code GRANT} nếu chưa
     * @throws com.htttdn.hrm.exception.ResourceNotFoundException lần gán không có hoặc thuộc tài khoản khác (→ 404)
     */
    @Transactional(readOnly = true)
    public List<PermissionOverrideOptionResponse> availablePermissions(Long accountId, Long assignmentId) {
        AccountRoleAssignment assignment = assignmentRepository.getRequired(assignmentId, accountId);
        return permissionRepository
            .findByIsActiveTrueAndAssignmentPolicyOrderByModuleAscCodeAsc(PermissionAssignmentPolicy.DELEGABLE)
            .stream()
            .map(permission -> new PermissionOverrideOptionResponse(
                permission.getId(),
                permission.getCode(),
                permission.getName(),
                permission.getModule(),
                permission.getDescription(),
                defaultEffect(assignment, permission)
            ))
            .toList();
    }

    /**
     * Tạo ngoại lệ GRANT/REVOKE cho một permission trên lần gán.
     *
     * <p>Thứ tự: không tự sửa quyền mình và qua guard Company Owner/System Admin → khóa lần gán → lần gán còn
     * dùng được → permission hợp lệ và hiệu ứng đúng chiều → thời hạn nằm trong lần gán → không chồng ngoại lệ
     * khác → lưu, rồi thu hồi refresh token để tài khoản lấy quyền mới khi đăng nhập lại.
     *
     * @param accountId    tài khoản sở hữu lần gán
     * @param assignmentId lần gán nhận ngoại lệ
     * @param request      permission, hiệu ứng, thời hạn, lý do
     * @return ngoại lệ vừa tạo
     * @throws ForbiddenException sửa chính tài khoản mình, hoặc bị {@code CompanyOwnerAccountGuard} chặn (→ 403)
     * @throws ResourceNotFoundException lần gán hoặc permission không có (→ 404)
     * @throws ConflictException lần gán đã thu hồi/role đã xóa/tài khoản bị khóa, permission không hợp lệ, hiệu
     *         ứng không đổi gì, hoặc trùng ngoại lệ (→ 409 {@code PERMISSION_OVERRIDE_NOT_ALLOWED} /
     *         {@code PERMISSION_OVERRIDE_EXISTS})
     * @throws BusinessException thời hạn sai hoặc ra ngoài lần gán (→ 400 {@code VALIDATION_ERROR})
     */
    public AccountPermissionOverrideResponse create(
        Long accountId,
        Long assignmentId,
        CreatePermissionOverrideRequest request
    ) {
        requireCanChangeOverrides(accountId);
        // Khóa ghi lần gán: hai request tạo ngoại lệ cùng lúc chạy lần lượt.
        AccountRoleAssignment assignment = assignmentRepository.getRequiredForUpdate(assignmentId, accountId);
        validateAssignment(assignment);
        Permission permission = permissionRepository.getRequired(request.permissionId());
        validatePermission(assignment, permission, request.effect());
        validatePeriod(assignment, request.effectiveFrom(), request.effectiveTo());
        // Kiểm tra trước để báo lỗi rõ; constraint excl_active_permission_override_overlap vẫn chặn ở database
        // (bắt DataIntegrityViolationException bên dưới).
        if (overrideRepository.existsOverlappingActive(
            assignmentId,
            permission.getId(),
            request.effectiveFrom(),
            request.effectiveTo()
        )) {
            throw new ConflictException(
                ErrorCode.PERMISSION_OVERRIDE_EXISTS,
                "An overlapping permission override already exists"
            );
        }

        Account actor = currentAccountProvider.account();
        AccountPermissionOverride permissionOverride = AccountPermissionOverride.builder()
            .accountRoleAssignment(assignment)
            .permission(permission)
            .effect(request.effect())
            .effectiveFrom(request.effectiveFrom())
            .effectiveTo(request.effectiveTo())
            .reason(request.reason().trim())
            .grantedByAccount(actor)
            .build();
        try {
            // saveAndFlush: INSERT ngay để vi phạm constraint ném lỗi tại đây (bắt được), không phải lúc commit.
            AccountPermissionOverride saved = overrideRepository.saveAndFlush(permissionOverride);
            // Quyền trong JWT đã cũ → buộc đăng nhập lại để lấy token có quyền mới.
            refreshTokenService.revokeAll(accountId);
            return toResponse(saved);
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException(
                ErrorCode.PERMISSION_OVERRIDE_EXISTS,
                "An overlapping permission override already exists"
            );
        }
    }

    /**
     * Thu hồi một ngoại lệ (giữ lại làm lịch sử): quyền của lần gán quay về mặc định của role.
     *
     * @param accountId    tài khoản sở hữu lần gán
     * @param assignmentId lần gán chứa ngoại lệ
     * @param overrideId   ngoại lệ cần thu hồi
     * @param request      lý do thu hồi
     * @return ngoại lệ sau khi thu hồi; đã thu hồi từ trước thì trả nguyên, không lỗi
     * @throws ForbiddenException như {@link #create} (→ 403)
     * @throws ResourceNotFoundException lần gán hoặc ngoại lệ không có, hoặc không thuộc nhau
     *         (→ 404 {@code ROLE_ASSIGNMENT_NOT_FOUND} / {@code PERMISSION_OVERRIDE_NOT_FOUND})
     */
    public AccountPermissionOverrideResponse revoke(
        Long accountId,
        Long assignmentId,
        Long overrideId,
        RevokePermissionOverrideRequest request
    ) {
        requireCanChangeOverrides(accountId);
        // Kiểm tra lần gán thuộc tài khoản và khóa nó, cùng thứ tự khóa với create.
        assignmentRepository.getRequiredForUpdate(assignmentId, accountId);
        AccountPermissionOverride permissionOverride = overrideRepository
            .findByIdAndAssignmentIdForUpdate(overrideId, assignmentId)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.PERMISSION_OVERRIDE_NOT_FOUND,
                "Permission override not found: " + overrideId
            ));
        if (permissionOverride.getRevokedAt() != null) {
            return toResponse(permissionOverride);
        }

        Account actor = currentAccountProvider.account();
        permissionOverride.setRevokedByAccount(actor);
        permissionOverride.setRevokedAt(Instant.now(clock));
        permissionOverride.setRevocationReason(request.reason().trim());
        refreshTokenService.revokeAll(accountId);
        return toResponse(permissionOverride);
    }

    /** Nobody changes their own effective permissions, and Company Owner accounts stay with Company Owners. */
    private void requireCanChangeOverrides(Long accountId) {
        if (accountId.equals(currentAccountProvider.accountId())) {
            throw new ForbiddenException(
                ErrorCode.FORBIDDEN,
                "Permission overrides cannot be changed on your own account"
            );
        }
        companyOwnerAccountGuard.requireCanManage(accountId);
    }

    /** Lần gán đã thu hồi, role đã xóa, hoặc tài khoản bị vô hiệu hóa thì không nhận ngoại lệ mới (→ 409). */
    private void validateAssignment(AccountRoleAssignment assignment) {
        if (assignment.getRevokedAt() != null || assignment.getRole().getDeletedAt() != null) {
            throw new ConflictException(
                ErrorCode.PERMISSION_OVERRIDE_NOT_ALLOWED,
                "Permission overrides require a non-revoked role assignment with an active role"
            );
        }
        if (assignment.getAccount().getStatus() == AccountStatus.DISABLED) {
            throw new ConflictException(
                ErrorCode.PERMISSION_OVERRIDE_NOT_ALLOWED,
                "Permission overrides cannot be added to a disabled account"
            );
        }
    }

    /**
     * Permission phải active, {@code DELEGABLE}, và hiệu ứng phải thật sự đổi gì đó: GRANT cái role chưa có,
     * REVOKE cái role đang có (→ 409 {@code PERMISSION_OVERRIDE_NOT_ALLOWED}).
     */
    private void validatePermission(
        AccountRoleAssignment assignment,
        Permission permission,
        PermissionOverrideEffect requestedEffect
    ) {
        if (!Boolean.TRUE.equals(permission.getIsActive())
            || permission.getAssignmentPolicy() != PermissionAssignmentPolicy.DELEGABLE) {
            throw new ConflictException(
                ErrorCode.PERMISSION_OVERRIDE_NOT_ALLOWED,
                "Only active DELEGABLE permissions can be overridden"
            );
        }
        PermissionOverrideEffect expectedEffect = defaultEffect(assignment, permission);
        if (requestedEffect != expectedEffect) {
            throw new ConflictException(
                ErrorCode.PERMISSION_OVERRIDE_NOT_ALLOWED,
                "The requested effect does not change the role's default permission"
            );
        }
    }

    /** Hiệu ứng ngoại lệ có nghĩa với permission này: role có sẵn → {@code REVOKE}, chưa có → {@code GRANT}. */
    private PermissionOverrideEffect defaultEffect(AccountRoleAssignment assignment, Permission permission) {
        boolean grantedByRole = rolePermissionRepository.existsByIdRoleIdAndIdPermissionId(
            assignment.getRole().getId(),
            permission.getId()
        );
        return grantedByRole ? PermissionOverrideEffect.REVOKE : PermissionOverrideEffect.GRANT;
    }

    /**
     * Thời hạn ngoại lệ phải hợp lệ và nằm trọn trong thời hạn lần gán: lần gán có ngày kết thúc thì ngoại lệ
     * cũng phải có và không vượt quá (→ 400 {@code VALIDATION_ERROR}).
     */
    private void validatePeriod(
        AccountRoleAssignment assignment,
        LocalDate effectiveFrom,
        LocalDate effectiveTo
    ) {
        if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw new BusinessException(
                ErrorCode.VALIDATION_ERROR,
                "effectiveTo must be on or after effectiveFrom",
                "effectiveTo"
            );
        }
        if (effectiveFrom.isBefore(assignment.getEffectiveFrom())) {
            throw new BusinessException(
                ErrorCode.VALIDATION_ERROR,
                "Override cannot start before the role assignment",
                "effectiveFrom"
            );
        }
        if (assignment.getEffectiveTo() != null
            && (effectiveTo == null || effectiveTo.isAfter(assignment.getEffectiveTo()))) {
            throw new BusinessException(
                ErrorCode.VALIDATION_ERROR,
                "Override must end within the role assignment period",
                "effectiveTo"
            );
        }
    }

    private AccountPermissionOverrideResponse toResponse(AccountPermissionOverride permissionOverride) {
        LocalDate today = LocalDate.now(clock);
        // Trạng thái không lưu trong database mà tính theo hôm nay: thu hồi > chưa tới hạn > hết hạn > đang áp dụng.
        PermissionOverrideStatus status;
        if (permissionOverride.getRevokedAt() != null) {
            status = PermissionOverrideStatus.REVOKED;
        } else if (permissionOverride.getEffectiveFrom().isAfter(today)) {
            status = PermissionOverrideStatus.SCHEDULED;
        } else if (permissionOverride.getEffectiveTo() != null
            && permissionOverride.getEffectiveTo().isBefore(today)) {
            status = PermissionOverrideStatus.EXPIRED;
        } else {
            status = PermissionOverrideStatus.ACTIVE;
        }

        Permission permission = permissionOverride.getPermission();
        Account grantedBy = permissionOverride.getGrantedByAccount();
        Account revokedBy = permissionOverride.getRevokedByAccount();
        return new AccountPermissionOverrideResponse(
            permissionOverride.getId(),
            permissionOverride.getAccountRoleAssignment().getId(),
            permissionOverride.getAccountRoleAssignment().getAccount().getId(),
            permission.getId(),
            permission.getCode(),
            permission.getName(),
            permission.getModule(),
            permissionOverride.getEffect(),
            permissionOverride.getEffectiveFrom(),
            permissionOverride.getEffectiveTo(),
            permissionOverride.getReason(),
            grantedBy.getId(),
            grantedBy.getUsername(),
            permissionOverride.getCreatedAt(),
            revokedBy == null ? null : revokedBy.getId(),
            revokedBy == null ? null : revokedBy.getUsername(),
            permissionOverride.getRevokedAt(),
            permissionOverride.getRevocationReason(),
            status
        );
    }
}
