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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class EmployeeLifecycleAccessTest {

    private static final String RESIGNATION_PATH = "/api/employees/1/resignation";
    private static final String RESIGNATION_BODY =
        "{\"terminationDate\":\"2026-10-31\",\"terminationReason\":\"Nghỉ theo nguyện vọng\"}";
    private static final String SOFT_DELETE_PATH = "/api/employees/1/soft-delete";
    private static final String SOFT_DELETE_BODY = "{\"deletionReason\":\"Tạo nhầm hồ sơ\"}";

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;

    @ParameterizedTest
    @CsvSource({
        "employee.update",
        "employee.probation.confirm",
        "employee.lifecycle.approve",
        "NONE"
    })
    void resignationIsDeniedWithoutLifecycleManagePermission(String permission) throws Exception {
        mockMvc.perform(resignation(permission, RESIGNATION_BODY))
            .andExpect(status().isForbidden());
    }

    /**
     * Reaching EMPLOYEE_NOT_FOUND proves the permission check passed; a missing route would
     * also answer 404 but without the business error envelope.
     */
    @ParameterizedTest
    @CsvSource({
        "employee.lifecycle.manage"
    })
    void resignationPassesPermissionCheckAndReachesTheService(String permission) throws Exception {
        mockMvc.perform(resignation(permission, RESIGNATION_BODY))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("EMPLOYEE_NOT_FOUND"));
    }

    @ParameterizedTest
    @CsvSource({
        "'{}'",
        "'{\"terminationDate\":\"2026-10-31\"}'",
        "'{\"terminationReason\":\"Nghỉ theo nguyện vọng\"}'"
    })
    void resignationRequiresBothDateAndReason(String body) throws Exception {
        mockMvc.perform(resignation("employee.lifecycle.manage", body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @ParameterizedTest
    @CsvSource({
        "employee.update",
        "employee.lifecycle.manage",
        "NONE"
    })
    void softDeleteIsDeniedWithoutDeletePermission(String permission) throws Exception {
        mockMvc.perform(softDelete(permission, SOFT_DELETE_BODY))
            .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @CsvSource({
        "employee.delete"
    })
    void softDeletePassesPermissionCheckAndReachesTheService(String permission) throws Exception {
        mockMvc.perform(softDelete(permission, SOFT_DELETE_BODY))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("EMPLOYEE_NOT_FOUND"));
    }

    @ParameterizedTest
    @CsvSource({
        "'{}'",
        "'{\"deletionReason\":\"\"}'"
    })
    void softDeleteRequiresAReason(String body) throws Exception {
        mockMvc.perform(softDelete("employee.delete", body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder resignation(
        String permission, String body
    ) {
        return request(RESIGNATION_PATH, permission, body);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder softDelete(
        String permission, String body
    ) {
        return request(SOFT_DELETE_PATH, permission, body);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(
        String path, String permission, String body
    ) {
        return post(path)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(permission))
            .contentType(MediaType.APPLICATION_JSON)
            .content(body);
    }

    private String token(String permission) {
        List<String> permissions = "NONE".equals(permission) ? List.of() : List.of(permission);
        return jwtService.generateAccessToken(
            Account.builder().id(42L).username("lifecycle-test").build(),
            new AuthorizationSnapshot(List.of("HR_MANAGER"), permissions)
        );
    }
}
