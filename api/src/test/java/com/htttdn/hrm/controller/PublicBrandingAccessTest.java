package com.htttdn.hrm.controller;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.CompanyProfile;
import com.htttdn.hrm.repository.CompanyProfileRepository;
import com.htttdn.hrm.security.JwtService;
import com.htttdn.hrm.service.rbac.AccountAuthorizationService.AuthorizationSnapshot;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The branding endpoints are the only company data open before sign-in; the full profile and the
 * logo upload stay behind authentication and {@code company.manage}.
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
class PublicBrandingAccessTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13};

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private CompanyProfileRepository companyProfileRepository;

    @BeforeEach
    void companyWithoutLogo() {
        CompanyProfile profile = companyProfileRepository.findById((short) 1)
            .orElseGet(() -> CompanyProfile.builder().id((short) 1).code("ACME").timezone("Asia/Ho_Chi_Minh").build());
        profile.setName("Công ty ACME");
        profile.setTaxCode("0101234567");
        profile.setLogo(null);
        profile.setLogoContentType(null);
        profile.setLogoUpdatedAt(null);
        companyProfileRepository.saveAndFlush(profile);
    }

    @Test
    void anyoneReadsTheBrandingButNotTheProfile() throws Exception {
        mockMvc.perform(get("/api/public/branding"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.name").value("Công ty ACME"))
            .andExpect(jsonPath("$.data.taxCode").doesNotExist());
        mockMvc.perform(get("/api/company")).andExpect(status().isUnauthorized());
    }

    @Test
    void theLogoIsServedPubliclyOnceUploaded() throws Exception {
        mockMvc.perform(get("/api/public/branding/logo")).andExpect(status().isNotFound());

        mockMvc.perform(upload().header(HttpHeaders.AUTHORIZATION, bearer("company.manage")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.logoUpdatedAt").exists());

        mockMvc.perform(get("/api/public/branding/logo"))
            .andExpect(status().isOk())
            .andExpect(content().contentType("image/png"))
            .andExpect(header().exists(HttpHeaders.ETAG))
            .andExpect(content().bytes(PNG));
    }

    @Test
    void uploadingNeedsCompanyManage() throws Exception {
        mockMvc.perform(upload()).andExpect(status().isUnauthorized());
        mockMvc.perform(upload().header(HttpHeaders.AUTHORIZATION, bearer("organization.manage")))
            .andExpect(status().isForbidden());
    }

    private org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder upload() {
        return (org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder)
            multipart(HttpMethod.PUT, "/api/company/logo").file(new MockMultipartFile("file", "logo.png", "image/png", PNG));
    }

    private String bearer(String permission) {
        return "Bearer " + jwtService.generateAccessToken(
            Account.builder().id(999999998L).username("branding-test").build(),
            new AuthorizationSnapshot(List.of("COMPANY_OWNER"), List.of(permission))
        );
    }
}
