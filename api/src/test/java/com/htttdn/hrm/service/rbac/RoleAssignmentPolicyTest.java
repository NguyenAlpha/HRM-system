package com.htttdn.hrm.service.rbac;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.htttdn.hrm.entity.Role;
import com.htttdn.hrm.entity.enums.RoleScopeType;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.repository.RolePermissionRepository;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleAssignmentPolicyTest {

    @Mock private RolePermissionRepository rolePermissionRepository;

    private final Role role = Role.builder().id(5L).code("CUSTOM_ROLE").isSystem(false).build();

    @Test
    void roleWithACompanyWidePermissionIsOnlyCompanyScoped() {
        when(rolePermissionRepository.existsByIdRoleIdAndPermissionCompanyWideTrue(5L)).thenReturn(true);
        RoleAssignmentPolicy policy = new RoleAssignmentPolicy(rolePermissionRepository);

        assertEquals(List.of(RoleScopeType.COMPANY), policy.allowedScopes(role));
        assertDoesNotThrow(() -> policy.validateScope(role, RoleScopeType.COMPANY));
        assertThrows(BusinessException.class, () -> policy.validateScope(role, RoleScopeType.ORG_UNIT));
        assertThrows(BusinessException.class, () -> policy.validateScope(role, RoleScopeType.LOCATION));
        assertThrows(BusinessException.class, () -> policy.validateScope(role, RoleScopeType.SELF));
    }

    @Test
    void directorIsOnlyCompanyScopedWhateverItsPermissions() {
        // The Director is everyone's fallback approver, so the role is company-wide by itself.
        Role director = Role.builder().id(6L).code(SystemRoles.DIRECTOR).isSystem(true).build();
        RoleAssignmentPolicy policy = new RoleAssignmentPolicy(rolePermissionRepository);

        assertEquals(List.of(RoleScopeType.COMPANY), policy.allowedScopes(director));
    }

    @Test
    void roleWithASelfServicePermissionTakesAnyScope() {
        when(rolePermissionRepository.existsByIdRoleIdAndPermissionCompanyWideTrue(5L)).thenReturn(false);
        when(rolePermissionRepository.existsByRoleIdAndPermissionCodeContaining(5L, ".self.")).thenReturn(true);
        RoleAssignmentPolicy policy = new RoleAssignmentPolicy(rolePermissionRepository);

        assertEquals(List.of(RoleScopeType.values()), policy.allowedScopes(role));
        for (RoleScopeType scope : RoleScopeType.values()) {
            assertDoesNotThrow(() -> policy.validateScope(role, scope));
        }
    }

    @Test
    void roleWithoutSelfServicePermissionsCannotBeAssignedToOneself() {
        // A manager role scoped to oneself would manage nobody.
        when(rolePermissionRepository.existsByIdRoleIdAndPermissionCompanyWideTrue(5L)).thenReturn(false);
        when(rolePermissionRepository.existsByRoleIdAndPermissionCodeContaining(5L, ".self.")).thenReturn(false);
        RoleAssignmentPolicy policy = new RoleAssignmentPolicy(rolePermissionRepository);

        assertEquals(
            List.of(RoleScopeType.COMPANY, RoleScopeType.ORG_UNIT, RoleScopeType.LOCATION),
            policy.allowedScopes(role)
        );
        assertThrows(BusinessException.class, () -> policy.validateScope(role, RoleScopeType.SELF));
    }
}
