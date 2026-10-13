package com.htttdn.hrm.config.seed;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.AccountRoleAssignment;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.EmployeeAssignment;
import com.htttdn.hrm.entity.JobPosition;
import com.htttdn.hrm.entity.OrganizationUnit;
import com.htttdn.hrm.entity.WorkLocation;
import com.htttdn.hrm.entity.enums.AccountStatus;
import com.htttdn.hrm.entity.enums.EmploymentStatus;
import com.htttdn.hrm.entity.enums.EmploymentType;
import com.htttdn.hrm.entity.enums.RoleScopeType;
import com.htttdn.hrm.repository.AccountRepository;
import com.htttdn.hrm.repository.AccountRoleAssignmentRepository;
import com.htttdn.hrm.repository.EmployeeAssignmentRepository;
import com.htttdn.hrm.repository.EmployeeRepository;
import com.htttdn.hrm.repository.JobPositionRepository;
import com.htttdn.hrm.repository.OrganizationUnitRepository;
import com.htttdn.hrm.repository.RoleRepository;
import com.htttdn.hrm.repository.WorkLocationRepository;
import com.htttdn.hrm.repository.WorkShiftRepository;
import com.htttdn.hrm.service.EmployeeCodeGenerator;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Khởi tạo nhân sự mẫu từ file JSON ({@code staff.seed.file}, mặc định {@code classpath:seed/staff.json}).
 *
 * <p>Mỗi phần tử tạo một nhân viên đang làm việc, account đã kích hoạt, các role với scope khai báo,
 * phân công chính và chấm công {@code PRESENT} từ ngày vào làm đến hôm qua (như Company Owner mẫu).
 * Ngày vào làm là {@code monthsAgo} tháng trước ngày chạy seed. Scope {@code ORG_UNIT} và
 * {@code LOCATION} lấy đơn vị và địa điểm của phân công chính.
 *
 * <p>{@code manager} là username của quản lý trực tiếp; người đó phải đã có (ví dụ {@code owner})
 * hoặc đứng trước trong file. Phần tử có username đã tồn tại được bỏ qua, nên chạy lại seed không
 * tạo trùng. Seeder chạy sau Company Owner (order 510).
 */
@Component
@Order(510)
public class StaffSeeder implements ApplicationRunner {

    private static final String SEED_REASON = "Seeded staff";
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private static final Logger log = LoggerFactory.getLogger(StaffSeeder.class);

    /** One person in the seed file; {@code workLocation} and {@code shift} default to the head office day shift. */
    record StaffEntry(
        String username,
        String password,
        String fullName,
        String email,
        Map<String, RoleScopeType> roles,
        String organizationUnit,
        String position,
        String workLocation,
        String shift,
        String manager,
        int monthsAgo
    ) {
    }

    private final AccountRepository accountRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeAssignmentRepository employeeAssignmentRepository;
    private final RoleRepository roleRepository;
    private final AccountRoleAssignmentRepository accountRoleAssignmentRepository;
    private final OrganizationUnitRepository organizationUnitRepository;
    private final WorkLocationRepository workLocationRepository;
    private final JobPositionRepository jobPositionRepository;
    private final WorkShiftRepository workShiftRepository;
    private final SeedAttendanceWriter seedAttendanceWriter;
    private final EmployeeCodeGenerator employeeCodeGenerator;
    private final PasswordEncoder passwordEncoder;
    private final JsonMapper jsonMapper;
    private final boolean enabled;
    private final Resource file;

