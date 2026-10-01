package com.htttdn.hrm.service.impl;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.request.employee.AssignEmployeeRequest;
import com.htttdn.hrm.dto.request.employee.CreateEmployeeProfileRequest;
import com.htttdn.hrm.dto.request.employee.CreateEmployeeRequest;
import com.htttdn.hrm.dto.request.employee.SoftDeleteEmployeeRequest;
import com.htttdn.hrm.dto.request.employee.UpdateEmployeeRequest;
import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.dto.response.employee.EmployeeAccountSummaryResponse;
import com.htttdn.hrm.dto.response.employee.EmployeeAssignmentResponse;
import com.htttdn.hrm.dto.response.employee.EmployeeCreationResponse;
import com.htttdn.hrm.dto.response.employee.EmployeeDetailResponse;
import com.htttdn.hrm.dto.response.employee.EmployeeSummaryResponse;
import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.EmployeeAssignment;
import com.htttdn.hrm.entity.enums.AccountStatus;
import com.htttdn.hrm.entity.enums.EmploymentStatus;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.exception.ResourceNotFoundException;
import com.htttdn.hrm.repository.AccountRepository;
import com.htttdn.hrm.repository.AttendanceRecordRepository;
import com.htttdn.hrm.repository.EmployeeAssignmentRepository;
import com.htttdn.hrm.repository.EmployeeRepository;
import com.htttdn.hrm.repository.EmployeeSalaryHistoryRepository;
import com.htttdn.hrm.repository.LeaveRequestRepository;
import com.htttdn.hrm.repository.PayslipRepository;
import com.htttdn.hrm.security.CurrentAccountProvider;
import com.htttdn.hrm.service.EmployeeAccessScopeService;
import com.htttdn.hrm.service.EmployeeAssignmentService;
import com.htttdn.hrm.service.EmployeeService;
import com.htttdn.hrm.service.RefreshTokenService;

@Service
@Transactional
public class EmployeeServiceImpl implements EmployeeService {

    private static final String EMPLOYEE_READ = "employee.read";
    private static final String EMPLOYEE_LIST_READ = "employee.list.read";
    private static final String EMPLOYEE_UPDATE = "employee.update";
    private static final String EMPLOYEE_PROBATION_CONFIRM = "employee.probation.confirm";
    private static final String EMPLOYEE_LIFECYCLE_MANAGE = "employee.lifecycle.manage";
    private static final String EMPLOYEE_DELETE = "employee.delete";

    private final EmployeeRepository employeeRepository;
    private final EmployeeAssignmentRepository employeeAssignmentRepository;
    private final EmployeeSalaryHistoryRepository employeeSalaryHistoryRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final PayslipRepository payslipRepository;
    private final AccountRepository accountRepository;
    private final RefreshTokenService refreshTokenService;
    private final CurrentAccountProvider currentAccountProvider;
    private final EmployeeAccessScopeService employeeAccessScopeService;
    private final EmployeeAssignmentService employeeAssignmentService;

    public EmployeeServiceImpl(
        EmployeeRepository employeeRepository,
        EmployeeAssignmentRepository employeeAssignmentRepository,
        EmployeeSalaryHistoryRepository employeeSalaryHistoryRepository,
        LeaveRequestRepository leaveRequestRepository,
        AttendanceRecordRepository attendanceRecordRepository,
        PayslipRepository payslipRepository,
        AccountRepository accountRepository,
        RefreshTokenService refreshTokenService,
        CurrentAccountProvider currentAccountProvider,
        EmployeeAccessScopeService employeeAccessScopeService,
        EmployeeAssignmentService employeeAssignmentService
    ) {
        this.employeeRepository = employeeRepository;
        this.employeeAssignmentRepository = employeeAssignmentRepository;
        this.employeeSalaryHistoryRepository = employeeSalaryHistoryRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.payslipRepository = payslipRepository;
        this.accountRepository = accountRepository;
        this.refreshTokenService = refreshTokenService;
        this.currentAccountProvider = currentAccountProvider;
        this.employeeAccessScopeService = employeeAccessScopeService;
        this.employeeAssignmentService = employeeAssignmentService;
    }

