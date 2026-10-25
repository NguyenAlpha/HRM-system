package com.htttdn.hrm.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.LeaveRequest;
import com.htttdn.hrm.entity.enums.LeaveRequestStatus;
import com.htttdn.hrm.entity.enums.LeaveType;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.repository.AccountRepository;
import com.htttdn.hrm.repository.EmployeeAssignmentRepository;
import com.htttdn.hrm.repository.EmployeeRepository;
import com.htttdn.hrm.repository.LeaveRequestRepository;
import com.htttdn.hrm.security.CurrentAccountProvider;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LeaveEntitlementServiceTest {

    private static final int DAY = 480;

    @Mock private LeaveRequestRepository leaveRequestRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private EmployeeAssignmentRepository employeeAssignmentRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private CurrentAccountProvider currentAccountProvider;
    @Mock private EmployeeAccessScopeService employeeAccessScopeService;

    private LeaveEntitlementService service() {
        return new LeaveEntitlementService(
            leaveRequestRepository, employeeRepository, employeeAssignmentRepository, currentAccountProvider, employeeAccessScopeService
        );
    }

    private Employee employee(LocalDate hireDate) {
        return Employee.builder().id(1L).hireDate(hireDate).build();
    }

    @Test
    void fullYearGrantsTwelveDaysWhateverTheSeniority() {
        Employee tenYears = employee(LocalDate.of(2016, 1, 1));

        assertEquals(12 * DAY, LeaveEntitlementService.grantedMinutes(tenYears, 2026, DAY));
    }

    @Test
    void joiningPartWayThroughTheYearProratesByMonthsWorked() {
        Employee hiredInSeptember = employee(LocalDate.of(2026, 9, 29));

        // September to December: the month of joining counts as a whole month.
        assertEquals(4 * DAY, LeaveEntitlementService.grantedMinutes(hiredInSeptember, 2026, DAY));
    }

    @Test
    void employeeHiredAfterTheYearGetsNothing() {
        Employee hiredNextYear = employee(LocalDate.of(2027, 2, 1));

        assertEquals(0, LeaveEntitlementService.grantedMinutes(hiredNextYear, 2026, DAY));
    }

    @Test
    void shorterShiftsProduceASmallerMinuteBalanceForTheSameDays() {
        Employee employee = employee(LocalDate.of(2020, 1, 1));

        assertEquals(12 * 240, LeaveEntitlementService.grantedMinutes(employee, 2026, 240));
    }

    @Test
    void approvingAnnualLeaveBeyondTheRemainingBalanceIsRejected() {
        stubCommitted(11 * DAY, 11 * DAY);

        ConflictException thrown = assertThrows(
            ConflictException.class, () -> service().requireSufficientBalance(annualRequest(2 * DAY))
        );
        assertEquals(true, thrown.getMessage().contains("not enough"));
        assertEquals(ErrorCode.INSUFFICIENT_LEAVE_BALANCE, thrown.getErrorCode());
    }

    @Test
    void pendingRequestsAreCountedSoConcurrentApprovalsCannotOverdraw() {
        // nothing approved yet, but 11 days already sit in PENDING
        stubCommitted(0, 11 * DAY);

        assertThrows(
            ConflictException.class, () -> service().requireSufficientBalance(annualRequest(2 * DAY))
        );
    }

    @Test
    void approvingWithinTheRemainingBalanceIsAllowed() {
        stubCommitted(5 * DAY, 5 * DAY);

        assertDoesNotThrow(() -> service().requireSufficientBalance(annualRequest(2 * DAY)));
    }

    @Test
    void otherLeaveTypesNeverTouchTheAnnualBalance() {
        LeaveRequest sickLeave = LeaveRequest.builder()
            .id(9L)
            .employee(employee(LocalDate.of(2020, 1, 1)))
            .leaveType(LeaveType.SICK)
            .requestedMinutes(100 * DAY)
            .startAt(Instant.parse("2026-03-02T01:00:00Z"))
            .build();

        assertDoesNotThrow(() -> service().requireSufficientBalance(sickLeave));
    }

    private LeaveRequest annualRequest(int minutes) {
        return LeaveRequest.builder()
            .id(9L)
            .employee(employee(LocalDate.of(2020, 1, 1)))
            .leaveType(LeaveType.ANNUAL)
            .requestedMinutes(minutes)
            .startAt(Instant.parse("2026-03-02T01:00:00Z"))
            .build();
    }

    /** No primary assignment is stubbed, so a leave day is the 480-minute fallback. */
    private void stubCommitted(int approvedMinutes, int approvedPlusPendingMinutes) {
        when(leaveRequestRepository.sumMinutesByTypeAndStatuses(
            anyLong(), eq(LeaveType.ANNUAL), eq(List.of(LeaveRequestStatus.APPROVED)),
            any(), any(), any()
        )).thenReturn((long) approvedMinutes);
        when(leaveRequestRepository.sumMinutesByTypeAndStatuses(
            anyLong(), eq(LeaveType.ANNUAL), eq(List.of(LeaveRequestStatus.APPROVED, LeaveRequestStatus.PENDING)),
            any(), any(), any()
        )).thenReturn((long) approvedPlusPendingMinutes);
    }
}
