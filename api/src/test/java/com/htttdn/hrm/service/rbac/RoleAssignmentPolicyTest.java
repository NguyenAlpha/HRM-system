package com.htttdn.hrm.service.rbac;

import org.junit.jupiter.api.Test;

import com.htttdn.hrm.entity.Role;
import com.htttdn.hrm.entity.enums.RoleScopeType;
import com.htttdn.hrm.exception.BusinessException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RoleAssignmentPolicyTest {

    private final RoleAssignmentPolicy policy = new RoleAssignmentPolicy();
    private final Role manager = Role.builder().code("MANAGER").isSystem(true).build();

    @Test
    void managerIsScopedToABranchOrAUnit() {
        assertDoesNotThrow(() -> policy.validateScope(manager, RoleScopeType.LOCATION));
        assertDoesNotThrow(() -> policy.validateScope(manager, RoleScopeType.ORG_UNIT));
    }

    @Test
    void managerCannotBeCompanyWideOrSelf() {
        assertThrows(BusinessException.class, () -> policy.validateScope(manager, RoleScopeType.COMPANY));
        assertThrows(BusinessException.class, () -> policy.validateScope(manager, RoleScopeType.SELF));
    }
}
