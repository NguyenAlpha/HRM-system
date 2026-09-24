package com.htttdn.hrm.config.seed;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;

import com.htttdn.hrm.entity.CompanyProfile;
import com.htttdn.hrm.repository.CompanyProfileRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyProfileSeederTest {

    @Mock
    private CompanyProfileRepository companyProfileRepository;

    @Mock
    private ApplicationArguments applicationArguments;

    @Test
    void doesNothingWhenDisabled() {
        createSeeder(false).run(applicationArguments);

        verifyNoInteractions(companyProfileRepository);
    }

    @Test
    void createsTheSingleCompanyProfileWhenMissing() {
        when(companyProfileRepository.existsById(CompanyProfileSeeder.COMPANY_PROFILE_ID)).thenReturn(false);

        createSeeder(true).run(applicationArguments);

        ArgumentCaptor<CompanyProfile> profileCaptor = ArgumentCaptor.forClass(CompanyProfile.class);
        verify(companyProfileRepository).save(profileCaptor.capture());
        CompanyProfile profile = profileCaptor.getValue();
        assertEquals(CompanyProfileSeeder.COMPANY_PROFILE_ID, profile.getId());
        assertEquals("HRM", profile.getCode());
        assertEquals("HRM Demo Company", profile.getName());
        assertEquals("0312345678", profile.getTaxCode());
        assertEquals("02812345678", profile.getPhone());
        assertEquals("contact@hrm.local", profile.getEmail());
        assertEquals("Ho Chi Minh City, Vietnam", profile.getAddress());
        assertEquals("Asia/Ho_Chi_Minh", profile.getTimezone());
        assertNotNull(profile.getCreatedAt());
        assertNotNull(profile.getUpdatedAt());
    }

    @Test
    void retainsExistingCompanyProfile() {
        when(companyProfileRepository.existsById(CompanyProfileSeeder.COMPANY_PROFILE_ID)).thenReturn(true);

        createSeeder(true).run(applicationArguments);

        verify(companyProfileRepository, never()).save(any());
    }

    private CompanyProfileSeeder createSeeder(boolean enabled) {
        return new CompanyProfileSeeder(companyProfileRepository, enabled);
    }
}
