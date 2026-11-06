package com.htttdn.hrm.service.account;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.request.account.CompleteAccountActivationRequest;
import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.exception.AppException;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.exception.InvalidTokenException;
import com.htttdn.hrm.repository.AccountRepository;

import jakarta.persistence.EntityManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The public activation link: a one-time token sets the password of a PENDING account and makes it ACTIVE.
 * Runs on an empty or seeded database inside a rolled-back transaction.
 */
@SpringBootTest
@Transactional
class AccountActivationIntegrationTest {

    private static final String PASSWORD = "Activate@123";

    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AccountRepository accountRepository;
    @Autowired private AccountActivationService activationService;

    private Long actorId;
    private Long pendingId;

    @BeforeEach
    void seed() {
        actorId = account("hr", "ACTIVE");
        pendingId = account("new", "PENDING");
    }

    @Test
    void theTokenSetsThePasswordActivatesTheAccountAndWorksOnlyOnce() {
        String token = issue();

        activationService.activate(token, new CompleteAccountActivationRequest(PASSWORD, PASSWORD));
        entityManager.flush();

        assertEquals("ACTIVE", status());
        assertTrue(passwordEncoder.matches(PASSWORD, jdbc.queryForObject(
            "SELECT password_hash FROM accounts WHERE id = ?", String.class, pendingId)));
        assertNotNull(jdbc.queryForObject(
            "SELECT used_at FROM account_activation_tokens WHERE account_id = ?", Instant.class, pendingId));
        assertCode(ErrorCode.ACTIVATION_TOKEN_INVALID, InvalidTokenException.class, token);
    }

    @Test
    void aMismatchedConfirmationChangesNothing() {
        String token = issue();

        BusinessException exception = assertThrows(BusinessException.class,
            () -> activationService.activate(token, new CompleteAccountActivationRequest(PASSWORD, "Other@123")));

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
        assertEquals("passwordConfirmation", exception.getField());
        assertEquals("PENDING", status());
    }

    @Test
    void anUnknownExpiredOrReplacedTokenIsRefused() {
        assertCode(ErrorCode.ACTIVATION_TOKEN_INVALID, InvalidTokenException.class, "not-a-real-token");
        assertCode(ErrorCode.ACTIVATION_TOKEN_INVALID, InvalidTokenException.class, " ");

        String expired = issue();
        jdbc.update("UPDATE account_activation_tokens SET expires_at = now() - interval '1 minute' WHERE account_id = ?",
            pendingId);
        assertCode(ErrorCode.ACTIVATION_TOKEN_EXPIRED, InvalidTokenException.class, expired);

        // Issuing a new link revokes the one sent before.
        String replaced = issue();
        issue();
        assertCode(ErrorCode.ACTIVATION_TOKEN_INVALID, InvalidTokenException.class, replaced);
        assertEquals("PENDING", status());
    }

    private String issue() {
        String token = activationService.issue(pendingId, accountRepository.getRequired(actorId)).rawToken();
        // The token is read back by a query; drop the cached copy so changes made through JDBC are seen.
        entityManager.flush();
        entityManager.clear();
        return token;
    }

    private void assertCode(ErrorCode code, Class<? extends AppException> type, String token) {
        AppException exception = assertThrows(type,
            () -> activationService.activate(token, new CompleteAccountActivationRequest(PASSWORD, PASSWORD)));
        assertEquals(code, exception.getErrorCode());
    }

    private String status() {
        return jdbc.queryForObject("SELECT status FROM accounts WHERE id = ?", String.class, pendingId);
    }

    private Long account(String prefix, String status) {
        String username = "act_" + prefix + "_" + UUID.randomUUID().toString().substring(0, 8);
        return jdbc.queryForObject("""
            INSERT INTO accounts(username, email, password_hash, status)
            VALUES (?, ?, ?, ?) RETURNING id
            """, Long.class, username, username + "@hrm.local", "PENDING".equals(status) ? null : "x", status);
    }
}
