package com.htttdn.hrm.security;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.exception.UnauthorizedException;
import com.htttdn.hrm.repository.AccountRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CurrentAccountProviderTest {

    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final CurrentAccountProvider provider = new CurrentAccountProvider(accountRepository);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void readsActorFromJwtClaim() {
        signIn(42L);

        assertEquals(42L, provider.accountId());
    }

    @Test
    void rejectsMissingAuthentication() {
        assertThrows(UnauthorizedException.class, provider::accountId);
    }

    @Test
    void loadsTheActorsAccount() {
        signIn(42L);
        Account account = Account.builder().id(42L).build();
        when(accountRepository.findById(42L)).thenReturn(Optional.of(account));

        assertSame(account, provider.account());
    }

    @Test
    void aTokenWhoseAccountIsGoneIsUnauthorized() {
        signIn(42L);
        when(accountRepository.findById(42L)).thenReturn(Optional.empty());

        assertThrows(UnauthorizedException.class, provider::account);
    }

    private static void signIn(long accountId) {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject("hr01")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60))
            .claim("accountId", accountId)
            .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
            jwt,
            java.util.List.of(new SimpleGrantedAuthority("employee.read"))
        ));
    }
}
