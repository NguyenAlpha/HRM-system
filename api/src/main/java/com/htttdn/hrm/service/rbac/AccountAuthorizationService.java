package com.htttdn.hrm.service.rbac;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.response.account.AccountPermissionGrantResponse;
import com.htttdn.hrm.dto.response.account.PermissionGrantSource;
import com.htttdn.hrm.entity.AccountPermissionOverride;
import com.htttdn.hrm.entity.AccountRoleAssignment;
import com.htttdn.hrm.entity.Permission;
import com.htttdn.hrm.entity.RolePermission;
import com.htttdn.hrm.entity.enums.PermissionOverrideEffect;
import com.htttdn.hrm.entity.enums.PermissionModule;
import com.htttdn.hrm.entity.enums.RoleScopeType;
import com.htttdn.hrm.repository.AccountPermissionOverrideRepository;
import com.htttdn.hrm.repository.AccountRoleAssignmentRepository;
import com.htttdn.hrm.repository.RolePermissionRepository;

import lombok.RequiredArgsConstructor;

/**
 * Tính quyền thực tế của một tài khoản tại ngày hôm nay, từ dữ liệu RBAC trong database.
 *
 * <p>Ba nguồn tạo nên quyền:
 * <ul>
 *   <li><b>Lần gán role</b> ({@link AccountRoleAssignment}): account + role + phạm vi ({@link RoleScopeType}
 *       và đơn vị/địa điểm) + thời gian hiệu lực. Một account có thể có nhiều lần gán, kể cả cùng một role
 *       với phạm vi khác nhau.</li>
 *   <li><b>Quyền của role</b> ({@link RolePermission}): danh sách permission mặc định của role.</li>
 *   <li><b>Ngoại lệ quyền</b> ({@link AccountPermissionOverride}): thêm ({@code GRANT}) hoặc bớt
 *       ({@code REVOKE}) một permission <i>trên một lần gán cụ thể</i>, không phải trên cả account.</li>
 * </ul>
 * Quyền của một lần gán = quyền của role + GRANT − REVOKE (xem {@link #effectivePermissions}). Quyền của
 * account = hợp quyền của mọi lần gán đang hiệu lực, nên REVOKE trên lần gán này không lấy đi quyền mà
 * lần gán khác vẫn cho.
 *
 * <p>JWT chỉ mang danh sách role và permission phẳng ({@link #getSnapshot}); phạm vi không nằm trong
 * token mà được tải lại mỗi lần cần qua {@link #getScopes} (dùng bởi {@link EmployeeAccessScopeService}).
 */
@Service
@RequiredArgsConstructor
public class AccountAuthorizationService {

    private final AccountRoleAssignmentRepository accountRoleAssignmentRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final AccountPermissionOverrideRepository accountPermissionOverrideRepository;
    private final Clock clock;

