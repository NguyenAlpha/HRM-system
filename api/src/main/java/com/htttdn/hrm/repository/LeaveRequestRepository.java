package com.htttdn.hrm.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.htttdn.hrm.entity.LeaveRequest;
import com.htttdn.hrm.entity.enums.LeaveRequestStatus;

import jakarta.persistence.LockModeType;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    Page<LeaveRequest> findByEmployeeIdOrderByCreatedAtDesc(Long employeeId, Pageable pageable);

    Page<LeaveRequest> findByStatusOrderByCreatedAtDesc(LeaveRequestStatus status, Pageable pageable);

    Page<LeaveRequest> findByEmployeeIdInAndStatusOrderByCreatedAtDesc(
        Collection<Long> employeeIds, LeaveRequestStatus status, Pageable pageable);

    Page<LeaveRequest> findByEmployeeIdAndStatusOrderByCreatedAtDesc(
        Long employeeId,
        LeaveRequestStatus status,
        Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT request
        FROM LeaveRequest request
        JOIN FETCH request.employee
        WHERE request.id = :id
        """)
    Optional<LeaveRequest> findByIdForUpdate(@Param("id") Long id);

    @Query("""
        SELECT CASE WHEN COUNT(request) > 0 THEN true ELSE false END
        FROM LeaveRequest request
        WHERE request.employee.id = :employeeId
          AND request.status = com.htttdn.hrm.entity.enums.LeaveRequestStatus.APPROVED
          AND request.startAt < :endAt
          AND request.endAt > :startAt
          AND (:excludedRequestId IS NULL OR request.id <> :excludedRequestId)
        """)
    boolean existsApprovedOverlap(
        @Param("employeeId") Long employeeId,
        @Param("startAt") Instant startAt,
        @Param("endAt") Instant endAt,
        @Param("excludedRequestId") Long excludedRequestId
    );

    boolean existsByEmployeeId(Long employeeId);
}
