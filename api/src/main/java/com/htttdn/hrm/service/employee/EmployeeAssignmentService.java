package com.htttdn.hrm.service.employee;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import com.htttdn.hrm.service.payroll.PayrollPeriodInvalidator;
import com.htttdn.hrm.service.rbac.EmployeeAccessScopeService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.request.employee.AssignEmployeeRequest;
import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.dto.response.employee.EmployeeAssignmentResponse;
import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.EmployeeAssignment;
import com.htttdn.hrm.entity.JobPosition;
import com.htttdn.hrm.entity.OrganizationUnit;
import com.htttdn.hrm.entity.WorkLocation;
import com.htttdn.hrm.entity.WorkShift;
import com.htttdn.hrm.entity.enums.EmploymentStatus;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.exception.ResourceNotFoundException;
import com.htttdn.hrm.repository.EmployeeAssignmentRepository;
import com.htttdn.hrm.repository.EmployeeRepository;
import com.htttdn.hrm.repository.JobPositionRepository;
import com.htttdn.hrm.repository.OrganizationUnitRepository;
import com.htttdn.hrm.repository.WorkLocationRepository;
import com.htttdn.hrm.repository.WorkShiftRepository;
import com.htttdn.hrm.security.CurrentAccountProvider;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class EmployeeAssignmentService {

    private static final String EMPLOYEE_CREATE = "employee.create";
    private static final String EMPLOYEE_ASSIGNMENT_READ = "employee.assignment.read";
    private static final String EMPLOYEE_ASSIGNMENT_MANAGE = "employee.assignment.manage";

    private final EmployeeRepository employeeRepository;
    private final EmployeeAssignmentRepository employeeAssignmentRepository;
    private final OrganizationUnitRepository organizationUnitRepository;
    private final WorkLocationRepository workLocationRepository;
    private final JobPositionRepository jobPositionRepository;
    private final WorkShiftRepository workShiftRepository;
    private final CurrentAccountProvider currentAccountProvider;
    private final EmployeeAccessScopeService employeeAccessScopeService;
    private final PayrollPeriodInvalidator payrollPeriodInvalidator;
    private final EmployeeMapper employeeMapper;
    private final Clock clock;

    @PreAuthorize("hasAuthority('employee.create')")
    public EmployeeAssignmentResponse createInitial(
        Employee employee,
        AssignEmployeeRequest request
    ) {
        validateEffectiveFrom(employee, request.effectiveFrom(), "initialAssignment.effectiveFrom");
        validateManagerIsNotSelf(employee.getId(), request.managerEmployeeId());
        AssignmentResources resources = resolveResources(request, EMPLOYEE_CREATE);
        return employeeMapper.toAssignmentResponse(savePrimaryAssignment(employee, request, resources, currentAccountProvider.account()));
    }

    @PreAuthorize("hasAuthority('employee.assignment.manage')")
    public EmployeeAssignmentResponse assign(Long employeeId, AssignEmployeeRequest request) {
        findEmployee(employeeId);
        employeeAccessScopeService.requireEmployeeAccess(employeeId, EMPLOYEE_ASSIGNMENT_MANAGE);
        Employee employee = employeeRepository.getRequiredForUpdate(employeeId);

        if (isEmploymentEnded(employee.getEmploymentStatus())) {
            throw new ConflictException(
                ErrorCode.CONFLICT,
                "Cannot assign an employee whose employment has ended"
            );
        }
        validateEffectiveFrom(employee, request.effectiveFrom(), "effectiveFrom");
        // A scheduled end leaves the employee working until the last working day, not after it.
        EmploymentEndGuard.requireNotAfterEnd(employee, request.effectiveFrom(), "effectiveFrom");
        validateManagerIsNotSelf(employeeId, request.managerEmployeeId());
        AssignmentResources resources = resolveResources(request, EMPLOYEE_ASSIGNMENT_MANAGE);
        payrollPeriodInvalidator.invalidateFrom(request.effectiveFrom());

        List<EmployeeAssignment> openAssignments = employeeAssignmentRepository
            .findOpenPrimaryForUpdate(employeeId);
        if (openAssignments.size() > 1) {
            throw new ConflictException(
                ErrorCode.CONFLICT,
                "Employee has multiple open primary assignments"
            );
        }
        openAssignments.stream().findFirst()
            .ifPresent(current -> closeCurrentAssignment(current, request.effectiveFrom()));
        employeeAssignmentRepository.flush();

        return employeeMapper.toAssignmentResponse(savePrimaryAssignment(employee, request, resources, currentAccountProvider.account()));
    }

    @PreAuthorize("hasAuthority('employee.assignment.read')")
    @Transactional(readOnly = true)
    public EmployeeAssignmentResponse getCurrent(Long employeeId) {
        findEmployee(employeeId);
        employeeAccessScopeService.requireEmployeeAccess(employeeId, EMPLOYEE_ASSIGNMENT_READ);
        return employeeAssignmentRepository
            .findCurrentPrimary(employeeId, LocalDate.now(clock))
            .map(employeeMapper::toAssignmentResponse)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.EMPLOYEE_ASSIGNMENT_NOT_FOUND,
                "No active assignment for employee: " + employeeId
            ));
    }

    @PreAuthorize("hasAuthority('employee.assignment.read')")
    @Transactional(readOnly = true)
    public List<EmployeeAssignmentResponse> list(Long employeeId) {
        findEmployee(employeeId);
        employeeAccessScopeService.requireEmployeeAccess(employeeId, EMPLOYEE_ASSIGNMENT_READ);
        return employeeAssignmentRepository.findByEmployeeIdOrderByEffectiveFromDesc(employeeId).stream()
            .map(employeeMapper::toAssignmentResponse)
            .toList();
    }

    private AssignmentResources resolveResources(AssignEmployeeRequest request, String permissionCode) {
        employeeAccessScopeService.requireDestinationAccess(
            request.organizationUnitId(), request.workLocationId(), permissionCode
        );

        OrganizationUnit organizationUnit = organizationUnitRepository.getRequiredActive(request.organizationUnitId());
        WorkLocation workLocation = workLocationRepository.getRequiredActive(request.workLocationId());
        JobPosition position = jobPositionRepository.getRequiredActive(request.positionId());
        return new AssignmentResources(
            organizationUnit,
            workLocation,
            position,
            findActiveShift(request.shiftId()),
            findManager(request.managerEmployeeId(), permissionCode)
        );
    }

    private EmployeeAssignment savePrimaryAssignment(
        Employee employee,
        AssignEmployeeRequest request,
        AssignmentResources resources,
        Account createdBy
    ) {
        return employeeAssignmentRepository.save(EmployeeAssignment.builder()
            .employee(employee)
            .organizationUnit(resources.organizationUnit())
            .workLocation(resources.workLocation())
            .position(resources.position())
            .shift(resources.shift())
            .managerEmployee(resources.manager())
            .employmentType(request.employmentType())
            .effectiveFrom(request.effectiveFrom())
            .isPrimary(true)
            .reason(request.reason())
            .createdByAccount(createdBy)
            .build());
    }

    private WorkShift findActiveShift(Long shiftId) {
        if (shiftId == null) {
            return null;
        }
        return workShiftRepository.getRequiredActive(shiftId);
    }

    private Employee findManager(Long managerEmployeeId, String permissionCode) {
        if (managerEmployeeId == null) {
            return null;
        }
        Employee manager = findEmployee(managerEmployeeId);
        employeeAccessScopeService.requireEmployeeAccess(managerEmployeeId, permissionCode);
        if (isEmploymentEnded(manager.getEmploymentStatus())) {
            throw new ConflictException(ErrorCode.CONFLICT, "Manager is no longer employed");
        }
        return manager;
    }

    private void validateEffectiveFrom(Employee employee, LocalDate effectiveFrom, String field) {
        if (effectiveFrom.isBefore(employee.getHireDate())) {
            throw new BusinessException(
                ErrorCode.VALIDATION_ERROR,
                "effectiveFrom must not be before employee hireDate",
                field
            );
        }
    }

    private void validateManagerIsNotSelf(Long employeeId, Long managerEmployeeId) {
        if (managerEmployeeId != null && managerEmployeeId.equals(employeeId)) {
            throw new BusinessException(
                ErrorCode.VALIDATION_ERROR,
                "An employee cannot be their own manager",
                "managerEmployeeId"
            );
        }
    }

    private void closeCurrentAssignment(EmployeeAssignment current, LocalDate nextEffectiveFrom) {
        if (!nextEffectiveFrom.isAfter(current.getEffectiveFrom())) {
            throw new BusinessException(
                ErrorCode.VALIDATION_ERROR,
                "effectiveFrom must be after the current assignment start date",
                "effectiveFrom"
            );
        }
        current.setEffectiveTo(nextEffectiveFrom.minusDays(1));
    }

    private Employee findEmployee(Long employeeId) {
        return employeeRepository.getRequired(employeeId);
    }

    private boolean isEmploymentEnded(EmploymentStatus status) {
        return status == EmploymentStatus.RESIGNED
            || status == EmploymentStatus.TERMINATED
            || status == EmploymentStatus.RETIRED;
    }

    private record AssignmentResources(
        OrganizationUnit organizationUnit,
        WorkLocation workLocation,
        JobPosition position,
        WorkShift shift,
        Employee manager
    ) {
    }
}
