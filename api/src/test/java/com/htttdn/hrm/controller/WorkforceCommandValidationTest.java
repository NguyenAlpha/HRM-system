package com.htttdn.hrm.controller;

import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.security.JwtService;
import com.htttdn.hrm.service.rbac.AccountAuthorizationService.AuthorizationSnapshot;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The attendance, leave and payroll command records are validated by Bean Validation before the
 * service runs; each case breaks exactly one field so the reported field is deterministic.
 */
@SpringBootTest(properties = {
    "company.seed.enabled=false",
    "rbac.seed.enabled=false",
    "admin.seed.enabled=false",
    "owner.seed.enabled=false",
    "staff.seed.enabled=false"
})
@AutoConfigureMockMvc
@Transactional
class WorkforceCommandValidationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "POST|/api/attendance/punches|{\"employeeId\":1,\"workDate\":\"2099-01-05\",\"checkInAt\":\"2099-01-05T01:00:00Z\",\"checkOutAt\":\"2026-01-05T10:00:00Z\"}|checkInAt",
        "POST|/api/leave-requests|{\"employeeId\":1,\"leaveType\":\"ANNUAL\",\"startAt\":\"2026-01-05T01:00:00Z\",\"endAt\":\"2026-01-05T05:00:00Z\",\"reason\":\" \"}|reason",
        "POST|/api/compensation/employees/1/salary-history|{\"baseSalary\":0,\"effectiveFrom\":\"2026-01-01\"}|baseSalary",
        "POST|/api/compensation/employees/1/payroll-profiles|{\"effectiveFrom\":\"2026-01-01\",\"taxResident\":true,\"socialInsurance\":true,\"healthInsurance\":true,\"unemploymentInsurance\":true,\"insuranceSalary\":5000000,\"wageRegion\":5}|wageRegion",
        "POST|/api/compensation/employees/1/tax-dependents|{\"fullName\":\"Dependent\"}|effectiveFrom",
        "PUT|/api/compensation/employees/1/tax-dependents/1/end|{}|effectiveTo"
    })
    void invalidCommandIsRejectedWithTheOffendingField(String method, String path, String body, String field)
        throws Exception {
        mockMvc.perform(request(HttpMethod.valueOf(method), path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.error.field").value(field));
    }

    private String token() {
        return jwtService.generateAccessToken(
            Account.builder().id(42L).username("validation-test").build(),
            new AuthorizationSnapshot(List.of("HR_MANAGER"), List.of())
        );
    }
}
