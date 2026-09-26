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
        permission("profile.self.read", "Xem hồ sơ cá nhân", PermissionModule.EMPLOYEE, "Xem hồ sơ nhân viên của chính mình"),
        permission("profile.self.update", "Cập nhật hồ sơ cá nhân", PermissionModule.EMPLOYEE, "Cập nhật các trường được phép trong hồ sơ của chính mình"),
        permission("employee.read", "Xem hồ sơ nhân viên", PermissionModule.EMPLOYEE, "Xem hồ sơ nhân viên trong phạm vi được phân công"),
        permission("employee.manage", "Quản lý hồ sơ nhân viên", PermissionModule.EMPLOYEE, "Tạo, cập nhật và xóa mềm hồ sơ nhân viên"),
        permission("employee.sensitive.read", "Xem dữ liệu nhân sự nhạy cảm", PermissionModule.EMPLOYEE, "Xem dữ liệu nhân sự nhạy cảm trong phạm vi được phân công"),
        permission("employee.sensitive.manage", "Quản lý dữ liệu nhân sự nhạy cảm", PermissionModule.EMPLOYEE, "Cập nhật dữ liệu nhân sự nhạy cảm trong phạm vi được phân công"),
        permission(
            "employee.lifecycle.approve",
            "Phê duyệt vòng đời nhân viên",
            PermissionModule.EMPLOYEE,
            "Phê duyệt cuối các nghiệp vụ trong vòng đời nhân viên"
        ),

        permission("account.read", "Xem tài khoản", PermissionModule.ACCOUNT, "Xem các tài khoản đăng nhập trong hệ thống"),
        permission("account.manage", "Quản lý tài khoản", PermissionModule.ACCOUNT, "Tạo và quản lý tài khoản đăng nhập"),
        permission(
            "account.activation.manage",
            "Quản lý kích hoạt tài khoản",
            PermissionModule.ACCOUNT,
            "Quản lý kích hoạt tài khoản và khôi phục mật khẩu"
        ),
        permission("account.role.assign", "Phân quyền tài khoản", PermissionModule.ACCOUNT, "Gán và thu hồi vai trò của tài khoản"),

        permission(
            "organization.change.approve",
            "Phê duyệt thay đổi cơ cấu",
            PermissionModule.ORGANIZATION,
            "Phê duyệt cuối các thay đổi về cơ cấu tổ chức"
        ),
        permission(
            "organization.director.provision",
            "Khởi tạo Giám đốc",
            PermissionModule.ORGANIZATION,
            "Khởi tạo hồ sơ nhân viên, tài khoản và vai trò Giám đốc công ty"
        ),

        permission("request.self.read", "Xem đơn cá nhân", PermissionModule.REQUEST, "Xem các đơn từ của chính mình"),
        permission("request.self.create", "Tạo đơn cá nhân", PermissionModule.REQUEST, "Tạo và gửi đơn từ của chính mình"),
        permission("request.self.cancel", "Hủy đơn cá nhân", PermissionModule.REQUEST, "Hủy các đơn của chính mình khi còn đủ điều kiện"),
        permission("request.read", "Xem đơn của nhân viên", PermissionModule.REQUEST, "Xem đơn từ của nhân viên trong phạm vi được phân công"),
        permission("request.approve", "Duyệt đơn của nhân viên", PermissionModule.REQUEST, "Phê duyệt hoặc từ chối đơn từ của nhân viên"),
        permission("request.final_approve", "Phê duyệt cuối đơn từ", PermissionModule.REQUEST, "Phê duyệt cuối các đơn từ được trình lên cấp cao hơn"),
        permission("request.manage", "Quản lý đơn của nhân viên", PermissionModule.REQUEST, "Quản lý đơn từ của nhân viên trong phạm vi được phân công"),

        permission("attendance.self.read", "Xem chấm công cá nhân", PermissionModule.ATTENDANCE, "Xem dữ liệu chấm công của chính mình"),
        permission("attendance.read", "Xem dữ liệu chấm công", PermissionModule.ATTENDANCE, "Xem dữ liệu chấm công trong phạm vi được phân công"),
        permission("attendance.manage", "Quản lý chấm công", PermissionModule.ATTENDANCE, "Tạo và điều chỉnh dữ liệu chấm công"),
        permission(
            "attendance.overtime.approve",
            "Phê duyệt làm thêm giờ",
            PermissionModule.ATTENDANCE,
            "Phê duyệt hoặc từ chối làm thêm giờ trong phạm vi được phân công"
        ),

        permission("payroll.self.read", "Xem phiếu lương cá nhân", PermissionModule.PAYROLL, "Xem phiếu lương của chính mình"),
        permission("payroll.self.print", "Tải phiếu lương cá nhân", PermissionModule.PAYROLL, "In hoặc tải phiếu lương của chính mình"),
        permission("compensation.read", "Xem chế độ đãi ngộ", PermissionModule.PAYROLL, "Xem lương và chế độ đãi ngộ trong phạm vi được phân công"),
        permission("compensation.manage", "Quản lý chế độ đãi ngộ", PermissionModule.PAYROLL, "Quản lý lương và chế độ đãi ngộ trong phạm vi được phân công"),
        permission("payroll.calculate", "Tính bảng lương", PermissionModule.PAYROLL, "Tính toán và kiểm tra bảng lương"),
        permission("payroll.approve", "Phê duyệt bảng lương", PermissionModule.PAYROLL, "Phê duyệt các kỳ lương"),
        permission("payroll.mark_paid", "Xác nhận đã trả lương", PermissionModule.PAYROLL, "Đánh dấu bảng lương đã được thanh toán"),
        permission("payroll.lock", "Khóa kỳ lương", PermissionModule.PAYROLL, "Khóa các kỳ lương đã hoàn tất"),

        permission("report.hr.read", "Xem báo cáo nhân sự", PermissionModule.REPORT, "Xem các báo cáo về nhân sự"),
        permission("report.payroll.read", "Xem báo cáo tiền lương", PermissionModule.REPORT, "Xem các báo cáo về tiền lương"),
        permission("rbac.manage", "Quản lý vai trò và quyền", PermissionModule.RBAC, "Quản lý danh mục vai trò, quyền và quan hệ phân quyền")
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
        permission.setDescription(definition.description());
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
