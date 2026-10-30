package com.htttdn.hrm.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.LeaveRequest;
import com.htttdn.hrm.entity.enums.LeaveRequestStatus;
import com.htttdn.hrm.entity.enums.LeaveType;
import com.htttdn.hrm.exception.ResourceNotFoundException;

import jakarta.persistence.LockModeType;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long>, JpaSpecificationExecutor<LeaveRequest> {

    Page<LeaveRequest> findByEmployeeIdOrderByCreatedAtDesc(Long employeeId, Pageable pageable);

    Page<LeaveRequest> findByStatusOrderByCreatedAtDesc(LeaveRequestStatus status, Pageable pageable);

    Page<LeaveRequest> findByApproverEmployeeIdAndStatusOrderByCreatedAtDesc(
        Long approverEmployeeId,
        LeaveRequestStatus status,
        Pageable pageable
    );

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

    /** Requests of the employee in {@code statuses} still running after {@code after}, locked as deciding one locks it. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT request
        FROM LeaveRequest request
        WHERE request.employee.id = :employeeId
          AND request.status IN :statuses
          AND request.endAt > :after
        """)
    List<LeaveRequest> findEndingAfterForUpdate(
        @Param("employeeId") Long employeeId,
        @Param("statuses") Collection<LeaveRequestStatus> statuses,
        @Param("after") Instant after
    );

    /**
     * Minutes already committed against a leave year. The caller passes the year bounds because
     * the business day is resolved in Asia/Ho_Chi_Minh, not UTC.
     */
    @Query("""
        SELECT COALESCE(SUM(request.requestedMinutes), 0)
        FROM LeaveRequest request
        WHERE request.employee.id = :employeeId
          AND request.leaveType = :leaveType
          AND request.status IN :statuses
          AND request.startAt >= :from
          AND request.startAt < :to
          AND (:excludedRequestId IS NULL OR request.id <> :excludedRequestId)
        """)
    long sumMinutesByTypeAndStatuses(
        @Param("employeeId") Long employeeId,
        @Param("leaveType") LeaveType leaveType,
        @Param("statuses") Collection<LeaveRequestStatus> statuses,
        @Param("from") Instant from,
        @Param("to") Instant to,
        @Param("excludedRequestId") Long excludedRequestId
    );

    /** Đơn nghỉ phép theo id, không có thì 404 {@code REQUEST_NOT_FOUND}. Xem {@link AccountRepository#getRequired}. */
    default LeaveRequest getRequired(Long id) {
        return findById(id).orElseThrow(() -> notFound(id));
    }

    /** Như {@link #getRequired}, kèm khóa ghi ({@link #findByIdForUpdate}). */
    default LeaveRequest getRequiredForUpdate(Long id) {
        return findByIdForUpdate(id).orElseThrow(() -> notFound(id));
    }

    private static ResourceNotFoundException notFound(Long id) {
        return new ResourceNotFoundException(ErrorCode.REQUEST_NOT_FOUND, "Leave request not found: " + id);
    }
}
