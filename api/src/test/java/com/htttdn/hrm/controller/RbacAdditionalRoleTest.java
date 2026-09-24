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
    "user.seed.enabled=false",
    "rbac.management.allowed-roles=SYSTEM_ADMIN,RBAC_MANAGER"
})
@AutoConfigureMockMvc
@Transactional
class RbacAdditionalRoleTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;

    @ParameterizedTest
    @CsvSource({
        "SYSTEM_ADMIN, /api/roles, 200",
        "SYSTEM_ADMIN, /api/permissions, 200",
        "RBAC_MANAGER, /api/roles, 200",
        "RBAC_MANAGER, /api/permissions, 200",
        "EMPLOYEE, /api/roles, 403",
        "EMPLOYEE, /api/permissions, 403"
    })
    void additionalRolesCanBeEnabledThroughConfiguration(String role, String path, int expectedStatus) throws Exception {
        String token = jwtService.generateAccessToken(Account.builder().id(42L).username("rbac-test").build(),
            new AuthorizationSnapshot(List.of(role), List.of()));
        mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().is(expectedStatus));
    }
}
