package com.htttdn.hrm.config.seed;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.entity.Role;
import com.htttdn.hrm.entity.enums.RoleGrantPolicy;
import com.htttdn.hrm.repository.RoleRepository;

import lombok.RequiredArgsConstructor;

/**
 * Khởi tạo các system role chuẩn của HRM trước khi seed permission assignment.
 *
 * <p>Role code không chứa prefix {@code ROLE_}; prefix này chỉ được thêm khi JWT claims
 * được chuyển thành Spring Security authorities. Seeder có tính idempotent: role đã tồn tại
 * được kiểm tra invariant và đồng bộ lại tên, mô tả chuẩn thay vì insert lại.
 */
@Component
@Order(100)
@RequiredArgsConstructor
public class RoleSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RoleSeeder.class);

    private static final List<RoleDefinition> DEFAULT_ROLES = List.of(
        new RoleDefinition(
            "EMPLOYEE",
            "Nhân viên",
            "Vai trò tự phục vụ cơ bản dành cho mọi nhân viên",
            RoleGrantPolicy.AUTO
        ),
        new RoleDefinition(
            "HR_MANAGER",
            "Quản lý nhân sự",
            "Quản lý nghiệp vụ nhân sự trên toàn công ty",
            RoleGrantPolicy.OWNER_APPROVAL
        ),
        new RoleDefinition(
            "MANAGER",
            "Quản lý đơn vị",
            "Theo dõi nhân sự, chấm công và đơn nghỉ trong phạm vi chi nhánh hoặc phòng ban được giao",
            RoleGrantPolicy.HR_ASSIGNABLE
        ),
        new RoleDefinition(
            "PAYROLL_ACCOUNTANT",
            "Kế toán tiền lương",
            "Tính toán và kiểm tra bảng lương toàn công ty",
            RoleGrantPolicy.OWNER_APPROVAL
        ),
        new RoleDefinition(
            "PAYROLL_APPROVER",
            "Người duyệt bảng lương",
            "Phê duyệt, xác nhận thanh toán và khóa kỳ lương",
            RoleGrantPolicy.OWNER_APPROVAL
        ),
        new RoleDefinition(
            "DIRECTOR",
            "Giám đốc",
            "Điều hành công ty và phê duyệt cuối các quyết định nghiệp vụ trên toàn công ty",
            RoleGrantPolicy.OWNER_APPROVAL
        ),
        new RoleDefinition(
            "COMPANY_OWNER",
            "Chủ sở hữu doanh nghiệp",
            "Quản trị tài khoản, quyền truy cập và cấu hình trong phạm vi doanh nghiệp",
            RoleGrantPolicy.SYSTEM_ONLY
        ),
        new RoleDefinition(
            "SYSTEM_ADMIN",
            "Quản trị viên hệ thống",
            "Khởi tạo Chủ sở hữu doanh nghiệp và tạm thời quản lý RBAC hệ thống",
            RoleGrantPolicy.SYSTEM_ONLY
        )
    );

    private final RoleRepository roleRepository;
    private final RbacSeedProperties properties;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.enabled()) {
            return;
        }

        int createdCount = 0;
        for (RoleDefinition definition : DEFAULT_ROLES) {
            Role existingRole = roleRepository.findByCodeAndDeletedAtIsNull(definition.code()).orElse(null);
            if (existingRole != null) {
                validateSystemRole(existingRole);
                existingRole.setName(definition.name());
                existingRole.setDescription(definition.description());
                existingRole.setGrantPolicy(definition.grantPolicy());
                existingRole.setUpdatedAt(Instant.now());
                continue;
            }

            Instant now = Instant.now();
            roleRepository.save(Role.builder()
                .code(definition.code())
                .name(definition.name())
                .description(definition.description())
                .isSystem(true)
                .grantPolicy(definition.grantPolicy())
                .createdAt(now)
                .updatedAt(now)
                .build());
            createdCount++;
        }

        log.info("Role seed completed: {} created, {} ensured", createdCount, DEFAULT_ROLES.size());
    }

    /** System role có code trùng phải tiếp tục là role hệ thống chưa bị xóa mềm. */
    private void validateSystemRole(Role role) {
        if (!Boolean.TRUE.equals(role.getIsSystem())) {
            throw new IllegalStateException(role.getCode() + " must be a system role");
        }
    }

    private record RoleDefinition(
        String code,
        String name,
        String description,
        RoleGrantPolicy grantPolicy
    ) {
    }
}
