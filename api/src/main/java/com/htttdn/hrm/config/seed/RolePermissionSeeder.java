package com.htttdn.hrm.config.seed;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.entity.Permission;
import com.htttdn.hrm.entity.Role;
import com.htttdn.hrm.entity.RolePermission;
import com.htttdn.hrm.entity.RolePermissionId;
import com.htttdn.hrm.entity.enums.PermissionAssignmentPolicy;
import com.htttdn.hrm.repository.PermissionRepository;
import com.htttdn.hrm.repository.RolePermissionRepository;
import com.htttdn.hrm.repository.RoleRepository;

/**
 * Gán bộ permission mặc định cho các system role sau khi role và permission đã được seed.
 *
 * <p>Project không cấu hình role hierarchy trong Spring Security, vì vậy role cấp cao phải
 * chứa tường minh các quyền self-service của {@code EMPLOYEE}. System role thuộc sở hữu của
 * ứng dụng nên seeder đồng bộ chính xác bộ permission chuẩn: thêm mapping còn thiếu và xóa
 * mapping ngoài định nghĩa. Mapping hợp lệ của custom role được giữ nguyên; mapping tới
 * permission {@link PermissionAssignmentPolicy#SYSTEM_ONLY} bị thu hồi.
 */
@Component
@Order(300)
public class RolePermissionSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RolePermissionSeeder.class);

    private static final List<String> EMPLOYEE_PERMISSIONS = List.of(
        "profile.self.read",
        "profile.self.update",
        "request.self.read",
        "request.self.create",
        "request.self.cancel",
        "attendance.self.read",
        "payroll.self.read",
        "payroll.self.print"
    );

    private static final List<String> SUPERVISOR_PERMISSIONS = with(
        EMPLOYEE_PERMISSIONS,
        "employee.read",
        "request.read",
        "request.approve",
        "attendance.read",
        "attendance.overtime.approve"
    );

    private static final List<String> PEOPLE_MANAGER_PERMISSIONS = with(
        EMPLOYEE_PERMISSIONS,
        "employee.read",
        "employee.manage",
        "request.read",
        "request.approve",
        "request.manage",
        "attendance.read",
        "attendance.manage",
        "attendance.overtime.approve",
        "report.hr.read"
    );

    private static final List<RolePermissionDefinition> DEFAULT_ASSIGNMENTS = List.of(
        role("EMPLOYEE", EMPLOYEE_PERMISSIONS),
        role("TEAM_LEAD", SUPERVISOR_PERMISSIONS),
        role("WAREHOUSE_SUPERVISOR", SUPERVISOR_PERMISSIONS),
        role("BRANCH_MANAGER", PEOPLE_MANAGER_PERMISSIONS),
        role("HR_STAFF", with(
            PEOPLE_MANAGER_PERMISSIONS,
            "employee.sensitive.read",
            "employee.sensitive.manage"
        )),
        role("PAYROLL_ACCOUNTANT", with(
            EMPLOYEE_PERMISSIONS,
            "employee.read",
            "compensation.read",
            "payroll.calculate",
            "report.payroll.read"
        )),
        role("PAYROLL_APPROVER", with(
            EMPLOYEE_PERMISSIONS,
            "employee.read",
            "payroll.approve",
            "payroll.mark_paid",
            "payroll.lock",
            "report.payroll.read"
        )),
        role("DIRECTOR", with(
            EMPLOYEE_PERMISSIONS,
            "employee.read",
            "employee.lifecycle.approve",
            "request.read",
            "request.final_approve",
            "organization.change.approve"
        )),
        role("COMPANY_OWNER", List.of(
            "account.read",
            "account.manage",
            "account.activation.manage",
            "account.role.assign",
            "organization.director.provision",
            "organization.hr_staff.bootstrap",
            "rbac.manage"
        )),
        role("SYSTEM_ADMIN", List.of(
            "organization.company_owner.bootstrap"
        ))
    );

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final boolean enabled;

    public RolePermissionSeeder(
        RoleRepository roleRepository,
        PermissionRepository permissionRepository,
        RolePermissionRepository rolePermissionRepository,
        @Value("${rbac.seed.enabled:true}") boolean enabled
    ) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.enabled = enabled;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }

        int createdCount = 0;
        List<RolePermission> invalidCustomMappings = rolePermissionRepository.findAll().stream()
            .filter(mapping -> !Boolean.TRUE.equals(mapping.getRole().getIsSystem()))
            .filter(mapping -> mapping.getPermission().getAssignmentPolicy()
                == PermissionAssignmentPolicy.SYSTEM_ONLY)
            .toList();
        if (!invalidCustomMappings.isEmpty()) {
            rolePermissionRepository.deleteAll(invalidCustomMappings);
        }
        int removedCount = invalidCustomMappings.size();
        for (RolePermissionDefinition definition : DEFAULT_ASSIGNMENTS) {
            Role role = roleRepository.findByCodeAndDeletedAtIsNull(definition.roleCode())
                .orElseThrow(() -> new IllegalStateException("Seed role not found: " + definition.roleCode()));
            Set<String> expectedPermissionCodes = Set.copyOf(definition.permissionCodes());
            List<RolePermission> unexpectedMappings = rolePermissionRepository.findByIdRoleId(role.getId()).stream()
                .filter(mapping -> !expectedPermissionCodes.contains(mapping.getPermission().getCode()))
                .toList();
            if (!unexpectedMappings.isEmpty()) {
                rolePermissionRepository.deleteAll(unexpectedMappings);
                removedCount += unexpectedMappings.size();
            }

            for (String permissionCode : definition.permissionCodes()) {
                Permission permission = permissionRepository.findByCode(permissionCode)
                    .orElseThrow(() -> new IllegalStateException("Seed permission not found: " + permissionCode));
                RolePermissionId id = new RolePermissionId(role.getId(), permission.getId());
                if (rolePermissionRepository.existsById(id)) {
                    continue;
                }

                rolePermissionRepository.save(RolePermission.builder()
                    .id(id)
                    .role(role)
                    .permission(permission)
                    .createdByAccount(null)
                    .createdAt(Instant.now())
                    .build());
                createdCount++;
            }
        }

        log.info(
            "Role-permission seed completed: {} mappings created, {} unexpected mappings removed",
            createdCount,
            removedCount
        );
    }

    private static RolePermissionDefinition role(String roleCode, List<String> permissionCodes) {
        return new RolePermissionDefinition(roleCode, permissionCodes);
    }

    /** Tạo danh sách quyền cộng dồn, giữ thứ tự ổn định và không sửa danh sách gốc. */
    private static List<String> with(List<String> basePermissions, String... additionalPermissions) {
        List<String> permissions = new ArrayList<>(basePermissions);
        permissions.addAll(List.of(additionalPermissions));
        return List.copyOf(permissions);
    }

    private record RolePermissionDefinition(String roleCode, List<String> permissionCodes) {
    }
}
