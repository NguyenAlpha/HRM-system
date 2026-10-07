package com.htttdn.hrm.controller;

import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.security.JwtService;
import com.htttdn.hrm.service.AccountAuthorizationService.AuthorizationSnapshot;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "company.seed.enabled=false",
    "rbac.seed.enabled=false",
    "admin.seed.enabled=false",
    "user.seed.enabled=false"
})
@AutoConfigureMockMvc
@Transactional
class EmployeeSensitiveDataAccessTest {

    private static final String PATH = "/api/employees/1/sensitive";

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;

    @ParameterizedTest
    @CsvSource({
        "employee.read",
        "employee.update",
        "NONE"
    })
    void readIsDeniedWithoutSensitiveReadPermission(String permission) throws Exception {
        mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + token(permission)))
            .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @CsvSource({
        "employee.sensitive.read",
        "employee.update",
        "NONE"
    })
    void updateIsDeniedWithoutSensitiveManagePermission(String permission) throws Exception {
        mockMvc.perform(put(PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(permission))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isForbidden());
    }

    /**
     * Reaching EMPLOYEE_NOT_FOUND proves the permission check passed; a missing route would
     * also answer 404 but without the business error envelope.
     */
    @ParameterizedTest
    @CsvSource({
        "employee.sensitive.read"
    })
    void readPassesPermissionCheckAndReachesTheService(String permission) throws Exception {
        mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + token(permission)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("EMPLOYEE_NOT_FOUND"));
    }

    @ParameterizedTest
    @CsvSource({
        "employee.sensitive.manage"
    })
    void updatePassesPermissionCheckAndReachesTheService(String permission) throws Exception {
        mockMvc.perform(put(PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(permission))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("EMPLOYEE_NOT_FOUND"));
    }

    private String token(String permission) {
        List<String> permissions = "NONE".equals(permission) ? List.of() : List.of(permission);
        return jwtService.generateAccessToken(
            Account.builder().id(42L).username("sensitive-test").build(),
            new AuthorizationSnapshot(List.of("HR_MANAGER"), permissions)
        );
    }
}
