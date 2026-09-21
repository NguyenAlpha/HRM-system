package com.htttdn.hrm.service.impl;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.request.employee.AssignEmployeeRequest;
import com.htttdn.hrm.dto.request.employee.CreateEmployeeRequest;
import com.htttdn.hrm.dto.request.employee.SetCompensationRequest;
import com.htttdn.hrm.dto.request.employee.SoftDeleteEmployeeRequest;
import com.htttdn.hrm.dto.request.employee.UpdateEmployeeProfileRequest;
import com.htttdn.hrm.dto.response.employee.EmployeeAssignmentResponse;
import com.htttdn.hrm.dto.response.employee.EmployeeCompensationResponse;
import com.htttdn.hrm.dto.response.employee.EmployeeResponse;
import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.EmployeeAssignment;
import com.htttdn.hrm.entity.EmployeeCompensation;
import com.htttdn.hrm.entity.JobPosition;
import com.htttdn.hrm.entity.OrganizationUnit;
import com.htttdn.hrm.entity.WorkLocation;
import com.htttdn.hrm.entity.WorkShift;
import com.htttdn.hrm.entity.enums.AccountStatus;
import com.htttdn.hrm.entity.enums.CompensationType;
import com.htttdn.hrm.entity.enums.EmploymentStatus;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.exception.ResourceNotFoundException;
import com.htttdn.hrm.repository.AccountRepository;
import com.htttdn.hrm.repository.EmployeeAssignmentRepository;
import com.htttdn.hrm.repository.EmployeeCompensationRepository;
import com.htttdn.hrm.repository.EmployeeRepository;
import com.htttdn.hrm.repository.JobPositionRepository;
import com.htttdn.hrm.repository.OrganizationUnitRepository;
import com.htttdn.hrm.repository.WorkLocationRepository;
import com.htttdn.hrm.repository.WorkShiftRepository;
import com.htttdn.hrm.service.EmployeeService;

