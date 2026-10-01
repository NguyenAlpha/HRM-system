package com.htttdn.hrm.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

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
import com.htttdn.hrm.repository.AccountRepository;
import com.htttdn.hrm.repository.EmployeeAssignmentRepository;
import com.htttdn.hrm.repository.EmployeeRepository;
import com.htttdn.hrm.repository.JobPositionRepository;
import com.htttdn.hrm.repository.OrganizationUnitRepository;
import com.htttdn.hrm.repository.WorkLocationRepository;
import com.htttdn.hrm.repository.WorkShiftRepository;
import com.htttdn.hrm.security.CurrentAccountProvider;

@Service
@Transactional
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
    private final AccountRepository accountRepository;
    private final CurrentAccountProvider currentAccountProvider;
    private final EmployeeAccessScopeService employeeAccessScopeService;

    public EmployeeAssignmentService(
        EmployeeRepository employeeRepository,
        EmployeeAssignmentRepository employeeAssignmentRepository,
        OrganizationUnitRepository organizationUnitRepository,
        WorkLocationRepository workLocationRepository,
        JobPositionRepository jobPositionRepository,
        WorkShiftRepository workShiftRepository,
        AccountRepository accountRepository,
        CurrentAccountProvider currentAccountProvider,
        EmployeeAccessScopeService employeeAccessScopeService
    ) {
        this.employeeRepository = employeeRepository;
        this.employeeAssignmentRepository = employeeAssignmentRepository;
        this.organizationUnitRepository = organizationUnitRepository;
        this.workLocationRepository = workLocationRepository;
        this.jobPositionRepository = jobPositionRepository;
        this.workShiftRepository = workShiftRepository;
        this.accountRepository = accountRepository;
        this.currentAccountProvider = currentAccountProvider;
        this.employeeAccessScopeService = employeeAccessScopeService;
    }

    @PreAuthorize("hasAuthority('employee.create')")
    public EmployeeAssignmentResponse createInitial(
        Employee employee,
        AssignEmployeeRequest request
    ) {
        validateEffectiveFrom(employee, request.effectiveFrom(), "initialAssignment.effectiveFrom");
        validateManagerIsNotSelf(employee.getId(), request.managerEmployeeId());
        AssignmentResources resources = resolveResources(request, EMPLOYEE_CREATE);
        return toResponse(savePrimaryAssignment(employee, request, resources, findCurrentAccount()));
    }

    @PreAuthorize("hasAuthority('employee.assignment.manage')")
    public EmployeeAssignmentResponse assign(Long employeeId, AssignEmployeeRequest request) {
        findEmployee(employeeId);
        employeeAccessScopeService.requireEmployeeAccess(employeeId, EMPLOYEE_ASSIGNMENT_MANAGE);
        Employee employee = employeeRepository.findByIdForUpdate(employeeId)
            .orElseThrow(() -> employeeNotFound(employeeId));

        if (isEmploymentEnded(employee.getEmploymentStatus())) {
            throw new ConflictException(
                ErrorCode.CONFLICT,
                "Cannot assign an employee whose employment has ended"
            );
        }
        validateEffectiveFrom(employee, request.effectiveFrom(), "effectiveFrom");
        validateManagerIsNotSelf(employeeId, request.managerEmployeeId());
        AssignmentResources resources = resolveResources(request, EMPLOYEE_ASSIGNMENT_MANAGE);

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

        return toResponse(savePrimaryAssignment(employee, request, resources, findCurrentAccount()));
    }

    @PreAuthorize("hasAuthority('employee.assignment.read')")
    @Transactional(readOnly = true)
    public EmployeeAssignmentResponse getCurrent(Long employeeId) {
        findEmployee(employeeId);
        employeeAccessScopeService.requireEmployeeAccess(employeeId, EMPLOYEE_ASSIGNMENT_READ);
        return employeeAssignmentRepository
            .findCurrentPrimaryCandidates(employeeId, LocalDate.now()).stream()
            .findFirst()
            .map(this::toResponse)
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
            .map(this::toResponse)
            .toList();
    }

    private AssignmentResources resolveResources(AssignEmployeeRequest request, String permissionCode) {
        employeeAccessScopeService.requireDestinationAccess(
            request.organizationUnitId(), request.workLocationId(), permissionCode
        );

        OrganizationUnit organizationUnit = organizationUnitRepository.findById(request.organizationUnitId())
            .filter(unit -> unit.getDeletedAt() == null && Boolean.TRUE.equals(unit.getIsActive()))
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.ORGANIZATION_UNIT_NOT_FOUND,
                "Active organization unit not found: " + request.organizationUnitId()
            ));
        WorkLocation workLocation = workLocationRepository.findById(request.workLocationId())
            .filter(location -> location.getDeletedAt() == null && Boolean.TRUE.equals(location.getIsActive()))
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.LOCATION_NOT_FOUND,
                "Active work location not found: " + request.workLocationId()
            ));
        JobPosition position = jobPositionRepository.findById(request.positionId())
            .filter(value -> value.getDeletedAt() == null && Boolean.TRUE.equals(value.getIsActive()))
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.JOB_POSITION_NOT_FOUND,
                "Active job position not found: " + request.positionId()
            ));
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
            .createdAt(Instant.now())
            .build());
    }

    private WorkShift findActiveShift(Long shiftId) {
        if (shiftId == null) {
            return null;
        }
        return workShiftRepository.findById(shiftId)
            .filter(shift -> shift.getDeletedAt() == null && Boolean.TRUE.equals(shift.getIsActive()))
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.WORK_SHIFT_NOT_FOUND,
                "Active work shift not found: " + shiftId
            ));
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
        return employeeRepository.findByIdAndDeletedAtIsNull(employeeId)
            .orElseThrow(() -> employeeNotFound(employeeId));
    }

    private ResourceNotFoundException employeeNotFound(Long employeeId) {
        return new ResourceNotFoundException(
            ErrorCode.EMPLOYEE_NOT_FOUND,
            "Employee not found: " + employeeId
        );
    }

    private Account findCurrentAccount() {
        Long accountId = currentAccountProvider.accountId();
        return accountRepository.findById(accountId)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.RESOURCE_NOT_FOUND,
                "Account not found: " + accountId
            ));
    }

    private boolean isEmploymentEnded(EmploymentStatus status) {
        return status == EmploymentStatus.RESIGNED
            || status == EmploymentStatus.TERMINATED
            || status == EmploymentStatus.RETIRED;
    }

    private EmployeeAssignmentResponse toResponse(EmployeeAssignment assignment) {
        OrganizationUnit unit = assignment.getOrganizationUnit();
        WorkLocation location = assignment.getWorkLocation();
        JobPosition position = assignment.getPosition();
        WorkShift shift = assignment.getShift();
        Employee manager = assignment.getManagerEmployee();
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

    private record AssignmentResources(
        OrganizationUnit organizationUnit,
        WorkLocation workLocation,
        JobPosition position,
        WorkShift shift,
        Employee manager
    ) {
    }
}
