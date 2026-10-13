package com.htttdn.hrm.controller;

import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.security.JwtService;
import com.htttdn.hrm.service.AccountAuthorizationService.AuthorizationSnapshot;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "company.seed.enabled=false",
    "rbac.seed.enabled=false",
    "admin.seed.enabled=false",
    "owner.seed.enabled=false"
})
@AutoConfigureMockMvc
@Transactional
class RbacPermissionAccessTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;

    @ParameterizedTest
    @CsvSource({
        "COMPANY_OWNER, rbac.manage, /api/roles, 200",
        "COMPANY_OWNER, rbac.manage, /api/permissions, 200",
        "RBAC_MANAGER, rbac.manage, /api/roles, 200",
        "RBAC_MANAGER, rbac.manage, /api/permissions, 200",
        "COMPANY_OWNER, NONE, /api/roles, 403",
        "COMPANY_OWNER, NONE, /api/permissions, 403",
        "SYSTEM_ADMIN, organization.company_owner.bootstrap, /api/roles, 403",
        "SYSTEM_ADMIN, organization.company_owner.bootstrap, /api/permissions, 403",
        "EMPLOYEE, holiday.read, /api/company-holidays?from=2026-01-01&to=2026-12-31, 200",
        "HR_MANAGER, organization.read, /api/company-holidays?from=2026-01-01&to=2026-12-31, 403",
        "COMPANY_OWNER, account.read, /api/accounts/999999999/permissions, 404",
        "HR_MANAGER, employee.read, /api/accounts/999999999/permissions, 403"
    })
    void rbacAccessDependsOnPermissionNotRole(
        String role,
        String permission,
        String path,
        int expectedStatus
    ) throws Exception {
        List<String> permissions = "NONE".equals(permission) ? List.of() : List.of(permission);
        String token = jwtService.generateAccessToken(Account.builder().id(42L).username("rbac-test").build(),
            new AuthorizationSnapshot(List.of(role), permissions));

        mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().is(expectedStatus));
    }
}
