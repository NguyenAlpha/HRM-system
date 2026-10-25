package com.htttdn.hrm.service.employee;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.stereotype.Component;

import com.htttdn.hrm.dto.response.employee.EmployeeAccountSummaryResponse;
import com.htttdn.hrm.dto.response.employee.EmployeeAssignmentResponse;
import com.htttdn.hrm.dto.response.employee.EmployeeDetailResponse;
import com.htttdn.hrm.dto.response.employee.EmployeeSummaryResponse;
import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.EmployeeAssignment;
import com.htttdn.hrm.repository.AccountRepository;
import com.htttdn.hrm.repository.EmployeeAssignmentRepository;

import lombok.RequiredArgsConstructor;

/** Nhân viên và phân công ra response; dùng chung cho các service trong package employee. */
@Component
@RequiredArgsConstructor
public class EmployeeMapper {

    private final EmployeeAssignmentRepository employeeAssignmentRepository;
    private final AccountRepository accountRepository;
    private final Clock clock;

    public EmployeeDetailResponse toDetailResponse(Employee employee) {
        EmployeeAssignmentResponse currentAssignment = employeeAssignmentRepository
            .findCurrentPrimaryCandidates(employee.getId(), LocalDate.now(clock)).stream()
            .findFirst()
            .map(this::toAssignmentResponse)
            .orElse(null);
        return new EmployeeDetailResponse(
            employee.getId(),
            employee.getEmployeeCode(),
            employee.getFullName(),
            employee.getDateOfBirth(),
            employee.getGender(),
            employee.getHighestEducationLevel(),
            employee.getMajor(),
            employee.getInstitution(),
            employee.getGraduationYear(),
            employee.getWorkEmail(),
            employee.getPhone(),
            employee.getHireDate(),
            employee.getEmploymentStatus(),
            employee.getTerminationDate(),
            employee.getScheduledEndStatus(),
            currentAssignment,
            accountRepository.findByEmployeeId(employee.getId())
                .map(this::toAccountSummaryResponse)
                .orElse(null)
        );
    }

    public EmployeeSummaryResponse toSummaryResponse(
        Employee employee,
        EmployeeAssignment currentAssignment,
        Account account
    ) {
        return new EmployeeSummaryResponse(
            employee.getId(),
            employee.getEmployeeCode(),
            employee.getFullName(),
            employee.getWorkEmail(),
            employee.getPhone(),
            employee.getHireDate(),
            employee.getEmploymentStatus(),
            employee.getTerminationDate(),
            currentAssignment == null ? null : toAssignmentResponse(currentAssignment),
            toAccountSummaryResponse(account)
        );
    }

    public EmployeeAssignmentResponse toAssignmentResponse(EmployeeAssignment assignment) {
        var unit = assignment.getOrganizationUnit();
        var location = assignment.getWorkLocation();
        var position = assignment.getPosition();
        var shift = assignment.getShift();
        var manager = assignment.getManagerEmployee();
        return new EmployeeAssignmentResponse(
            assignment.getId(),
            assignment.getEmployee().getId(),
            unit.getId(),
            unit.getName(),
            location.getId(),
            location.getName(),
            position.getId(),
            position.getTitle(),
            shift == null ? null : shift.getId(),
            shift == null ? null : shift.getName(),
            manager == null ? null : manager.getId(),
            manager == null ? null : manager.getFullName(),
            assignment.getEmploymentType(),
            assignment.getEffectiveFrom(),
            assignment.getEffectiveTo(),
            assignment.getIsPrimary()
        );
    }

    private EmployeeAccountSummaryResponse toAccountSummaryResponse(Account account) {
        if (account == null) {
            return null;
        }
        return new EmployeeAccountSummaryResponse(
            account.getId(),
            account.getUsername(),
            account.getEmail(),
            account.getStatus()
        );
    }
}
