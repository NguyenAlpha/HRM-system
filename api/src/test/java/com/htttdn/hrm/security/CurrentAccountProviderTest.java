package com.htttdn.hrm.security;

import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.htttdn.hrm.exception.UnauthorizedException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CurrentAccountProviderTest {

    private final CurrentAccountProvider provider = new CurrentAccountProvider();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void readsActorFromJwtClaim() {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject("hr01")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60))
            .claim("accountId", 42L)
            .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
            jwt,
            java.util.List.of(new SimpleGrantedAuthority("employee.read"))
        ));

        assertEquals(42L, provider.accountId());
    }

    @Test
    void rejectsMissingAuthentication() {
        assertThrows(UnauthorizedException.class, provider::accountId);
    }
}
