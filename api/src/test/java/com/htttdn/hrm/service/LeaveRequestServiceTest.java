package com.htttdn.hrm.service;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.LeaveRequest;
import com.htttdn.hrm.entity.enums.LeaveRequestStatus;
import com.htttdn.hrm.entity.enums.LeaveSalaryTreatment;
import com.htttdn.hrm.entity.enums.LeaveType;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.repository.AccountRepository;
import com.htttdn.hrm.repository.EmployeeRepository;
import com.htttdn.hrm.repository.LeaveRequestRepository;
import com.htttdn.hrm.security.CurrentAccountProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LeaveRequestServiceTest {

    @Mock private LeaveRequestRepository leaveRequestRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private CurrentAccountProvider currentAccountProvider;
    @Mock private EmployeeAccessScopeService employeeAccessScopeService;
    @Mock private AttendanceService attendanceService;

    @Test
    void employeeCreatesOwnDraftUsingMinuteBasedPeriod() {
        Employee employee = Employee.builder().id(1L).build();
        Account actor = Account.builder().id(9L).employee(employee).build();
        when(employeeRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(employee));
        when(currentAccountProvider.accountId()).thenReturn(9L);
        when(accountRepository.findById(9L)).thenReturn(Optional.of(actor));
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(invocation -> {
            LeaveRequest saved = invocation.getArgument(0);
            saved.setId(3L);
            return saved;
        });

        Instant startAt = Instant.parse("2026-01-05T01:00:00Z");
        Instant endAt = Instant.parse("2026-01-05T05:00:00Z");
        var result = service().createDraft(new LeaveRequestService.CreateLeaveCommand(
            1L,
            LeaveType.ANNUAL,
            LeaveSalaryTreatment.EMPLOYER_PAID,
            startAt,
            endAt,
            240,
            "Personal appointment",
            null
        ));

        assertEquals(LeaveRequestStatus.DRAFT, result.status());
        assertEquals(240, result.requestedMinutes());
        assertEquals(startAt, result.startAt());
    }

    @Test
    void approvingLeaveUpdatesAttendance() {
        Employee employee = Employee.builder().id(1L).build();
        LeaveRequest request = LeaveRequest.builder().id(3L).employee(employee)
            .status(LeaveRequestStatus.PENDING)
            .startAt(Instant.parse("2026-01-05T01:00:00Z"))
            .endAt(Instant.parse("2026-01-05T10:00:00Z"))
            .requestedMinutes(480).build();
        when(leaveRequestRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(request));
        when(currentAccountProvider.accountId()).thenReturn(9L);
        when(accountRepository.findById(9L)).thenReturn(Optional.of(Account.builder().id(9L).build()));

        service().approve(3L, "Approved");

        verify(attendanceService).applyApprovedLeave(request);
        assertEquals(LeaveRequestStatus.APPROVED, request.getStatus());
    }

    @Test
    void approveRejectsOverlappingApprovedLeave() {
        Employee employee = Employee.builder().id(1L).build();
        LeaveRequest request = LeaveRequest.builder()
            .id(3L)
            .employee(employee)
            .status(LeaveRequestStatus.PENDING)
            .startAt(Instant.parse("2026-01-05T01:00:00Z"))
            .endAt(Instant.parse("2026-01-05T05:00:00Z"))
            .build();
        when(leaveRequestRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(request));
        when(leaveRequestRepository.existsApprovedOverlap(
            1L, request.getStartAt(), request.getEndAt(), 3L
        )).thenReturn(true);

        ConflictException exception = assertThrows(
            ConflictException.class,
            () -> service().approve(3L, "Approved")
        );

        assertTrue(exception.getMessage().contains("approved leave request"));
    }

    private LeaveRequestService service() {
        return new LeaveRequestService(
            leaveRequestRepository,
            employeeRepository,
            accountRepository,
            currentAccountProvider,
            employeeAccessScopeService,
            attendanceService
        );
    }
}
