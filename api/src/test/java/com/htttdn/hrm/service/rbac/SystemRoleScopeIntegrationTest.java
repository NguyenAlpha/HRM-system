package com.htttdn.hrm.service.rbac;

import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.entity.Role;
import com.htttdn.hrm.entity.enums.RoleScopeType;
import com.htttdn.hrm.repository.RoleRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Scopes of the seeded system roles follow from their permissions ({@code PermissionSeeder} marks the company-wide
 * ones), so changing a role's permissions or a permission's flag shows up here.
 */
@SpringBootTest(properties = {
    "company.seed.enabled=false",
    "admin.seed.enabled=false",
    "owner.seed.enabled=false",
    "staff.seed.enabled=false"
})
@Transactional
class SystemRoleScopeIntegrationTest {

    @Autowired private RoleRepository roleRepository;
    @Autowired private RoleAssignmentPolicy roleAssignmentPolicy;

    @ParameterizedTest
    @ValueSource(strings = {"PAYROLL_ACCOUNTANT", "PAYROLL_APPROVER", "DIRECTOR", "COMPANY_OWNER", "SYSTEM_ADMIN"})
    void rolesHoldingCompanyWidePermissionsAreOnlyCompanyScoped(String roleCode) {
        assertEquals(List.of(RoleScopeType.COMPANY), roleAssignmentPolicy.allowedScopes(role(roleCode)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"EMPLOYEE", "HR_MANAGER", "MANAGER"})
    void rolesWorkingPerEmployeeTakeAnyScope(String roleCode) {
        assertEquals(List.of(RoleScopeType.values()), roleAssignmentPolicy.allowedScopes(role(roleCode)));
    }

    private Role role(String code) {
        return roleRepository.findByCodeAndDeletedAtIsNull(code).orElseThrow();
    }
}
