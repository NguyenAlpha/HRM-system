package com.htttdn.hrm.config.seed;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.entity.Permission;
import com.htttdn.hrm.entity.enums.PermissionAssignmentPolicy;
import com.htttdn.hrm.entity.enums.PermissionModule;
import com.htttdn.hrm.repository.PermissionRepository;

import lombok.RequiredArgsConstructor;

/**
 * Khởi tạo catalog permission chuẩn được API sử dụng để phân quyền.
 *
 * <p>Permission code được lưu nguyên dạng authority, ví dụ {@code employee.read}; không thêm
 * prefix {@code ROLE_}. Seeder tạo permission còn thiếu, đồng bộ tên, mô tả, chính sách gán và cờ toàn công ty
 * chuẩn, đồng thời từ chối chạy nếu một code chuẩn đã tồn tại với module sai hoặc đã bị vô hiệu hóa.
 *
 * <p>{@link PermissionDefinition#companyWideOnly} đánh dấu permission mà mọi thao tác là việc chung cả công ty
 * (kỳ lương, danh mục tổ chức, tài khoản, RBAC), không chia theo đơn vị/địa điểm được. Role chứa
 * permission này chỉ gán được với scope {@code COMPANY} ({@code RoleAssignmentPolicy}). Permission có thao tác
 * theo từng nhân viên thì không đánh dấu, kể cả khi vài thao tác của nó cần toàn công ty (VD {@code attendance.manage}
 * tạo công cả tháng): các thao tác đó tự gọi {@code EmployeeAccessScopeService.requireCompanyWide}.
 */
