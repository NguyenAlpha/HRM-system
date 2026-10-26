package com.htttdn.hrm.security;

import java.time.Clock;
import java.time.Instant;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.service.rbac.AccountAuthorizationService.AuthorizationSnapshot;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class JwtService {

    private static final JwsHeader JWT_HEADER = JwsHeader.with(MacAlgorithm.HS256)
        .type("JWT")
        .build();

    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwt;
    private final Clock clock;

    public long getAccessTokenExpirationSeconds() {
        return jwt.accessTokenExpirationSeconds();
    }

    public String generateAccessToken(Account account, AuthorizationSnapshot authorization) {
        Instant now = Instant.now(clock);
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(jwt.issuer())
            .subject(account.getUsername())
            .issuedAt(now)
            .expiresAt(now.plusSeconds(jwt.accessTokenExpirationSeconds()))
            .claim("accountId", account.getId())
            .claim("roles", authorization.roles())
            .claim("permissions", authorization.permissions())
            .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(JWT_HEADER, claims)).getTokenValue();
    }
}
