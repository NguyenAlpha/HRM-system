package com.htttdn.hrm.service.leave;

import com.htttdn.hrm.config.ClockConfig;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import com.htttdn.hrm.service.attendance.AttendanceService;
import com.htttdn.hrm.service.employee.ApproverResolver;
import com.htttdn.hrm.service.rbac.EmployeeAccessScopeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.LeaveRequest;
import com.htttdn.hrm.entity.enums.LeaveRequestStatus;
import com.htttdn.hrm.entity.enums.LeaveSalaryTreatment;
import com.htttdn.hrm.entity.enums.LeaveType;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.exception.ForbiddenException;
import com.htttdn.hrm.repository.AccountRepository;
import com.htttdn.hrm.repository.EmployeeRepository;
import com.htttdn.hrm.repository.LeaveRequestRepository;
import com.htttdn.hrm.security.CurrentAccountProvider;
import com.htttdn.hrm.service.NotificationService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
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
    @Mock private LeaveEntitlementService leaveEntitlementService;
    @Mock private ApproverResolver approverResolver;
    @Mock private NotificationService notificationService;

    @Test
    void employeeCreatesOwnDraftUsingMinuteBasedPeriod() {
        Employee employee = Employee.builder().id(1L).build();
        Account actor = Account.builder().id(9L).employee(employee).build();
        when(employeeRepository.getRequired(1L)).thenReturn(employee);
        when(currentAccountProvider.account()).thenReturn(actor);
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(invocation -> {
            LeaveRequest saved = invocation.getArgument(0);
            saved.setId(3L);
            return saved;
        });

        Instant startAt = Instant.parse("2026-01-05T01:00:00Z");
        Instant endAt = Instant.parse("2026-01-05T05:00:00Z");
        when(attendanceService.leaveMinutesFor(1L, startAt, endAt)).thenReturn(240);
        var result = service().createDraft(new LeaveRequestService.CreateLeaveCommand(
            1L,
            LeaveType.ANNUAL,
            LeaveSalaryTreatment.EMPLOYER_PAID,
            startAt,
            endAt,
            "Personal appointment",
            null
        ));

        assertEquals(LeaveRequestStatus.DRAFT, result.status());
        assertEquals(240, result.requestedMinutes());
        assertEquals(startAt, result.startAt());
    }

    @Test
    void hrCannotCreateLeaveForAnotherEmployeeEvenWithManagePermission() {
        Employee applicant = Employee.builder().id(1L).build();
        Employee hr = Employee.builder().id(2L).build();
        when(currentAccountProvider.account()).thenReturn(
            Account.builder().id(9L).employee(hr).build());

        assertThrows(ForbiddenException.class, () -> service().createDraft(
            new LeaveRequestService.CreateLeaveCommand(
                applicant.getId(), LeaveType.ANNUAL, null,
                Instant.parse("2026-01-05T01:00:00Z"), Instant.parse("2026-01-05T05:00:00Z"),
                "Personal", null)));
    }

    @Test
    void hrCannotSubmitOrCancelAnotherEmployeesDraft() {
        Employee applicant = Employee.builder().id(1L).build();
        Employee hr = Employee.builder().id(2L).build();
        LeaveRequest request = LeaveRequest.builder().id(3L).employee(applicant)
            .status(LeaveRequestStatus.DRAFT).build();
        when(leaveRequestRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(request));
        when(currentAccountProvider.account()).thenReturn(
            Account.builder().id(9L).employee(hr).build());

        assertThrows(ForbiddenException.class, () -> service().submit(3L));
        assertThrows(ForbiddenException.class, () -> service().cancel(3L));
        assertEquals(LeaveRequestStatus.DRAFT, request.getStatus());
    }

    @Test
    void submittingLeaveNotifiesHrAndApprover() {
        Employee employee = Employee.builder().id(1L).fullName("Employee").build();
        Employee manager = Employee.builder().id(5L).fullName("Manager").build();
        LeaveRequest request = LeaveRequest.builder().id(3L).employee(employee)
            .status(LeaveRequestStatus.DRAFT).build();
        when(leaveRequestRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(request));
        when(leaveRequestRepository.saveAndFlush(request)).thenReturn(request);
        when(currentAccountProvider.account())
            .thenReturn(Account.builder().id(9L).employee(employee).build());
        when(approverResolver.approverFor(employee)).thenReturn(Optional.of(manager));

        var result = service().submit(3L);

        assertEquals(LeaveRequestStatus.PENDING, result.status());
        assertEquals(5L, result.approverEmployeeId());
        verify(notificationService).notifyLeaveSubmitted(request);
    }

    @Test
    void approvingLeaveUpdatesAttendance() {
        Employee employee = Employee.builder().id(1L).build();
        Employee manager = Employee.builder().id(5L).build();
        LeaveRequest request = LeaveRequest.builder().id(3L).employee(employee).approverEmployee(manager)
            .status(LeaveRequestStatus.PENDING)
            .startAt(Instant.parse("2026-01-05T01:00:00Z"))
            .endAt(Instant.parse("2026-01-05T10:00:00Z"))
            .requestedMinutes(480).build();
        when(leaveRequestRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(request));
        when(leaveRequestRepository.saveAndFlush(request)).thenReturn(request);
        when(currentAccountProvider.account()).thenReturn(Account.builder().id(9L).employee(manager).build());

        service().approve(3L, "Approved");

        verify(attendanceService).applyApprovedLeave(request);
        verify(notificationService).notifyLeaveDecision(request);
        assertEquals(LeaveRequestStatus.APPROVED, request.getStatus());
    }

    @Test
    void rejectingLeaveNotifiesApplicant() {
        Employee employee = Employee.builder().id(1L).build();
        Employee manager = Employee.builder().id(5L).build();
        LeaveRequest request = LeaveRequest.builder().id(3L).employee(employee).approverEmployee(manager)
            .status(LeaveRequestStatus.PENDING).build();
        when(leaveRequestRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(request));
        when(leaveRequestRepository.saveAndFlush(request)).thenReturn(request);
        when(currentAccountProvider.account()).thenReturn(
            Account.builder().id(9L).employee(manager).build());

        service().reject(3L, "Not approved");

        assertEquals(LeaveRequestStatus.REJECTED, request.getStatus());
        verify(notificationService).notifyLeaveDecision(request);
    }

    @Test
    void approveRejectsOverlappingApprovedLeave() {
        Employee employee = Employee.builder().id(1L).build();
        Employee manager = Employee.builder().id(5L).build();
        LeaveRequest request = LeaveRequest.builder()
            .id(3L)
            .employee(employee)
            .approverEmployee(manager)
            .status(LeaveRequestStatus.PENDING)
            .startAt(Instant.parse("2026-01-05T01:00:00Z"))
            .endAt(Instant.parse("2026-01-05T05:00:00Z"))
            .build();
        when(leaveRequestRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(request));
        when(currentAccountProvider.account()).thenReturn(Account.builder().id(9L).employee(manager).build());
        when(leaveRequestRepository.existsApprovedOverlap(
            1L, request.getStartAt(), request.getEndAt(), 3L
        )).thenReturn(true);

        ConflictException exception = assertThrows(
            ConflictException.class,
            () -> service().approve(3L, "Approved")
        );

        assertTrue(exception.getMessage().contains("approved leave request"));
    }

    @Test
    void draftTakesItsMinutesFromTheScheduleOverTheWholePeriod() {
        stubOwnEmployee(11 * 480);
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Two weeks of leave: the schedule, not the client, says how much work time that is.
        var result = service().createDraft(annualLeave("2026-11-12T01:00:00Z", "2026-11-27T10:00:00Z"));

        assertEquals(11 * 480, result.requestedMinutes());
    }

    @Test
    void draftOutsideTheWorkScheduleIsRejected() {
        stubOwnEmployee(0);

        BusinessException thrown = assertThrows(BusinessException.class,
            () -> service().createDraft(annualLeave("2026-11-15T01:00:00Z", "2026-11-15T10:00:00Z")));

        assertEquals(ErrorCode.LEAVE_OUTSIDE_WORK_SCHEDULE, thrown.getErrorCode());
        verify(leaveRequestRepository, never()).save(any());
    }

    @Test
    void draftBeyondTheAnnualBalanceIsRejectedBeforeItIsSaved() {
        stubOwnEmployee(12 * 480);
        doThrow(new ConflictException(ErrorCode.INSUFFICIENT_LEAVE_BALANCE, "not enough"))
            .when(leaveEntitlementService).requireSufficientBalance(any());

        ConflictException thrown = assertThrows(ConflictException.class,
            () -> service().createDraft(annualLeave("2026-11-12T01:00:00Z", "2026-11-27T10:00:00Z")));

        assertEquals(ErrorCode.INSUFFICIENT_LEAVE_BALANCE, thrown.getErrorCode());
        verify(leaveRequestRepository, never()).save(any());
    }

    @Test
    void submitRechecksTheAnnualBalance() {
        Employee employee = Employee.builder().id(1L).build();
        LeaveRequest request = LeaveRequest.builder().id(3L).employee(employee)
            .leaveType(LeaveType.ANNUAL).status(LeaveRequestStatus.DRAFT).build();
        when(leaveRequestRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(request));
        when(currentAccountProvider.account())
            .thenReturn(Account.builder().id(9L).employee(employee).build());
        doThrow(new ConflictException(ErrorCode.INSUFFICIENT_LEAVE_BALANCE, "not enough"))
            .when(leaveEntitlementService).requireSufficientBalance(request);

        assertThrows(ConflictException.class, () -> service().submit(3L));

        assertEquals(LeaveRequestStatus.DRAFT, request.getStatus());
        verify(notificationService, never()).notifyLeaveSubmitted(any());
    }

    @Test
    void salaryTreatmentFollowsTheLeaveTypeWhenTheRequesterPicksAnother() {
        stubSelfServiceDraft();

        var result = service().createDraft(new LeaveRequestService.CreateLeaveCommand(
            1L, LeaveType.SICK, null,
            Instant.parse("2026-01-05T01:00:00Z"), Instant.parse("2026-01-05T05:00:00Z"),
            "Sick", null
        ));

        assertEquals(LeaveSalaryTreatment.SOCIAL_INSURANCE, result.salaryTreatment());
    }

    @Test
    void otherLeaveDefaultsToUnpaidSoItCannotBypassTheAnnualQuota() {
        stubSelfServiceDraft();

        var result = service().createDraft(new LeaveRequestService.CreateLeaveCommand(
            1L, LeaveType.OTHER, null,
            Instant.parse("2026-01-05T01:00:00Z"), Instant.parse("2026-01-05T05:00:00Z"),
            "Personal", null
        ));

        assertEquals(LeaveSalaryTreatment.UNPAID, result.salaryTreatment());
    }

    @Test
    void requesterCannotMarkUnpaidLeaveAsEmployerPaid() {
        Employee employee = Employee.builder().id(1L).build();
        Account actor = Account.builder().id(9L).employee(employee).build();
        when(employeeRepository.getRequired(1L)).thenReturn(employee);
        when(currentAccountProvider.account()).thenReturn(actor);
        when(currentAccountProvider.hasAuthority("request.manage")).thenReturn(false);

        assertThrows(BusinessException.class, () -> service().createDraft(
            new LeaveRequestService.CreateLeaveCommand(
                1L, LeaveType.UNPAID, LeaveSalaryTreatment.EMPLOYER_PAID,
                Instant.parse("2026-01-05T01:00:00Z"), Instant.parse("2026-01-05T05:00:00Z"),
                "Personal", null
            )
        ));
    }

    @Test
    void hrMayOverrideTheTreatmentForCompanyPaidSickLeave() {
        Employee employee = Employee.builder().id(1L).build();
        Account actor = Account.builder().id(9L).employee(employee).build();
        when(employeeRepository.getRequired(1L)).thenReturn(employee);
        when(currentAccountProvider.account()).thenReturn(actor);
        when(currentAccountProvider.hasAuthority("request.manage")).thenReturn(true);
        when(attendanceService.leaveMinutesFor(eq(1L), any(), any())).thenReturn(240);
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(invocation -> {
            LeaveRequest saved = invocation.getArgument(0);
            saved.setId(4L);
            return saved;
        });

        var result = service().createDraft(new LeaveRequestService.CreateLeaveCommand(
            1L, LeaveType.SICK, LeaveSalaryTreatment.EMPLOYER_PAID,
            Instant.parse("2026-01-05T01:00:00Z"), Instant.parse("2026-01-05T05:00:00Z"),
            "Company pays the first days", null
        ));

        assertEquals(LeaveSalaryTreatment.EMPLOYER_PAID, result.salaryTreatment());
    }

    private void stubSelfServiceDraft() {
        stubOwnEmployee(240);
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(invocation -> {
            LeaveRequest saved = invocation.getArgument(0);
            saved.setId(3L);
            return saved;
        });
    }

    private void stubOwnEmployee(int scheduledMinutes) {
        Employee employee = Employee.builder().id(1L).build();
        Account actor = Account.builder().id(9L).employee(employee).build();
        when(employeeRepository.getRequired(1L)).thenReturn(employee);
        when(currentAccountProvider.account()).thenReturn(actor);
        when(attendanceService.leaveMinutesFor(eq(1L), any(), any())).thenReturn(scheduledMinutes);
    }

    private static LeaveRequestService.CreateLeaveCommand annualLeave(String startAt, String endAt) {
        return new LeaveRequestService.CreateLeaveCommand(
            1L, LeaveType.ANNUAL, null, Instant.parse(startAt), Instant.parse(endAt), "Trip", null);
    }

    private LeaveRequestService service() {
        return new LeaveRequestService(
            leaveRequestRepository,
            employeeRepository,
            currentAccountProvider,
            employeeAccessScopeService,
            attendanceService,
            leaveEntitlementService,
            approverResolver,
            notificationService,
            Clock.system(ClockConfig.BUSINESS_ZONE)
        );
    }
}
