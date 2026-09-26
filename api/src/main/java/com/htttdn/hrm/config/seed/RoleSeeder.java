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

import com.htttdn.hrm.entity.Role;
import com.htttdn.hrm.repository.RoleRepository;

/**
 * Khởi tạo các system role chuẩn của HRM trước khi seed permission assignment.
 *
 * <p>Role code không chứa prefix {@code ROLE_}; prefix này chỉ được thêm khi JWT claims
 * được chuyển thành Spring Security authorities. Seeder có tính idempotent: role đã tồn tại
 * sẽ được kiểm tra invariant thay vì insert lại.
 */
@Component
@Order(100)
public class RoleSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RoleSeeder.class);

    private static final List<RoleDefinition> DEFAULT_ROLES = List.of(
        new RoleDefinition("EMPLOYEE", "Employee", "Base self-service role for every employee"),
        new RoleDefinition("TEAM_LEAD", "Team Lead", "Manages employees within an organization unit"),
        new RoleDefinition("WAREHOUSE_SUPERVISOR","Warehouse Supervisor","Supervises employees and attendance within an assigned warehouse"),
        new RoleDefinition("BRANCH_MANAGER", "Branch Manager", "Manages employees and operations within a branch"),
        new RoleDefinition("HR_STAFF", "HR Staff", "Manages human resources operations across the company"),
        new RoleDefinition("PAYROLL_ACCOUNTANT", "Payroll Accountant", "Calculates and reviews company payroll"),
        new RoleDefinition("PAYROLL_APPROVER", "Payroll Approver", "Approves, pays, and locks payroll periods"),
        new RoleDefinition(
            "DIRECTOR",
            "Director",
            "Leads the company and provides final approval for company-wide business decisions"
        ),
        new RoleDefinition("SYSTEM_ADMIN", "System Administrator", "Manages accounts, roles, and permissions")
    );

    private final RoleRepository roleRepository;
    private final boolean enabled;

    public RoleSeeder(
        RoleRepository roleRepository,
        @Value("${rbac.seed.enabled:true}") boolean enabled
    ) {
        this.roleRepository = roleRepository;
        this.enabled = enabled;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }

        int createdCount = 0;
        for (RoleDefinition definition : DEFAULT_ROLES) {
            Role existingRole = roleRepository.findByCodeAndDeletedAtIsNull(definition.code()).orElse(null);
            if (existingRole != null) {
                validateSystemRole(existingRole);
                continue;
            }

            Instant now = Instant.now();
            roleRepository.save(Role.builder()
                .code(definition.code())
                .name(definition.name())
                .description(definition.description())
                .isSystem(true)
                .isActive(true)
                .createdAt(now)
                .updatedAt(now)
                .build());
            createdCount++;
        }

        log.info("Role seed completed: {} created, {} ensured", createdCount, DEFAULT_ROLES.size());
    }

    /** System role có code trùng phải tiếp tục là role hệ thống đang hoạt động. */
    private void validateSystemRole(Role role) {
        if (!Boolean.TRUE.equals(role.getIsSystem()) || !Boolean.TRUE.equals(role.getIsActive())) {
            throw new IllegalStateException(role.getCode() + " must be an active system role");
        }
    }

    private record RoleDefinition(String code, String name, String description) {
    }
}
