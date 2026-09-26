package com.htttdn.hrm.config.seed;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.AccountRoleAssignment;
import com.htttdn.hrm.entity.Permission;
import com.htttdn.hrm.entity.Role;
import com.htttdn.hrm.entity.RolePermission;
import com.htttdn.hrm.entity.enums.AccountStatus;
import com.htttdn.hrm.entity.enums.PermissionModule;
import com.htttdn.hrm.entity.enums.RoleScopeType;
import com.htttdn.hrm.repository.AccountRepository;
import com.htttdn.hrm.repository.AccountRoleAssignmentRepository;
import com.htttdn.hrm.repository.PermissionRepository;
import com.htttdn.hrm.repository.RolePermissionRepository;
import com.htttdn.hrm.repository.RoleRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemAdminSeederTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private RolePermissionRepository rolePermissionRepository;

    @Mock
    private AccountRoleAssignmentRepository accountRoleAssignmentRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ApplicationArguments applicationArguments;

    @Test
    void doesNothingWhenDisabled() {
        createSeeder(false, "admin", "admin@hrm.local", "secret123").run(applicationArguments);

        verifyNoInteractions(
            accountRepository,
            roleRepository,
            permissionRepository,
            rolePermissionRepository,
            accountRoleAssignmentRepository,
            passwordEncoder
        );
    }

    @Test
    void rejectsMissingPasswordWhenEnabled() {
        var seeder = createSeeder(true, "admin", "admin@hrm.local", " ");

        var exception = assertThrows(IllegalStateException.class, () -> seeder.run(applicationArguments));

        assertTrue(exception.getMessage().contains("admin.seed.password"));
        verifyNoInteractions(accountRepository);
    }

    @Test
    void rejectsPasswordShorterThanAccountPolicy() {
        var seeder = createSeeder(true, "admin", "admin@hrm.local", "short");

        var exception = assertThrows(IllegalStateException.class, () -> seeder.run(applicationArguments));

        assertTrue(exception.getMessage().contains("between 8 and 100"));
        verifyNoInteractions(accountRepository);
    }

    @Test
    void createsAdminRolePermissionAndCompanyAssignment() {
        when(accountRepository.findByUsername("admin")).thenReturn(Optional.empty());
        when(accountRepository.findByEmail("admin@hrm.local")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("secret123")).thenReturn("encoded-secret");
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
            Account account = invocation.getArgument(0);
            account.setId(1L);
            return account;
        });

        when(roleRepository.findByCodeAndDeletedAtIsNull(SystemAdminSeeder.SYSTEM_ADMIN_ROLE_CODE))
            .thenReturn(Optional.empty());
        when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> {
            Role role = invocation.getArgument(0);
            role.setId(2L);
            return role;
        });

        when(permissionRepository.findByCode(SystemAdminSeeder.COMPANY_OWNER_BOOTSTRAP_PERMISSION_CODE))
            .thenReturn(Optional.empty());
        when(permissionRepository.save(any(Permission.class))).thenAnswer(invocation -> {
            Permission permission = invocation.getArgument(0);
            permission.setId(3L);
            return permission;
        });

        createSeeder(true, " admin ", " ADMIN@HRM.LOCAL ", "secret123").run(applicationArguments);

        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(accountCaptor.capture());
        Account account = accountCaptor.getValue();
        assertEquals("admin", account.getUsername());
        assertEquals("admin@hrm.local", account.getEmail());
        assertEquals("encoded-secret", account.getPasswordHash());
        assertEquals(AccountStatus.ACTIVE, account.getStatus());

        ArgumentCaptor<Role> roleCaptor = ArgumentCaptor.forClass(Role.class);
        verify(roleRepository).save(roleCaptor.capture());
        Role role = roleCaptor.getValue();
        assertEquals(SystemAdminSeeder.SYSTEM_ADMIN_ROLE_CODE, role.getCode());
        assertTrue(role.getIsSystem());

        ArgumentCaptor<Permission> permissionCaptor = ArgumentCaptor.forClass(Permission.class);
        verify(permissionRepository).save(permissionCaptor.capture());
        Permission permission = permissionCaptor.getValue();
        assertEquals(SystemAdminSeeder.COMPANY_OWNER_BOOTSTRAP_PERMISSION_CODE, permission.getCode());
        assertEquals(PermissionModule.ORGANIZATION, permission.getModule());

        ArgumentCaptor<RolePermission> rolePermissionCaptor = ArgumentCaptor.forClass(RolePermission.class);
        verify(rolePermissionRepository).save(rolePermissionCaptor.capture());
        assertSame(account, rolePermissionCaptor.getValue().getCreatedByAccount());

        ArgumentCaptor<AccountRoleAssignment> assignmentCaptor =
            ArgumentCaptor.forClass(AccountRoleAssignment.class);
        verify(accountRoleAssignmentRepository).save(assignmentCaptor.capture());
        AccountRoleAssignment assignment = assignmentCaptor.getValue();
        assertSame(account, assignment.getAccount());
        assertSame(account, assignment.getGrantedByAccount());
        assertEquals(RoleScopeType.COMPANY, assignment.getScopeType());
    }

    @Test
    void doesNotDuplicateExistingSeedData() {
        Account account = Account.builder()
            .id(1L)
            .username("admin")
            .email("admin@hrm.local")
            .build();
        Role role = Role.builder()
            .id(2L)
            .code(SystemAdminSeeder.SYSTEM_ADMIN_ROLE_CODE)
            .isSystem(true)
            .isActive(true)
            .build();
        Permission permission = Permission.builder()
            .id(3L)
            .code(SystemAdminSeeder.COMPANY_OWNER_BOOTSTRAP_PERMISSION_CODE)
            .name("Khởi tạo Chủ sở hữu doanh nghiệp")
            .module(PermissionModule.ORGANIZATION)
            .isActive(true)
            .build();

        when(accountRepository.findByUsername("admin")).thenReturn(Optional.of(account));
        when(accountRepository.findByEmail("admin@hrm.local")).thenReturn(Optional.of(account));
        when(roleRepository.findByCodeAndDeletedAtIsNull(SystemAdminSeeder.SYSTEM_ADMIN_ROLE_CODE))
            .thenReturn(Optional.of(role));
        when(permissionRepository.findByCode(SystemAdminSeeder.COMPANY_OWNER_BOOTSTRAP_PERMISSION_CODE))
            .thenReturn(Optional.of(permission));
        when(rolePermissionRepository.existsById(any())).thenReturn(true);
        when(accountRoleAssignmentRepository.existsByAccountIdAndRoleIdAndScopeTypeAndEffectiveToIsNull(
            1L, 2L, RoleScopeType.COMPANY)).thenReturn(true);

        createSeeder(true, "admin", "admin@hrm.local", "secret123").run(applicationArguments);

        verify(accountRepository, never()).save(any());
        verify(roleRepository, never()).save(any());
        verify(permissionRepository, never()).save(any());
        verify(rolePermissionRepository, never()).save(any());
        verify(accountRoleAssignmentRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }

    private SystemAdminSeeder createSeeder(
        boolean enabled,
        String username,
        String email,
        String password
    ) {
        return new SystemAdminSeeder(
            accountRepository,
            roleRepository,
            permissionRepository,
            rolePermissionRepository,
            accountRoleAssignmentRepository,
            passwordEncoder,
            enabled,
            username,
            email,
            password
        );
    }
}
