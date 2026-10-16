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
import com.htttdn.hrm.dto.response.organization.CompanyProfileResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    private void authenticate(String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(
            new TestingAuthenticationToken("tester", null, authorities));
    }
}
