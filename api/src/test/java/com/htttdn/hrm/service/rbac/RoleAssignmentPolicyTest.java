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
    void roleWithoutCompanyWidePermissionsTakesAnyScope() {
        when(rolePermissionRepository.existsByIdRoleIdAndPermissionCompanyWideTrue(5L)).thenReturn(false);
        RoleAssignmentPolicy policy = new RoleAssignmentPolicy(rolePermissionRepository);

        assertEquals(List.of(RoleScopeType.values()), policy.allowedScopes(role));
        for (RoleScopeType scope : RoleScopeType.values()) {
            assertDoesNotThrow(() -> policy.validateScope(role, scope));
        }
    }
}
