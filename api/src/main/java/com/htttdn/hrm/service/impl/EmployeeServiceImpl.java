package com.htttdn.hrm.service.impl;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.request.employee.AssignEmployeeRequest;
import com.htttdn.hrm.dto.request.employee.CreateEmployeeRequest;
import com.htttdn.hrm.dto.request.employee.SoftDeleteEmployeeRequest;
import com.htttdn.hrm.dto.request.employee.UpdateEmployeeRequest;
import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.dto.response.employee.EmployeeAccountSummaryResponse;
import com.htttdn.hrm.dto.response.employee.EmployeeAssignmentResponse;
import com.htttdn.hrm.dto.response.employee.EmployeeDetailResponse;
import com.htttdn.hrm.dto.response.employee.EmployeeSummaryResponse;
import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.EmployeeAssignment;
import com.htttdn.hrm.entity.JobPosition;
import com.htttdn.hrm.entity.OrganizationUnit;
import com.htttdn.hrm.entity.WorkLocation;
import com.htttdn.hrm.entity.WorkShift;
import com.htttdn.hrm.entity.enums.AccountStatus;
import com.htttdn.hrm.entity.enums.EmploymentStatus;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.exception.ResourceNotFoundException;
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
import com.htttdn.hrm.service.EmployeeAccessScopeService;
import com.htttdn.hrm.service.EmployeeService;
import com.htttdn.hrm.service.RefreshTokenService;

@Service
@Transactional
public class EmployeeServiceImpl implements EmployeeService {

    private static final String EMPLOYEE_READ = "employee.read";
    private static final String EMPLOYEE_MANAGE = "employee.manage";

    private final EmployeeRepository employeeRepository;
    private final EmployeeAssignmentRepository employeeAssignmentRepository;
    private final EmployeeCompensationRepository employeeCompensationRepository;
    private final EmployeeRequestRepository employeeRequestRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final PayslipRepository payslipRepository;
    private final OrganizationUnitRepository organizationUnitRepository;
    private final WorkLocationRepository workLocationRepository;
    private final JobPositionRepository jobPositionRepository;
    private final WorkShiftRepository workShiftRepository;
    private final AccountRepository accountRepository;
    private final RefreshTokenService refreshTokenService;
    private final CurrentAccountProvider currentAccountProvider;
    private final EmployeeAccessScopeService employeeAccessScopeService;

    public EmployeeServiceImpl(
        EmployeeRepository employeeRepository,
        EmployeeAssignmentRepository employeeAssignmentRepository,
        EmployeeCompensationRepository employeeCompensationRepository,
        EmployeeRequestRepository employeeRequestRepository,
        AttendanceRecordRepository attendanceRecordRepository,
        PayslipRepository payslipRepository,
        OrganizationUnitRepository organizationUnitRepository,
        WorkLocationRepository workLocationRepository,
        JobPositionRepository jobPositionRepository,
        WorkShiftRepository workShiftRepository,
        AccountRepository accountRepository,
        RefreshTokenService refreshTokenService,
        CurrentAccountProvider currentAccountProvider,
        EmployeeAccessScopeService employeeAccessScopeService
    ) {
        this.employeeRepository = employeeRepository;
        this.employeeAssignmentRepository = employeeAssignmentRepository;
        this.employeeCompensationRepository = employeeCompensationRepository;
        this.employeeRequestRepository = employeeRequestRepository;
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.payslipRepository = payslipRepository;
        this.organizationUnitRepository = organizationUnitRepository;
        this.workLocationRepository = workLocationRepository;
        this.jobPositionRepository = jobPositionRepository;
        this.workShiftRepository = workShiftRepository;
        this.accountRepository = accountRepository;
        this.refreshTokenService = refreshTokenService;
        this.currentAccountProvider = currentAccountProvider;
        this.employeeAccessScopeService = employeeAccessScopeService;
    }