    /**
     * Tổng hợp role và permission hôm nay của tài khoản, để đưa vào access token và trả cho web.
     *
     * <p>Được {@code AuthService} gọi khi đăng nhập, làm mới token ({@code JwtService.generateAccessToken}
     * ghi {@code roles}/{@code permissions} vào JWT) và ở {@code /me}. Vì quyền nằm trong token, thay đổi
     * RBAC chỉ có hiệu lực ở token kế tiếp.
     *
     * @param accountId tài khoản cần tính quyền
     * @return mã role và mã permission (sắp xếp, không trùng) cùng bản chi tiết của chúng; toàn bộ rỗng
     *         nếu tài khoản không có lần gán role nào đang hiệu lực
     */
    @Transactional(readOnly = true)
    public AuthorizationSnapshot getSnapshot(Long accountId) {
        AuthorizationContext context = loadContext(accountId, LocalDate.now(clock));
        if (context.assignments().isEmpty()) {
            return new AuthorizationSnapshot(List.of(), List.of(), List.of(), List.of());
        }

        Set<String> roles = new LinkedHashSet<>();
        Set<String> permissions = new LinkedHashSet<>();
        for (AccountRoleAssignment assignment : context.assignments()) {
            roles.add(assignment.getRole().getCode());
            permissions.addAll(effectivePermissions(assignment, context));
        }

        // Mỗi lần gán thành một dòng (role + phạm vi); web dùng danh sách này để hiển thị phạm vi hiện có.
        // distinct là phòng thủ: AccountRoleAssignmentCommandService đã chặn hai lần gán cùng role + phạm vi
        // có thời gian chồng nhau.
        List<AuthorizationRole> roleDetails = context.assignments().stream()
            .map(this::toAuthorizationRole)
            .distinct()
            .sorted(Comparator.comparing(AuthorizationRole::code)
                .thenComparing(
                    AuthorizationRole::scopeType,
                    Comparator.nullsFirst(Comparator.naturalOrder())
                )
                .thenComparing(AuthorizationRole::organizationUnitId, Comparator.nullsFirst(Long::compareTo))
                .thenComparing(AuthorizationRole::workLocationId, Comparator.nullsFirst(Long::compareTo)))
            .toList();
        // permissionsByCode được dựng từ chính các dòng role/override đã nạp, nên filter nonNull chỉ là phòng thủ.
        List<AuthorizationPermission> permissionDetails = permissions.stream()
            .map(context.permissionsByCode()::get)
            .filter(Objects::nonNull)
            .map(permission -> new AuthorizationPermission(
                permission.getCode(),
                permission.getName(),
                permission.getModule()
            ))
            .sorted(Comparator.comparing(AuthorizationPermission::code))
            .toList();

        return new AuthorizationSnapshot(
            sorted(roles),
            sorted(permissions),
            roleDetails,
            permissionDetails
        );
    }

    /**
     * Today's permissions of the account, each with the role assignments that give it.
     *
     * <p>Dùng cho màn quản trị tài khoản ({@code AccountAdminService.getPermissionGrants}) để trả lời
     * "quyền này đến từ đâu": một permission có thể có nhiều nguồn khi nhiều lần gán cùng cho nó.
     *
     * @param accountId tài khoản cần xem
     * @return mỗi permission (theo mã, tăng dần) kèm danh sách lần gán cho nó; {@code viaOverride = true}
     *         khi permission không thuộc role mà đến từ ngoại lệ GRANT; rỗng nếu không có lần gán hiệu lực
     */
    @Transactional(readOnly = true)
    public List<AccountPermissionGrantResponse> getPermissionGrants(Long accountId) {
        AuthorizationContext context = loadContext(accountId, LocalDate.now(clock));
        // TreeMap → kết quả sắp theo mã permission. Map<mã permission, các lần gán cho permission đó>.
        Map<String, List<PermissionGrantSource>> sourcesByCode = new TreeMap<>();
        for (AccountRoleAssignment assignment : context.assignments()) {
            Set<String> rolePermissions =
                context.permissionsByRole().getOrDefault(assignment.getRole().getId(), Set.of());
            for (String code : effectivePermissions(assignment, context)) {
                sourcesByCode.computeIfAbsent(code, ignored -> new ArrayList<>()).add(new PermissionGrantSource(
                    assignment.getId(),
                    assignment.getRole().getCode(),
                    assignment.getRole().getName(),
                    !rolePermissions.contains(code)
                ));
            }
        }
        return sourcesByCode.entrySet().stream()
            .filter(entry -> context.permissionsByCode().containsKey(entry.getKey()))
            .map(entry -> {
                Permission permission = context.permissionsByCode().get(entry.getKey());
                return new AccountPermissionGrantResponse(
                    permission.getCode(), permission.getName(), permission.getModule(), entry.getValue());
            })
            .toList();
    }

