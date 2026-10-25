package com.htttdn.hrm.controller;

import java.time.Duration;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

import com.htttdn.hrm.dto.response.common.ApiResult;
import com.htttdn.hrm.dto.response.organization.BrandingResponse;
import com.htttdn.hrm.service.CompanyProfileService;

import lombok.RequiredArgsConstructor;

/**
 * Company name and logo for screens shown before sign-in. Open to anonymous callers, so it returns
 * nothing beyond what the sign-in page displays (no tax code, phone or address).
 */
@RestController
@RequestMapping("/api/public/branding")
@RequiredArgsConstructor
public class PublicBrandingController {

    private final CompanyProfileService service;

    @GetMapping
    public ApiResult<BrandingResponse> get() {
        return ApiResult.ok(service.branding());
    }

    /** The logo image; 404 when there is none. The ETag is the upload time, so a new logo is never served stale. */
    @GetMapping("/logo")
    public ResponseEntity<byte[]> logo(WebRequest request) {
        return service.logo()
            .map(logo -> {
                String etag = "\"" + logo.updatedAt().toEpochMilli() + "\"";
                if (request.checkNotModified(etag)) {
                    return ResponseEntity.status(304).eTag(etag).<byte[]>build();
                }
                return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(logo.contentType()))
                    .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                    .eTag(etag)
                    .body(logo.content());
            })
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
