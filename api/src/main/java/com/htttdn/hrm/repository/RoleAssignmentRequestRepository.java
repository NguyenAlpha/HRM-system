package com.htttdn.hrm.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.htttdn.hrm.entity.RoleAssignmentRequest;
import com.htttdn.hrm.entity.enums.RoleAssignmentRequestStatus;

import jakarta.persistence.LockModeType;

public interface RoleAssignmentRequestRepository extends JpaRepository<RoleAssignmentRequest, Long> {

    Page<RoleAssignmentRequest> findByStatusOrderByRequestedAtDesc(
        RoleAssignmentRequestStatus status,
        Pageable pageable
    );

    Page<RoleAssignmentRequest> findByRequestedByAccountIdOrderByRequestedAtDesc(
        Long requestedByAccountId,
        Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT request FROM RoleAssignmentRequest request WHERE request.id = :id")
    Optional<RoleAssignmentRequest> findByIdForUpdate(@Param("id") Long id);
}