    @Override
    @PreAuthorize("hasAuthority('employee.manage') and hasAuthority('employee.sensitive.manage')")
    public EmployeeDetailResponse create(CreateEmployeeRequest request) {
        employeeAccessScopeService.requireCompanyWide(EMPLOYEE_MANAGE);
        employeeAccessScopeService.requireCompanyWide("employee.sensitive.manage");
        validateCreateUniqueness(request);

        Instant now = Instant.now();
        Employee employee = Employee.builder()
            .employeeCode(request.employeeCode())
            .fullName(request.fullName())
            .dateOfBirth(request.dateOfBirth())
            .gender(request.gender())
            .highestEducationLevel(request.highestEducationLevel())
            .major(request.major())
            .institution(request.institution())
            .graduationYear(request.graduationYear())
            .nationalId(request.nationalId())
            .personalEmail(request.personalEmail())
            .workEmail(request.workEmail())
            .phone(request.phone())
            .address(request.address())
            .taxCode(request.taxCode())
            .bankName(request.bankName())
            .bankAccountNumber(request.bankAccountNumber())
            .bankAccountHolder(request.bankAccountHolder())
            .hireDate(request.hireDate())
            .employmentStatus(EmploymentStatus.PROBATION)
            .createdAt(now)
            .updatedAt(now)
            .build();

        return toDetailResponse(employeeRepository.save(employee));
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('employee.read')")
    public EmployeeDetailResponse getById(Long id) {
        Employee employee = findEmployeeOrThrow(id);
        employeeAccessScopeService.requireEmployeeAccess(id, EMPLOYEE_READ);
        return toDetailResponse(employee);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('employee.read')")
    public Page<EmployeeSummaryResponse> list(Pageable pageable) {
        Page<Employee> employees = employeeRepository.findAll(
            employeeAccessScopeService.accessibleEmployees(EMPLOYEE_READ),
            pageable
        );
        Map<Long, Account> accountsByEmployeeId = findAccountsByEmployeeId(employees.getContent());
        return employees.map(employee -> toSummaryResponse(employee, accountsByEmployeeId.get(employee.getId())));
    }

    @Override
    @PreAuthorize("hasAuthority('employee.manage')")
    public EmployeeDetailResponse update(Long id, UpdateEmployeeRequest request) {
        Employee employee = findEmployeeOrThrow(id);
        employeeAccessScopeService.requireEmployeeAccess(id, EMPLOYEE_MANAGE);
        if (request.workEmail() != null
            && employeeRepository.existsByWorkEmailAndIdNot(request.workEmail(), id)) {
            throw new ConflictException(ErrorCode.CONFLICT, "Work email is already taken", "workEmail");
        }

        employee.setFullName(request.fullName());
        employee.setDateOfBirth(request.dateOfBirth());
        employee.setGender(request.gender());
        employee.setHighestEducationLevel(request.highestEducationLevel());
        employee.setMajor(request.major());
        employee.setInstitution(request.institution());
        employee.setGraduationYear(request.graduationYear());
        employee.setWorkEmail(request.workEmail());
        employee.setPhone(request.phone());
        employee.setUpdatedAt(Instant.now());
        return toDetailResponse(employee);
    }

    @Override
    @PreAuthorize("hasAuthority('employee.manage')")
    public EmployeeAssignmentResponse assign(Long employeeId, AssignEmployeeRequest request) {
        Employee employee = findEmployeeOrThrow(employeeId);
        employeeAccessScopeService.requireEmployeeAccess(employeeId, EMPLOYEE_MANAGE);
        employeeAccessScopeService.requireDestinationAccess(
            request.organizationUnitId(), request.workLocationId(), EMPLOYEE_MANAGE
        );

        if (request.managerEmployeeId() != null && request.managerEmployeeId().equals(employeeId)) {
            throw new BusinessException(
                ErrorCode.VALIDATION_ERROR, "An employee cannot be their own manager", "managerEmployeeId"
            );
        }

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
                ErrorCode.RESOURCE_NOT_FOUND, "Active job position not found: " + request.positionId()
            ));
        WorkShift shift = findActiveShift(request.shiftId());
        Employee manager = findManager(request.managerEmployeeId());
        Account createdBy = findCurrentAccount();

        employeeAssignmentRepository.findFirstByEmployeeIdAndIsPrimaryTrueAndEffectiveToIsNull(employeeId)
            .ifPresent(current -> closeCurrentAssignment(current, request.effectiveFrom()));

        EmployeeAssignment assignment = EmployeeAssignment.builder()
            .employee(employee)
            .organizationUnit(organizationUnit)
            .workLocation(workLocation)
            .position(position)
            .shift(shift)
            .managerEmployee(manager)
            .employmentType(request.employmentType())
            .effectiveFrom(request.effectiveFrom())
            .isPrimary(true)
            .reason(request.reason())
            .createdByAccount(createdBy)
            .createdAt(Instant.now())
            .build();

        return toAssignmentResponse(employeeAssignmentRepository.save(assignment));
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('employee.read')")
    public EmployeeAssignmentResponse getCurrentAssignment(Long employeeId) {
        findEmployeeOrThrow(employeeId);
        employeeAccessScopeService.requireEmployeeAccess(employeeId, EMPLOYEE_READ);
        return findCurrentAssignment(employeeId)
            .map(this::toAssignmentResponse)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.RESOURCE_NOT_FOUND, "No active assignment for employee: " + employeeId
            ));
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('employee.read')")
    public List<EmployeeAssignmentResponse> listAssignments(Long employeeId) {
        findEmployeeOrThrow(employeeId);
        employeeAccessScopeService.requireEmployeeAccess(employeeId, EMPLOYEE_READ);
        return employeeAssignmentRepository.findByEmployeeIdOrderByEffectiveFromDesc(employeeId).stream()
            .map(this::toAssignmentResponse)
            .toList();
    }

