package com.htttdn.hrm.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.request.organization.CompanyProfileRequest;
import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.dto.response.organization.BrandingResponse;
import com.htttdn.hrm.dto.response.organization.CompanyProfileResponse;
import com.htttdn.hrm.exception.BusinessException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Only {@code company.manage} edits the company profile; anyone reads it. Runs on an empty or
 * seeded database (the row is created when missing) inside a rolled-back transaction.
 */
@SpringBootTest
@Transactional
class CompanyProfileIntegrationTest {

    @Autowired private CompanyProfileService companyProfileService;

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void theOwnerUpdatesTheProfileAndEveryoneReadsIt() {
        authenticate("company.manage");

        companyProfileService.update(new CompanyProfileRequest(
            " ACME ", " Công ty ACME ", "0101234567", " ", "lienhe@acme.vn", "1 Lê Lợi, Quận 1"));

        authenticate("profile.self.read");
        CompanyProfileResponse profile = companyProfileService.get();
        assertEquals("ACME", profile.code());
        assertEquals("Công ty ACME", profile.name());
        assertEquals("0101234567", profile.taxCode());
        assertNull(profile.phone());
        assertEquals("1 Lê Lợi, Quận 1", profile.address());
    }

    @Test
    void withoutCompanyManageTheProfileCannotBeChanged() {
        authenticate("organization.manage", "account.manage");

        assertThrows(AccessDeniedException.class, () -> companyProfileService.update(
            new CompanyProfileRequest("X", "X", null, null, null, null)));
    }

    @Test
    void theOwnerUploadsAPngLogoAndTheBrandingAnnouncesIt() {
        authenticate("company.manage");
        companyProfileService.update(new CompanyProfileRequest("ACME", "Công ty ACME", null, null, null, null));

        CompanyProfileResponse saved = companyProfileService.updateLogo(PNG);

        assertNotNull(saved.logoUpdatedAt());
        BrandingResponse branding = companyProfileService.branding();
        assertEquals("Công ty ACME", branding.name());
        assertEquals(saved.logoUpdatedAt(), branding.logoUpdatedAt());
        CompanyProfileService.LogoImage logo = companyProfileService.logo().orElseThrow();
        assertEquals("image/png", logo.contentType());
        assertArrayEquals(PNG, logo.content());
    }

    @Test
    void theLogoTypeComesFromTheFileSignature() {
        assertEquals("image/png", CompanyProfileService.detectImageType(PNG));
        assertEquals("image/jpeg", CompanyProfileService.detectImageType(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0}));
        assertEquals("image/webp", CompanyProfileService.detectImageType(
            new byte[] {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' '}));
        assertNull(CompanyProfileService.detectImageType("<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes()));
        assertNull(CompanyProfileService.detectImageType(new byte[] {(byte) 0x89, 'P'}));
    }

    @Test
    void svgEmptyAndOversizedLogosAreRefused() {
        authenticate("company.manage");
        companyProfileService.update(new CompanyProfileRequest("ACME", "Công ty ACME", null, null, null, null));

        assertLogoRefused("<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>".getBytes());
        assertLogoRefused(new byte[0]);
        byte[] oversized = new byte[CompanyProfileService.MAX_LOGO_BYTES + 1];
        System.arraycopy(PNG, 0, oversized, 0, PNG.length);
        assertLogoRefused(oversized);
    }

    @Test
    void removingTheLogoClearsIt() {
        authenticate("company.manage");
        companyProfileService.update(new CompanyProfileRequest("ACME", "Công ty ACME", null, null, null, null));
        companyProfileService.updateLogo(PNG);

        CompanyProfileResponse cleared = companyProfileService.deleteLogo();

        assertNull(cleared.logoUpdatedAt());
        assertTrue(companyProfileService.logo().isEmpty());
        assertNull(companyProfileService.branding().logoUpdatedAt());
    }

    @Test
    void withoutCompanyManageTheLogoCannotBeChanged() {
        authenticate("organization.manage");

        assertThrows(AccessDeniedException.class, () -> companyProfileService.updateLogo(PNG));
        assertThrows(AccessDeniedException.class, () -> companyProfileService.deleteLogo());
    }

    /** The 8-byte PNG signature followed by a few bytes; enough for the type check. */
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13};

    private void assertLogoRefused(byte[] content) {
        BusinessException refused = assertThrows(BusinessException.class, () -> companyProfileService.updateLogo(content));
        assertEquals(ErrorCode.COMPANY_LOGO_INVALID, refused.getErrorCode());
    }

    private void authenticate(String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(
            new TestingAuthenticationToken("tester", null, authorities));
    }
}
