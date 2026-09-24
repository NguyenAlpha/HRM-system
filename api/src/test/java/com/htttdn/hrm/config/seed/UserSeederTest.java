package com.htttdn.hrm.config.seed;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserSeederTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private AccountRoleAssignmentRepository accountRoleAssignmentRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ApplicationArguments applicationArguments;

    @Test
    void doesNothingWhenDisabled() {
        createSeeder(false).run(applicationArguments);

        verifyNoInteractions(
            employeeRepository,
            accountRepository,
            roleRepository,
            accountRoleAssignmentRepository,
            passwordEncoder
        );
    }

    @Test
    void createsEveryDefaultUserWithConfiguredRoleAndScope() {
        Account admin = activeAdmin();
        Map<String, Role> roles = defaultRoles();
        AtomicLong employeeId = new AtomicLong(100);
        AtomicLong accountId = new AtomicLong(200);

        when(roleRepository.findByCodeAndDeletedAtIsNull(any()))
            .thenAnswer(invocation -> Optional.ofNullable(roles.get(invocation.getArgument(0))));
        when(employeeRepository.findByEmployeeCode(any())).thenReturn(Optional.empty());
        when(employeeRepository.findByWorkEmail(any())).thenReturn(Optional.empty());
        when(employeeRepository.save(any(Employee.class))).thenAnswer(invocation -> {
            Employee employee = invocation.getArgument(0);
            employee.setId(employeeId.incrementAndGet());
            return employee;
        });
        when(accountRepository.findByUsername(any())).thenAnswer(invocation ->
            "admin".equals(invocation.getArgument(0)) ? Optional.of(admin) : Optional.empty());
        when(accountRepository.findByEmail(any())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenAnswer(invocation -> "encoded:" + invocation.getArgument(0));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
            Account account = invocation.getArgument(0);
            account.setId(accountId.incrementAndGet());
            return account;
        });

        createSeeder(true).run(applicationArguments);

        ArgumentCaptor<Employee> employeeCaptor = ArgumentCaptor.forClass(Employee.class);
        verify(employeeRepository, times(UserSeeder.DEFAULT_USERS.size())).save(employeeCaptor.capture());
        assertEquals(
            List.of("EMP001", "EMP002", "EMP003"),
            employeeCaptor.getAllValues().stream().map(Employee::getEmployeeCode).toList()
        );
        employeeCaptor.getAllValues().forEach(employee ->
            assertEquals(EmploymentStatus.ACTIVE, employee.getEmploymentStatus()));

        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository, times(UserSeeder.DEFAULT_USERS.size())).save(accountCaptor.capture());
        assertEquals(
            List.of("employee01", "hr01", "payroll01"),
            accountCaptor.getAllValues().stream().map(Account::getUsername).toList()
        );
        accountCaptor.getAllValues().forEach(account -> {
            assertEquals(AccountStatus.ACTIVE, account.getStatus());
            assertEquals("encoded:", account.getPasswordHash().substring(0, 8));
        });

        ArgumentCaptor<AccountRoleAssignment> assignmentCaptor =
            ArgumentCaptor.forClass(AccountRoleAssignment.class);
        verify(accountRoleAssignmentRepository, times(UserSeeder.DEFAULT_USERS.size()))
            .save(assignmentCaptor.capture());

        Map<String, AccountRoleAssignment> assignments = assignmentCaptor.getAllValues().stream()
            .collect(Collectors.toMap(assignment -> assignment.getAccount().getUsername(), Function.identity()));
        assertAssignment(assignments.get("employee01"), "EMPLOYEE", RoleScopeType.SELF, admin);
        assertAssignment(assignments.get("hr01"), "HR_STAFF", RoleScopeType.COMPANY, admin);
        assertAssignment(assignments.get("payroll01"), "PAYROLL_ACCOUNTANT", RoleScopeType.COMPANY, admin);
    }

    @Test
    void doesNotDuplicateExistingSeedUsers() {
        Account admin = activeAdmin();
        Map<String, Role> roles = defaultRoles();
        Map<String, Employee> employeesByCode = new HashMap<>();
        Map<String, Employee> employeesByEmail = new HashMap<>();
        Map<String, Account> accountsByUsername = new HashMap<>();
        Map<String, Account> accountsByEmail = new HashMap<>();
        AtomicLong employeeId = new AtomicLong(100);
        AtomicLong accountId = new AtomicLong(200);

        for (UserSeeder.UserDefinition definition : UserSeeder.DEFAULT_USERS) {
            Employee employee = Employee.builder()
                .id(employeeId.incrementAndGet())
                .employeeCode(definition.employeeCode())
                .workEmail(definition.email())
                .employmentStatus(EmploymentStatus.ACTIVE)
                .build();
            Account account = Account.builder()
                .id(accountId.incrementAndGet())
                .employee(employee)
                .username(definition.username())
                .email(definition.email())
                .status(AccountStatus.ACTIVE)
                .build();
            employeesByCode.put(definition.employeeCode(), employee);
            employeesByEmail.put(definition.email(), employee);
            accountsByUsername.put(definition.username(), account);
            accountsByEmail.put(definition.email(), account);
        }

        when(roleRepository.findByCodeAndDeletedAtIsNull(any()))
            .thenAnswer(invocation -> Optional.ofNullable(roles.get(invocation.getArgument(0))));
        when(employeeRepository.findByEmployeeCode(any()))
            .thenAnswer(invocation -> Optional.ofNullable(employeesByCode.get(invocation.getArgument(0))));
        when(employeeRepository.findByWorkEmail(any()))
            .thenAnswer(invocation -> Optional.ofNullable(employeesByEmail.get(invocation.getArgument(0))));
        when(accountRepository.findByUsername(any()))
            .thenAnswer(invocation -> {
                String username = invocation.getArgument(0);
                return "admin".equals(username)
                    ? Optional.of(admin)
                    : Optional.ofNullable(accountsByUsername.get(username));
            });
        when(accountRepository.findByEmail(any()))
            .thenAnswer(invocation -> Optional.ofNullable(accountsByEmail.get(invocation.getArgument(0))));
        when(accountRoleAssignmentRepository.existsByAccountIdAndRoleIdAndScopeTypeAndEffectiveToIsNull(
            any(), any(), any())).thenReturn(true);

        createSeeder(true).run(applicationArguments);

        verify(employeeRepository, never()).save(any());
        verify(accountRepository, never()).save(any());
        verify(accountRoleAssignmentRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }

    private UserSeeder createSeeder(boolean enabled) {
        return new UserSeeder(
            employeeRepository,
            accountRepository,
            roleRepository,
            accountRoleAssignmentRepository,
            passwordEncoder,
            enabled,
            "admin"
        );
    }

    private Account activeAdmin() {
        return Account.builder()
            .id(1L)
            .username("admin")
            .status(AccountStatus.ACTIVE)
            .build();
    }

    private Map<String, Role> defaultRoles() {
        Map<String, Role> roles = new HashMap<>();
        AtomicLong roleId = new AtomicLong(10);
        for (UserSeeder.UserDefinition definition : UserSeeder.DEFAULT_USERS) {
            roles.computeIfAbsent(definition.roleCode(), code -> Role.builder()
                .id(roleId.incrementAndGet())
                .code(code)
                .isSystem(true)
                .isActive(true)
                .build());
        }
        return roles;
    }

    private void assertAssignment(
        AccountRoleAssignment assignment,
        String roleCode,
        RoleScopeType scopeType,
        Account grantor
    ) {
        assertEquals(roleCode, assignment.getRole().getCode());
        assertEquals(scopeType, assignment.getScopeType());
        assertSame(grantor, assignment.getGrantedByAccount());
    }
}
