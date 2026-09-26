package com.htttdn.hrm.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.htttdn.hrm.entity.AccountRoleAssignment;
import com.htttdn.hrm.entity.enums.RoleScopeType;

import jakarta.persistence.LockModeType;

public interface AccountRoleAssignmentRepository extends JpaRepository<AccountRoleAssignment, Long> {

    List<AccountRoleAssignment> findByAccountIdOrderByCreatedAtDesc(Long accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT assignment
        FROM AccountRoleAssignment assignment
        WHERE assignment.id = :id AND assignment.account.id = :accountId
        """)
    Optional<AccountRoleAssignment> findByIdAndAccountIdForUpdate(
        @Param("id") Long id,
        @Param("accountId") Long accountId
    );

    List<AccountRoleAssignment> findByAccountIdAndRoleIdAndRevokedAtIsNull(Long accountId, Long roleId);

    List<AccountRoleAssignment> findByRoleIdAndRevokedAtIsNull(Long roleId);

    boolean existsByAccountIdAndRoleIdAndScopeTypeAndEffectiveToIsNull(
        Long accountId,
        Long roleId,
        RoleScopeType scopeType
    );

    @Query("""
        SELECT assignment
        FROM AccountRoleAssignment assignment
        JOIN FETCH assignment.role role
        WHERE assignment.account.id = :accountId
          AND assignment.revokedAt IS NULL
          AND assignment.effectiveFrom <= :date
          AND (assignment.effectiveTo IS NULL OR assignment.effectiveTo >= :date)
          AND role.isActive = true
          AND role.deletedAt IS NULL
        """)
    List<AccountRoleAssignment> findActiveWithRoleByAccountId(
        @Param("accountId") Long accountId,
        @Param("date") LocalDate date
    );
}
