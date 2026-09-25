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
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw unauthorized();
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof Jwt jwt) {
            Object accountId = jwt.getClaim("accountId");
            if (accountId instanceof Number number) {
                return number.longValue();
            }
        }

        throw unauthorized();
    }

    private UnauthorizedException unauthorized() {
        return new UnauthorizedException(
            ErrorCode.UNAUTHORIZED,
            "Authenticated token does not contain a valid accountId claim"
        );
    }
}
