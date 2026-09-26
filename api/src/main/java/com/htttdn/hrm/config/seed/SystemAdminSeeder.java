package com.htttdn.hrm.config.seed;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.AccountRoleAssignment;
import com.htttdn.hrm.entity.Permission;
import com.htttdn.hrm.entity.Role;
import com.htttdn.hrm.entity.RolePermission;
import com.htttdn.hrm.entity.RolePermissionId;
import com.htttdn.hrm.entity.enums.AccountStatus;
import com.htttdn.hrm.entity.enums.PermissionModule;
import com.htttdn.hrm.entity.enums.RoleScopeType;
import com.htttdn.hrm.repository.AccountRepository;
import com.htttdn.hrm.repository.AccountRoleAssignmentRepository;
import com.htttdn.hrm.repository.PermissionRepository;
import com.htttdn.hrm.repository.RolePermissionRepository;
import com.htttdn.hrm.repository.RoleRepository;

/**
 * Khởi tạo tài khoản quản trị hệ thống và dữ liệu RBAC tối thiểu khi ứng dụng khởi động.
 *
 * <p>Seeder chỉ chạy khi {@code admin.seed.enabled=true}. Thông tin đăng nhập được đọc từ
 * nhóm cấu hình {@code admin.seed.*}; mật khẩu luôn được hash bằng {@link PasswordEncoder}
 * trước khi lưu, không ghi mật khẩu rõ vào database hoặc log.
 *
 * <h3>Dữ liệu được bảo đảm tồn tại</h3>
 * <ol>
 *   <li>Tài khoản admin ở trạng thái {@link AccountStatus#ACTIVE}.</li>
 *   <li>System role {@code SYSTEM_ADMIN}.</li>
 *   <li>Permission {@code rbac.manage} thuộc module {@link PermissionModule#RBAC}.</li>
 *   <li>Quan hệ role–permission và role assignment phạm vi {@link RoleScopeType#COMPANY}.</li>
 * </ol>
 *
 * <p>Các bước đều kiểm tra dữ liệu trước khi insert nên có thể chạy lại sau mỗi lần restart
 * mà không tạo bản ghi trùng. Toàn bộ quá trình nằm trong một transaction; nếu bất kỳ bước
 * nào thất bại thì các thay đổi của lần seed hiện tại được rollback.
 */
@Component
@Order(400)
public class SystemAdminSeeder implements ApplicationRunner {

    /** Code ổn định dùng để tra cứu system role trong database và JWT. */
    static final String SYSTEM_ADMIN_ROLE_CODE = "SYSTEM_ADMIN";

    /** Permission tối thiểu cho phép admin quản lý account, role và permission. */
    static final String RBAC_MANAGE_PERMISSION_CODE = "rbac.manage";

    private static final Logger log = LoggerFactory.getLogger(SystemAdminSeeder.class);

    private final AccountRepository accountRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final AccountRoleAssignmentRepository accountRoleAssignmentRepository;
    private final PasswordEncoder passwordEncoder;

    /** Bật/tắt seeder, giúp môi trường không cần bootstrap admin có thể bỏ qua hoàn toàn. */
    private final boolean enabled;

    /** Các thông tin định danh và mật khẩu lấy từ cấu hình {@code admin.seed.*}. */
    private final String username;
    private final String email;
    private final String password;

    public SystemAdminSeeder(
        AccountRepository accountRepository,
        RoleRepository roleRepository,
        PermissionRepository permissionRepository,
        RolePermissionRepository rolePermissionRepository,
        AccountRoleAssignmentRepository accountRoleAssignmentRepository,
        PasswordEncoder passwordEncoder,
        @Value("${admin.seed.enabled:false}") boolean enabled,
        @Value("${admin.seed.username:admin}") String username,
        @Value("${admin.seed.email:admin@hrm.local}") String email,
        @Value("${admin.seed.password:}") String password
    ) {
        this.accountRepository = accountRepository;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.accountRoleAssignmentRepository = accountRoleAssignmentRepository;
        this.passwordEncoder = passwordEncoder;
        this.enabled = enabled;
        this.username = username;
        this.email = email;
        this.password = password;
    }

