package com.htttdn.hrm.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.exception.UnauthorizedException;

@Component
public class CurrentAccountProvider {

    public Long accountId() {
        Authentication authentication = authentication();
        Object principal = authentication.getPrincipal();
        if (principal instanceof Jwt jwt) {
            Object accountId = jwt.getClaim("accountId");
            if (accountId instanceof Number number) {
                return number.longValue();
            }
        }

        throw unauthorized();
    }

    public boolean hasAuthority(String authority) {
        return authentication().getAuthorities().stream()
            .anyMatch(grantedAuthority -> grantedAuthority.getAuthority().equals(authority));
    }

    private Authentication authentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw unauthorized();
        }
        return authentication;
    }

    private UnauthorizedException unauthorized() {
        return new UnauthorizedException(
            ErrorCode.UNAUTHORIZED,
            "Authenticated token does not contain a valid accountId claim"
        );
    }
}
