package com.htttdn.hrm.config.seed;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.config.ClockConfig;
import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.AccountRoleAssignment;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.EmployeeAssignment;
import com.htttdn.hrm.entity.EmployeePayrollProfile;
import com.htttdn.hrm.entity.EmployeeSalaryHistory;
import com.htttdn.hrm.entity.EmployeeTaxDependent;
import com.htttdn.hrm.entity.Role;
import com.htttdn.hrm.entity.enums.AccountStatus;
import com.htttdn.hrm.entity.enums.EducationLevel;
import com.htttdn.hrm.entity.enums.EmploymentStatus;
import com.htttdn.hrm.entity.enums.EmploymentType;
import com.htttdn.hrm.entity.enums.Gender;
import com.htttdn.hrm.entity.enums.RoleScopeType;
import com.htttdn.hrm.repository.AccountRepository;
import com.htttdn.hrm.repository.AccountRoleAssignmentRepository;
import com.htttdn.hrm.repository.EmployeeAssignmentRepository;
import com.htttdn.hrm.repository.EmployeePayrollProfileRepository;
import com.htttdn.hrm.repository.EmployeeRepository;
import com.htttdn.hrm.repository.EmployeeSalaryHistoryRepository;
import com.htttdn.hrm.repository.EmployeeTaxDependentRepository;
import com.htttdn.hrm.repository.JobPositionRepository;
import com.htttdn.hrm.repository.OrganizationUnitRepository;
import com.htttdn.hrm.repository.RoleRepository;
import com.htttdn.hrm.repository.WorkLocationRepository;
import com.htttdn.hrm.repository.WorkShiftRepository;
import com.htttdn.hrm.service.employee.EmployeeCodeGenerator;

import lombok.RequiredArgsConstructor;

/**
 * Khởi tạo Company Owner mẫu: nhân viên, account đã kích hoạt, role {@code COMPANY_OWNER},
 * {@code DIRECTOR}, {@code PAYROLL_APPROVER} và {@code EMPLOYEE}, cùng phân công chính tại Ban giám
 * đốc / Trụ sở chính. Owner duyệt, xác nhận trả và khóa kỳ lương do kế toán tính.
 *
 * <p>Dữ liệu giống kết quả của luồng bootstrap Company Owner trong Admin Console, nhưng account
 * được kích hoạt sẵn với mật khẩu cố định để dùng trong môi trường phát triển. Ngày tạo, ngày vào
 * làm, ngày cấp role và ngày bắt đầu phân công đều là 6 năm trước ngày chạy seed, sớm hơn mọi nhân
 * sự trong {@code staff.json}.
 *
 * <p>Owner có lương cơ bản 50.000.000 và hồ sơ bảo hiểm/thuế (đóng đủ BHXH/BHYT/BHTN trên 50.000.000,
 * vùng 1) từ ngày vào làm, cùng 2 người con phụ thuộc giảm trừ: một từ ngày vào làm, một từ 8 tháng trước.
 *
 * <p>Owner có sẵn chấm công {@code PRESENT} cho mọi ngày làm việc từ ngày vào làm đến hôm qua:
 * phần lớn đúng giờ, cứ 10 ngày công có một ngày đi muộn 30 phút và một ngày về sớm 30 phút,
 * thỉnh thoảng có tăng ca (xem {@link SeedAttendanceWriter}); Owner tự xác nhận tăng ca của mình.
 * Từ hôm nay trở đi do job chuẩn bị chấm công tạo như mọi nhân viên khác.
 *
 * <p>Seeder chạy sau các seeder doanh nghiệp và RBAC (order 500). Nếu đã có Company Owner đang
 * hiệu lực thì bỏ qua để không vi phạm ràng buộc chỉ một Company Owner.
 */
@Component
@Order(500)
@RequiredArgsConstructor
public class CompanyOwnerSeeder implements ApplicationRunner {

