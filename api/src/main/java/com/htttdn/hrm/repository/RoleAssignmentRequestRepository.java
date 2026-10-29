package com.htttdn.hrm.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.RoleAssignmentRequest;
import com.htttdn.hrm.entity.enums.RoleAssignmentRequestStatus;
import com.htttdn.hrm.exception.ResourceNotFoundException;

import jakarta.persistence.LockModeType;

public interface RoleAssignmentRequestRepository extends JpaRepository<RoleAssignmentRequest, Long> {

    boolean existsByOrganizationUnitIdAndStatus(
        Long organizationUnitId,
        RoleAssignmentRequestStatus status
    );

    boolean existsByWorkLocationIdAndStatus(
        Long workLocationId,
        RoleAssignmentRequestStatus status
    );

    @Override
    @EntityGraph(attributePaths = {
        "account.employee", "role", "organizationUnit", "workLocation",
        "requestedByAccount", "reviewedByAccount", "cancelledByAccount", "accountRoleAssignment",
        "targetAssignment"
    })
    Page<RoleAssignmentRequest> findAll(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {
        "account.employee", "role", "organizationUnit", "workLocation",
        "requestedByAccount", "reviewedByAccount", "cancelledByAccount", "accountRoleAssignment",
        "targetAssignment"
    })
    Optional<RoleAssignmentRequest> findById(Long id);

    @EntityGraph(attributePaths = {
        "account.employee", "role", "organizationUnit", "workLocation",
        "requestedByAccount", "reviewedByAccount", "cancelledByAccount", "accountRoleAssignment",
        "targetAssignment"
    })
    Page<RoleAssignmentRequest> findByStatus(
        RoleAssignmentRequestStatus status,
        Pageable pageable
    );

    @EntityGraph(attributePaths = {
        "account.employee", "role", "organizationUnit", "workLocation",
        "requestedByAccount", "reviewedByAccount", "cancelledByAccount", "accountRoleAssignment",
        "targetAssignment"
    })
    Page<RoleAssignmentRequest> findByRequestedByAccountId(
        Long requestedByAccountId,
        Pageable pageable
    );

    @EntityGraph(attributePaths = {
        "account.employee", "role", "organizationUnit", "workLocation",
        "requestedByAccount", "reviewedByAccount", "cancelledByAccount", "accountRoleAssignment",
        "targetAssignment"
    })
    Page<RoleAssignmentRequest> findByRequestedByAccountIdAndStatus(
        Long requestedByAccountId,
        RoleAssignmentRequestStatus status,
        Pageable pageable
    );

    /** Requests of the account in {@code status}, locked as approving one locks it, so the two never race. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT request FROM RoleAssignmentRequest request WHERE request.account.id = :accountId AND request.status = :status")
    List<RoleAssignmentRequest> findByAccountIdAndStatusForUpdate(
        @Param("accountId") Long accountId,
        @Param("status") RoleAssignmentRequestStatus status
    );

    /** Như {@link #findByAccountIdAndStatusForUpdate}, theo role. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT request FROM RoleAssignmentRequest request WHERE request.role.id = :roleId AND request.status = :status")
    List<RoleAssignmentRequest> findByRoleIdAndStatusForUpdate(
        @Param("roleId") Long roleId,
        @Param("status") RoleAssignmentRequestStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT request FROM RoleAssignmentRequest request WHERE request.id = :id")
    Optional<RoleAssignmentRequest> findByIdForUpdate(@Param("id") Long id);

    /** Yêu cầu cấp/thu hồi role theo id, không có thì 404. Xem {@link AccountRepository#getRequired}. */
    default RoleAssignmentRequest getRequired(Long id) {
        return findById(id).orElseThrow(() -> notFound(id));
    }

    /** Như {@link #getRequired}, kèm khóa ghi ({@link #findByIdForUpdate}). */
    default RoleAssignmentRequest getRequiredForUpdate(Long id) {
        return findByIdForUpdate(id).orElseThrow(() -> notFound(id));
    }

    private static ResourceNotFoundException notFound(Long id) {
        return new ResourceNotFoundException(
            ErrorCode.ROLE_ASSIGNMENT_REQUEST_NOT_FOUND,
            "Role assignment request not found: " + id
        );
    }
}