    /**
     * Các phạm vi mà tài khoản đang có cho một permission: phạm vi của mọi lần gán (đang hiệu lực) mà
     * quyền thực tế chứa permission đó.
     *
     * <p>Đây là bước "quyền này áp dụng ở đâu" mà {@code @PreAuthorize("hasAuthority(...)")} không trả lời
     * được, vì JWT chỉ biết account có permission chứ không biết ở phạm vi nào.
     * {@link EmployeeAccessScopeService} hợp (OR) các phạm vi trả về: có một phạm vi {@code COMPANY} là
     * thấy toàn công ty. Mỗi lần gọi đọc lại database, nên phạm vi luôn mới dù token đã cấp từ trước.
     *
     * @param accountId      tài khoản cần xét
     * @param permissionCode mã permission, VD {@code employee.read}
     * @return phạm vi không trùng lặp; rỗng khi tài khoản không có permission này ở lần gán nào
     */
    @Transactional(readOnly = true)
    public List<AuthorizationScope> getScopes(Long accountId, String permissionCode) {
        AuthorizationContext context = loadContext(accountId, LocalDate.now(clock));
        return context.assignments().stream()
            .filter(assignment -> effectivePermissions(assignment, context).contains(permissionCode))
            .map(assignment -> new AuthorizationScope(
                assignment.getScopeType(),
                assignment.getOrganizationUnit() == null ? null : assignment.getOrganizationUnit().getId(),
                assignment.getWorkLocation() == null ? null : assignment.getWorkLocation().getId()
            ))
            .distinct()
            .toList();
    }

    /**
     * Nạp mọi dữ liệu RBAC của tài khoản tại {@code date} bằng 3 query cố định (không N+1 theo số lần gán).
     *
     * <p>Chỉ lấy dữ liệu đang hiệu lực: lần gán chưa thu hồi, trong khoảng hiệu lực và role chưa xóa mềm;
     * permission đang active; ngoại lệ chưa thu hồi và trong khoảng hiệu lực.
     *
     * @param accountId tài khoản cần nạp
     * @param date      ngày xét hiệu lực (các method public truyền hôm nay theo {@link Clock})
     * @return context rỗng nếu không có lần gán nào
     */
    private AuthorizationContext loadContext(Long accountId, LocalDate date) {
        // JOIN FETCH role, organizationUnit, workLocation; lọc revokedAt IS NULL, effectiveFrom/To bao date,
        // role.deletedAt IS NULL.
        List<AccountRoleAssignment> assignments =
            accountRoleAssignmentRepository.findActiveWithRoleByAccountId(accountId, date);
        if (assignments.isEmpty()) {
            return new AuthorizationContext(List.of(), Map.of(), Map.of(), Map.of());
        }

        List<Long> roleIds = assignments.stream()
            .map(assignment -> assignment.getRole().getId())
            .distinct()
            .toList();
        // Chỉ permission có isActive = true; permission bị tắt coi như không tồn tại ở mọi role.
        List<RolePermission> rolePermissions = rolePermissionRepository.findActiveByRoleIds(roleIds);
        Map<Long, Set<String>> permissionsByRole = groupPermissionsByRole(rolePermissions);

        List<Long> assignmentIds = assignments.stream()
            .map(AccountRoleAssignment::getId)
            .toList();
        List<AccountPermissionOverride> overrides =
            accountPermissionOverrideRepository.findActiveByAssignmentIds(assignmentIds, date);
        Map<Long, List<AccountPermissionOverride>> overridesByAssignment = groupOverridesByAssignment(overrides);
        // Bảng tra mã → Permission để dựng chi tiết (tên, module) mà không query thêm.
        Map<String, Permission> permissionsByCode = new HashMap<>();
        rolePermissions.forEach(rolePermission -> permissionsByCode.put(
            rolePermission.getPermission().getCode(),
            rolePermission.getPermission()
        ));
        overrides.forEach(permissionOverride -> permissionsByCode.put(
            permissionOverride.getPermission().getCode(),
            permissionOverride.getPermission()
        ));
        return new AuthorizationContext(
            assignments,
            permissionsByRole,
            overridesByAssignment,
            permissionsByCode
        );
    }

    /**
     * Quyền thực tế của một lần gán: quyền của role, rồi áp lần lượt các ngoại lệ của chính lần gán đó
     * (GRANT thêm, REVOKE bớt).
     *
     * <p>Ngoại lệ đã được sắp theo thời điểm tạo ({@link #groupOverridesByAssignment}), nên nếu có nhiều
     * ngoại lệ cùng một permission thì cái tạo sau thắng. Thực tế hiếm vì database
     * ({@code excl_active_permission_override_overlap}) chặn hai ngoại lệ chưa thu hồi cùng permission có
     * thời gian chồng nhau trên một lần gán.
     *
     * @return tập mã permission của riêng lần gán này (không gộp với lần gán khác)
     */
    private Set<String> effectivePermissions(
        AccountRoleAssignment assignment,
        AuthorizationContext context
    ) {
        Set<String> permissions = new LinkedHashSet<>(
            context.permissionsByRole().getOrDefault(assignment.getRole().getId(), Set.of())
        );
        for (AccountPermissionOverride permissionOverride :
            context.overridesByAssignment().getOrDefault(assignment.getId(), List.of())) {
            String permissionCode = permissionOverride.getPermission().getCode();
            if (permissionOverride.getEffect() == PermissionOverrideEffect.GRANT) {
                permissions.add(permissionCode);
            } else {
                permissions.remove(permissionCode);
            }
        }
        return permissions;
    }