@Component
@Order(200)
@RequiredArgsConstructor
public class PermissionSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PermissionSeeder.class);

    private static final List<PermissionDefinition> DEFAULT_PERMISSIONS = List.of(
        permission("profile.self.read", "Xem hồ sơ cá nhân", PermissionModule.EMPLOYEE, "Xem hồ sơ nhân viên của chính mình"),
        permission("profile.self.update", "Cập nhật hồ sơ cá nhân", PermissionModule.EMPLOYEE, "Cập nhật các trường được phép trong hồ sơ của chính mình"),
        permission("employee.list.read", "Xem danh sách nhân viên", PermissionModule.EMPLOYEE, "Xem danh sách nhân viên trong phạm vi được phân công"),
        permission("employee.read", "Xem chi tiết nhân viên", PermissionModule.EMPLOYEE, "Xem chi tiết hồ sơ nhân viên trong phạm vi được phân công"),
        permission("employee.create", "Tạo hồ sơ nhân viên", PermissionModule.EMPLOYEE, "Tạo hồ sơ nhân viên và phân công ban đầu"),
        permission("employee.update", "Cập nhật hồ sơ nhân viên", PermissionModule.EMPLOYEE, "Cập nhật thông tin hồ sơ nhân viên trong phạm vi được phân công"),
        permission("employee.probation.confirm", "Xác nhận hoàn thành thử việc", PermissionModule.EMPLOYEE, "Xác nhận nhân viên thử việc trở thành nhân viên chính thức"),
        permission("employee.assignment.read", "Xem phân công nhân viên", PermissionModule.EMPLOYEE, "Xem phân công hiện tại và lịch sử phân công của nhân viên"),
        permission("employee.assignment.manage", "Quản lý phân công nhân viên", PermissionModule.EMPLOYEE, "Điều chuyển, bổ nhiệm và thay đổi phân công của nhân viên"),
        permission("employee.lifecycle.manage", "Quản lý vòng đời nhân viên", PermissionModule.EMPLOYEE, "Thực hiện nghiệp vụ vòng đời nhân viên đã được phê duyệt"),
        permission("employee.delete", "Xóa hồ sơ nhân viên", PermissionModule.EMPLOYEE, "Xóa mềm hồ sơ nhân viên chưa phát sinh dữ liệu nghiệp vụ"),
        permission("employee.sensitive.read", "Xem dữ liệu nhân sự nhạy cảm", PermissionModule.EMPLOYEE, "Xem dữ liệu nhân sự nhạy cảm trong phạm vi được phân công"),
        permission("employee.sensitive.manage", "Quản lý dữ liệu nhân sự nhạy cảm", PermissionModule.EMPLOYEE, "Cập nhật dữ liệu nhân sự nhạy cảm trong phạm vi được phân công"),

        permission("account.read", "Xem tài khoản", PermissionModule.ACCOUNT, "Xem các tài khoản đăng nhập trong hệ thống").companyWideOnly(),
        permission(
            "account.manage",
            "Quản lý tài khoản",
            PermissionModule.ACCOUNT,
            "Khóa, mở lại và quản lý trạng thái tài khoản đăng nhập"
        ).companyWideOnly(),
        permission(
            "account.provision",
            "Cấp tài khoản nhân viên",
            PermissionModule.ACCOUNT,
            "Tạo tài khoản đăng nhập cho hồ sơ nhân viên hợp lệ"
        ),
        permission(
            "account.activation.manage",
            "Quản lý kích hoạt tài khoản",
            PermissionModule.ACCOUNT,
            "Quản lý kích hoạt tài khoản và khôi phục mật khẩu"
        ).companyWideOnly(),
        permission("account.role.assign", "Phân quyền tài khoản", PermissionModule.ACCOUNT, "Gán và thu hồi vai trò của tài khoản").companyWideOnly(),
        permission(
            "role.assignment.request",
            "Đề xuất cấp vai trò",
            PermissionModule.ACCOUNT,
            "Tạo yêu cầu cấp vai trò nghiệp vụ cho tài khoản nhân viên"
        ),
        systemPermission(
            "account.permission.override.manage",
            "Quản lý ngoại lệ quyền tài khoản",
            PermissionModule.ACCOUNT,
            "Cấp và thu hồi ngoại lệ quyền trên từng lần gán vai trò"
        ).companyWideOnly(),
        systemPermission(
            "role.assignment.approve",
            "Phê duyệt cấp vai trò",
            PermissionModule.ACCOUNT,
            "Phê duyệt hoặc từ chối yêu cầu cấp vai trò nghiệp vụ cho tài khoản nhân viên"
        ).companyWideOnly(),
        systemPermission(
            "system.rate_limit.read",
            "Xem thống kê giới hạn truy cập",
            PermissionModule.ACCOUNT,
            "Xem cấu hình giới hạn truy cập API và số request bị chặn theo IP, tài khoản"
        ).companyWideOnly(),
        systemPermission(
            "system.rate_limit.manage",
            "Chỉnh giới hạn truy cập",
            PermissionModule.ACCOUNT,
            "Sửa hạn mức, bật/tắt từng mức giới hạn truy cập API và khôi phục giá trị mặc định"
        ).companyWideOnly(),
        systemPermission(
            "system.traffic.read",
            "Xem lưu lượng API",
            PermissionModule.ACCOUNT,
            "Xem số request, thời gian xử lý và số lỗi của từng API từ lần khởi động gần nhất"
        ).companyWideOnly(),

        permission(
            "organization.read",
            "Xem danh mục tổ chức",
            PermissionModule.ORGANIZATION,
            "Xem cơ cấu tổ chức, địa điểm làm việc, chức danh và ca làm việc"
        ),
        permission(
            "organization.manage",
            "Quản lý danh mục tổ chức",
            PermissionModule.ORGANIZATION,
            "Tạo, cập nhật, ngừng sử dụng và xóa mềm danh mục tổ chức và ca làm việc"
        ).companyWideOnly(),
        systemPermission(
            "organization.company_owner.bootstrap",
            "Khởi tạo Chủ sở hữu doanh nghiệp",
            PermissionModule.ORGANIZATION,
            "Khởi tạo tài khoản Chủ sở hữu doanh nghiệp đầu tiên của công ty"
        ).companyWideOnly(),
        systemPermission(
            "company.manage",
            "Cập nhật thông tin doanh nghiệp",
            PermissionModule.ORGANIZATION,
            "Cập nhật tên, mã số thuế, liên hệ và địa chỉ của doanh nghiệp"
        ).companyWideOnly(),
        permission("request.self.read", "Xem đơn cá nhân", PermissionModule.REQUEST, "Xem các đơn từ của chính mình"),
        permission("request.self.create", "Tạo đơn cá nhân", PermissionModule.REQUEST, "Tạo và gửi đơn từ của chính mình"),
        permission("request.self.cancel", "Hủy đơn cá nhân", PermissionModule.REQUEST, "Hủy các đơn của chính mình khi còn đủ điều kiện"),
        permission("request.read", "Xem đơn của nhân viên", PermissionModule.REQUEST, "Xem đơn từ của nhân viên trong phạm vi được phân công"),
        permission("request.manage", "Quản lý đơn của nhân viên", PermissionModule.REQUEST, "Quản lý đơn từ của nhân viên trong phạm vi được phân công"),

        permission("attendance.self.read", "Xem chấm công cá nhân", PermissionModule.ATTENDANCE, "Xem dữ liệu chấm công của chính mình"),
        permission("attendance.read", "Xem dữ liệu chấm công", PermissionModule.ATTENDANCE, "Xem dữ liệu chấm công trong phạm vi được phân công"),
        permission("attendance.manage", "Quản lý chấm công", PermissionModule.ATTENDANCE, "Tạo và điều chỉnh dữ liệu chấm công"),
        permission("holiday.read", "Xem lịch nghỉ lễ", PermissionModule.ATTENDANCE, "Xem các ngày nghỉ lễ, Tết của công ty"),

        permission("payroll.self.read", "Xem phiếu lương cá nhân", PermissionModule.PAYROLL, "Xem phiếu lương của chính mình"),
        permission("compensation.read", "Xem chế độ đãi ngộ", PermissionModule.PAYROLL, "Xem lương và chế độ đãi ngộ trong phạm vi được phân công"),
        permission("compensation.manage", "Quản lý chế độ đãi ngộ", PermissionModule.PAYROLL, "Quản lý lương và chế độ đãi ngộ trong phạm vi được phân công"),
        permission("payroll.calculate", "Tính bảng lương", PermissionModule.PAYROLL, "Tính toán và kiểm tra bảng lương").companyWideOnly(),
        permission("payroll.approve", "Phê duyệt bảng lương", PermissionModule.PAYROLL, "Phê duyệt các kỳ lương").companyWideOnly(),
        permission("payroll.mark_paid", "Xác nhận đã trả lương", PermissionModule.PAYROLL, "Đánh dấu bảng lương đã được thanh toán").companyWideOnly(),
        permission("payroll.lock", "Khóa kỳ lương", PermissionModule.PAYROLL, "Khóa các kỳ lương đã hoàn tất").companyWideOnly(),

        permission("report.hr.read", "Xem báo cáo nhân sự", PermissionModule.REPORT, "Xem các báo cáo về nhân sự"),
        permission("report.payroll.read", "Xem báo cáo tiền lương", PermissionModule.REPORT, "Xem các báo cáo về tiền lương"),
        permission(
            "rbac.manage",
            "Quản lý phân quyền",
            PermissionModule.RBAC,
            "Xem danh mục quyền và quản lý vai trò tùy chỉnh cùng quan hệ phân quyền"
        ).companyWideOnly()
    );

    private final PermissionRepository permissionRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
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
                .assignmentPolicy(definition.assignmentPolicy())
                .companyWide(definition.companyWide())
                .isActive(true)
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
        if (permission.getName().equals(definition.name())
            && permission.getDescription().equals(definition.description())
            && permission.getAssignmentPolicy() == definition.assignmentPolicy()
            && permission.isCompanyWide() == definition.companyWide()) {
            return;
        }
        permission.setName(definition.name());
        permission.setDescription(definition.description());
        permission.setAssignmentPolicy(definition.assignmentPolicy());
        permission.setCompanyWide(definition.companyWide());
    }

    private static PermissionDefinition permission(
        String code,
        String name,
        PermissionModule module,
        String description
    ) {
        return new PermissionDefinition(
            code,
            name,
            module,
            description,
            PermissionAssignmentPolicy.DELEGABLE,
            false
        );
    }

    private static PermissionDefinition systemPermission(
        String code,
        String name,
        PermissionModule module,
        String description
    ) {
        return new PermissionDefinition(
            code,
            name,
            module,
            description,
            PermissionAssignmentPolicy.SYSTEM_ONLY,
            false
        );
    }

    private record PermissionDefinition(
        String code,
        String name,
        PermissionModule module,
        String description,
        PermissionAssignmentPolicy assignmentPolicy,
        boolean companyWide
    ) {
        /** Bản sao đánh dấu permission chỉ có nghĩa ở phạm vi toàn công ty. */
        PermissionDefinition companyWideOnly() {
            return new PermissionDefinition(code, name, module, description, assignmentPolicy, true);
        }
    }
}
