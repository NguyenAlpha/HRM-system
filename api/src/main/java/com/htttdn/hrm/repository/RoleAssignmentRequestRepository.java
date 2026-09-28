package com.htttdn.hrm.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.htttdn.hrm.entity.RoleAssignmentRequest;
import com.htttdn.hrm.entity.enums.RoleAssignmentRequestStatus;

import jakarta.persistence.LockModeType;

public interface RoleAssignmentRequestRepository extends JpaRepository<RoleAssignmentRequest, Long> {

    boolean existsByOrganizationUnitIdAndStatus(
        Long organizationUnitId,
        RoleAssignmentRequestStatus status
    );

    @Override
    @EntityGraph(attributePaths = {
        "account.employee", "role", "organizationUnit", "workLocation",
        "requestedByAccount", "reviewedByAccount", "cancelledByAccount", "accountRoleAssignment"
    })
    Page<RoleAssignmentRequest> findAll(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {
        "account.employee", "role", "organizationUnit", "workLocation",
        "requestedByAccount", "reviewedByAccount", "cancelledByAccount", "accountRoleAssignment"
    })
    Optional<RoleAssignmentRequest> findById(Long id);

    @EntityGraph(attributePaths = {
        "account.employee", "role", "organizationUnit", "workLocation",
        "requestedByAccount", "reviewedByAccount", "cancelledByAccount", "accountRoleAssignment"
    })
    Page<RoleAssignmentRequest> findByStatus(
        RoleAssignmentRequestStatus status,
        Pageable pageable
    );

    @EntityGraph(attributePaths = {
        "account.employee", "role", "organizationUnit", "workLocation",
        "requestedByAccount", "reviewedByAccount", "cancelledByAccount", "accountRoleAssignment"
    })
    Page<RoleAssignmentRequest> findByRequestedByAccountId(
        Long requestedByAccountId,
        Pageable pageable
    );

    @EntityGraph(attributePaths = {
        "account.employee", "role", "organizationUnit", "workLocation",
        "requestedByAccount", "reviewedByAccount", "cancelledByAccount", "accountRoleAssignment"
    })
    Page<RoleAssignmentRequest> findByRequestedByAccountIdAndStatus(
        Long requestedByAccountId,
        RoleAssignmentRequestStatus status,
        Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT request FROM RoleAssignmentRequest request WHERE request.id = :id")
    Optional<RoleAssignmentRequest> findByIdForUpdate(@Param("id") Long id);
}
