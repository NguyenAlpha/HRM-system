package com.htttdn.hrm.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.htttdn.hrm.dto.request.employee.SoftDeleteEmployeeRequest;
import com.htttdn.hrm.dto.request.employee.CreateEmployeeProfileRequest;
import com.htttdn.hrm.dto.request.employee.CreateEmployeeRequest;
import com.htttdn.hrm.dto.request.employee.AssignEmployeeRequest;
import com.htttdn.hrm.dto.request.employee.UpdateEmployeeRequest;
import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.AccountRoleAssignment;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.EmployeeAssignment;
import com.htttdn.hrm.entity.Role;
import com.htttdn.hrm.entity.enums.AccountStatus;
import com.htttdn.hrm.entity.enums.EmploymentStatus;
import com.htttdn.hrm.entity.enums.EmploymentType;
import com.htttdn.hrm.entity.enums.RoleGrantPolicy;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.repository.AccountRepository;
import com.htttdn.hrm.repository.AccountRoleAssignmentRepository;
import com.htttdn.hrm.repository.AttendanceRecordRepository;
import com.htttdn.hrm.repository.EmployeeAssignmentRepository;
import com.htttdn.hrm.repository.EmployeeRepository;
import com.htttdn.hrm.repository.EmployeeSalaryHistoryRepository;
import com.htttdn.hrm.repository.LeaveRequestRepository;
import com.htttdn.hrm.repository.PayslipRepository;
import com.htttdn.hrm.security.CurrentAccountProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

    @Mock private EmployeeRepository employeeRepository;
    @Mock private EmployeeAssignmentRepository employeeAssignmentRepository;
    @Mock private EmployeeSalaryHistoryRepository employeeSalaryHistoryRepository;
    @Mock private LeaveRequestRepository leaveRequestRepository;
    @Mock private AttendanceRecordRepository attendanceRecordRepository;
    @Mock private PayslipRepository payslipRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private AccountRoleAssignmentRepository accountRoleAssignmentRepository;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private CurrentAccountProvider currentAccountProvider;
    @Mock private EmployeeAccessScopeService employeeAccessScopeService;
    @Mock private EmployeeAssignmentService employeeAssignmentService;
    @Mock private EmployeeCodeGenerator employeeCodeGenerator;

    @Test
    void createUsesServerGeneratedCodeFromInitialPosition() {
        var profile = new CreateEmployeeProfileRequest(
            "Nguyễn Văn An", LocalDate.of(2000, 1, 1), null, null, null, null, null, null, null,
            LocalDate.of(2026, 10, 5)
        );
        var assignment = new AssignEmployeeRequest(
            1L, 1L, 7L, null, null, EmploymentType.FULL_TIME,
            LocalDate.of(2026, 10, 5), null
        );
        when(employeeCodeGenerator.next()).thenReturn("NV0001");
        when(employeeRepository.save(any(Employee.class))).thenAnswer(invocation -> {
            Employee employee = invocation.getArgument(0);
            employee.setId(42L);
            return employee;
        });
        when(employeeAssignmentRepository.findCurrentPrimaryCandidates(any(), any()))
            .thenReturn(java.util.List.of());

        var result = service().create(new CreateEmployeeRequest(profile, assignment));

        assertEquals("NV0001", result.employee().employeeCode());
        verify(employeeAssignmentService).createInitial(any(Employee.class), any(AssignEmployeeRequest.class));
    }

    @Test
    void createRejectsMissingOrUnderageBirthDate() {
        LocalDate cutoff = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).minusYears(17);
        var assignment = new AssignEmployeeRequest(
            1L, 1L, 7L, null, null, EmploymentType.FULL_TIME,
            LocalDate.of(2026, 10, 5), null
        );

        for (LocalDate birthDate : new LocalDate[] { null, cutoff.plusDays(1) }) {
            var profile = new CreateEmployeeProfileRequest(
                "Nguyễn Văn An", birthDate, null, null, null, null, null, null, null,
                LocalDate.of(2026, 10, 5)
            );
            BusinessException exception = assertThrows(BusinessException.class,
                () -> service().create(new CreateEmployeeRequest(profile, assignment)));
            assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
            assertEquals("dateOfBirth", exception.getField());
        }
    }

    @Test
    void birthDateExactlySeventeenYearsAgoIsAllowed() {
        LocalDate cutoff = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).minusYears(17);
        EmployeeBirthDateValidator.validate(cutoff, true);
    }

    @Test
    void updateRejectsUnderageBirthDate() {
        Employee employee = employee(1L);
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        LocalDate cutoff = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).minusYears(17);
        UpdateEmployeeRequest request = new UpdateEmployeeRequest(
            "Employee One", cutoff.plusDays(1), null, null, null, null, null, null, null
        );

        BusinessException exception = assertThrows(BusinessException.class,
            () -> service().update(1L, request));
        assertEquals("dateOfBirth", exception.getField());
    }

    @Test
    void updateRejectsWorkEmailOwnedByAnotherEmployee() {
        Employee employee = employee(1L);
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(employeeRepository.existsByWorkEmailIgnoreCaseAndIdNot("duplicate@hrm.local", 1L)).thenReturn(true);
        UpdateEmployeeRequest request = new UpdateEmployeeRequest(
            "Employee One", null, null, null, null, null, null, "duplicate@hrm.local", null
        );

        assertThrows(ConflictException.class, () -> service().update(1L, request));
    }

    @Test
    void softDeleteRejectsEmployeeReassignedAtLeastOnce() {
        Employee employee = employee(1L);
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(accountRepository.existsByEmployeeId(1L)).thenReturn(false);
        when(employeeAssignmentRepository.countByEmployeeId(1L)).thenReturn(2L);

        assertThrows(
            ConflictException.class,
            () -> service().softDelete(1L, new SoftDeleteEmployeeRequest("Created by mistake"))
        );
    }

    @Test
    void softDeleteAcceptsEmployeeHoldingOnlyTheAssignmentMadeWithTheProfile() {
        Employee employee = employee(1L);
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(accountRepository.existsByEmployeeId(1L)).thenReturn(false);
        when(employeeAssignmentRepository.countByEmployeeId(1L)).thenReturn(1L);
        when(currentAccountProvider.accountId()).thenReturn(9L);
        when(accountRepository.findById(9L)).thenReturn(Optional.of(Account.builder().id(9L).build()));

        service().softDelete(1L, new SoftDeleteEmployeeRequest("Created by mistake"));

        assertNotNull(employee.getDeletedAt());
    }

    @Test
    void softDeleteUsesAuthenticatedAccountAsActor() {
        Employee employee = employee(1L);
        Account actor = Account.builder().id(9L).build();
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(accountRepository.existsByEmployeeId(1L)).thenReturn(false);
        when(currentAccountProvider.accountId()).thenReturn(9L);
        when(accountRepository.findById(9L)).thenReturn(Optional.of(actor));

        service().softDelete(1L, new SoftDeleteEmployeeRequest("Created by mistake"));

        assertEquals(actor, employee.getDeletedByAccount());
        assertEquals("Created by mistake", employee.getDeletionReason());
        assertNotNull(employee.getDeletedAt());
    }

    @Test
    void confirmEmploymentActivatesProbationaryEmployee() {
        Employee employee = employee(1L);
        employee.setEmploymentStatus(EmploymentStatus.PROBATION);
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));

        var result = service().confirmEmployment(1L);

        assertEquals(EmploymentStatus.ACTIVE, employee.getEmploymentStatus());
        assertEquals(EmploymentStatus.ACTIVE, result.employmentStatus());
        verify(employeeAccessScopeService).requireEmployeeAccess(1L, "employee.probation.confirm");
    }

    @Test
    void confirmEmploymentRejectsEmployeeOutsideProbation() {
        Employee employee = employee(1L);
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));

        ConflictException exception = assertThrows(
            ConflictException.class,
            () -> service().confirmEmployment(1L)
        );

        assertEquals(ErrorCode.EMPLOYMENT_STATUS_TRANSITION_NOT_ALLOWED, exception.getErrorCode());
    }

    @ParameterizedTest
    @EnumSource(value = EmploymentStatus.class, names = { "RESIGNED", "TERMINATED", "RETIRED" })
    void endEmploymentSetsTheChosenStatusAndClosesAccess(EmploymentStatus endStatus) {
        Employee employee = employee(1L);
        EmployeeAssignment assignment = EmployeeAssignment.builder().effectiveFrom(LocalDate.of(2025, 1, 1)).build();
        Account account = Account.builder().id(5L).status(AccountStatus.ACTIVE).build();
        LocalDate endDate = LocalDate.of(2026, 10, 31);
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(employeeAssignmentRepository.findFirstByEmployeeIdAndIsPrimaryTrueAndEffectiveToIsNull(1L))
            .thenReturn(Optional.of(assignment));
        when(accountRepository.findByEmployeeId(1L)).thenReturn(Optional.of(account));
        stubActor();

        service().endEmployment(1L, endStatus, endDate, "Reason");

        assertEquals(endStatus, employee.getEmploymentStatus());
        assertEquals(endDate, employee.getTerminationDate());
        assertEquals(endDate, assignment.getEffectiveTo());
        assertEquals(AccountStatus.DISABLED, account.getStatus());
        verify(refreshTokenService).revokeAll(5L);
    }

    @Test
    void endEmploymentRevokesTheBusinessRolesStillHeld() {
        Employee employee = employee(1L);
        Account account = Account.builder().id(5L).status(AccountStatus.ACTIVE).build();
        LocalDate endDate = LocalDate.of(2026, 10, 31);
        AccountRoleAssignment current = roleAssignment(RoleGrantPolicy.HR_ASSIGNABLE, LocalDate.of(2025, 1, 1), null);
        AccountRoleAssignment director = roleAssignment(RoleGrantPolicy.OWNER_APPROVAL, LocalDate.of(2025, 1, 1), LocalDate.of(2027, 1, 1));
        AccountRoleAssignment future = roleAssignment(RoleGrantPolicy.HR_ASSIGNABLE, LocalDate.of(2026, 12, 1), null);
        AccountRoleAssignment ended = roleAssignment(RoleGrantPolicy.HR_ASSIGNABLE, LocalDate.of(2025, 1, 1), LocalDate.of(2026, 3, 31));
        AccountRoleAssignment automatic = roleAssignment(RoleGrantPolicy.AUTO, LocalDate.of(2025, 1, 1), null);
        AccountRoleAssignment systemOnly = roleAssignment(RoleGrantPolicy.SYSTEM_ONLY, LocalDate.of(2025, 1, 1), null);
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(accountRepository.findByEmployeeId(1L)).thenReturn(Optional.of(account));
        when(accountRoleAssignmentRepository.findByAccountIdAndRevokedAtIsNull(5L))
            .thenReturn(List.of(current, director, future, ended, automatic, systemOnly));
        Account actor = stubActor();

        service().endEmployment(1L, EmploymentStatus.RESIGNED, endDate, " Nghỉ việc ");

        assertEquals(endDate, current.getEffectiveTo());
        assertEquals(actor, current.getRevokedByAccount());
        assertNotNull(current.getRevokedAt());
        assertEquals("Kết thúc làm việc: Nghỉ việc", current.getRevocationReason());
        assertEquals(endDate, director.getEffectiveTo());
        assertNotNull(director.getRevokedAt());
        // A role that had not started yet is revoked but keeps its own dates.
        assertNotNull(future.getRevokedAt());
        assertNull(future.getEffectiveTo());
        assertNull(ended.getRevokedAt());
        assertNull(automatic.getRevokedAt());
        assertNull(systemOnly.getRevokedAt());
    }

    @ParameterizedTest
    @EnumSource(value = EmploymentStatus.class, names = { "PROBATION", "ACTIVE" })
    void endEmploymentRejectsStatusThatDoesNotEndEmployment(EmploymentStatus endStatus) {
        BusinessException exception = assertThrows(BusinessException.class,
            () -> service().endEmployment(1L, endStatus, LocalDate.of(2026, 10, 31), "Reason"));

        assertEquals("endStatus", exception.getField());
    }

    private Account stubActor() {
        Account actor = Account.builder().id(9L).status(AccountStatus.ACTIVE).build();
        when(currentAccountProvider.accountId()).thenReturn(9L);
        when(accountRepository.findById(9L)).thenReturn(Optional.of(actor));
        return actor;
    }

    private AccountRoleAssignment roleAssignment(RoleGrantPolicy policy, LocalDate from, LocalDate to) {
        AccountRoleAssignment assignment = new AccountRoleAssignment();
        assignment.setRole(Role.builder().grantPolicy(policy).build());
        assignment.setEffectiveFrom(from);
        assignment.setEffectiveTo(to);
        return assignment;
    }

    private Employee employee(Long id) {
        return Employee.builder()
            .id(id)
            .employeeCode("EMP001")
            .fullName("Employee One")
            .hireDate(LocalDate.of(2025, 1, 1))
            .employmentStatus(EmploymentStatus.ACTIVE)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
    }

    private EmployeeService service() {
        return new EmployeeService(
            employeeRepository,
            employeeAssignmentRepository,
            employeeSalaryHistoryRepository,
            leaveRequestRepository,
            attendanceRecordRepository,
            payslipRepository,
            accountRepository,
            accountRoleAssignmentRepository,
            refreshTokenService,
            currentAccountProvider,
            employeeAccessScopeService,
            employeeAssignmentService,
            employeeCodeGenerator
        );
    }
}