    @Override
    @PreAuthorize("hasAuthority('employee.manage')")
    public void completeResignation(Long employeeId, LocalDate terminationDate, String terminationReason) {
        Employee employee = findEmployeeOrThrow(employeeId);
        employeeAccessScopeService.requireEmployeeAccess(employeeId, EMPLOYEE_MANAGE);
        if (terminationDate.isBefore(employee.getHireDate())) {
            throw new BusinessException(
                ErrorCode.VALIDATION_ERROR, "terminationDate must not be before hireDate", "terminationDate"
            );
        }
        if (isEmploymentEnded(employee.getEmploymentStatus())) {
            throw new ConflictException(ErrorCode.CONFLICT, "Employee employment has already ended");
        }

        employee.setEmploymentStatus(EmploymentStatus.RESIGNED);
        employee.setTerminationDate(terminationDate);
        employee.setTerminationReason(terminationReason);
        employee.setUpdatedAt(Instant.now());

        employeeAssignmentRepository.findFirstByEmployeeIdAndIsPrimaryTrueAndEffectiveToIsNull(employeeId)
            .ifPresent(assignment -> closeAssignmentAtTermination(assignment, terminationDate));
        accountRepository.findByEmployeeId(employeeId).ifPresent(account -> {
            account.setStatus(AccountStatus.DISABLED);
            account.setUpdatedAt(Instant.now());
            refreshTokenService.revokeAll(account.getId());
        });
    }

    @Override
    @PreAuthorize("hasAuthority('employee.manage')")
    public void softDelete(Long id, SoftDeleteEmployeeRequest request) {
        Employee employee = findEmployeeOrThrow(id);
        employeeAccessScopeService.requireEmployeeAccess(id, EMPLOYEE_MANAGE);
        ensureEmployeeHasNoBusinessHistory(id);

        employee.setDeletedAt(Instant.now());
        employee.setUpdatedAt(Instant.now());
        employee.setDeletedByAccount(findCurrentAccount());
        employee.setDeletionReason(request.deletionReason());
    }

    private void validateCreateUniqueness(CreateEmployeeRequest request) {
        if (employeeRepository.existsByEmployeeCode(request.employeeCode())) {
            throw new ConflictException(
                ErrorCode.EMPLOYEE_CODE_TAKEN, "Employee code is already taken", "employeeCode"
            );
        }
        if (request.workEmail() != null && employeeRepository.existsByWorkEmail(request.workEmail())) {
            throw new ConflictException(ErrorCode.CONFLICT, "Work email is already taken", "workEmail");
        }
        if (request.nationalId() != null && employeeRepository.existsByNationalId(request.nationalId())) {
            throw new ConflictException(ErrorCode.CONFLICT, "National ID is already taken", "nationalId");
        }
    }