@Service
@Transactional
public class EmployeeServiceImpl implements EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final EmployeeAssignmentRepository employeeAssignmentRepository;
    private final EmployeeCompensationRepository employeeCompensationRepository;
    private final OrganizationUnitRepository organizationUnitRepository;
    private final WorkLocationRepository workLocationRepository;
    private final JobPositionRepository jobPositionRepository;
    private final WorkShiftRepository workShiftRepository;
    private final AccountRepository accountRepository;

    public EmployeeServiceImpl(
        EmployeeRepository employeeRepository,
        EmployeeAssignmentRepository employeeAssignmentRepository,
        EmployeeCompensationRepository employeeCompensationRepository,
        OrganizationUnitRepository organizationUnitRepository,
        WorkLocationRepository workLocationRepository,
        JobPositionRepository jobPositionRepository,
        WorkShiftRepository workShiftRepository,
        AccountRepository accountRepository
    ) {
        this.employeeRepository = employeeRepository;
        this.employeeAssignmentRepository = employeeAssignmentRepository;
        this.employeeCompensationRepository = employeeCompensationRepository;
        this.organizationUnitRepository = organizationUnitRepository;
        this.workLocationRepository = workLocationRepository;
        this.jobPositionRepository = jobPositionRepository;
        this.workShiftRepository = workShiftRepository;
        this.accountRepository = accountRepository;
    }

    @Override
    public EmployeeResponse create(CreateEmployeeRequest request) {
        if (employeeRepository.existsByEmployeeCode(request.employeeCode())) {
            throw new ConflictException(ErrorCode.EMPLOYEE_CODE_TAKEN, "Employee code is already taken", "employeeCode");
        }
        if (request.workEmail() != null && employeeRepository.existsByWorkEmail(request.workEmail())) {
            throw new ConflictException(ErrorCode.CONFLICT, "Work email is already taken", "workEmail");
        }
        if (request.nationalId() != null && employeeRepository.existsByNationalId(request.nationalId())) {
            throw new ConflictException(ErrorCode.CONFLICT, "National ID is already taken", "nationalId");
        }

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

        return toResponse(employeeRepository.save(employee));
    }

    @Override
    @Transactional(readOnly = true)
    public EmployeeResponse getById(Long id) {
        return toResponse(findEmployeeOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<EmployeeResponse> list(Pageable pageable) {
        return employeeRepository.findByDeletedAtIsNull(pageable).map(this::toResponse);
    }

    @Override
    public EmployeeResponse updateProfile(Long id, UpdateEmployeeProfileRequest request) {
        Employee employee = findEmployeeOrThrow(id);
        employee.setFullName(request.fullName());
        employee.setDateOfBirth(request.dateOfBirth());
        employee.setGender(request.gender());
        employee.setHighestEducationLevel(request.highestEducationLevel());
        employee.setMajor(request.major());
        employee.setInstitution(request.institution());
        employee.setGraduationYear(request.graduationYear());
        employee.setNationalId(request.nationalId());
        employee.setPersonalEmail(request.personalEmail());
        employee.setWorkEmail(request.workEmail());
        employee.setPhone(request.phone());
        employee.setAddress(request.address());
        employee.setTaxCode(request.taxCode());
        employee.setBankName(request.bankName());
        employee.setBankAccountNumber(request.bankAccountNumber());
        employee.setBankAccountHolder(request.bankAccountHolder());
        employee.setUpdatedAt(Instant.now());
        return toResponse(employee);
    }

    @Override
    public EmployeeAssignmentResponse assignDepartment(Long employeeId, AssignEmployeeRequest request) {
        Employee employee = findEmployeeOrThrow(employeeId);

        if (request.managerEmployeeId() != null && request.managerEmployeeId().equals(employeeId)) {
            throw new BusinessException(
                ErrorCode.VALIDATION_ERROR, "An employee cannot be their own manager", "managerEmployeeId");
        }

        OrganizationUnit organizationUnit = organizationUnitRepository.findById(request.organizationUnitId())
            .filter(unit -> unit.getDeletedAt() == null)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.ORGANIZATION_UNIT_NOT_FOUND, "Organization unit not found: " + request.organizationUnitId()));

        WorkLocation workLocation = workLocationRepository.findById(request.workLocationId())
            .filter(location -> location.getDeletedAt() == null)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.LOCATION_NOT_FOUND, "Work location not found: " + request.workLocationId()));

        JobPosition position = jobPositionRepository.findById(request.positionId())
            .filter(p -> p.getDeletedAt() == null)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.RESOURCE_NOT_FOUND, "Job position not found: " + request.positionId()));

        WorkShift shift = null;
        if (request.shiftId() != null) {
            shift = workShiftRepository.findById(request.shiftId())
                .filter(s -> s.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException(
                    ErrorCode.RESOURCE_NOT_FOUND, "Work shift not found: " + request.shiftId()));
        }

        Employee manager = null;
        if (request.managerEmployeeId() != null) {
            manager = findEmployeeOrThrow(request.managerEmployeeId());
        }

        Account createdBy = accountRepository.findById(request.createdByAccountId())
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.RESOURCE_NOT_FOUND, "Account not found: " + request.createdByAccountId()));

        employeeAssignmentRepository.findFirstByEmployeeIdAndIsPrimaryTrueAndEffectiveToIsNull(employeeId)
            .ifPresent(current -> current.setEffectiveTo(request.effectiveFrom().minusDays(1)));

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

        return toResponse(employeeAssignmentRepository.save(assignment));
    }

    @Override
    @Transactional(readOnly = true)
    public EmployeeAssignmentResponse getCurrentAssignment(Long employeeId) {
        return employeeAssignmentRepository.findFirstByEmployeeIdAndIsPrimaryTrueAndEffectiveToIsNull(employeeId)
            .map(this::toResponse)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.RESOURCE_NOT_FOUND, "No active assignment for employee: " + employeeId));
    }

    @Override
    public EmployeeCompensationResponse setCompensation(Long employeeId, SetCompensationRequest request) {
        Employee employee = findEmployeeOrThrow(employeeId);
        Account approvedBy = accountRepository.findById(request.approvedByAccountId())
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.RESOURCE_NOT_FOUND, "Account not found: " + request.approvedByAccountId()));

        List<EmployeeCompensation> existing = employeeCompensationRepository.findByEmployeeIdAndEffectiveToIsNull(employeeId);
        LocalDate closingDate = request.effectiveFrom().minusDays(1);
        for (EmployeeCompensation compensation : existing) {
            boolean sameCode = compensation.getComponentCode().equals(request.componentCode());
            boolean bothBasicSalary = compensation.getComponentType() == CompensationType.BASIC_SALARY
                && request.componentType() == CompensationType.BASIC_SALARY;
            if (sameCode || bothBasicSalary) {
                compensation.setEffectiveTo(closingDate);
            }
        }

        EmployeeCompensation compensation = EmployeeCompensation.builder()
            .employee(employee)
            .componentType(request.componentType())
            .componentCode(request.componentCode())
            .componentName(request.componentName())
            .monthlyAmount(request.monthlyAmount())
            .effectiveFrom(request.effectiveFrom())
            .approvedByAccount(approvedBy)
            .note(request.note())
            .createdAt(Instant.now())
            .build();

        return toResponse(employeeCompensationRepository.save(compensation));
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmployeeCompensationResponse> getActiveCompensations(Long employeeId, LocalDate asOfDate) {
        return employeeCompensationRepository.findByEmployeeId(employeeId).stream()
            .filter(c -> !c.getEffectiveFrom().isAfter(asOfDate))
            .filter(c -> c.getEffectiveTo() == null || !c.getEffectiveTo().isBefore(asOfDate))
            .map(this::toResponse)
            .toList();
    }

    @Override
    public void completeResignation(Long employeeId, LocalDate terminationDate, String terminationReason) {
        Employee employee = findEmployeeOrThrow(employeeId);
        employee.setEmploymentStatus(EmploymentStatus.RESIGNED);
        employee.setTerminationDate(terminationDate);
        employee.setTerminationReason(terminationReason);
        employee.setUpdatedAt(Instant.now());

        employeeAssignmentRepository.findFirstByEmployeeIdAndIsPrimaryTrueAndEffectiveToIsNull(employeeId)
            .ifPresent(assignment -> assignment.setEffectiveTo(terminationDate));

        Optional<Account> account = accountRepository.findByEmployeeId(employeeId);
        account.ifPresent(a -> {
            a.setStatus(AccountStatus.DISABLED);
            a.setUpdatedAt(Instant.now());
        });
    }

    @Override
    public void softDelete(Long id, SoftDeleteEmployeeRequest request) {
        Employee employee = findEmployeeOrThrow(id);
        Account deletedBy = accountRepository.findById(request.deletedByAccountId())
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.RESOURCE_NOT_FOUND, "Account not found: " + request.deletedByAccountId()));

        employee.setDeletedAt(Instant.now());
        employee.setDeletedByAccount(deletedBy);
        employee.setDeletionReason(request.deletionReason());
    }

    private Employee findEmployeeOrThrow(Long id) {
        return employeeRepository.findById(id)
            .filter(employee -> employee.getDeletedAt() == null)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.EMPLOYEE_NOT_FOUND, "Employee not found: " + id));
    }

    private EmployeeResponse toResponse(Employee employee) {
        return new EmployeeResponse(
            employee.getId(),
            employee.getEmployeeCode(),
            employee.getFullName(),
            employee.getDateOfBirth(),
            employee.getGender(),
            employee.getHighestEducationLevel(),
            employee.getWorkEmail(),
            employee.getPhone(),
            employee.getHireDate(),
            employee.getEmploymentStatus(),
            employee.getTerminationDate()
        );
    }

    private EmployeeAssignmentResponse toResponse(EmployeeAssignment assignment) {
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

    private EmployeeCompensationResponse toResponse(EmployeeCompensation compensation) {
        return new EmployeeCompensationResponse(
            compensation.getId(),
            compensation.getEmployee().getId(),
            compensation.getComponentType(),
            compensation.getComponentCode(),
            compensation.getComponentName(),
            compensation.getMonthlyAmount(),
            compensation.getEffectiveFrom(),
            compensation.getEffectiveTo()
        );
    }
}
