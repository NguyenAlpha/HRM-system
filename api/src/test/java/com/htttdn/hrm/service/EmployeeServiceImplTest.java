package com.htttdn.hrm.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.htttdn.hrm.dto.request.employee.SoftDeleteEmployeeRequest;
import com.htttdn.hrm.dto.request.employee.UpdateEmployeeRequest;
import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.enums.EmploymentStatus;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.repository.AccountRepository;
import com.htttdn.hrm.repository.AttendanceRecordRepository;
import com.htttdn.hrm.repository.EmployeeAssignmentRepository;
import com.htttdn.hrm.repository.EmployeeRepository;
import com.htttdn.hrm.repository.EmployeeSalaryHistoryRepository;
import com.htttdn.hrm.repository.LeaveRequestRepository;
import com.htttdn.hrm.repository.PayslipRepository;
import com.htttdn.hrm.security.CurrentAccountProvider;
import com.htttdn.hrm.service.impl.EmployeeServiceImpl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceImplTest {

    @Mock private EmployeeRepository employeeRepository;
    @Mock private EmployeeAssignmentRepository employeeAssignmentRepository;
    @Mock private EmployeeSalaryHistoryRepository employeeSalaryHistoryRepository;
    @Mock private LeaveRequestRepository leaveRequestRepository;
    @Mock private AttendanceRecordRepository attendanceRecordRepository;
    @Mock private PayslipRepository payslipRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private CurrentAccountProvider currentAccountProvider;
    @Mock private EmployeeAccessScopeService employeeAccessScopeService;
    @Mock private EmployeeAssignmentService employeeAssignmentService;

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
    void softDeleteRejectsEmployeeWithBusinessHistory() {
        Employee employee = employee(1L);
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(accountRepository.findByEmployeeId(1L)).thenReturn(Optional.empty());
        when(employeeAssignmentRepository.existsByEmployeeId(1L)).thenReturn(true);

        assertThrows(
            ConflictException.class,
            () -> service().softDelete(1L, new SoftDeleteEmployeeRequest("Created by mistake"))
        );
    }

    @Test
    void softDeleteUsesAuthenticatedAccountAsActor() {
        Employee employee = employee(1L);
        Account actor = Account.builder().id(9L).build();
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(accountRepository.findByEmployeeId(1L)).thenReturn(Optional.empty());
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
        verify(employeeAccessScopeService).requireEmployeeAccess(1L, "employee.manage");
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

    private EmployeeServiceImpl service() {
        return new EmployeeServiceImpl(
            employeeRepository,
            employeeAssignmentRepository,
            employeeSalaryHistoryRepository,
            leaveRequestRepository,
            attendanceRecordRepository,
            payslipRepository,
            accountRepository,
            refreshTokenService,
            currentAccountProvider,
            employeeAccessScopeService,
            employeeAssignmentService
        );
    }
}