    private Map<Long, Set<String>> groupPermissionsByRole(List<RolePermission> rolePermissions) {
        Map<Long, Set<String>> result = new HashMap<>();
        for (RolePermission rolePermission : rolePermissions) {
            result.computeIfAbsent(rolePermission.getRole().getId(), ignored -> new LinkedHashSet<>())
                .add(rolePermission.getPermission().getCode());
        }
        return result;
    }

    /** Map&lt;id lần gán, ngoại lệ của lần gán đó&gt;, mỗi danh sách theo thứ tự tạo (cũ → mới). */
    private Map<Long, List<AccountPermissionOverride>> groupOverridesByAssignment(
        List<AccountPermissionOverride> overrides
    ) {
        Map<Long, List<AccountPermissionOverride>> result = new HashMap<>();
        overrides.stream()
            .sorted(Comparator.comparing(AccountPermissionOverride::getCreatedAt)
                .thenComparing(AccountPermissionOverride::getId))
            .forEach(permissionOverride -> result
                .computeIfAbsent(
                    permissionOverride.getAccountRoleAssignment().getId(),
                    ignored -> new ArrayList<>()
                )
                .add(permissionOverride));
        return result;
    }

    private List<String> sorted(Set<String> values) {
        return values.stream().sorted().toList();
    }

    private AuthorizationRole toAuthorizationRole(AccountRoleAssignment assignment) {
        return new AuthorizationRole(
            assignment.getRole().getCode(),
            assignment.getRole().getName(),
            assignment.getScopeType(),
            assignment.getOrganizationUnit() == null ? null : assignment.getOrganizationUnit().getId(),
            assignment.getOrganizationUnit() == null ? null : assignment.getOrganizationUnit().getName(),
            assignment.getWorkLocation() == null ? null : assignment.getWorkLocation().getId(),
            assignment.getWorkLocation() == null ? null : assignment.getWorkLocation().getName()
        );
    }

    public record AuthorizationSnapshot(
        List<String> roles,
        List<String> permissions,
        List<AuthorizationRole> roleDetails,
        List<AuthorizationPermission> permissionDetails
    ) {

        public AuthorizationSnapshot(List<String> roles, List<String> permissions) {
            this(roles, permissions, List.of(), List.of());
        }
    }

    public record AuthorizationRole(
        String code,
        String name,
        RoleScopeType scopeType,
        Long organizationUnitId,
        String organizationUnitName,
        Long workLocationId,
        String workLocationName
    ) {
    }

    public record AuthorizationPermission(
        String code,
        String name,
        PermissionModule module
    ) {
    }

    /**
     * Phạm vi của một lần gán: {@code organizationUnitId} chỉ có khi {@code ORG_UNIT}, {@code workLocationId}
     * chỉ có khi {@code LOCATION} (ràng buộc {@code chk_account_role_assignments_scope}); đơn vị/địa điểm con
     * được {@link EmployeeAccessScopeService} mở rộng sau.
     */
    public record AuthorizationScope(
        RoleScopeType scopeType,
        Long organizationUnitId,
        Long workLocationId
    ) {
    }

    /**
     * Dữ liệu RBAC đã nạp của một tài khoản: {@code permissionsByRole} theo id role,
     * {@code overridesByAssignment} theo id lần gán, {@code permissionsByCode} theo mã permission.
     */
    private record AuthorizationContext(
        List<AccountRoleAssignment> assignments,
        Map<Long, Set<String>> permissionsByRole,
        Map<Long, List<AccountPermissionOverride>> overridesByAssignment,
        Map<String, Permission> permissionsByCode
    ) {
    }
}
