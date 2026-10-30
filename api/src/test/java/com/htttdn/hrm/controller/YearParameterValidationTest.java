package com.htttdn.hrm.controller;

import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.security.JwtService;
import com.htttdn.hrm.service.rbac.AccountAuthorizationService.AuthorizationSnapshot;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A year out of range is a 400 naming the parameter, not a 500. */
@SpringBootTest(properties = {
    "company.seed.enabled=false",
    "rbac.seed.enabled=false",
    "admin.seed.enabled=false",
    "owner.seed.enabled=false",
    "staff.seed.enabled=false"
})
@AutoConfigureMockMvc
@Transactional
class YearParameterValidationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;

    @ParameterizedTest
    @ValueSource(strings = {
        "/api/leave-entitlements/employees/1?year=1000000000",
        "/api/reports/hr/turnover?year=1",
        // A @Validated controller: method validation throws ConstraintViolationException there.
        "/api/attendance/timesheet?year=1&month=1"
    })
    void yearOutOfRangeIsABadRequestNamingTheParameter(String url) throws Exception {
        mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, "Bearer " + token()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.error.field").value("year"));
    }

    private String token() {
        return jwtService.generateAccessToken(
            Account.builder().id(999999999L).username("year-test").build(),
            new AuthorizationSnapshot(List.of("EMPLOYEE"),
                List.of("request.read", "request.self.read", "report.hr.read", "attendance.read"))
        );
    }
}
