package com.htttdn.hrm.config.seed;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.Role;
import com.htttdn.hrm.entity.enums.AccountStatus;
import com.htttdn.hrm.entity.enums.EmploymentStatus;
import com.htttdn.hrm.entity.enums.RoleScopeType;
import com.htttdn.hrm.repository.AccountRepository;
import com.htttdn.hrm.repository.AccountRoleAssignmentRepository;
import com.htttdn.hrm.repository.EmployeeRepository;
import com.htttdn.hrm.repository.RoleRepository;

/**
 * Khởi tạo các tài khoản nhân viên dùng để kiểm thử cổng HRM dành cho người dùng thường.
 *
 * <p>Mỗi phần tử trong {@link #DEFAULT_USERS} mô tả employee, account và role assignment
 * cần tạo. Seeder chạy sau RBAC và {@link SystemAdminSeeder}; system admin được dùng làm
 * người cấp role theo ràng buộc audit của bảng {@code account_role_assignments}.
 *
 * <p>Các bước đều idempotent. Seeder không đổi mật khẩu hoặc tự kích hoạt lại dữ liệu đã tồn
 * tại; cấu hình xung đột làm ứng dụng fail-fast để tránh cấp quyền cho nhầm account.
 */
@Component
@Order(500)
public class UserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(UserSeeder.class);

    static final List<UserDefinition> DEFAULT_USERS = List.of(
        new UserDefinition(
            "EMP001", "Demo Employee", "employee01", "employee01@hrm.local",
            "Employee@123", "EMPLOYEE", RoleScopeType.SELF
        ),
        new UserDefinition(
            "EMP002", "Demo HR Staff", "hr01", "hr01@hrm.local",
            "HrStaff@123", "HR_STAFF", RoleScopeType.COMPANY
        ),
        new UserDefinition(
            "EMP003", "Demo Payroll Accountant", "payroll01", "payroll01@hrm.local",
            "Payroll@123", "PAYROLL_ACCOUNTANT", RoleScopeType.COMPANY
        )
    );

    private final EmployeeRepository employeeRepository;
    private final AccountRepository accountRepository;
    private final RoleRepository roleRepository;
    private final AccountRoleAssignmentRepository accountRoleAssignmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final boolean enabled;
    private final String grantedByUsername;

    public UserSeeder(
        EmployeeRepository employeeRepository,
        AccountRepository accountRepository,
        RoleRepository roleRepository,
        AccountRoleAssignmentRepository accountRoleAssignmentRepository,
        PasswordEncoder passwordEncoder,
        @Value("${user.seed.enabled:false}") boolean enabled,
        @Value("${admin.seed.username:admin}") String grantedByUsername
    ) {
        this.employeeRepository = employeeRepository;
        this.accountRepository = accountRepository;
        this.roleRepository = roleRepository;
        this.accountRoleAssignmentRepository = accountRoleAssignmentRepository;
        this.passwordEncoder = passwordEncoder;
        this.enabled = enabled;
        this.grantedByUsername = grantedByUsername;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }

        validateDefinitions();
        Account grantor = findActiveGrantor(grantedByUsername.trim());

        for (UserDefinition definition : DEFAULT_USERS) {
            seedUser(definition, grantor);
        }

        log.info("User seed completed: {} accounts ensured", DEFAULT_USERS.size());
    }

    private void seedUser(UserDefinition definition, Account grantor) {
        String normalizedUsername = definition.username().trim();
        String normalizedEmail = definition.email().trim().toLowerCase(Locale.ROOT);
        String normalizedEmployeeCode = definition.employeeCode().trim().toUpperCase(Locale.ROOT);
        String normalizedFullName = definition.fullName().trim();
        String normalizedRoleCode = definition.roleCode().trim().toUpperCase(Locale.ROOT);

        Role role = findRole(normalizedRoleCode);
        Employee employee = findOrCreateEmployee(normalizedEmployeeCode, normalizedEmail, normalizedFullName);
        Account account = findOrCreateAccount(
            normalizedUsername,
            normalizedEmail,
            definition.password(),
            employee
        );
        assignRoleIfMissing(account, role, definition.scopeType(), grantor);
    }

    private void validateDefinitions() {
        if (isBlank(grantedByUsername)) {
            throw new IllegalStateException("User seed requires admin.seed.username");
        }

        for (UserDefinition definition : DEFAULT_USERS) {
            String label = isBlank(definition.username()) ? "<blank>" : definition.username().trim();
            if (isBlank(definition.username()) || isBlank(definition.email()) || isBlank(definition.password())
                || isBlank(definition.employeeCode()) || isBlank(definition.fullName())
                || isBlank(definition.roleCode()) || definition.scopeType() == null) {
                throw new IllegalStateException("User seed definition is incomplete: " + label);
            }
            if (definition.username().trim().length() > 50) {
                throw new IllegalStateException("User seed username must not exceed 50 characters: " + label);
            }
            if (definition.email().trim().length() > 100) {
                throw new IllegalStateException("User seed email must not exceed 100 characters: " + label);
            }
            if (definition.employeeCode().trim().length() > 30) {
                throw new IllegalStateException("User seed employee code must not exceed 30 characters: " + label);
            }
            if (definition.fullName().trim().length() > 200) {
                throw new IllegalStateException("User seed full name must not exceed 200 characters: " + label);
            }
            if (definition.password().length() < 8 || definition.password().length() > 100) {
                throw new IllegalStateException(
                    "User seed password must contain between 8 and 100 characters: " + label
                );
            }
        }
    }

    private Account findActiveGrantor(String adminUsername) {
        Account grantor = accountRepository.findByUsername(adminUsername)
            .orElseThrow(() -> new IllegalStateException("User seed grantor not found: " + adminUsername));
        if (grantor.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException("User seed grantor must be an active account: " + adminUsername);
        }
        return grantor;
    }

    private Role findRole(String roleCode) {
        Role role = roleRepository.findByCodeAndDeletedAtIsNull(roleCode)
            .orElseThrow(() -> new IllegalStateException("Seed role not found: " + roleCode));
        if (!Boolean.TRUE.equals(role.getIsSystem())) {
            throw new IllegalStateException(roleCode + " must be a system role");
        }
        return role;
    }

    private Employee findOrCreateEmployee(String code, String workEmail, String normalizedFullName) {
        Optional<Employee> employeeByCode = employeeRepository.findByEmployeeCode(code);
        Optional<Employee> employeeByEmail = employeeRepository.findByWorkEmail(workEmail);

        if (employeeByCode.isPresent() || employeeByEmail.isPresent()) {
            return resolveExistingEmployee(employeeByCode, employeeByEmail, code, workEmail);
        }

        Instant now = Instant.now();
        return employeeRepository.save(Employee.builder()
            .employeeCode(code)
            .fullName(normalizedFullName)
            .workEmail(workEmail)
            .hireDate(LocalDate.now())
            .employmentStatus(EmploymentStatus.ACTIVE)
            .createdAt(now)
            .updatedAt(now)
            .build());
    }

    private Employee resolveExistingEmployee(
        Optional<Employee> employeeByCode,
        Optional<Employee> employeeByEmail,
        String code,
        String workEmail
    ) {
        if (employeeByCode.isEmpty() || employeeByEmail.isEmpty()
            || !employeeByCode.get().getId().equals(employeeByEmail.get().getId())) {
            throw new IllegalStateException("User seed employee code or email belongs to a different employee");
        }

        Employee employee = employeeByCode.get();
        if (!code.equals(employee.getEmployeeCode()) || !workEmail.equalsIgnoreCase(employee.getWorkEmail())
            || employee.getDeletedAt() != null || employee.getEmploymentStatus() != EmploymentStatus.ACTIVE) {
            throw new IllegalStateException("Existing seed employee is not an active matching employee");
        }
        return employee;
    }

    private Account findOrCreateAccount(
        String normalizedUsername,
        String normalizedEmail,
        String password,
        Employee employee
    ) {
        Optional<Account> accountByUsername = accountRepository.findByUsername(normalizedUsername);
        Optional<Account> accountByEmail = accountRepository.findByEmail(normalizedEmail);

        if (accountByUsername.isPresent() || accountByEmail.isPresent()) {
            return resolveExistingAccount(accountByUsername, accountByEmail, normalizedUsername, normalizedEmail, employee);
        }

        Instant now = Instant.now();
        return accountRepository.save(Account.builder()
            .employee(employee)
            .username(normalizedUsername)
            .email(normalizedEmail)
            .passwordHash(passwordEncoder.encode(password))
            .status(AccountStatus.ACTIVE)
            .failedLoginCount(0)
            .createdAt(now)
            .updatedAt(now)
            .build());
    }

    private Account resolveExistingAccount(
        Optional<Account> accountByUsername,
        Optional<Account> accountByEmail,
        String normalizedUsername,
        String normalizedEmail,
        Employee employee
    ) {
        if (accountByUsername.isEmpty() || accountByEmail.isEmpty()
            || !accountByUsername.get().getId().equals(accountByEmail.get().getId())) {
            throw new IllegalStateException("User seed username or email belongs to a different account");
        }

        Account account = accountByUsername.get();
        if (!normalizedUsername.equals(account.getUsername()) || !normalizedEmail.equalsIgnoreCase(account.getEmail())
            || account.getEmployee() == null || !employee.getId().equals(account.getEmployee().getId())
            || account.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Existing seed account is not an active matching employee account");
        }
        return account;
    }

    private void assignRoleIfMissing(Account account, Role role, RoleScopeType scopeType, Account grantor) {
        if (accountRoleAssignmentRepository.existsByAccountIdAndRoleIdAndScopeTypeAndEffectiveToIsNull(
            account.getId(), role.getId(), scopeType)) {
            return;
        }

        accountRoleAssignmentRepository.save(AccountRoleAssignment.builder()
            .account(account)
            .role(role)
            .scopeType(scopeType)
            .effectiveFrom(LocalDate.now())
            .grantedByAccount(grantor)
            .reason("Bootstrap demo user account")
            .createdAt(Instant.now())
            .build());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    record UserDefinition(
        String employeeCode,
        String fullName,
        String username,
        String email,
        String password,
        String roleCode,
        RoleScopeType scopeType
    ) {
    }
}
