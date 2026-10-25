package com.htttdn.hrm.service.organization;

import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.config.ClockConfig;
import com.htttdn.hrm.dto.request.organization.CompanyProfileRequest;
import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.dto.response.organization.BrandingResponse;
import com.htttdn.hrm.dto.response.organization.CompanyProfileResponse;
import com.htttdn.hrm.entity.CompanyProfile;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.exception.ResourceNotFoundException;
import com.htttdn.hrm.repository.CompanyProfileRepository;

import lombok.RequiredArgsConstructor;

/**
 * The one company the system runs for (row {@code id = 1}). The system admin fills it in when
 * bootstrapping the first Company Owner; after the handover only the owner ({@code company.manage}) edits it.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class CompanyProfileService {

    static final short COMPANY_PROFILE_ID = 1;

    /** Largest logo accepted; the UI shows it at most a few dozen pixels high. */
    public static final int MAX_LOGO_BYTES = 512 * 1024;

    /** A stored logo with the content type to serve it under. */
    public record LogoImage(byte[] content, String contentType, Instant updatedAt) {
    }

    private final CompanyProfileRepository companyProfileRepository;

    /** Anyone signed in may read it: it heads payslips and printed sheets. */
    @Transactional(readOnly = true)
    public CompanyProfileResponse get() {
        return toResponse(requireProfile());
    }

    @PreAuthorize("hasAuthority('company.manage')")
    public CompanyProfileResponse update(CompanyProfileRequest request) {
        return toResponse(save(request));
    }

    /** Public: the sign-in page shows the name and logo before anyone is authenticated. */
    @Transactional(readOnly = true)
    public BrandingResponse branding() {
        CompanyProfile profile = requireProfile();
        return new BrandingResponse(profile.getName(), profile.getLogoUpdatedAt());
    }

    /** Public, like {@link #branding()}; empty when the company has no logo. */
    @Transactional(readOnly = true)
    public Optional<LogoImage> logo() {
        return companyProfileRepository.findById(COMPANY_PROFILE_ID)
            .filter(profile -> profile.getLogo() != null)
            .map(profile -> new LogoImage(profile.getLogo(), profile.getLogoContentType(), profile.getLogoUpdatedAt()));
    }

    /**
     * Replaces the logo. The image type comes from the file's own signature, not the client's
     * Content-Type, and only PNG, JPEG and WebP pass: SVG is refused because it can carry script.
     */
    @PreAuthorize("hasAuthority('company.manage')")
    public CompanyProfileResponse updateLogo(byte[] content) {
        if (content == null || content.length == 0) {
            throw new BusinessException(ErrorCode.COMPANY_LOGO_INVALID, "Logo file is empty", "file");
        }
        if (content.length > MAX_LOGO_BYTES) {
            throw new BusinessException(ErrorCode.COMPANY_LOGO_INVALID, "Logo must be at most 512 KB", "file");
        }
        String contentType = detectImageType(content);
        if (contentType == null) {
            throw new BusinessException(ErrorCode.COMPANY_LOGO_INVALID, "Logo must be a PNG, JPEG or WebP image", "file");
        }
        Instant now = Instant.now();
        CompanyProfile profile = requireProfile();
        profile.setLogo(content);
        profile.setLogoContentType(contentType);
        profile.setLogoUpdatedAt(now);
        return toResponse(companyProfileRepository.saveAndFlush(profile));
    }

    @PreAuthorize("hasAuthority('company.manage')")
    public CompanyProfileResponse deleteLogo() {
        CompanyProfile profile = requireProfile();
        profile.setLogo(null);
        profile.setLogoContentType(null);
        profile.setLogoUpdatedAt(null);
        return toResponse(companyProfileRepository.saveAndFlush(profile));
    }

    /** Creates the row on first use; callers check the permission. */
    CompanyProfile save(CompanyProfileRequest request) {
        CompanyProfile profile = companyProfileRepository.findById(COMPANY_PROFILE_ID)
            .orElseGet(() -> CompanyProfile.builder()
                .id(COMPANY_PROFILE_ID)
                .timezone(ClockConfig.BUSINESS_ZONE_ID)
                .build());
        profile.setCode(request.code().trim());
        profile.setName(request.name().trim());
        profile.setTaxCode(blankToNull(request.taxCode()));
        profile.setPhone(blankToNull(request.phone()));
        profile.setEmail(blankToNull(request.email()));
        profile.setAddress(blankToNull(request.address()));
        return companyProfileRepository.saveAndFlush(profile);
    }

    private CompanyProfile requireProfile() {
        return companyProfileRepository.findById(COMPANY_PROFILE_ID)
            .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND, "Company profile not set up"));
    }

    private CompanyProfileResponse toResponse(CompanyProfile profile) {
        return new CompanyProfileResponse(profile.getCode(), profile.getName(), profile.getTaxCode(),
            profile.getPhone(), profile.getEmail(), profile.getAddress(), profile.getLogoUpdatedAt(), profile.getUpdatedAt());
    }

    /** MIME type from the file signature (magic bytes), or null when it is not PNG, JPEG or WebP. */
    static String detectImageType(byte[] content) {
        if (startsWith(content, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return "image/png";
        }
        if (startsWith(content, 0, 0xFF, 0xD8, 0xFF)) {
            return "image/jpeg";
        }
        if (startsWith(content, 0, 'R', 'I', 'F', 'F') && startsWith(content, 8, 'W', 'E', 'B', 'P')) {
            return "image/webp";
        }
        return null;
    }

    private static boolean startsWith(byte[] content, int offset, int... signature) {
        if (content.length < offset + signature.length) {
            return false;
        }
        byte[] expected = new byte[signature.length];
        for (int index = 0; index < signature.length; index++) {
            expected[index] = (byte) signature[index];
        }
        return Arrays.equals(content, offset, offset + signature.length, expected, 0, signature.length);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
