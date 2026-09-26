package com.htttdn.hrm.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.htttdn.hrm.entity.AccountActivationToken;

import jakarta.persistence.LockModeType;

public interface AccountActivationTokenRepository extends JpaRepository<AccountActivationToken, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT token
        FROM AccountActivationToken token
        JOIN FETCH token.account
        WHERE token.tokenHash = :tokenHash
        """)
    Optional<AccountActivationToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Modifying(flushAutomatically = true)
    @Query("""
        UPDATE AccountActivationToken token
        SET token.revokedAt = :revokedAt
        WHERE token.account.id = :accountId
          AND token.usedAt IS NULL
          AND token.revokedAt IS NULL
        """)
    int revokeActiveByAccountId(
        @Param("accountId") Long accountId,
        @Param("revokedAt") Instant revokedAt
    );

    @Modifying(flushAutomatically = true)
    @Query("""
        UPDATE AccountActivationToken token
        SET token.revokedAt = :revokedAt
        WHERE token.account.id = :accountId
          AND token.id <> :excludedTokenId
          AND token.usedAt IS NULL
          AND token.revokedAt IS NULL
        """)
    int revokeOtherActiveByAccountId(
        @Param("accountId") Long accountId,
        @Param("excludedTokenId") Long excludedTokenId,
        @Param("revokedAt") Instant revokedAt
    );
}
