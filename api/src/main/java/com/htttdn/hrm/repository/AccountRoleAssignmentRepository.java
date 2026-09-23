package com.htttdn.hrm.repository;

import java.util.List;
import java.time.LocalDate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.htttdn.hrm.entity.AccountRoleAssignment;
import com.htttdn.hrm.entity.enums.RoleScopeType;

public interface AccountRoleAssignmentRepository extends JpaRepository<AccountRoleAssignment, Long> {

    List<AccountRoleAssignment> findByAccountId(Long accountId);

    List<AccountRoleAssignment> findByAccountIdAndEffectiveToIsNull(Long accountId);

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
