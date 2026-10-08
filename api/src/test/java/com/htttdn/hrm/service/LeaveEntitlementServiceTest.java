package com.htttdn.hrm.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.EmployeeLeaveEntitlement;
import com.htttdn.hrm.entity.LeaveEntitlementRule;
import com.htttdn.hrm.entity.LeaveRequest;
import com.htttdn.hrm.entity.enums.LeaveType;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.repository.AccountRepository;
import com.htttdn.hrm.repository.EmployeeAssignmentRepository;
import com.htttdn.hrm.repository.EmployeeLeaveEntitlementRepository;
import com.htttdn.hrm.repository.EmployeeRepository;
import com.htttdn.hrm.repository.LeaveEntitlementRuleRepository;
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

    @Mock private EmployeeLeaveEntitlementRepository entitlementRepository;
    @Mock private LeaveEntitlementRuleRepository ruleRepository;
    @Mock private LeaveRequestRepository leaveRequestRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private EmployeeAssignmentRepository employeeAssignmentRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private CurrentAccountProvider currentAccountProvider;
    @Mock private EmployeeAccessScopeService employeeAccessScopeService;

    private LeaveEntitlementService service() {
        return new LeaveEntitlementService(
            entitlementRepository, ruleRepository, leaveRequestRepository, employeeRepository,
            employeeAssignmentRepository, accountRepository, currentAccountProvider,
            employeeAccessScopeService
        );
    }

    private LeaveEntitlementRule rule() {
        return LeaveEntitlementRule.builder()
            .id(1L)
            .effectiveFrom(LocalDate.of(2026, 1, 1))
            .baseDays(12)
            .seniorityBlockYears(5)
            .seniorityBonusDays(1)
            .sourceReference("test")
            .build();
    }

    private Employee employee(LocalDate hireDate, LocalDate seniorityStart) {
        return Employee.builder()
            .id(1L)
            .hireDate(hireDate)
            .seniorityStartDate(seniorityStart)
            .build();
    }

    @Test
    void fullYearWithoutSeniorityGrantsTheBaseDays() {
        Employee employee = employee(LocalDate.of(2020, 3, 1), LocalDate.of(2026, 1, 1));

        assertEquals(12 * DAY, service().computeBaseMinutes(employee, 2026, rule(), DAY));
    }

    @Test
    void everyCompletedSeniorityBlockAddsABonusDay() {
        Employee tenYears = employee(LocalDate.of(2016, 1, 1), LocalDate.of(2016, 1, 1));

        assertEquals(14 * DAY, service().computeBaseMinutes(tenYears, 2026, rule(), DAY));
    }

    @Test
    void aBlockStillRunningDoesNotCountYet() {
        Employee fourYears = employee(LocalDate.of(2022, 1, 1), LocalDate.of(2022, 1, 1));

        assertEquals(12 * DAY, service().computeBaseMinutes(fourYears, 2026, rule(), DAY));
    }

    @Test
    void joiningPartWayThroughTheYearProratesByMonthsWorked() {
        Employee hiredInJuly = employee(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 1));

        assertEquals(12 * DAY * 6 / 12, service().computeBaseMinutes(hiredInJuly, 2026, rule(), DAY));
    }

    @Test
    void employeeHiredAfterTheYearGetsNothing() {
        Employee hiredNextYear = employee(LocalDate.of(2027, 2, 1), LocalDate.of(2027, 2, 1));

        assertEquals(0, service().computeBaseMinutes(hiredNextYear, 2026, rule(), DAY));
    }

    @Test
    void withoutARuleNothingIsGranted() {
        Employee employee = employee(LocalDate.of(2020, 1, 1), LocalDate.of(2020, 1, 1));

        assertEquals(0, service().computeBaseMinutes(employee, 2026, null, DAY));
    }

    @Test
    void shorterShiftsProduceASmallerMinuteBalanceForTheSameDays() {
        Employee employee = employee(LocalDate.of(2020, 1, 1), LocalDate.of(2026, 1, 1));

        assertEquals(12 * 240, service().computeBaseMinutes(employee, 2026, rule(), 240));
    }

    @Test
    void approvingAnnualLeaveBeyondTheRemainingBalanceIsRejected() {
        stubEntitlement(12 * DAY);
        stubCommitted(11 * DAY, 11 * DAY);

        LeaveRequest request = annualRequest(2 * DAY);

        ConflictException thrown = assertThrows(
            ConflictException.class, () -> service().requireSufficientBalance(request)
        );
        assertEquals(true, thrown.getMessage().contains("not enough"));
    }

    @Test
    void pendingRequestsAreCountedSoConcurrentApprovalsCannotOverdraw() {
        stubEntitlement(12 * DAY);
        // nothing approved yet, but 11 days already sit in PENDING
        stubCommitted(0, 11 * DAY);

        assertThrows(
            ConflictException.class, () -> service().requireSufficientBalance(annualRequest(2 * DAY))
        );
    }

    @Test
    void approvingWithinTheRemainingBalanceIsAllowed() {
        stubEntitlement(12 * DAY);
        stubCommitted(5 * DAY, 5 * DAY);

        assertDoesNotThrow(() -> service().requireSufficientBalance(annualRequest(2 * DAY)));
    }

    @Test
    void otherLeaveTypesNeverTouchTheAnnualBalance() {
        LeaveRequest sickLeave = LeaveRequest.builder()
            .id(9L)
            .employee(employee(LocalDate.of(2020, 1, 1), LocalDate.of(2020, 1, 1)))
            .leaveType(LeaveType.SICK)
            .requestedMinutes(100 * DAY)
            .startAt(Instant.parse("2026-03-02T01:00:00Z"))
            .build();

        assertDoesNotThrow(() -> service().requireSufficientBalance(sickLeave));
    }

    private LeaveRequest annualRequest(int minutes) {
        return LeaveRequest.builder()
            .id(9L)
            .employee(employee(LocalDate.of(2020, 1, 1), LocalDate.of(2020, 1, 1)))
            .leaveType(LeaveType.ANNUAL)
            .requestedMinutes(minutes)
            .startAt(Instant.parse("2026-03-02T01:00:00Z"))
            .build();
    }

    private void stubEntitlement(int baseMinutes) {
        EmployeeLeaveEntitlement entitlement = EmployeeLeaveEntitlement.builder()
            .id(5L)
            .employee(employee(LocalDate.of(2020, 1, 1), LocalDate.of(2020, 1, 1)))
            .year((short) 2026)
            .baseMinutes(baseMinutes)
            .carriedOverMinutes(0)
            .adjustmentMinutes(0)
            .standardDayMinutes(DAY)
            .build();
        when(entitlementRepository.findByEmployeeIdAndYear(anyLong(), eq((short) 2026)))
            .thenReturn(Optional.of(entitlement));
    }

    private void stubCommitted(int approvedMinutes, int approvedPlusPendingMinutes) {
        when(leaveRequestRepository.sumMinutesByTypeAndStatuses(
            anyLong(), eq(LeaveType.ANNUAL), eq(List.of(com.htttdn.hrm.entity.enums.LeaveRequestStatus.APPROVED)),
            any(), any(), any()
        )).thenReturn((long) approvedMinutes);
        when(leaveRequestRepository.sumMinutesByTypeAndStatuses(
            anyLong(), eq(LeaveType.ANNUAL),
            eq(List.of(
                com.htttdn.hrm.entity.enums.LeaveRequestStatus.APPROVED,
                com.htttdn.hrm.entity.enums.LeaveRequestStatus.PENDING
            )),
            any(), any(), any()
        )).thenReturn((long) approvedPlusPendingMinutes);
    }
}
