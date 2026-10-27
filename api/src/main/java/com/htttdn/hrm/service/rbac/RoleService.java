package com.htttdn.hrm.service.rbac;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.request.role.CreateRoleRequest;
import com.htttdn.hrm.dto.request.role.GrantPermissionRequest;
import com.htttdn.hrm.dto.request.role.ReplaceRolePermissionsRequest;
import com.htttdn.hrm.dto.request.role.UpdateRoleRequest;
import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.dto.response.permission.PermissionResponse;
import com.htttdn.hrm.dto.response.role.RoleResponse;
import com.htttdn.hrm.dto.response.role.RoleWithPermissionsResponse;
import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.Permission;
import com.htttdn.hrm.entity.Role;
import com.htttdn.hrm.entity.RolePermission;
import com.htttdn.hrm.entity.RolePermissionId;
import com.htttdn.hrm.entity.enums.PermissionAssignmentPolicy;
import com.htttdn.hrm.entity.enums.RoleGrantPolicy;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.exception.ResourceNotFoundException;
import com.htttdn.hrm.repository.PermissionRepository;
import com.htttdn.hrm.repository.RolePermissionRepository;
import com.htttdn.hrm.repository.RoleRepository;
import com.htttdn.hrm.security.CanManageRbac;
import com.htttdn.hrm.security.CurrentAccountProvider;

import lombok.RequiredArgsConstructor;

/**
 * Quản lý role tự tạo và danh sách permission của chúng ({@code role_permissions}).
 *
 * <p>Hai loại role: role hệ thống ({@code isSystem = true}, do seeder tạo, ứng dụng dựa vào mã của chúng)
 * không sửa, xóa hay đổi permission được; role tự tạo thì được, nhưng chỉ nhận permission
 * {@code DELEGABLE} và chính sách cấp {@code HR_ASSIGNABLE}/{@code OWNER_APPROVAL}. {@link CanManageRbac} trên class → mọi method cần {@code rbac.manage}.
 *
 * <p>Đổi permission của role không thu hồi token: người đang giữ role thấy thay đổi ở access token kế tiếp,
 * còn kiểm tra phạm vi ({@link EmployeeAccessScopeService}) đọc database nên áp dụng ngay.
 */