    /**
     * Điểm vào được Spring Boot gọi sau khi {@code ApplicationContext} khởi tạo thành công.
     *
     * <p>Thứ tự tạo dữ liệu rất quan trọng: account, role và permission phải có ID trước khi
     * tạo hai bảng liên kết {@link RolePermission} và {@link AccountRoleAssignment}.
     */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }

        validateConfiguration();

        String normalizedUsername = username.trim();
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        Account admin = findOrCreateAdmin(normalizedUsername, normalizedEmail);
        Role systemAdminRole = findOrCreateSystemAdminRole();
        Permission rbacManagePermission = findOrCreateRbacManagePermission();

        grantPermissionIfMissing(admin, systemAdminRole, rbacManagePermission);
        assignRoleIfMissing(admin, systemAdminRole);

        log.info("System admin seed completed for username '{}'", admin.getUsername());
    }

    /**
     * Kiểm tra cấu hình trước khi ghi dữ liệu để ứng dụng fail-fast với thông báo rõ ràng.
     * Các giới hạn độ dài tương ứng với constraint của bảng {@code accounts}.
     */
    private void validateConfiguration() {
        if (isBlank(username) || isBlank(email) || isBlank(password)) {
            throw new IllegalStateException(
                "Admin seed requires admin.seed.username, admin.seed.email, and admin.seed.password"
            );
        }
        if (username.trim().length() > 50) {
            throw new IllegalStateException("admin.seed.username must not exceed 50 characters");
        }
        if (email.trim().length() > 100) {
            throw new IllegalStateException("admin.seed.email must not exceed 100 characters");
        }
        if (password.length() < 8 || password.length() > 100) {
            throw new IllegalStateException("admin.seed.password must contain between 8 and 100 characters");
        }
    }

    /**
     * Tìm account theo cả username và email; chỉ tạo mới khi cả hai đều chưa tồn tại.
     *
     * <p>Username được trim và email được chuẩn hóa lowercase từ {@link #run(ApplicationArguments)}
     * trước khi truyền vào đây. Password chỉ được encode khi tạo mới nên restart ứng dụng
     * không làm thay đổi mật khẩu của admin đang tồn tại.
     */
    private Account findOrCreateAdmin(String normalizedUsername, String normalizedEmail) {
        Optional<Account> accountByUsername = accountRepository.findByUsername(normalizedUsername);
        Optional<Account> accountByEmail = accountRepository.findByEmail(normalizedEmail);

        if (accountByUsername.isPresent() || accountByEmail.isPresent()) {
            return resolveExistingAccount(accountByUsername, accountByEmail, normalizedUsername, normalizedEmail);
        }

        Instant now = Instant.now();
        return accountRepository.save(Account.builder()
            .username(normalizedUsername)
            .email(normalizedEmail)
            .passwordHash(passwordEncoder.encode(password))
            .status(AccountStatus.ACTIVE)
            .failedLoginCount(0)
            .createdAt(now)
            .updatedAt(now)
            .build());
    }

    /**
     * Xác nhận username và email cấu hình cùng trỏ đến đúng một account.
     *
     * <p>Không tự động chiếm dụng một account chỉ trùng username hoặc email vì có thể vô tình
     * cấp quyền quản trị cho người dùng đã tồn tại. Trường hợp xung đột làm startup thất bại
     * để người vận hành sửa cấu hình một cách tường minh.
     */
    private Account resolveExistingAccount(
        Optional<Account> accountByUsername,
        Optional<Account> accountByEmail,
        String normalizedUsername,
        String normalizedEmail
    ) {
        if (accountByUsername.isEmpty() || accountByEmail.isEmpty()
            || !accountByUsername.get().getId().equals(accountByEmail.get().getId())) {
            throw new IllegalStateException(
                "Admin seed username or email belongs to a different existing account"
            );
        }

        Account account = accountByUsername.get();
        if (!normalizedUsername.equals(account.getUsername()) || !normalizedEmail.equals(account.getEmail())) {
            throw new IllegalStateException("Existing admin account does not match the configured credentials");
        }
        return account;
    }

    /**
     * Trả về role {@code SYSTEM_ADMIN} đang hoạt động hoặc tạo system role nếu chưa tồn tại.
     * Role đã soft-delete không được tái sử dụng vì repository chỉ tìm bản ghi chưa bị xóa.
     */
    private Role findOrCreateSystemAdminRole() {
        return roleRepository.findByCodeAndDeletedAtIsNull(SYSTEM_ADMIN_ROLE_CODE)
            .map(this::validateSystemAdminRole)
            .orElseGet(() -> {
                Instant now = Instant.now();
                return roleRepository.save(Role.builder()
                    .code(SYSTEM_ADMIN_ROLE_CODE)
                    .name("System Administrator")
                    .description("Manages accounts, roles, and permissions")
                    .isSystem(true)
                    .isActive(true)
                    .createdAt(now)
                    .updatedAt(now)
                    .build());
            });
    }

    /** Bảo vệ invariant: role bootstrap phải là system role và đang hoạt động. */
    private Role validateSystemAdminRole(Role role) {
        if (!Boolean.TRUE.equals(role.getIsSystem()) || !Boolean.TRUE.equals(role.getIsActive())) {
            throw new IllegalStateException("SYSTEM_ADMIN role must be an active system role");
        }
        return role;
    }

    /**
     * Trả về permission {@code rbac.manage} hoặc tạo permission RBAC tối thiểu nếu chưa có.
     */
    private Permission findOrCreateRbacManagePermission() {
        return permissionRepository.findByCode(RBAC_MANAGE_PERMISSION_CODE)
            .map(this::validateRbacManagePermission)
            .orElseGet(() -> permissionRepository.save(Permission.builder()
                .code(RBAC_MANAGE_PERMISSION_CODE)
                .name("Manage RBAC")
                .module(PermissionModule.RBAC)
                .description("Manage accounts, roles, and permissions")
                .isActive(true)
                .createdAt(Instant.now())
                .build()));
    }

    /** Bảo vệ invariant: permission bootstrap phải thuộc module RBAC và đang hoạt động. */
    private Permission validateRbacManagePermission(Permission permission) {
        if (permission.getModule() != PermissionModule.RBAC || !Boolean.TRUE.equals(permission.getIsActive())) {
            throw new IllegalStateException("rbac.manage permission must be active and belong to RBAC module");
        }
        return permission;
    }

    /**
     * Cấp {@code rbac.manage} cho {@code SYSTEM_ADMIN} nếu cặp role–permission chưa tồn tại.
     * {@link RolePermissionId} là khóa ghép nên cũng ngăn một permission được gán trùng cho role.
     */
    private void grantPermissionIfMissing(Account admin, Role role, Permission permission) {
        RolePermissionId id = new RolePermissionId(role.getId(), permission.getId());
        if (rolePermissionRepository.existsById(id)) {
            return;
        }

        rolePermissionRepository.save(RolePermission.builder()
            .id(id)
            .role(role)
            .permission(permission)
            .createdByAccount(admin)
            .createdAt(Instant.now())
            .build());
    }

    /**
     * Gán role quản trị cho account ở phạm vi toàn công ty nếu chưa có assignment còn hiệu lực.
     * Assignment bootstrap bắt đầu từ ngày hiện tại và để {@code effectiveTo=null}, nghĩa là
     * tiếp tục có hiệu lực cho đến khi bị thu hồi tường minh.
     */
    private void assignRoleIfMissing(Account admin, Role role) {
        if (accountRoleAssignmentRepository.existsByAccountIdAndRoleIdAndScopeTypeAndEffectiveToIsNull(
            admin.getId(), role.getId(), RoleScopeType.COMPANY)) {
            return;
        }

        accountRoleAssignmentRepository.save(AccountRoleAssignment.builder()
            .account(admin)
            .role(role)
            .scopeType(RoleScopeType.COMPANY)
            .effectiveFrom(LocalDate.now())
            .grantedByAccount(admin)
            .reason("Bootstrap system administrator")
            .createdAt(Instant.now())
            .build());
    }

    /** Kiểm tra chuỗi null, rỗng hoặc chỉ chứa khoảng trắng. */
    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
