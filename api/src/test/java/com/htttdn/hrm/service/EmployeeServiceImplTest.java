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
import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.enums.EmploymentStatus;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.repository.AccountRepository;
import com.htttdn.hrm.repository.AttendanceRecordRepository;
import com.htttdn.hrm.repository.EmployeeAssignmentRepository;
import com.htttdn.hrm.repository.EmployeeCompensationRepository;
import com.htttdn.hrm.repository.EmployeeRepository;
import com.htttdn.hrm.repository.EmployeeRequestRepository;
import com.htttdn.hrm.repository.JobPositionRepository;
import com.htttdn.hrm.repository.OrganizationUnitRepository;
import com.htttdn.hrm.repository.PayslipRepository;
import com.htttdn.hrm.repository.WorkLocationRepository;
import com.htttdn.hrm.repository.WorkShiftRepository;
import com.htttdn.hrm.security.CurrentAccountProvider;
import com.htttdn.hrm.service.impl.EmployeeServiceImpl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceImplTest {

    @Mock private EmployeeRepository employeeRepository;
    @Mock private EmployeeAssignmentRepository employeeAssignmentRepository;
    @Mock private EmployeeCompensationRepository employeeCompensationRepository;
    @Mock private EmployeeRequestRepository employeeRequestRepository;
    @Mock private AttendanceRecordRepository attendanceRecordRepository;
    @Mock private PayslipRepository payslipRepository;
    @Mock private OrganizationUnitRepository organizationUnitRepository;
    @Mock private WorkLocationRepository workLocationRepository;
    @Mock private JobPositionRepository jobPositionRepository;
    @Mock private WorkShiftRepository workShiftRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private CurrentAccountProvider currentAccountProvider;
    @Mock private EmployeeAccessScopeService employeeAccessScopeService;

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
            employeeCompensationRepository,
            employeeRequestRepository,
            attendanceRecordRepository,
            payslipRepository,
            organizationUnitRepository,
            workLocationRepository,
            jobPositionRepository,
            workShiftRepository,
            accountRepository,
            refreshTokenService,
            currentAccountProvider,
            employeeAccessScopeService
        );
    }
}
