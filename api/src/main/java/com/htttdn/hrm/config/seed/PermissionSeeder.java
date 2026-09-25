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
 * prefix {@code ROLE_}. Seeder chỉ tạo permission còn thiếu và từ chối chạy nếu một code chuẩn
 * đã tồn tại với module sai hoặc đã bị vô hiệu hóa.
 */
@Component
@Order(200)
public class PermissionSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PermissionSeeder.class);

    private static final List<PermissionDefinition> DEFAULT_PERMISSIONS = List.of(
        permission("profile.self.read", PermissionModule.EMPLOYEE, "View own employee profile"),
        permission("profile.self.update", PermissionModule.EMPLOYEE, "Update allowed fields in own profile"),
        permission("employee.read", PermissionModule.EMPLOYEE, "View employee profiles within assigned scope"),
        permission("employee.manage", PermissionModule.EMPLOYEE, "Create, update, and soft-delete employees"),
        permission("employee.sensitive.read", PermissionModule.EMPLOYEE, "View sensitive employee data within assigned scope"),
        permission("employee.sensitive.manage", PermissionModule.EMPLOYEE, "Update sensitive employee data within assigned scope"),

        permission("request.self.read", PermissionModule.REQUEST, "View own employee requests"),
        permission("request.self.create", PermissionModule.REQUEST, "Create and submit own employee requests"),
        permission("request.self.cancel", PermissionModule.REQUEST, "Cancel own eligible employee requests"),
        permission("request.read", PermissionModule.REQUEST, "View employee requests within assigned scope"),
        permission("request.approve", PermissionModule.REQUEST, "Approve or reject employee requests"),
        permission("request.manage", PermissionModule.REQUEST, "Manage employee requests within assigned scope"),

        permission("attendance.self.read", PermissionModule.ATTENDANCE, "View own attendance records"),
        permission("attendance.read", PermissionModule.ATTENDANCE, "View attendance within assigned scope"),
        permission("attendance.manage", PermissionModule.ATTENDANCE, "Create and adjust attendance records"),
        permission(
            "attendance.overtime.approve",
            PermissionModule.ATTENDANCE,
            "Approve or reject overtime within assigned scope"
        ),

        permission("payroll.self.read", PermissionModule.PAYROLL, "View own payslips"),
        permission("payroll.self.print", PermissionModule.PAYROLL, "Print or download own payslips"),
        permission("compensation.read", PermissionModule.PAYROLL, "View employee compensation within assigned scope"),
        permission("compensation.manage", PermissionModule.PAYROLL, "Manage employee compensation within assigned scope"),
        permission("payroll.calculate", PermissionModule.PAYROLL, "Calculate and review payroll"),
        permission("payroll.approve", PermissionModule.PAYROLL, "Approve payroll periods"),
        permission("payroll.mark_paid", PermissionModule.PAYROLL, "Mark approved payroll as paid"),
        permission("payroll.lock", PermissionModule.PAYROLL, "Lock finalized payroll periods"),

        permission("report.hr.read", PermissionModule.REPORT, "View human resources reports"),
        permission("report.payroll.read", PermissionModule.REPORT, "View payroll reports"),
        permission("rbac.manage", PermissionModule.RBAC, "Manage accounts, roles, and permissions")
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
    }

    private static PermissionDefinition permission(String code, PermissionModule module, String description) {
        return new PermissionDefinition(code, module, description);
    }

    private record PermissionDefinition(String code, PermissionModule module, String description) {
    }
}
