package com.htttdn.hrm.repository;

import java.time.LocalDate;
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
import com.htttdn.hrm.entity.ResignationRequest;
import com.htttdn.hrm.entity.enums.ResignationRequestStatus;
import com.htttdn.hrm.exception.ResourceNotFoundException;

import jakarta.persistence.LockModeType;

public interface ResignationRequestRepository
    extends JpaRepository<ResignationRequest, Long>, JpaSpecificationExecutor<ResignationRequest> {

    Page<ResignationRequest> findByEmployeeIdOrderByCreatedAtDesc(Long employeeId, Pageable pageable);

    Page<ResignationRequest> findByApproverEmployeeIdAndStatusOrderByCreatedAtDesc(
        Long approverEmployeeId,
        ResignationRequestStatus status,
        Pageable pageable
    );

    /** A request still waiting for a decision, or approved with the last working day still ahead. */
    @Query("""
        SELECT CASE WHEN COUNT(request) > 0 THEN true ELSE false END
        FROM ResignationRequest request
        WHERE request.employee.id = :employeeId
          AND (request.status = com.htttdn.hrm.entity.enums.ResignationRequestStatus.PENDING
            OR (request.status = com.htttdn.hrm.entity.enums.ResignationRequestStatus.APPROVED
              AND request.completedAt IS NULL))
        """)
    boolean existsOpen(@Param("employeeId") Long employeeId);

    /** Approved requests whose last working day is before {@code today}: employment ends now. */
    @Query("""
        SELECT request
        FROM ResignationRequest request
        JOIN FETCH request.employee
        WHERE request.status = com.htttdn.hrm.entity.enums.ResignationRequestStatus.APPROVED
          AND request.completedAt IS NULL
          AND request.lastWorkingDate < :today
        ORDER BY request.lastWorkingDate, request.id
        """)
    List<ResignationRequest> findDue(@Param("today") LocalDate today);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT request
        FROM ResignationRequest request
        JOIN FETCH request.employee
        WHERE request.id = :id
        """)
    Optional<ResignationRequest> findByIdForUpdate(@Param("id") Long id);

    /** Đơn nghỉ việc theo id, không có thì 404 {@code REQUEST_NOT_FOUND}. Xem {@link AccountRepository#getRequired}. */
    default ResignationRequest getRequired(Long id) {
        return findById(id).orElseThrow(() -> notFound(id));
    }

    /** Như {@link #getRequired}, kèm khóa ghi ({@link #findByIdForUpdate}). */
    default ResignationRequest getRequiredForUpdate(Long id) {
        return findByIdForUpdate(id).orElseThrow(() -> notFound(id));
    }

    private static ResourceNotFoundException notFound(Long id) {
        return new ResourceNotFoundException(ErrorCode.REQUEST_NOT_FOUND, "Resignation request not found: " + id);
    }
}
