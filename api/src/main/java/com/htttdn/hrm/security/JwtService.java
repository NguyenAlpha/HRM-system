package com.htttdn.hrm.security;

import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.service.AccountAuthorizationService.AuthorizationSnapshot;

import lombok.Getter;

@Service
public class JwtService {

    private static final JwsHeader JWT_HEADER = JwsHeader.with(MacAlgorithm.HS256)
        .type("JWT")
        .build();

    private final JwtEncoder jwtEncoder;
    private final String issuer;

    @Getter
    private final long accessTokenExpirationSeconds;

    public JwtService(
        JwtEncoder jwtEncoder,
        @Value("${jwt.issuer:https://hrm.local}") String issuer,
        @Value("${jwt.access-token-expiration-seconds:900}") long accessTokenExpirationSeconds
    ) {
        this.jwtEncoder = jwtEncoder;
        this.issuer = issuer;
        this.accessTokenExpirationSeconds = accessTokenExpirationSeconds;
    }

    public String generateAccessToken(Account account, AuthorizationSnapshot authorization) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(issuer)
            .subject(account.getUsername())
            .issuedAt(now)
            .expiresAt(now.plusSeconds(accessTokenExpirationSeconds))
            .claim("accountId", account.getId())
            .claim("roles", authorization.roles())
            .claim("permissions", authorization.permissions())
            .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(JWT_HEADER, claims)).getTokenValue();
    }
}