@Service
@Transactional
@CanManageRbac
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final CurrentAccountProvider currentAccountProvider;
    private final Clock clock;

    /**
     * Tạo role tự tạo (không phải role hệ thống), chưa có permission nào.
     *
     * @param request mã, tên, mô tả và chính sách cấp role
     * @return role vừa tạo
     * @throws ConflictException mã đã dùng, kể cả bởi role đã xóa mềm (→ 409 {@code CONFLICT}, field {@code code})
     * @throws BusinessException chính sách cấp {@code AUTO}/{@code SYSTEM_ONLY} (→ 400 {@code VALIDATION_ERROR})
     */
    public RoleResponse create(CreateRoleRequest request) {
        requireWorkflowGrantPolicy(request.grantPolicy());
        // existsByCode xét cả role đã xóa mềm, khớp với UNIQUE trên roles.code: mã đã dùng thì không dùng lại.
        if (roleRepository.existsByCode(request.code())) {
            throw new ConflictException(ErrorCode.CONFLICT, "Role code is already taken", "code");
        }
        Role role = Role.builder()
            .code(request.code())
            .name(request.name())
            .description(request.description())
            .isSystem(false)
            .grantPolicy(request.grantPolicy())
            .build();

        return toResponse(roleRepository.save(role));
    }

    @Transactional(readOnly = true)
    public RoleResponse getById(Long id) {
        return toResponse(roleRepository.getRequired(id));
    }

    @Transactional(readOnly = true)
    public Page<RoleResponse> list(Pageable pageable) {
        return roleRepository.findByDeletedAtIsNull(pageable).map(this::toResponse);
    }

    /**
     * Mọi role chưa xóa kèm permission của từng role, cho màn ma trận role × permission.
     *
     * @return role theo id tăng dần; permission của mỗi role theo mã; rỗng nếu chưa có role
     */
    @Transactional(readOnly = true)
    public List<RoleWithPermissionsResponse> listWithPermissions() {
        List<Role> roles = roleRepository.findByDeletedAtIsNullOrderByIdAsc();
        if (roles.isEmpty()) {
            return List.of();
        }

        // 1 query JOIN FETCH cho mọi role (không N+1), gom thành Map<id role, permission của role>.
        Map<Long, List<PermissionResponse>> permissionsByRole = new HashMap<>();
        List<Long> roleIds = roles.stream().map(Role::getId).toList();
        rolePermissionRepository.findAllByRoleIds(roleIds).forEach(rolePermission ->
            permissionsByRole
                .computeIfAbsent(rolePermission.getId().getRoleId(), ignored -> new ArrayList<>())
                .add(toPermissionResponse(rolePermission.getPermission()))
        );

        return roles.stream()
            .map(role -> new RoleWithPermissionsResponse(
                role.getId(),
                role.getCode(),
                role.getName(),
                role.getDescription(),
                role.getIsSystem(),
                role.getGrantPolicy(),
                List.copyOf(permissionsByRole.getOrDefault(role.getId(), List.of()))
            ))
            .toList();
    }

    /**
     * Sửa tên, mô tả và chính sách cấp của role tự tạo; mã role không đổi được.
     *
     * <p>Đổi chính sách cấp chỉ ảnh hưởng các lần gán sau, không đụng lần gán đã có.
     *
     * @throws com.htttdn.hrm.exception.ResourceNotFoundException role không có hoặc đã xóa (→ 404 {@code ROLE_NOT_FOUND})
     * @throws ConflictException role hệ thống (→ 409 {@code CONFLICT})
     * @throws BusinessException chính sách cấp {@code AUTO}/{@code SYSTEM_ONLY} (→ 400 {@code VALIDATION_ERROR})
     */
    public RoleResponse update(Long id, UpdateRoleRequest request) {
        Role role = roleRepository.getRequired(id);
        if (Boolean.TRUE.equals(role.getIsSystem())) {
            throw new ConflictException(ErrorCode.CONFLICT, "System roles cannot be modified");
        }
        requireWorkflowGrantPolicy(request.grantPolicy());
        // Không gọi save: role là entity đang được quản lý trong transaction, Hibernate tự UPDATE khi commit.
        role.setName(request.name());
        role.setDescription(request.description());
        role.setGrantPolicy(request.grantPolicy());
        return toResponse(role);
    }

    /**
     * Xóa mềm role tự tạo.
     *
     * <p>Lần gán role này vẫn còn trong database nhưng không còn được tính khi nạp quyền: query
     * {@code findActiveWithRoleByAccountId} bỏ qua role có {@code deletedAt} (phạm vi mất ngay, permission trong
     * JWT mất ở token kế tiếp).
     *
     * @throws com.htttdn.hrm.exception.ResourceNotFoundException role không có hoặc đã xóa (→ 404 {@code ROLE_NOT_FOUND})
     * @throws ConflictException role hệ thống (→ 409 {@code CONFLICT})
     */
    public void softDelete(Long id) {
        Role role = roleRepository.getRequired(id);
        if (Boolean.TRUE.equals(role.getIsSystem())) {
            throw new ConflictException(ErrorCode.CONFLICT, "System roles cannot be deleted");
        }
        Instant now = Instant.now(clock);
        role.setDeletedAt(now);
    }

    /**
     * Thêm một permission vào role tự tạo.
     *
     * @param roleId  role cần thêm
     * @param request permission cần thêm
     * @throws com.htttdn.hrm.exception.ResourceNotFoundException role hoặc permission không có (→ 404)
     * @throws ConflictException role hệ thống, permission {@code SYSTEM_ONLY}, hoặc role đã có permission này
     *         (→ 409 {@code CONFLICT})
     */
    public void grantPermission(Long roleId, GrantPermissionRequest request) {
        Role role = roleRepository.getRequired(roleId);
        rejectSystemRolePermissionChange(role);
        Permission permission = permissionRepository.getRequired(request.permissionId());
        rejectNonDelegablePermission(permission, "permissionId");

        RolePermissionId id = new RolePermissionId(roleId, request.permissionId());
        if (rolePermissionRepository.existsById(id)) {
            throw new ConflictException(ErrorCode.CONFLICT, "Permission is already granted to this role");
        }

        Account grantedBy = currentAccountProvider.account();

        RolePermission rolePermission = RolePermission.builder()
            .id(id)
            .role(role)
            .permission(permission)
            .createdByAccount(grantedBy)
            .build();

        rolePermissionRepository.save(rolePermission);
    }

    /**
     * Đặt lại toàn bộ permission của role tự tạo bằng danh sách gửi lên (màn ma trận lưu một lần).
     *
     * <p>Chỉ xóa dòng không còn trong danh sách và thêm dòng mới; dòng giữ nguyên không đụng tới, nên
     * {@code createdByAccount}/{@code createdAt} của chúng còn nguyên. Danh sách rỗng → role không còn permission.
     *
     * @param roleId  role cần đặt lại
     * @param request id các permission role phải có sau khi lưu
     * @return permission của role sau khi lưu, sắp theo mã
     * @throws com.htttdn.hrm.exception.ResourceNotFoundException role hoặc một permission không có
     *         (→ 404 {@code ROLE_NOT_FOUND} / {@code PERMISSION_NOT_FOUND})
     * @throws ConflictException role hệ thống hoặc có permission {@code SYSTEM_ONLY} (→ 409 {@code CONFLICT})
     */
    public List<PermissionResponse> replacePermissions(
        Long roleId,
        ReplaceRolePermissionsRequest request
    ) {
        // Khóa ghi role: hai lần lưu ma trận cùng lúc chạy lần lượt, không trộn kết quả của nhau.
        Role role = roleRepository.getRequiredForUpdate(roleId);
        rejectSystemRolePermissionChange(role);

        // LinkedHashSet bỏ id gửi trùng.
        Set<Long> requestedIds = new LinkedHashSet<>(request.permissionIds());
        List<Permission> requestedPermissions = requestedIds.isEmpty()
            ? List.of()
            : permissionRepository.findAllById(requestedIds);
        Map<Long, Permission> requestedById = requestedPermissions.stream()
            .collect(Collectors.toMap(Permission::getId, Function.identity()));

        // findAllById bỏ qua id không tồn tại thay vì báo lỗi → tự đối chiếu để trả 404.
        for (Long permissionId : requestedIds) {
            Permission permission = requestedById.get(permissionId);
            if (permission == null) {
                throw new ResourceNotFoundException(
                    ErrorCode.PERMISSION_NOT_FOUND,
                    "Permission not found: " + permissionId
                );
            }
            rejectNonDelegablePermission(permission, "permissionIds");
        }

        Account grantedBy = currentAccountProvider.account();
        List<RolePermission> existingMappings = rolePermissionRepository.findByIdRoleId(roleId);
        Map<Long, RolePermission> existingByPermissionId = existingMappings.stream()
            .collect(Collectors.toMap(mapping -> mapping.getPermission().getId(), Function.identity()));

        List<RolePermission> removedMappings = existingMappings.stream()
            .filter(mapping -> !requestedIds.contains(mapping.getPermission().getId()))
            .toList();
        if (!removedMappings.isEmpty()) {
            rolePermissionRepository.deleteAll(removedMappings);
        }
        List<RolePermission> addedMappings = requestedIds.stream()
            .filter(permissionId -> !existingByPermissionId.containsKey(permissionId))
            .map(permissionId -> RolePermission.builder()
                .id(new RolePermissionId(roleId, permissionId))
                .role(role)
                .permission(requestedById.get(permissionId))
                .createdByAccount(grantedBy)
                .build())
            .toList();
        if (!addedMappings.isEmpty()) {
            rolePermissionRepository.saveAll(addedMappings);
        }

        return requestedPermissions.stream()
            .sorted(Comparator.comparing(Permission::getCode))
            .map(this::toPermissionResponse)
            .toList();
    }

    /**
     * Bỏ một permission khỏi role tự tạo (xóa hẳn dòng {@code role_permissions}).
     *
     * @throws com.htttdn.hrm.exception.ResourceNotFoundException role không có, hoặc role không có permission
     *         này (→ 404 {@code PERMISSION_NOT_FOUND})
     * @throws ConflictException role hệ thống (→ 409 {@code CONFLICT})
     */
    public void revokePermission(Long roleId, Long permissionId) {
        Role role = roleRepository.getRequired(roleId);
        rejectSystemRolePermissionChange(role);
        RolePermissionId id = new RolePermissionId(roleId, permissionId);
        if (!rolePermissionRepository.existsById(id)) {
            throw new ResourceNotFoundException(ErrorCode.PERMISSION_NOT_FOUND, "Role does not have this permission");
        }
        rolePermissionRepository.deleteById(id);
    }

    private void rejectSystemRolePermissionChange(Role role) {
        if (Boolean.TRUE.equals(role.getIsSystem())) {
            throw new ConflictException(
                ErrorCode.CONFLICT,
                "System role permissions are defined by the application and cannot be modified"
            );
        }
    }

    /**
     * Role tự tạo chỉ được cấp qua HR ({@code HR_ASSIGNABLE}) hoặc Company Owner duyệt ({@code OWNER_APPROVAL}).
     * Không luồng nào cấp hay thu hồi role {@code AUTO}/{@code SYSTEM_ONLY}, kể cả khi nghỉ việc hoặc xóa tài
     * khoản, nên lần gán của một role tự tạo đổi sang hai chính sách đó sẽ không gỡ được nữa (→ 400).
     */
    private void requireWorkflowGrantPolicy(RoleGrantPolicy grantPolicy) {
        if (grantPolicy != RoleGrantPolicy.HR_ASSIGNABLE && grantPolicy != RoleGrantPolicy.OWNER_APPROVAL) {
            throw new BusinessException(
                ErrorCode.VALIDATION_ERROR,
                "Custom roles must be HR_ASSIGNABLE or OWNER_APPROVAL",
                "grantPolicy"
            );
        }
    }

    /** Permission {@code SYSTEM_ONLY} chỉ thuộc role hệ thống, không được gán cho role tự tạo (→ 409). */
    private void rejectNonDelegablePermission(Permission permission, String field) {
        if (permission.getAssignmentPolicy() != PermissionAssignmentPolicy.DELEGABLE) {
            throw new ConflictException(
                ErrorCode.CONFLICT,
                "System-only permissions cannot be assigned to custom roles",
                field
            );
        }
    }

    /** @throws com.htttdn.hrm.exception.ResourceNotFoundException role không có hoặc đã xóa (→ 404 {@code ROLE_NOT_FOUND}) */
    @Transactional(readOnly = true)
    public List<PermissionResponse> listPermissions(Long roleId) {
        roleRepository.getRequired(roleId);
        return rolePermissionRepository.findByIdRoleId(roleId).stream()
            .map(RolePermission::getPermission)
            .map(this::toPermissionResponse)
            .toList();
    }

    private PermissionResponse toPermissionResponse(Permission permission) {
        return new PermissionResponse(
            permission.getId(),
            permission.getCode(),
            permission.getName(),
            permission.getModule(),
            permission.getDescription(),
            permission.getAssignmentPolicy(),
            permission.getIsActive()
        );
    }

    private RoleResponse toResponse(Role role) {
        return new RoleResponse(
            role.getId(),
            role.getCode(),
            role.getName(),
            role.getDescription(),
            role.getIsSystem(),
            role.getGrantPolicy()
        );
    }
}
