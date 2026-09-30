package com.htttdn.hrm.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.htttdn.hrm.entity.AccountPermissionOverride;

import jakarta.persistence.LockModeType;

public interface AccountPermissionOverrideRepository extends JpaRepository<AccountPermissionOverride, Long> {

    boolean existsByPermissionId(Long permissionId);

    @Query("""
        SELECT permissionOverride
        FROM AccountPermissionOverride permissionOverride
        JOIN FETCH permissionOverride.permission
        JOIN FETCH permissionOverride.grantedByAccount
        LEFT JOIN FETCH permissionOverride.revokedByAccount
        WHERE permissionOverride.accountRoleAssignment.id = :assignmentId
        ORDER BY permissionOverride.createdAt DESC, permissionOverride.id DESC
        """)
    List<AccountPermissionOverride> findHistoryByAssignmentId(@Param("assignmentId") Long assignmentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT permissionOverride
        FROM AccountPermissionOverride permissionOverride
        JOIN FETCH permissionOverride.accountRoleAssignment assignment
        JOIN FETCH permissionOverride.permission
        WHERE permissionOverride.id = :overrideId
          AND assignment.id = :assignmentId
        """)
    Optional<AccountPermissionOverride> findByIdAndAssignmentIdForUpdate(
        @Param("overrideId") Long overrideId,
        @Param("assignmentId") Long assignmentId
    );

    @Query("""
        SELECT CASE WHEN COUNT(permissionOverride) > 0 THEN true ELSE false END
        FROM AccountPermissionOverride permissionOverride
        WHERE permissionOverride.accountRoleAssignment.id = :assignmentId
          AND permissionOverride.permission.id = :permissionId
          AND permissionOverride.revokedAt IS NULL
          AND (:effectiveTo IS NULL OR permissionOverride.effectiveFrom <= :effectiveTo)
          AND (permissionOverride.effectiveTo IS NULL OR permissionOverride.effectiveTo >= :effectiveFrom)
        """)
    boolean existsOverlappingActive(
        @Param("assignmentId") Long assignmentId,
        @Param("permissionId") Long permissionId,
        @Param("effectiveFrom") LocalDate effectiveFrom,
        @Param("effectiveTo") LocalDate effectiveTo
    );

    @Query("""
        SELECT permissionOverride
        FROM AccountPermissionOverride permissionOverride
        JOIN FETCH permissionOverride.permission permission
        WHERE permissionOverride.accountRoleAssignment.id IN :assignmentIds
          AND permissionOverride.effectiveFrom <= :date
          AND (permissionOverride.effectiveTo IS NULL OR permissionOverride.effectiveTo >= :date)
          AND permissionOverride.revokedAt IS NULL
          AND permission.isActive = true
        """)
    List<AccountPermissionOverride> findActiveByAssignmentIds(
        @Param("assignmentIds") List<Long> assignmentIds,
        @Param("date") LocalDate date
    );
}
