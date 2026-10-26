package com.htttdn.hrm.service.payroll;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.htttdn.hrm.service.employee.EmploymentEndGuard;
import com.htttdn.hrm.service.rbac.EmployeeAccessScopeService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.EmployeePayrollProfile;
import com.htttdn.hrm.entity.EmployeeTaxDependent;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.exception.ResourceNotFoundException;
import com.htttdn.hrm.repository.AccountRepository;
import com.htttdn.hrm.repository.EmployeePayrollProfileRepository;
import com.htttdn.hrm.repository.EmployeeRepository;
import com.htttdn.hrm.repository.EmployeeTaxDependentRepository;
import com.htttdn.hrm.security.CurrentAccountProvider;
import com.htttdn.hrm.util.Strings;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class PayrollDeductionsService {
    private final EmployeePayrollProfileRepository profileRepository;
    private final EmployeeTaxDependentRepository dependentRepository;
    private final AccountRepository accounts;
    private final EmployeeRepository employees;
    private final EmployeeAccessScopeService access;
    private final CurrentAccountProvider actor;
    private final PayrollPeriodInvalidator invalidator;

    @PreAuthorize("hasAuthority('compensation.read')")
    @Transactional(readOnly = true)
    public List<Profile> profiles(Long employeeId) {
        requireEmployee(employeeId, "compensation.read");
        return profileRepository.findByEmployeeIdOrderByEffectiveFromDesc(employeeId).stream()
            .map(PayrollDeductionsService::toProfile).toList();
    }

    @PreAuthorize("hasAuthority('compensation.manage')")
    public Profile setProfile(Long employeeId, ProfileCommand command) {
        Employee employee = requireEmployee(employeeId, "compensation.manage");
        if (command.effectiveFrom().isBefore(employee.getHireDate())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Invalid payroll profile or effective date");
        }
        EmploymentEndGuard.requireNotAfterEnd(employee, command.effectiveFrom(), "effectiveFrom");
        if (!command.taxResident()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                "Non-resident income tax requires a separate rule set; this payroll supports resident employees only");
        }
        if ((command.socialInsurance() || command.healthInsurance() || command.unemploymentInsurance())
            && command.insuranceSalary().signum() == 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "insuranceSalary is required when insurance is enabled");
        }
        invalidator.invalidateFrom(command.effectiveFrom());
        EmployeePayrollProfile open = profileRepository.findByEmployeeIdForUpdate(employeeId).stream()
            .filter(profile -> profile.getEffectiveTo() == null).findFirst().orElse(null);
        if (open != null) {
            if (!command.effectiveFrom().isAfter(open.getEffectiveFrom())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "effectiveFrom must be after the current payroll profile start date");
            }
            open.setEffectiveTo(command.effectiveFrom().minusDays(1));
            // Hibernate flushes inserts before updates; close the old period first or the
            // overlap exclusion constraint rejects the new profile.
            profileRepository.saveAndFlush(open);
        }
        EmployeePayrollProfile created = profileRepository.save(EmployeePayrollProfile.builder()
            .employee(employee)
            .effectiveFrom(command.effectiveFrom())
            .taxResident(command.taxResident())
            .socialInsurance(command.socialInsurance())
            .healthInsurance(command.healthInsurance())
            .unemploymentInsurance(command.unemploymentInsurance())
            .insuranceSalary(command.insuranceSalary())
            .wageRegion(command.wageRegion())
            .createdByAccount(accounts.getReferenceById(actor.accountId()))
            .build());
        return toProfile(created);
    }

    @PreAuthorize("hasAuthority('compensation.read')")
    @Transactional(readOnly = true)
    public List<Dependent> dependents(Long employeeId) {
        requireEmployee(employeeId, "compensation.read");
        return dependentRepository.findByEmployeeIdOrderByEffectiveFromDescIdDesc(employeeId).stream()
            .map(PayrollDeductionsService::toDependent).toList();
    }

    @PreAuthorize("hasAuthority('compensation.manage')")
    public Dependent addDependent(Long employeeId, DependentCommand command) {
        Employee employee = requireEmployee(employeeId, "compensation.manage");
        if (command.effectiveFrom().isBefore(employee.getHireDate())
            || command.effectiveTo() != null && command.effectiveTo().isBefore(command.effectiveFrom())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Invalid dependent or effective period");
        }
        EmploymentEndGuard.requireNotAfterEnd(employee, command.effectiveFrom(), "effectiveFrom");
        // Nothing is deducted after the last working day, so the period stops there.
        LocalDate effectiveTo = EmploymentEndGuard.capAtEnd(employee, command.effectiveTo());
        if (dependentRepository.existsOverlappingPeriod(employeeId, command.fullName().trim(),
            command.effectiveFrom(), effectiveTo)) {
            throw new ConflictException(ErrorCode.CONFLICT, "Dependent already overlaps this effective period");
        }
        invalidator.invalidateTaxPaymentsBetween(command.effectiveFrom(), effectiveTo);
        EmployeeTaxDependent created = dependentRepository.save(EmployeeTaxDependent.builder()
            .employee(employee)
            .fullName(command.fullName().trim())
            .identifier(Strings.trimToNull(command.identifier()))
            .effectiveFrom(command.effectiveFrom())
            .effectiveTo(effectiveTo)
            .createdByAccount(accounts.getReferenceById(actor.accountId()))
            .build());
        return toDependent(created);
    }

    @PreAuthorize("hasAuthority('compensation.manage')")
    public Dependent endDependent(Long employeeId, Long dependentId, LocalDate effectiveTo) {
        requireEmployee(employeeId, "compensation.manage");
        EmployeeTaxDependent current = dependentRepository.findById(dependentId)
            .filter(dependent -> dependent.getEmployee().getId().equals(employeeId))
            .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND,
                "Dependent not found: " + dependentId));
        if (effectiveTo == null || effectiveTo.isBefore(current.getEffectiveFrom())
            || current.getEffectiveTo() != null && !effectiveTo.isBefore(current.getEffectiveTo())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Invalid dependent end date");
        }
        invalidator.invalidateTaxPaymentsBetween(effectiveTo.plusDays(1), current.getEffectiveTo());
        current.setEffectiveTo(effectiveTo);
        return toDependent(current);
    }

    private static Profile toProfile(EmployeePayrollProfile profile) {
        return new Profile(profile.getId(), profile.getEmployee().getId(), profile.getEffectiveFrom(),
            profile.getEffectiveTo(), profile.getTaxResident(), profile.getSocialInsurance(),
            profile.getHealthInsurance(), profile.getUnemploymentInsurance(), profile.getInsuranceSalary(),
            profile.getWageRegion());
    }

    private static Dependent toDependent(EmployeeTaxDependent dependent) {
        return new Dependent(dependent.getId(), dependent.getEmployee().getId(), dependent.getFullName(),
            dependent.getIdentifier(), dependent.getEffectiveFrom(), dependent.getEffectiveTo());
    }

    private Employee requireEmployee(Long employeeId, String permission) {
        Employee employee = employees.findByIdAndDeletedAtIsNull(employeeId)
            .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.EMPLOYEE_NOT_FOUND,
                "Employee not found: " + employeeId));
        access.requireEmployeeAccess(employeeId, permission);
        return employee;
    }

    public record ProfileCommand(@NotNull LocalDate effectiveFrom, @NotNull Boolean taxResident,
        @NotNull Boolean socialInsurance, @NotNull Boolean healthInsurance,
        @NotNull Boolean unemploymentInsurance, @NotNull @PositiveOrZero BigDecimal insuranceSalary,
        @NotNull @Min(1) @Max(4) Short wageRegion) {}
    public record Profile(Long id, Long employeeId, LocalDate effectiveFrom, @Nullable LocalDate effectiveTo,
        boolean taxResident, boolean socialInsurance, boolean healthInsurance,
        boolean unemploymentInsurance, BigDecimal insuranceSalary, short wageRegion) {}
    public record DependentCommand(@NotBlank String fullName, @Nullable String identifier,
        @NotNull LocalDate effectiveFrom, @Nullable LocalDate effectiveTo) {}
    public record Dependent(Long id, Long employeeId, String fullName, @Nullable String identifier,
        LocalDate effectiveFrom, @Nullable LocalDate effectiveTo) {}
    public record EndDependentCommand(@NotNull LocalDate effectiveTo) {}
}
