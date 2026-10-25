package com.htttdn.hrm.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.AccountRoleAssignment;
import com.htttdn.hrm.entity.enums.RoleScopeType;
import com.htttdn.hrm.exception.ResourceNotFoundException;

import jakarta.persistence.LockModeType;

public interface AccountRoleAssignmentRepository extends JpaRepository<AccountRoleAssignment, Long> {

    List<AccountRoleAssignment> findByAccountIdOrderByCreatedAtDesc(Long accountId);

    /** Pairs of (account id, role code) for roles not revoked, the same test the account guard uses. */
    @Query("""
        SELECT assignment.account.id, assignment.role.code
        FROM AccountRoleAssignment assignment
        WHERE assignment.account.id IN :accountIds AND assignment.revokedAt IS NULL
        """)
    List<Object[]> findHeldRoleCodes(@Param("accountIds") List<Long> accountIds);

    Optional<AccountRoleAssignment> findByIdAndAccountId(Long id, Long accountId);

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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT assignment FROM AccountRoleAssignment assignment WHERE assignment.id = :id")
    Optional<AccountRoleAssignment> findByIdForUpdate(@Param("id") Long id);

    List<AccountRoleAssignment> findByAccountIdAndRoleIdAndRevokedAtIsNull(Long accountId, Long roleId);

    List<AccountRoleAssignment> findByAccountIdAndRevokedAtIsNull(Long accountId);

    /** Accounts holding the role on the date, earliest assignment first. */
    @Query("""
        SELECT assignment.account
        FROM AccountRoleAssignment assignment
        WHERE assignment.role.code = :roleCode
          AND assignment.revokedAt IS NULL
          AND assignment.effectiveFrom <= :date
          AND (assignment.effectiveTo IS NULL OR assignment.effectiveTo >= :date)
        ORDER BY assignment.effectiveFrom
        """)
    List<Account> findAccountsHoldingRole(
        @Param("roleCode") String roleCode,
        @Param("date") LocalDate date
    );

    @Query("""
        SELECT DISTINCT assignment.account
        FROM AccountRoleAssignment assignment
        WHERE assignment.role.code = :roleCode
          AND assignment.role.isActive = true
          AND assignment.role.deletedAt IS NULL
          AND assignment.revokedAt IS NULL
          AND assignment.effectiveFrom <= :date
          AND (assignment.effectiveTo IS NULL OR assignment.effectiveTo >= :date)
        """)
    List<Account> findActiveAccountsHoldingRole(
        @Param("roleCode") String roleCode,
        @Param("date") LocalDate date
    );

    boolean existsByAccountIdAndRoleCodeAndRevokedAtIsNull(Long accountId, String roleCode);

    List<AccountRoleAssignment> findByRoleIdAndRevokedAtIsNull(Long roleId);

    @Query("""
        SELECT CASE WHEN COUNT(assignment) > 0 THEN true ELSE false END
        FROM AccountRoleAssignment assignment
        WHERE assignment.organizationUnit.id = :organizationUnitId
          AND assignment.revokedAt IS NULL
          AND (assignment.effectiveTo IS NULL OR assignment.effectiveTo >= :date)
        """)
    boolean existsCurrentOrFutureByOrganizationUnitId(
        @Param("organizationUnitId") Long organizationUnitId,
        @Param("date") LocalDate date
    );

    @Query("""
        SELECT CASE WHEN COUNT(assignment) > 0 THEN true ELSE false END
        FROM AccountRoleAssignment assignment
        WHERE assignment.workLocation.id = :workLocationId
          AND assignment.revokedAt IS NULL
          AND (assignment.effectiveTo IS NULL OR assignment.effectiveTo >= :date)
        """)
    boolean existsCurrentOrFutureByWorkLocationId(
        @Param("workLocationId") Long workLocationId,
        @Param("date") LocalDate date
    );

    boolean existsByAccountIdAndRoleIdAndScopeTypeAndEffectiveToIsNull(
        Long accountId,
        Long roleId,
        RoleScopeType scopeType
    );

    @Query("""
        SELECT assignment
        FROM AccountRoleAssignment assignment
        JOIN FETCH assignment.role role
        LEFT JOIN FETCH assignment.organizationUnit
        LEFT JOIN FETCH assignment.workLocation
        WHERE assignment.account.id = :accountId
          AND assignment.revokedAt IS NULL
          AND assignment.effectiveFrom <= :date
          AND (assignment.effectiveTo IS NULL OR assignment.effectiveTo >= :date)
          AND role.deletedAt IS NULL
        """)
    List<AccountRoleAssignment> findActiveWithRoleByAccountId(
        @Param("accountId") Long accountId,
        @Param("date") LocalDate date
    );

    /** Gán role theo id, không có thì 404 {@code ROLE_ASSIGNMENT_NOT_FOUND}. Xem {@link AccountRepository#getRequired}. */
    default AccountRoleAssignment getRequiredForUpdate(Long id) {
        return findByIdForUpdate(id).orElseThrow(() -> notFound(id));
    }

    /** Gán role theo id, chỉ khi thuộc đúng tài khoản {@code accountId}; khác tài khoản cũng là 404. */
    default AccountRoleAssignment getRequired(Long id, Long accountId) {
        return findByIdAndAccountId(id, accountId).orElseThrow(() -> notFound(id));
    }

    /** Như {@link #getRequired(Long, Long)}, kèm khóa ghi. */
    default AccountRoleAssignment getRequiredForUpdate(Long id, Long accountId) {
        return findByIdAndAccountIdForUpdate(id, accountId).orElseThrow(() -> notFound(id));
    }

    private static ResourceNotFoundException notFound(Long id) {
        return new ResourceNotFoundException(ErrorCode.ROLE_ASSIGNMENT_NOT_FOUND, "Role assignment not found: " + id);
    }
}
