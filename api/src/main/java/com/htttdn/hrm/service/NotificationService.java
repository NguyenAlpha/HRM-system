package com.htttdn.hrm.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.htttdn.hrm.config.ClockConfig;
import com.htttdn.hrm.service.rbac.EmployeeAccessScopeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.LeaveRequest;
import com.htttdn.hrm.entity.Notification;
import com.htttdn.hrm.entity.enums.AccountStatus;
import com.htttdn.hrm.entity.enums.LeaveRequestStatus;
import com.htttdn.hrm.entity.enums.LeaveType;
import com.htttdn.hrm.exception.ResourceNotFoundException;
import com.htttdn.hrm.repository.AccountRepository;
import com.htttdn.hrm.repository.AccountRoleAssignmentRepository;
import com.htttdn.hrm.repository.NotificationRepository;
import com.htttdn.hrm.security.CurrentAccountProvider;

import jakarta.annotation.Nullable;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final String LEAVE_SUBMITTED = "LEAVE_SUBMITTED";
    private static final String LEAVE_APPROVED = "LEAVE_APPROVED";
    private static final String LEAVE_REJECTED = "LEAVE_REJECTED";

    private final NotificationRepository notificationRepository;
    private final AccountRepository accountRepository;
    private final AccountRoleAssignmentRepository roleAssignmentRepository;
    private final EmployeeAccessScopeService employeeAccessScopeService;
    private final CurrentAccountProvider currentAccountProvider;

    /** Runs in the leave submission transaction, so a failed submission never leaves a notification. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void notifyLeaveSubmitted(LeaveRequest request) {
        Long actorId = currentAccountProvider.accountId();
        Map<Long, Account> recipients = new LinkedHashMap<>();

        if (request.getApproverEmployee() != null) {
            accountRepository.findByEmployeeId(request.getApproverEmployee().getId())
                .filter(account -> account.getStatus() == AccountStatus.ACTIVE)
                .ifPresent(account -> recipients.put(account.getId(), account));
        }

        LocalDate today = LocalDate.now(ClockConfig.BUSINESS_ZONE);
        for (Account hr : roleAssignmentRepository.findActiveAccountsHoldingRole("HR_MANAGER", today)) {
            if (hr.getStatus() == AccountStatus.ACTIVE
                && employeeAccessScopeService.canAccountAccessEmployee(
                    hr.getId(), request.getEmployee().getId(), "request.read")) {
                recipients.put(hr.getId(), hr);
            }
        }

        recipients.remove(actorId);
        notificationRepository.saveAll(recipients.values().stream()
            .map(account -> Notification.builder()
                .recipient(account)
                .leaveRequest(request)
                .type(LEAVE_SUBMITTED)
                .build())
            .toList());
    }

    /** The applicant receives the decision in the same transaction as the review. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void notifyLeaveDecision(LeaveRequest request) {
        String type = switch (request.getStatus()) {
            case APPROVED -> LEAVE_APPROVED;
            case REJECTED -> LEAVE_REJECTED;
            default -> throw new IllegalArgumentException("Leave request has no decision");
        };
        accountRepository.findByEmployeeId(request.getEmployee().getId())
            .filter(account -> account.getStatus() == AccountStatus.ACTIVE)
            .filter(account -> !account.getId().equals(currentAccountProvider.accountId()))
            .ifPresent(account -> notificationRepository.save(Notification.builder()
                .recipient(account)
                .leaveRequest(request)
                .type(type)
                .build()));
    }

    @Transactional(readOnly = true)
    public InboxView inbox() {
        Account account = currentAccountProvider.account();
        List<NotificationView> items = notificationRepository.findTop30ByRecipientIdOrderByCreatedAtDesc(account.getId())
            .stream().map(notification -> toView(notification, account)).toList();
        return new InboxView(notificationRepository.countByRecipientIdAndReadAtIsNull(account.getId()), items);
    }

    @Transactional
    public NotificationView markRead(Long id) {
        Long accountId = currentAccountProvider.accountId();
        Notification notification = notificationRepository.findByIdAndRecipientId(id, accountId)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.RESOURCE_NOT_FOUND, "Notification not found: " + id));
        if (notification.getReadAt() == null) {
            notification.setReadAt(Instant.now());
        }
        return toView(notification, currentAccountProvider.account());
    }

    private NotificationView toView(Notification notification, Account account) {
        LeaveRequest request = notification.getLeaveRequest();
        boolean requiresApproval = request.getStatus() == LeaveRequestStatus.PENDING
            && account.getEmployee() != null
            && request.getApproverEmployee() != null
            && account.getEmployee().getId().equals(request.getApproverEmployee().getId());
        return new NotificationView(
            notification.getId(), notification.getType(), request.getId(),
            request.getEmployee().getFullName(), request.getLeaveType(), request.getStartAt(),
            notification.getCreatedAt(), notification.getReadAt(), requiresApproval
        );
    }

    public record InboxView(long unreadCount, List<NotificationView> items) { }

    public record NotificationView(
        Long id,
        String type,
        Long leaveRequestId,
        String employeeName,
        LeaveType leaveType,
        Instant startAt,
        Instant createdAt,
        @Nullable Instant readAt,
        boolean requiresApproval
    ) { }
}
