package com.htttdn.hrm.config.seed;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.entity.Permission;
import com.htttdn.hrm.entity.enums.PermissionModule;
import com.htttdn.hrm.repository.PermissionRepository;

/**
 * Khởi tạo catalog permission chuẩn được API sử dụng để phân quyền.
 *
 * <p>Permission code được lưu nguyên dạng authority, ví dụ {@code employee.read}; không thêm
 * prefix {@code ROLE_}. Seeder tạo permission còn thiếu, đồng bộ tên hiển thị chuẩn và từ chối
 * chạy nếu một code chuẩn đã tồn tại với module sai hoặc đã bị vô hiệu hóa.
 */
@Component
@Order(200)
public class PermissionSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PermissionSeeder.class);

    private static final List<PermissionDefinition> DEFAULT_PERMISSIONS = List.of(
        permission("profile.self.read", "View own profile", PermissionModule.EMPLOYEE, "View own employee profile"),
        permission("profile.self.update", "Update own profile", PermissionModule.EMPLOYEE, "Update allowed fields in own profile"),
        permission("employee.read", "View employees", PermissionModule.EMPLOYEE, "View employee profiles within assigned scope"),
        permission("employee.manage", "Manage employees", PermissionModule.EMPLOYEE, "Create, update, and soft-delete employees"),
        permission("employee.sensitive.read", "View sensitive employee data", PermissionModule.EMPLOYEE, "View sensitive employee data within assigned scope"),
        permission("employee.sensitive.manage", "Manage sensitive employee data", PermissionModule.EMPLOYEE, "Update sensitive employee data within assigned scope"),
        permission(
            "employee.lifecycle.approve",
            "Approve employee lifecycle",
            PermissionModule.EMPLOYEE,
            "Give final approval for employee lifecycle actions"
        ),

        permission("account.read", "View accounts", PermissionModule.ACCOUNT, "View application accounts"),
        permission("account.manage", "Manage accounts", PermissionModule.ACCOUNT, "Create and manage application accounts"),
        permission(
            "account.activation.manage",
            "Manage account activation",
            PermissionModule.ACCOUNT,
            "Manage account activation and password recovery"
        ),
        permission("account.role.assign", "Assign account roles", PermissionModule.ACCOUNT, "Assign and revoke account roles"),

        permission(
            "organization.change.approve",
            "Approve organization changes",
            PermissionModule.ORGANIZATION,
            "Give final approval for organization structure changes"
        ),
        permission(
            "organization.director.provision",
            "Provision company director",
            PermissionModule.ORGANIZATION,
            "Provision the company director employee identity and account"
        ),

        permission("request.self.read", "View own requests", PermissionModule.REQUEST, "View own employee requests"),
        permission("request.self.create", "Create own requests", PermissionModule.REQUEST, "Create and submit own employee requests"),
        permission("request.self.cancel", "Cancel own requests", PermissionModule.REQUEST, "Cancel own eligible employee requests"),
        permission("request.read", "View employee requests", PermissionModule.REQUEST, "View employee requests within assigned scope"),
        permission("request.approve", "Approve employee requests", PermissionModule.REQUEST, "Approve or reject employee requests"),
        permission("request.final_approve", "Give final request approval", PermissionModule.REQUEST, "Give final approval for escalated employee requests"),
        permission("request.manage", "Manage employee requests", PermissionModule.REQUEST, "Manage employee requests within assigned scope"),

        permission("attendance.self.read", "View own attendance", PermissionModule.ATTENDANCE, "View own attendance records"),
        permission("attendance.read", "View attendance", PermissionModule.ATTENDANCE, "View attendance within assigned scope"),
        permission("attendance.manage", "Manage attendance", PermissionModule.ATTENDANCE, "Create and adjust attendance records"),
        permission(
            "attendance.overtime.approve",
            "Approve overtime",
            PermissionModule.ATTENDANCE,
            "Approve or reject overtime within assigned scope"
        ),

        permission("payroll.self.read", "View own payslips", PermissionModule.PAYROLL, "View own payslips"),
        permission("payroll.self.print", "Download own payslips", PermissionModule.PAYROLL, "Print or download own payslips"),
        permission("compensation.read", "View compensation", PermissionModule.PAYROLL, "View employee compensation within assigned scope"),
        permission("compensation.manage", "Manage compensation", PermissionModule.PAYROLL, "Manage employee compensation within assigned scope"),
        permission("payroll.calculate", "Calculate payroll", PermissionModule.PAYROLL, "Calculate and review payroll"),
        permission("payroll.approve", "Approve payroll", PermissionModule.PAYROLL, "Approve payroll periods"),
        permission("payroll.mark_paid", "Mark payroll as paid", PermissionModule.PAYROLL, "Mark approved payroll as paid"),
        permission("payroll.lock", "Lock payroll", PermissionModule.PAYROLL, "Lock finalized payroll periods"),

        permission("report.hr.read", "View HR reports", PermissionModule.REPORT, "View human resources reports"),
        permission("report.payroll.read", "View payroll reports", PermissionModule.REPORT, "View payroll reports"),
        permission("rbac.manage", "Manage RBAC", PermissionModule.RBAC, "Manage accounts, roles, and permissions")
    );

    private final PermissionRepository permissionRepository;
    private final boolean enabled;

    public PermissionSeeder(
        PermissionRepository permissionRepository,
        @Value("${rbac.seed.enabled:true}") boolean enabled
    ) {
        this.permissionRepository = permissionRepository;
        this.enabled = enabled;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }

        int createdCount = 0;
        for (PermissionDefinition definition : DEFAULT_PERMISSIONS) {
            Permission existingPermission = permissionRepository.findByCode(definition.code()).orElse(null);
            if (existingPermission != null) {
                validatePermission(existingPermission, definition);
                continue;
            }

            permissionRepository.save(Permission.builder()
                .code(definition.code())
                .name(definition.name())
                .module(definition.module())
                .description(definition.description())
                .isActive(true)
                .createdAt(Instant.now())
                .build());
            createdCount++;
        }

        log.info("Permission seed completed: {} created, {} ensured", createdCount, DEFAULT_PERMISSIONS.size());
    }

    private void validatePermission(Permission permission, PermissionDefinition definition) {
        if (permission.getModule() != definition.module() || !Boolean.TRUE.equals(permission.getIsActive())) {
            throw new IllegalStateException(
                definition.code() + " must be active and belong to module " + definition.module()
            );
        }
        permission.setName(definition.name());
    }

    private static PermissionDefinition permission(
        String code,
        String name,
        PermissionModule module,
        String description
    ) {
        return new PermissionDefinition(code, name, module, description);
    }

    private record PermissionDefinition(String code, String name, PermissionModule module, String description) {
    }
}
