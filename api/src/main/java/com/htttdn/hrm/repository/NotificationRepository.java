package com.htttdn.hrm.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.htttdn.hrm.entity.Notification;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @EntityGraph(attributePaths = { "leaveRequest", "leaveRequest.employee", "leaveRequest.approverEmployee" })
    List<Notification> findTop30ByRecipientIdOrderByCreatedAtDesc(Long recipientId);

    long countByRecipientIdAndReadAtIsNull(Long recipientId);

    @EntityGraph(attributePaths = { "leaveRequest", "leaveRequest.employee", "leaveRequest.approverEmployee" })
    Optional<Notification> findByIdAndRecipientId(Long id, Long recipientId);
}
