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
 * được kiểm tra invariant và đồng bộ lại tên, mô tả chuẩn thay vì insert lại.
 */
@Component
@Order(100)
public class RoleSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RoleSeeder.class);

    private static final List<RoleDefinition> DEFAULT_ROLES = List.of(
        new RoleDefinition("EMPLOYEE", "Nhân viên", "Vai trò tự phục vụ cơ bản dành cho mọi nhân viên"),
        new RoleDefinition("TEAM_LEAD", "Trưởng nhóm", "Quản lý nhân viên trong đơn vị tổ chức được phân công"),
        new RoleDefinition(
            "WAREHOUSE_SUPERVISOR",
            "Giám sát kho",
            "Giám sát nhân viên và chấm công tại kho được phân công"
        ),
        new RoleDefinition(
            "BRANCH_MANAGER",
            "Quản lý chi nhánh",
            "Quản lý nhân viên và hoạt động trong chi nhánh được phân công"
        ),
        new RoleDefinition("HR_STAFF", "Nhân viên nhân sự", "Quản lý nghiệp vụ nhân sự trên toàn công ty"),
        new RoleDefinition(
            "PAYROLL_ACCOUNTANT",
            "Kế toán tiền lương",
            "Tính toán và kiểm tra bảng lương toàn công ty"
        ),
        new RoleDefinition(
            "PAYROLL_APPROVER",
            "Người duyệt bảng lương",
            "Phê duyệt, xác nhận thanh toán và khóa kỳ lương"
        ),
        new RoleDefinition(
            "DIRECTOR",
            "Giám đốc",
            "Điều hành công ty và phê duyệt cuối các quyết định nghiệp vụ trên toàn công ty"
        ),
        new RoleDefinition(
            "SYSTEM_ADMIN",
            "Quản trị viên hệ thống",
            "Quản lý tài khoản, vai trò và quyền trong giai đoạn quản trị hệ thống"
        )
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
                existingRole.setName(definition.name());
                existingRole.setDescription(definition.description());
                existingRole.setUpdatedAt(Instant.now());
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
