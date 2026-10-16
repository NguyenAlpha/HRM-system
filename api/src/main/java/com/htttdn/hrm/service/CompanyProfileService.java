package com.htttdn.hrm.service;

import java.time.Instant;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.request.organization.CompanyProfileRequest;
import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.dto.response.organization.CompanyProfileResponse;
import com.htttdn.hrm.entity.CompanyProfile;
import com.htttdn.hrm.exception.ResourceNotFoundException;
import com.htttdn.hrm.repository.CompanyProfileRepository;

/**
 * The one company the system runs for (row {@code id = 1}). The system admin fills it in when
 * bootstrapping the first Company Owner; after the handover only the owner ({@code company.manage}) edits it.
 */
@Service
@Transactional
public class CompanyProfileService {

    static final short COMPANY_PROFILE_ID = 1;

    private final CompanyProfileRepository companyProfileRepository;

    public CompanyProfileService(CompanyProfileRepository companyProfileRepository) {
        this.companyProfileRepository = companyProfileRepository;
    }

    /** Anyone signed in may read it: it heads payslips and printed sheets. */
    @Transactional(readOnly = true)
    public CompanyProfileResponse get() {
        return companyProfileRepository.findById(COMPANY_PROFILE_ID)
            .map(this::toResponse)
            .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND, "Company profile not set up"));
    }

    @PreAuthorize("hasAuthority('company.manage')")
    public CompanyProfileResponse update(CompanyProfileRequest request) {
        return toResponse(save(request));
    }

    /** Creates the row on first use; callers check the permission. */
    CompanyProfile save(CompanyProfileRequest request) {
        Instant now = Instant.now();
        CompanyProfile profile = companyProfileRepository.findById(COMPANY_PROFILE_ID)
            .orElseGet(() -> CompanyProfile.builder()
                .id(COMPANY_PROFILE_ID)
                .timezone("Asia/Ho_Chi_Minh")
                .createdAt(now)
                .build());
        profile.setCode(request.code().trim());
        profile.setName(request.name().trim());
        profile.setTaxCode(blankToNull(request.taxCode()));
        profile.setPhone(blankToNull(request.phone()));
        profile.setEmail(blankToNull(request.email()));
        profile.setAddress(blankToNull(request.address()));
        profile.setUpdatedAt(now);
        return companyProfileRepository.save(profile);
    }

    private CompanyProfileResponse toResponse(CompanyProfile profile) {
        return new CompanyProfileResponse(profile.getCode(), profile.getName(), profile.getTaxCode(),
            profile.getPhone(), profile.getEmail(), profile.getAddress(), profile.getUpdatedAt());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
