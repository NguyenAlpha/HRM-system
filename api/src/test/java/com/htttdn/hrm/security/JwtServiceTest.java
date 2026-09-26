package com.htttdn.hrm.security;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

import com.htttdn.hrm.config.ApplicationConfig;
import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.service.AccountAuthorizationService.AuthorizationSnapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    @Test
    void generatedTokenCanBeDecodedAndContainsAuthorizationClaims() {
        String secret = Base64.getEncoder().encodeToString(
            "a-development-test-secret-with-more-than-32-bytes".getBytes(StandardCharsets.UTF_8)
        );
        ApplicationConfig config = new ApplicationConfig();
        JwtEncoder encoder = config.jwtEncoder(secret);
        JwtDecoder decoder = config.jwtDecoder(secret, "https://hrm.test");
        JwtService jwtService = new JwtService(encoder, "https://hrm.test", 900);
        Account account = Account.builder().id(42L).username("admin").build();
        AuthorizationSnapshot authorization = new AuthorizationSnapshot(
            List.of("SYSTEM_ADMIN"),
            List.of("organization.company_owner.bootstrap")
        );

        var jwt = decoder.decode(jwtService.generateAccessToken(account, authorization));

        assertEquals("https://hrm.test", jwt.getIssuer().toString());
        assertEquals("admin", jwt.getSubject());
        assertEquals(42L, ((Number) jwt.getClaim("accountId")).longValue());
        assertEquals(List.of("SYSTEM_ADMIN"), jwt.getClaimAsStringList("roles"));
        assertEquals(List.of("organization.company_owner.bootstrap"), jwt.getClaimAsStringList("permissions"));
        assertTrue(jwt.getExpiresAt().isAfter(jwt.getIssuedAt()));

        var authentication = config.jwtAuthenticationConverter().convert(jwt);
        assertTrue(authentication.getAuthorities().stream()
            .anyMatch(authority -> authority.getAuthority().equals("ROLE_SYSTEM_ADMIN")));
        assertTrue(authentication.getAuthorities().stream()
            .anyMatch(authority -> authority.getAuthority().equals("organization.company_owner.bootstrap")));
    }
}
