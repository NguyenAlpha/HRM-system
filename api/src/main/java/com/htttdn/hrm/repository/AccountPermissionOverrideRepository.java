package com.htttdn.hrm.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.htttdn.hrm.entity.AccountPermissionOverride;

public interface AccountPermissionOverrideRepository extends JpaRepository<AccountPermissionOverride, Long> {

    boolean existsByPermissionId(Long permissionId);

    @Query("""
        SELECT permissionOverride
        FROM AccountPermissionOverride permissionOverride
        JOIN FETCH permissionOverride.permission permission
        WHERE permissionOverride.accountRoleAssignment.id IN :assignmentIds
          AND permissionOverride.effectiveFrom <= :date
          AND (permissionOverride.effectiveTo IS NULL OR permissionOverride.effectiveTo >= :date)
          AND permission.isActive = true
        """)
    List<AccountPermissionOverride> findActiveByAssignmentIds(
        @Param("assignmentIds") List<Long> assignmentIds,
        @Param("date") LocalDate date
    );
}