    @Override
    @PreAuthorize("hasAuthority('employee.create')")
    public EmployeeCreationResponse create(CreateEmployeeRequest request) {
        CreateEmployeeProfileRequest profile = request.employee();
        AssignEmployeeRequest assignmentRequest = request.initialAssignment();

        String workEmail = normalizeEmail(profile.workEmail());
        validateCreateUniqueness(profile, workEmail);

        Instant now = Instant.now();
        Employee employee = Employee.builder()
            .employeeCode(profile.employeeCode().trim())
            .fullName(profile.fullName().trim())
            .dateOfBirth(profile.dateOfBirth())
            .gender(profile.gender())
            .highestEducationLevel(profile.highestEducationLevel())
            .major(profile.major())
            .institution(profile.institution())
            .graduationYear(profile.graduationYear())
            .workEmail(workEmail)
            .phone(profile.phone())
            .hireDate(profile.hireDate())
            .seniorityStartDate(profile.hireDate())
            .employmentStatus(EmploymentStatus.PROBATION)
            .createdAt(now)
            .updatedAt(now)
            .build();

        Employee savedEmployee = employeeRepository.save(employee);
        EmployeeAssignmentResponse initialAssignment = employeeAssignmentService.createInitial(
            savedEmployee,
            assignmentRequest
        );
        return new EmployeeCreationResponse(
            toDetailResponse(savedEmployee),
            initialAssignment
        );
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
    @PreAuthorize("hasAuthority('employee.list.read')")
    public Page<EmployeeSummaryResponse> list(Pageable pageable) {
        Page<Employee> employees = employeeRepository.findAll(
            employeeAccessScopeService.accessibleEmployees(EMPLOYEE_LIST_READ),
            pageable
        );
        Map<Long, Account> accountsByEmployeeId = findAccountsByEmployeeId(employees.getContent());
        Map<Long, EmployeeAssignment> assignmentsByEmployeeId = findCurrentAssignmentsByEmployeeId(
            employees.getContent()
        );
        return employees.map(employee -> toSummaryResponse(
            employee,
            assignmentsByEmployeeId.get(employee.getId()),
            accountsByEmployeeId.get(employee.getId())
        ));
    }

    @Override
    @PreAuthorize("hasAuthority('employee.update')")
    public EmployeeDetailResponse update(Long id, UpdateEmployeeRequest request) {
        Employee employee = findEmployeeOrThrow(id);
        employeeAccessScopeService.requireEmployeeAccess(id, EMPLOYEE_UPDATE);

        Instant now = Instant.now();
        updateWorkEmail(employee, request.workEmail(), now);

        employee.setFullName(request.fullName());
        employee.setDateOfBirth(request.dateOfBirth());
        employee.setGender(request.gender());
        employee.setHighestEducationLevel(request.highestEducationLevel());
        employee.setMajor(request.major());
        employee.setInstitution(request.institution());
        employee.setGraduationYear(request.graduationYear());
        employee.setPhone(request.phone());
        employee.setUpdatedAt(now);
        return toDetailResponse(employee);
    }

    @Override
    @PreAuthorize("hasAuthority('employee.probation.confirm')")
    public EmployeeDetailResponse confirmEmployment(Long id) {
        Employee employee = findEmployeeOrThrow(id);
        employeeAccessScopeService.requireEmployeeAccess(id, EMPLOYEE_PROBATION_CONFIRM);
        if (employee.getEmploymentStatus() != EmploymentStatus.PROBATION) {
            throw new ConflictException(
                ErrorCode.EMPLOYMENT_STATUS_TRANSITION_NOT_ALLOWED,
                "Only employees in PROBATION status can be confirmed"
            );
        }

        employee.setEmploymentStatus(EmploymentStatus.ACTIVE);
        employee.setUpdatedAt(Instant.now());
        return toDetailResponse(employee);
    }

    private void updateWorkEmail(Employee employee, String requestedWorkEmail, Instant now) {
        String workEmail = normalizeEmail(requestedWorkEmail);
        if (workEmail != null
            && employeeRepository.existsByWorkEmailIgnoreCaseAndIdNot(workEmail, employee.getId())) {
            throw new ConflictException(ErrorCode.CONFLICT, "Work email is already taken", "workEmail");
        }

        Account account = accountRepository.findByEmployeeId(employee.getId()).orElse(null);
        if (account != null && workEmail == null) {
            throw new ConflictException(
                ErrorCode.CONFLICT,
                "Work email cannot be removed while the employee has an account",
                "workEmail"
            );
        }
        if (account != null && accountRepository.existsByEmailIgnoreCaseAndIdNot(workEmail, account.getId())) {
            throw new ConflictException(ErrorCode.EMAIL_TAKEN, "Account email is already taken", "workEmail");
        }
        if (account == null && workEmail != null && accountRepository.existsByEmailIgnoreCase(workEmail)) {
            throw new ConflictException(ErrorCode.EMAIL_TAKEN, "Account email is already taken", "workEmail");
        }

        employee.setWorkEmail(workEmail);
        if (account != null) {
            account.setEmail(workEmail);
            account.setUpdatedAt(now);
        }
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    @PreAuthorize("hasAuthority('employee.lifecycle.manage')")
    public void completeResignation(Long employeeId, LocalDate terminationDate, String terminationReason) {
        Employee employee = findEmployeeOrThrow(employeeId);
        employeeAccessScopeService.requireEmployeeAccess(employeeId, EMPLOYEE_LIFECYCLE_MANAGE);
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
    @PreAuthorize("hasAuthority('employee.delete')")
    public void softDelete(Long id, SoftDeleteEmployeeRequest request) {
        Employee employee = findEmployeeOrThrow(id);
        employeeAccessScopeService.requireEmployeeAccess(id, EMPLOYEE_DELETE);
        ensureEmployeeHasNoBusinessHistory(id);

        employee.setDeletedAt(Instant.now());
        employee.setUpdatedAt(Instant.now());
        employee.setDeletedByAccount(findCurrentAccount());
        employee.setDeletionReason(request.deletionReason());
    }

    private void validateCreateUniqueness(CreateEmployeeProfileRequest request, String workEmail) {
        if (employeeRepository.existsByEmployeeCodeIgnoreCase(request.employeeCode().trim())) {
            throw new ConflictException(
                ErrorCode.EMPLOYEE_CODE_TAKEN, "Employee code is already taken", "employee.employeeCode"
            );
        }
        if (workEmail != null && (employeeRepository.existsByWorkEmailIgnoreCase(workEmail)
            || accountRepository.existsByEmailIgnoreCase(workEmail))) {
            throw new ConflictException(ErrorCode.EMAIL_TAKEN, "Work email is already taken", "employee.workEmail");
        }
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
            || employeeSalaryHistoryRepository.existsByEmployeeId(employeeId)
            || leaveRequestRepository.existsByEmployeeId(employeeId)
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

    private Map<Long, EmployeeAssignment> findCurrentAssignmentsByEmployeeId(List<Employee> employees) {
        if (employees.isEmpty()) {
            return Map.of();
        }

        List<Long> employeeIds = employees.stream().map(Employee::getId).toList();
        Map<Long, EmployeeAssignment> assignmentsByEmployeeId = new HashMap<>();
        employeeAssignmentRepository.findCurrentPrimaryByEmployeeIds(employeeIds, LocalDate.now())
            .forEach(assignment -> assignmentsByEmployeeId.put(assignment.getEmployee().getId(), assignment));
        return assignmentsByEmployeeId;
    }

    private EmployeeSummaryResponse toSummaryResponse(
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
            currentAssignment,
            accountRepository.findByEmployeeId(employee.getId())
                .map(this::toAccountSummaryResponse)
                .orElse(null)
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

    private EmployeeAssignmentResponse toAssignmentResponse(EmployeeAssignment assignment) {
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

}
