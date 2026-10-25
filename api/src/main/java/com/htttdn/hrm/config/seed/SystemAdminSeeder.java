package com.htttdn.hrm.config.seed;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.AccountRoleAssignment;
import com.htttdn.hrm.entity.Role;
import com.htttdn.hrm.entity.enums.AccountStatus;
import com.htttdn.hrm.entity.enums.RoleScopeType;
import com.htttdn.hrm.repository.AccountRepository;
import com.htttdn.hrm.repository.AccountRoleAssignmentRepository;
import com.htttdn.hrm.repository.RoleRepository;

import lombok.RequiredArgsConstructor;

/**
 * Khởi tạo account quản trị hệ thống và gán role {@code SYSTEM_ADMIN} đã được seed trước đó.
 *
 * <p>{@link RoleSeeder}, {@link PermissionSeeder} và {@link RolePermissionSeeder} lần lượt
 * chạy ở order 100, 200 và 300. Seeder này chạy ở order 400 nên không tạo hoặc chỉnh sửa
 * role, permission hay role-permission; thiếu role chuẩn sẽ làm ứng dụng fail-fast.
 *
 * <p>Thông tin đăng nhập được đọc từ {@code admin.seed.*}. Mật khẩu chỉ được hash khi tạo
 * account mới và không được ghi dạng rõ vào database hoặc log.
 */
@Component
@Order(400)
@RequiredArgsConstructor
public class SystemAdminSeeder implements ApplicationRunner {

    static final String SYSTEM_ADMIN_ROLE_CODE = "SYSTEM_ADMIN";

    private static final Logger log = LoggerFactory.getLogger(SystemAdminSeeder.class);

    private final AccountRepository accountRepository;
    private final RoleRepository roleRepository;
    private final AccountRoleAssignmentRepository accountRoleAssignmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminSeedProperties properties;
    private final Clock clock;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.enabled()) {
            return;
        }

        validateConfiguration();
        Role systemAdminRole = findRequiredSystemAdminRole();
        Account admin = findOrCreateAdmin(
            properties.username().trim(),
            properties.email().trim().toLowerCase(Locale.ROOT)
        );
        assignRoleIfMissing(admin, systemAdminRole);

        log.info("System admin seed completed for username '{}'", admin.getUsername());
    }

    private void validateConfiguration() {
        String username = properties.username();
        String email = properties.email();
        String password = properties.password();
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

    private Role findRequiredSystemAdminRole() {
        Role role = roleRepository.findByCodeAndDeletedAtIsNull(SYSTEM_ADMIN_ROLE_CODE)
            .orElseThrow(() -> new IllegalStateException("SYSTEM_ADMIN seed role not found"));
        if (!Boolean.TRUE.equals(role.getIsSystem())) {
            throw new IllegalStateException("SYSTEM_ADMIN role must be a system role");
        }
        return role;
    }

    private Account findOrCreateAdmin(String normalizedUsername, String normalizedEmail) {
        Optional<Account> accountByUsername = accountRepository.findByUsername(normalizedUsername);
        Optional<Account> accountByEmail = accountRepository.findByEmail(normalizedEmail);

        if (accountByUsername.isPresent() || accountByEmail.isPresent()) {
            return resolveExistingAccount(
                accountByUsername,
                accountByEmail,
                normalizedUsername,
                normalizedEmail
            );
        }
        return accountRepository.save(Account.builder()
            .username(normalizedUsername)
            .email(normalizedEmail)
            .passwordHash(passwordEncoder.encode(properties.password()))
            .status(AccountStatus.ACTIVE)
            .failedLoginCount(0)
            .build());
    }

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

    private void assignRoleIfMissing(Account admin, Role role) {
        if (accountRoleAssignmentRepository.existsByAccountIdAndRoleIdAndScopeTypeAndEffectiveToIsNull(
            admin.getId(), role.getId(), RoleScopeType.COMPANY)) {
            return;
        }

        accountRoleAssignmentRepository.save(AccountRoleAssignment.builder()
            .account(admin)
            .role(role)
            .scopeType(RoleScopeType.COMPANY)
            .effectiveFrom(LocalDate.now(clock))
            .grantedByAccount(admin)
            .reason("Bootstrap system administrator")
            .build());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
