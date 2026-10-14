package com.htttdn.hrm.controller;

import java.util.List;

import org.junit.jupiter.api.Test;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "company.seed.enabled=false",
    "rbac.seed.enabled=false",
    "admin.seed.enabled=false",
    "owner.seed.enabled=false",
    "staff.seed.enabled=false"
})
@AutoConfigureMockMvc
@Transactional
class EmployeeSelfProfileAccessTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;

    @Test
    void ownProfileNeedsProfileSelfRead() throws Exception {
        mockMvc.perform(get("/api/employees/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token(List.of("employee.read"))))
            .andExpect(status().isForbidden());
    }

    /** The token's account does not exist, so passing the permission check ends in EMPLOYEE_NOT_FOUND. */
    @Test
    void ownProfilePassesPermissionCheckAndReachesTheService() throws Exception {
        mockMvc.perform(get("/api/employees/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token(List.of("profile.self.read"))))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("EMPLOYEE_NOT_FOUND"));
    }

    private String token(List<String> permissions) {
        return jwtService.generateAccessToken(
            Account.builder().id(999999999L).username("self-profile-test").build(),
            new AuthorizationSnapshot(List.of("EMPLOYEE"), permissions)
        );
    }
}
