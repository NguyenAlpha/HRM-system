package com.htttdn.hrm.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.htttdn.hrm.entity.RefreshToken;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("""
        UPDATE RefreshToken token
        SET token.revokedAt = :revokedAt
        WHERE token.tokenHash = :tokenHash AND token.revokedAt IS NULL
        """)
    int revokeIfActive(@Param("tokenHash") String tokenHash, @Param("revokedAt") Instant revokedAt);

    @Modifying
    @Query("""
        UPDATE RefreshToken token
        SET token.revokedAt = :revokedAt
        WHERE token.account.id = :accountId AND token.revokedAt IS NULL
        """)
    int revokeAllByAccountId(@Param("accountId") Long accountId, @Param("revokedAt") Instant revokedAt);

    @Modifying
    @Query("""
        UPDATE RefreshToken token
        SET token.revokedAt = :revokedAt
        WHERE token.tokenHash = :tokenHash
          AND token.account.id = :accountId
          AND token.revokedAt IS NULL
        """)
    int revokeForAccount(
        @Param("tokenHash") String tokenHash,
        @Param("accountId") Long accountId,
        @Param("revokedAt") Instant revokedAt
    );
}
