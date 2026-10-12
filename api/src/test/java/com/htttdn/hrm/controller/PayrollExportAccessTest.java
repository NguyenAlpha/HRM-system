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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The export endpoint produces XLSX, but a refused request must still answer with the usual JSON
 * error envelope so the web can show the message.
 */
@SpringBootTest(properties = {
    "company.seed.enabled=false",
    "rbac.seed.enabled=false",
    "admin.seed.enabled=false",
    "owner.seed.enabled=false"
})
@AutoConfigureMockMvc
@Transactional
class PayrollExportAccessTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;

    @ParameterizedTest
    @CsvSource({
        "payroll.calculate",
        "payroll.self.read",
        // Holding the permission without a COMPANY-scope assignment is still refused.
        "report.payroll.read"
    })
    void exportIsRefusedWithJsonEnvelope(String permission) throws Exception {
        mockMvc.perform(get("/api/payroll/periods/1/export")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(permission))
                .accept(MediaType.parseMediaType(PayrollController.XLSX), MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    private String token(String permission) {
        return jwtService.generateAccessToken(
            Account.builder().id(42L).username("export-test").build(),
            new AuthorizationSnapshot(List.of("PAYROLL_ACCOUNTANT"), List.of(permission))
        );
    }
}
