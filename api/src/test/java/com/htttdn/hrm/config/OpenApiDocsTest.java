package com.htttdn.hrm.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasKey;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "company.seed.enabled=false",
    "rbac.seed.enabled=false",
    "admin.seed.enabled=false",
    "owner.seed.enabled=false"
})
@AutoConfigureMockMvc
class OpenApiDocsTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void specIsPublicAndDeclaresBearerAuth() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
            .andExpect(jsonPath("$.paths", hasKey("/api/employees/{employeeId}/sensitive")))
            .andExpect(jsonPath("$.components.schemas", hasKey("EmployeeSensitiveResponse")));
    }

    /** Frontend types are generated from these rules; see OpenApiConfig. */
    @Test
    void responseFieldsAreRequiredAndOnlyNullableFieldsAcceptNull() throws Exception {
        String detail = "$.components.schemas.EmployeeDetailResponse";
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(jsonPath(detail + ".type").value("object"))
            .andExpect(jsonPath(detail + ".required", hasItems("hireDate", "terminationDate", "currentAssignment")))
            .andExpect(jsonPath(detail + ".properties.hireDate.type").value("string"))
            .andExpect(jsonPath(detail + ".properties.terminationDate.type", hasItems("string", "null")))
            .andExpect(jsonPath(detail + ".properties.currentAssignment.anyOf[0].$ref")
                .value("#/components/schemas/EmployeeAssignmentResponse"))
            .andExpect(jsonPath(detail + ".properties.currentAssignment.anyOf[1].type").value("null"))
            .andExpect(jsonPath("$.components.schemas.EmployeeAssignmentResponse.type").value("object"))
            .andExpect(jsonPath("$.components.schemas.UpdateEmployeeRequest.required", contains("fullName")));
    }

    @Test
    void swaggerUiIsPublic() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
            .andExpect(status().isOk());
    }
}