    private WorkShift findActiveShift(Long shiftId) {
        if (shiftId == null) {
            return null;
        }
        return workShiftRepository.findById(shiftId)
            .filter(value -> value.getDeletedAt() == null && Boolean.TRUE.equals(value.getIsActive()))
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.RESOURCE_NOT_FOUND, "Active work shift not found: " + shiftId
            ));
    }

    private Employee findManager(Long managerEmployeeId) {
        if (managerEmployeeId == null) {
            return null;
        }
        Employee manager = findEmployeeOrThrow(managerEmployeeId);
        employeeAccessScopeService.requireEmployeeAccess(managerEmployeeId, EMPLOYEE_MANAGE);
        if (isEmploymentEnded(manager.getEmploymentStatus())) {
            throw new ConflictException(ErrorCode.CONFLICT, "Manager is no longer employed");
        }
        return manager;
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

    private void closeAssignmentAtTermination(EmployeeAssignment assignment, LocalDate terminationDate) {
        if (terminationDate.isBefore(assignment.getEffectiveFrom())) {
            throw new BusinessException(
                ErrorCode.VALIDATION_ERROR,
                "terminationDate must not be before the current assignment start date",
                "terminationDate"
            );
        }
        assignment.setEffectiveTo(terminationDate);
    }

    private void ensureEmployeeHasNoBusinessHistory(Long employeeId) {
        boolean hasHistory = accountRepository.findByEmployeeId(employeeId).isPresent()
            || employeeAssignmentRepository.existsByEmployeeId(employeeId)
            || employeeCompensationRepository.existsByEmployeeId(employeeId)
            || employeeRequestRepository.existsByEmployeeId(employeeId)
            || attendanceRecordRepository.existsByEmployeeId(employeeId)
            || payslipRepository.existsByEmployeeId(employeeId);
        if (hasHistory) {
            throw new ConflictException(
                ErrorCode.CONFLICT,
                "Employee has related business data and cannot be deleted; end employment instead"
            );
        }
    }

    private Account findCurrentAccount() {
        Long accountId = currentAccountProvider.accountId();
        return accountRepository.findById(accountId)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.RESOURCE_NOT_FOUND, "Account not found: " + accountId
            ));
    }

    private Employee findEmployeeOrThrow(Long id) {
        return employeeRepository.findById(id)
            .filter(employee -> employee.getDeletedAt() == null)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.EMPLOYEE_NOT_FOUND, "Employee not found: " + id
            ));
    }

    private Optional<EmployeeAssignment> findCurrentAssignment(Long employeeId) {
        return employeeAssignmentRepository.findCurrentPrimaryCandidates(employeeId, LocalDate.now()).stream()
            .findFirst();
    }

    private boolean isEmploymentEnded(EmploymentStatus status) {
        return status == EmploymentStatus.RESIGNED
            || status == EmploymentStatus.TERMINATED
            || status == EmploymentStatus.RETIRED;
    }

    private Map<Long, Account> findAccountsByEmployeeId(List<Employee> employees) {
        if (employees.isEmpty()) {
            return Map.of();
        }

        List<Long> employeeIds = employees.stream().map(Employee::getId).toList();
        Map<Long, Account> accountsByEmployeeId = new HashMap<>();
        accountRepository.findAllByEmployeeIds(employeeIds).forEach(account ->
            accountsByEmployeeId.put(account.getEmployee().getId(), account)
        );
        return accountsByEmployeeId;
    }

    private EmployeeSummaryResponse toSummaryResponse(Employee employee, Account account) {
        return new EmployeeSummaryResponse(
            employee.getId(),
            employee.getEmployeeCode(),
            employee.getFullName(),
            employee.getWorkEmail(),
            employee.getPhone(),
            employee.getHireDate(),
            employee.getEmploymentStatus(),
            employee.getTerminationDate(),
            account == null ? null : new EmployeeAccountSummaryResponse(
                account.getId(),
                account.getUsername(),
                account.getEmail(),
                account.getStatus()
            )
        );
    }

    private EmployeeDetailResponse toDetailResponse(Employee employee) {
        EmployeeAssignmentResponse currentAssignment = findCurrentAssignment(employee.getId())
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
            currentAssignment
        );
    }

    private EmployeeAssignmentResponse toAssignmentResponse(EmployeeAssignment assignment) {
        return new EmployeeAssignmentResponse(
            assignment.getId(),
            assignment.getEmployee().getId(),
            assignment.getOrganizationUnit().getId(),
            assignment.getWorkLocation().getId(),
            assignment.getPosition().getId(),
            assignment.getShift() != null ? assignment.getShift().getId() : null,
            assignment.getManagerEmployee() != null ? assignment.getManagerEmployee().getId() : null,
            assignment.getEmploymentType(),
            assignment.getEffectiveFrom(),
            assignment.getEffectiveTo(),
            assignment.getIsPrimary()
        );
    }
}
