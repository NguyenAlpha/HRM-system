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
import com.htttdn.hrm.entity.Role;
import com.htttdn.hrm.entity.enums.AccountStatus;
import com.htttdn.hrm.entity.enums.RoleScopeType;
import com.htttdn.hrm.repository.AccountRepository;
import com.htttdn.hrm.repository.AccountRoleAssignmentRepository;
import com.htttdn.hrm.repository.RoleRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
            accountRoleAssignmentRepository,
            passwordEncoder
        );
    }

    @Test
    void rejectsMissingPasswordWhenEnabled() {
        var seeder = createSeeder(true, "admin", "admin@hrm.local", " ");

        var exception = assertThrows(IllegalStateException.class, () -> seeder.run(applicationArguments));

        assertTrue(exception.getMessage().contains("admin.seed.password"));
        verifyNoInteractions(accountRepository, roleRepository);
    }

    @Test
    void rejectsPasswordShorterThanAccountPolicy() {
        var seeder = createSeeder(true, "admin", "admin@hrm.local", "short");

        var exception = assertThrows(IllegalStateException.class, () -> seeder.run(applicationArguments));

        assertTrue(exception.getMessage().contains("between 8 and 100"));
        verifyNoInteractions(accountRepository, roleRepository);
    }

    @Test
    void rejectsMissingSystemAdminRoleBeforeCreatingAccount() {
        when(roleRepository.findByCodeAndDeletedAtIsNull(SystemAdminSeeder.SYSTEM_ADMIN_ROLE_CODE))
            .thenReturn(Optional.empty());

        var exception = assertThrows(
            IllegalStateException.class,
            () -> createSeeder(true, "admin", "admin@hrm.local", "secret123").run(applicationArguments)
        );

        assertTrue(exception.getMessage().contains("SYSTEM_ADMIN seed role not found"));
        verifyNoInteractions(accountRepository, accountRoleAssignmentRepository, passwordEncoder);
    }

    @Test
    void createsAdminAndAssignsExistingSystemRole() {
        Role role = systemAdminRole();
        when(roleRepository.findByCodeAndDeletedAtIsNull(SystemAdminSeeder.SYSTEM_ADMIN_ROLE_CODE))
            .thenReturn(Optional.of(role));
        when(accountRepository.findByUsername("admin")).thenReturn(Optional.empty());
        when(accountRepository.findByEmail("admin@hrm.local")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("secret123")).thenReturn("encoded-secret");
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
            Account account = invocation.getArgument(0);
            account.setId(1L);
            return account;
        });

        createSeeder(true, " admin ", " ADMIN@HRM.LOCAL ", "secret123").run(applicationArguments);

        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(accountCaptor.capture());
        Account account = accountCaptor.getValue();
        assertEquals("admin", account.getUsername());
        assertEquals("admin@hrm.local", account.getEmail());
        assertEquals("encoded-secret", account.getPasswordHash());
        assertEquals(AccountStatus.ACTIVE, account.getStatus());

        ArgumentCaptor<AccountRoleAssignment> assignmentCaptor =
            ArgumentCaptor.forClass(AccountRoleAssignment.class);
        verify(accountRoleAssignmentRepository).save(assignmentCaptor.capture());
        AccountRoleAssignment assignment = assignmentCaptor.getValue();
        assertSame(account, assignment.getAccount());
        assertSame(account, assignment.getGrantedByAccount());
        assertSame(role, assignment.getRole());
        assertEquals(RoleScopeType.COMPANY, assignment.getScopeType());
    }

    @Test
    void doesNotDuplicateExistingAdminOrAssignment() {
        Account account = Account.builder()
            .id(1L)
            .username("admin")
            .email("admin@hrm.local")
            .build();
        Role role = systemAdminRole();

        when(roleRepository.findByCodeAndDeletedAtIsNull(SystemAdminSeeder.SYSTEM_ADMIN_ROLE_CODE))
            .thenReturn(Optional.of(role));
        when(accountRepository.findByUsername("admin")).thenReturn(Optional.of(account));
        when(accountRepository.findByEmail("admin@hrm.local")).thenReturn(Optional.of(account));
        when(accountRoleAssignmentRepository.existsByAccountIdAndRoleIdAndScopeTypeAndEffectiveToIsNull(
            1L, 2L, RoleScopeType.COMPANY)).thenReturn(true);

        createSeeder(true, "admin", "admin@hrm.local", "secret123").run(applicationArguments);

        verify(accountRepository, never()).save(any());
        verify(accountRoleAssignmentRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }

    private Role systemAdminRole() {
        return Role.builder()
            .id(2L)
            .code(SystemAdminSeeder.SYSTEM_ADMIN_ROLE_CODE)
            .isSystem(true)
            .build();
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
            accountRoleAssignmentRepository,
            passwordEncoder,
            enabled,
            username,
            email,
            password
        );
    }
}