    private static final String USERNAME = "owner";
    private static final String PASSWORD = "password";
    private static final String EMAIL = "owner@hrm.local";
    private static final String FULL_NAME = "Nguyễn Văn An";
    private static final String COMPANY_OWNER_ROLE = "COMPANY_OWNER";
    private static final String DIRECTOR_ROLE = "DIRECTOR";
    private static final String PAYROLL_APPROVER_ROLE = "PAYROLL_APPROVER";
    private static final String EMPLOYEE_ROLE = "EMPLOYEE";
    private static final String SEED_REASON = "Seeded company owner";
    private static final BigDecimal BASE_SALARY = new BigDecimal("50000000");

    private static final Logger log = LoggerFactory.getLogger(CompanyOwnerSeeder.class);

    private final AccountRepository accountRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeAssignmentRepository employeeAssignmentRepository;
    private final EmployeeSalaryHistoryRepository employeeSalaryHistoryRepository;
    private final EmployeePayrollProfileRepository employeePayrollProfileRepository;
    private final EmployeeTaxDependentRepository employeeTaxDependentRepository;
    private final RoleRepository roleRepository;
    private final AccountRoleAssignmentRepository accountRoleAssignmentRepository;
    private final OrganizationUnitRepository organizationUnitRepository;
    private final WorkLocationRepository workLocationRepository;
    private final JobPositionRepository jobPositionRepository;
    private final WorkShiftRepository workShiftRepository;
    private final SeedAttendanceWriter seedAttendanceWriter;
    private final EmployeeCodeGenerator employeeCodeGenerator;
    private final PasswordEncoder passwordEncoder;
    private final SeedTimestamps seedTimestamps;
    private final OwnerSeedProperties properties;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.enabled()) {
            return;
        }

        Role ownerRole = findRequiredRole(COMPANY_OWNER_ROLE);
        Role directorRole = findRequiredRole(DIRECTOR_ROLE);
        Role payrollApproverRole = findRequiredRole(PAYROLL_APPROVER_ROLE);
        Role employeeRole = findRequiredRole(EMPLOYEE_ROLE);
        if (!accountRoleAssignmentRepository.findByRoleIdAndRevokedAtIsNull(ownerRole.getId()).isEmpty()) {
            log.info("Company owner seed completed: existing company owner retained");
            return;
        }
        if (accountRepository.findByUsername(USERNAME).isPresent()) {
            throw new IllegalStateException(
                "Company owner seed username '" + USERNAME + "' belongs to an existing non-owner account"
            );
        }

        ZonedDateTime seededAt = ZonedDateTime.now().minusYears(6);
        Instant createdAt = seededAt.toInstant();
        LocalDate startDate = seededAt.toLocalDate();

        Employee employee = employeeRepository.save(Employee.builder()
            .employeeCode(employeeCodeGenerator.next())
            .fullName(FULL_NAME)
            .gender(Gender.MALE)
            .dateOfBirth(LocalDate.of(1982, 6, 18))
            .highestEducationLevel(EducationLevel.MASTER)
            .major("Quản trị kinh doanh")
            .institution("Trường Đại học Kinh tế Quốc dân")
            .graduationYear((short) 2010)
            .workEmail(EMAIL)
            .hireDate(startDate)
            .seniorityStartDate(startDate)
            .employmentStatus(EmploymentStatus.ACTIVE)
            .build());
        seedTimestamps.at(employee, createdAt);

        Account account = accountRepository.save(Account.builder()
            .employee(employee)
            .username(USERNAME)
            .email(EMAIL)
            .passwordHash(passwordEncoder.encode(PASSWORD))
            .status(AccountStatus.ACTIVE)
            .failedLoginCount(0)
            .build());
        seedTimestamps.at(account, createdAt);

        assignRole(account, ownerRole, RoleScopeType.COMPANY, startDate, createdAt);
        assignRole(account, directorRole, RoleScopeType.COMPANY, startDate, createdAt);
        assignRole(account, payrollApproverRole, RoleScopeType.COMPANY, startDate, createdAt);
        assignRole(account, employeeRole, RoleScopeType.SELF, startDate, createdAt);
        createPrimaryAssignment(employee, account, startDate, createdAt);
        seedTimestamps.at(employeeSalaryHistoryRepository.save(EmployeeSalaryHistory.builder()
            .employee(employee)
            .baseSalary(BASE_SALARY)
            .effectiveFrom(startDate)
            .approvedByAccount(account)
            .reason("Mức lương ban đầu")
            .build()), createdAt);
        seedTimestamps.at(employeePayrollProfileRepository.save(EmployeePayrollProfile.builder()
            .employee(employee)
            .effectiveFrom(startDate)
            .taxResident(true)
            .socialInsurance(true)
            .healthInsurance(true)
            .unemploymentInsurance(true)
            .insuranceSalary(BASE_SALARY)
            .wageRegion((short) 1)
            .createdByAccount(account)
            .build()), createdAt);
        addDependent(employee, account, "Nguyễn Minh Khang", "001224000101", startDate);
        addDependent(employee, account, "Nguyễn Bảo Ngọc", "001325000102", LocalDate.now(ClockConfig.BUSINESS_ZONE).minusMonths(8));
        // The Company Owner, with nobody above, confirms their own overtime.
        int attendanceDays = seedAttendanceWriter.write(employee, startDate, LocalDate.now(ClockConfig.BUSINESS_ZONE).minusDays(1), date -> account);

        seedTimestamps.apply();
        log.info("Company owner seed completed: '{}' created as {} with {} attendance days",
            USERNAME, employee.getEmployeeCode(), attendanceDays);
    }

    private void addDependent(Employee employee, Account account, String fullName, String identifier, LocalDate effectiveFrom) {
        seedTimestamps.at(employeeTaxDependentRepository.save(EmployeeTaxDependent.builder()
            .employee(employee)
            .fullName(fullName)
            .identifier(identifier)
            .effectiveFrom(effectiveFrom)
            .createdByAccount(account)
            .build()), effectiveFrom.atStartOfDay(ClockConfig.BUSINESS_ZONE).toInstant());
    }

    private Role findRequiredRole(String code) {
        return roleRepository.findByCodeAndDeletedAtIsNull(code)
            .orElseThrow(() -> new IllegalStateException(code + " seed role not found"));
    }

    private void assignRole(
        Account account,
        Role role,
        RoleScopeType scopeType,
        LocalDate effectiveFrom,
        Instant createdAt
    ) {
        seedTimestamps.at(accountRoleAssignmentRepository.save(AccountRoleAssignment.builder()
            .account(account)
            .role(role)
            .scopeType(scopeType)
            .effectiveFrom(effectiveFrom)
            .grantedByAccount(account)
            .reason(SEED_REASON)
            .build()), createdAt);
    }

    private void createPrimaryAssignment(
        Employee employee,
        Account createdBy,
        LocalDate effectiveFrom,
        Instant createdAt
    ) {
        seedTimestamps.at(employeeAssignmentRepository.save(EmployeeAssignment.builder()
            .employee(employee)
            .organizationUnit(organizationUnitRepository.findByCodeAndDeletedAtIsNull("BOARD")
                .orElseThrow(() -> new IllegalStateException("BOARD seed organization unit not found")))
            .workLocation(workLocationRepository.findByCodeAndDeletedAtIsNull("HO")
                .orElseThrow(() -> new IllegalStateException("HO seed work location not found")))
            .position(jobPositionRepository.findByCodeAndDeletedAtIsNull("DIRECTOR")
                .orElseThrow(() -> new IllegalStateException("DIRECTOR seed job position not found")))
            .shift(workShiftRepository.findByCodeAndDeletedAtIsNull("OFFICE_DAY")
                .orElseThrow(() -> new IllegalStateException("OFFICE_DAY seed work shift not found")))
            .employmentType(EmploymentType.FULL_TIME)
            .effectiveFrom(effectiveFrom)
            .isPrimary(true)
            .reason(SEED_REASON)
            .createdByAccount(createdBy)
            .build()), createdAt);
    }
}