    public StaffSeeder(
        AccountRepository accountRepository,
        EmployeeRepository employeeRepository,
        EmployeeAssignmentRepository employeeAssignmentRepository,
        RoleRepository roleRepository,
        AccountRoleAssignmentRepository accountRoleAssignmentRepository,
        OrganizationUnitRepository organizationUnitRepository,
        WorkLocationRepository workLocationRepository,
        JobPositionRepository jobPositionRepository,
        WorkShiftRepository workShiftRepository,
        SeedAttendanceWriter seedAttendanceWriter,
        EmployeeCodeGenerator employeeCodeGenerator,
        PasswordEncoder passwordEncoder,
        JsonMapper jsonMapper,
        @Value("${staff.seed.enabled:true}") boolean enabled,
        @Value("${staff.seed.file:classpath:seed/staff.json}") Resource file
    ) {
        this.accountRepository = accountRepository;
        this.employeeRepository = employeeRepository;
        this.employeeAssignmentRepository = employeeAssignmentRepository;
        this.roleRepository = roleRepository;
        this.accountRoleAssignmentRepository = accountRoleAssignmentRepository;
        this.organizationUnitRepository = organizationUnitRepository;
        this.workLocationRepository = workLocationRepository;
        this.jobPositionRepository = jobPositionRepository;
        this.workShiftRepository = workShiftRepository;
        this.seedAttendanceWriter = seedAttendanceWriter;
        this.employeeCodeGenerator = employeeCodeGenerator;
        this.passwordEncoder = passwordEncoder;
        this.jsonMapper = jsonMapper;
        this.enabled = enabled;
        this.file = file;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws IOException {
        if (!enabled) {
            return;
        }

        List<StaffEntry> entries;
        try (InputStream input = file.getInputStream()) {
            entries = jsonMapper.readValue(input, new TypeReference<List<StaffEntry>>() { });
        }

        int created = 0;
        for (StaffEntry entry : entries) {
            if (accountRepository.findByUsername(entry.username()).isPresent()) {
                continue;
            }
            seed(entry);
            created++;
        }
        log.info("Staff seed completed: {} created, {} already present", created, entries.size() - created);
    }

    private void seed(StaffEntry entry) {
        ZonedDateTime seededAt = ZonedDateTime.now(ZONE).minusMonths(entry.monthsAgo());
        Instant createdAt = seededAt.toInstant();
        LocalDate startDate = seededAt.toLocalDate();

        OrganizationUnit unit = organizationUnitRepository.findByCodeAndDeletedAtIsNull(entry.organizationUnit())
            .orElseThrow(() -> missing(entry, "organization unit", entry.organizationUnit()));
        String locationCode = entry.workLocation() != null ? entry.workLocation() : "HO";
        WorkLocation location = workLocationRepository.findByCodeAndDeletedAtIsNull(locationCode)
            .orElseThrow(() -> missing(entry, "work location", locationCode));
        JobPosition position = jobPositionRepository.findByCodeAndDeletedAtIsNull(entry.position())
            .orElseThrow(() -> missing(entry, "job position", entry.position()));
        String shiftCode = entry.shift() != null ? entry.shift() : "OFFICE_DAY";
        Employee manager = entry.manager() == null ? null : accountRepository.findByUsername(entry.manager())
            .map(Account::getEmployee)
            .orElseThrow(() -> missing(entry, "manager", entry.manager()));

        Employee employee = employeeRepository.save(Employee.builder()
            .employeeCode(employeeCodeGenerator.forPosition(position.getId()))
            .fullName(entry.fullName())
            .workEmail(entry.email())
            .hireDate(startDate)
            .seniorityStartDate(startDate)
            .employmentStatus(EmploymentStatus.ACTIVE)
            .createdAt(createdAt)
            .updatedAt(createdAt)
            .build());

        Account account = accountRepository.save(Account.builder()
            .employee(employee)
            .username(entry.username())
            .email(entry.email())
            .passwordHash(passwordEncoder.encode(entry.password()))
            .status(AccountStatus.ACTIVE)
            .failedLoginCount(0)
            .createdAt(createdAt)
            .updatedAt(createdAt)
            .build());

        entry.roles().forEach((code, scope) -> accountRoleAssignmentRepository.save(AccountRoleAssignment.builder()
            .account(account)
            .role(roleRepository.findByCodeAndDeletedAtIsNull(code).orElseThrow(() -> missing(entry, "role", code)))
            .scopeType(scope)
            .organizationUnit(scope == RoleScopeType.ORG_UNIT ? unit : null)
            .workLocation(scope == RoleScopeType.LOCATION ? location : null)
            .effectiveFrom(startDate)
            .grantedByAccount(account)
            .reason(SEED_REASON)
            .createdAt(createdAt)
            .build()));

        employeeAssignmentRepository.save(EmployeeAssignment.builder()
            .employee(employee)
            .organizationUnit(unit)
            .workLocation(location)
            .position(position)
            .shift(workShiftRepository.findByCodeAndDeletedAtIsNull(shiftCode)
                .orElseThrow(() -> missing(entry, "work shift", shiftCode)))
            .managerEmployee(manager)
            .employmentType(EmploymentType.FULL_TIME)
            .effectiveFrom(startDate)
            .isPrimary(true)
            .reason(SEED_REASON)
            .createdByAccount(account)
            .createdAt(createdAt)
            .build());

        int attendanceDays = seedAttendanceWriter.write(employee, startDate, LocalDate.now(ZONE).minusDays(1));
        log.info("Staff seed: '{}' created as {} with {} attendance days",
            entry.username(), employee.getEmployeeCode(), attendanceDays);
    }

    private static IllegalStateException missing(StaffEntry entry, String what, String code) {
        return new IllegalStateException("Staff seed '" + entry.username() + "': " + what + " '" + code + "' not found");
    }
}
