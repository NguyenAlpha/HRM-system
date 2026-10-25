package com.htttdn.hrm.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

import com.htttdn.hrm.service.rbac.EmployeeAccessScopeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.LeaveRequest;
import com.htttdn.hrm.entity.Notification;
import com.htttdn.hrm.entity.enums.AccountStatus;
import com.htttdn.hrm.entity.enums.LeaveRequestStatus;
import com.htttdn.hrm.exception.ResourceNotFoundException;
import com.htttdn.hrm.repository.AccountRepository;
import com.htttdn.hrm.repository.AccountRoleAssignmentRepository;
import com.htttdn.hrm.repository.NotificationRepository;
import com.htttdn.hrm.security.CurrentAccountProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private AccountRoleAssignmentRepository roleAssignmentRepository;
    @Mock private EmployeeAccessScopeService employeeAccessScopeService;
    @Mock private CurrentAccountProvider currentAccountProvider;

    @Test
    void notifiesApproverAndOnlyHrWhoCanReadEmployee() {
        Employee applicant = Employee.builder().id(10L).build();
        Employee manager = Employee.builder().id(20L).build();
        LeaveRequest leave = LeaveRequest.builder().id(40L).employee(applicant)
            .approverEmployee(manager).build();
        Account approver = Account.builder().id(2L).status(AccountStatus.ACTIVE).employee(manager).build();
        Account hr = Account.builder().id(3L).status(AccountStatus.ACTIVE).build();
        Account outOfScopeHr = Account.builder().id(4L).status(AccountStatus.ACTIVE).build();
        when(currentAccountProvider.accountId()).thenReturn(1L);
        when(accountRepository.findByEmployeeId(20L)).thenReturn(Optional.of(approver));
        when(roleAssignmentRepository.findActiveAccountsHoldingRole(eq("HR_MANAGER"), any(LocalDate.class)))
            .thenReturn(List.of(hr, outOfScopeHr));
        when(employeeAccessScopeService.canAccountAccessEmployee(3L, 10L, "request.read")).thenReturn(true);

        service().notifyLeaveSubmitted(leave);

        verify(notificationRepository).saveAll(org.mockito.ArgumentMatchers.argThat(items -> {
            List<Notification> saved = StreamSupport.stream(items.spliterator(), false).toList();
            return saved.size() == 2
                && saved.stream().map(item -> item.getRecipient().getId()).toList().containsAll(List.of(2L, 3L))
                && saved.stream().allMatch(item -> item.getLeaveRequest() == leave);
        }));
    }

    @Test
    void anotherAccountCannotMarkNotificationRead() {
        when(currentAccountProvider.accountId()).thenReturn(9L);
        when(notificationRepository.findByIdAndRecipientId(5L, 9L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service().markRead(5L));
    }

    @Test
    void approvedLeaveNotifiesOnlyTheActiveApplicantAccount() {
        Employee applicant = Employee.builder().id(10L).build();
        LeaveRequest leave = LeaveRequest.builder().id(40L).employee(applicant)
            .status(LeaveRequestStatus.APPROVED).build();
        Account recipient = Account.builder().id(3L).employee(applicant)
            .status(AccountStatus.ACTIVE).build();
        when(accountRepository.findByEmployeeId(10L)).thenReturn(Optional.of(recipient));
        when(currentAccountProvider.accountId()).thenReturn(2L);

        service().notifyLeaveDecision(leave);

        verify(notificationRepository).save(org.mockito.ArgumentMatchers.argThat(item ->
            item.getRecipient() == recipient && item.getLeaveRequest() == leave
                && "LEAVE_APPROVED".equals(item.getType())));
    }

    @Test
    void rejectedLeaveDoesNotNotifyAnInactiveApplicant() {
        Employee applicant = Employee.builder().id(10L).build();
        LeaveRequest leave = LeaveRequest.builder().id(40L).employee(applicant)
            .status(LeaveRequestStatus.REJECTED).build();
        Account recipient = Account.builder().id(3L).employee(applicant)
            .status(AccountStatus.DISABLED).build();
        when(accountRepository.findByEmployeeId(10L)).thenReturn(Optional.of(recipient));

        service().notifyLeaveDecision(leave);

        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    void markingReadTwiceKeepsOriginalTimestamp() {
        Instant firstRead = Instant.parse("2026-10-15T00:00:00Z");
        Account recipient = Account.builder().id(9L).build();
        LeaveRequest leave = LeaveRequest.builder().id(40L)
            .employee(Employee.builder().id(10L).fullName("Example").build()).build();
        Notification notification = Notification.builder().id(5L).recipient(recipient)
            .leaveRequest(leave).type("LEAVE_SUBMITTED").readAt(firstRead).build();
        when(currentAccountProvider.accountId()).thenReturn(9L);
        when(notificationRepository.findByIdAndRecipientId(5L, 9L)).thenReturn(Optional.of(notification));
        when(currentAccountProvider.account()).thenReturn(recipient);

        assertEquals(firstRead, service().markRead(5L).readAt());
    }

    private NotificationService service() {
        return new NotificationService(notificationRepository, accountRepository,
            roleAssignmentRepository, employeeAccessScopeService, currentAccountProvider);
    }
}
