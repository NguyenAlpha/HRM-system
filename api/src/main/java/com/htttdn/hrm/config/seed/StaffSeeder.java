package com.htttdn.hrm.config.seed;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
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
import com.htttdn.hrm.entity.EmployeeSalaryHistory;
import com.htttdn.hrm.entity.JobPosition;
import com.htttdn.hrm.entity.OrganizationUnit;
import com.htttdn.hrm.entity.WorkLocation;
import com.htttdn.hrm.entity.WorkShift;
import com.htttdn.hrm.entity.enums.AccountStatus;
import com.htttdn.hrm.entity.enums.EmploymentStatus;
import com.htttdn.hrm.entity.enums.EmploymentType;
import com.htttdn.hrm.entity.enums.RoleScopeType;
import com.htttdn.hrm.repository.AccountRepository;
import com.htttdn.hrm.repository.AccountRoleAssignmentRepository;
import com.htttdn.hrm.repository.EmployeeAssignmentRepository;
import com.htttdn.hrm.repository.EmployeeRepository;
import com.htttdn.hrm.repository.EmployeeSalaryHistoryRepository;
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
 * lịch sử phân công chính và chấm công {@code PRESENT} từ ngày vào làm đến hôm qua (như Company
 * Owner mẫu).
 *
 * <p>{@code assignments} liệt kê các giai đoạn phân công chính từ cũ đến mới; mỗi giai đoạn bắt đầu
 * {@code monthsAgo} tháng trước ngày chạy seed và kết thúc ngay trước giai đoạn sau, giai đoạn cuối
 * là phân công hiện hành. Ngày vào làm là ngày bắt đầu giai đoạn đầu tiên; role có hiệu lực từ ngày
 * đó. Scope {@code ORG_UNIT} và {@code LOCATION} lấy đơn vị và địa điểm của phân công hiện hành.
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

    /** One person in the seed file, with their primary assignments oldest first. */
    record StaffEntry(
        String username,
        String password,
        String fullName,
        String email,
        Map<String, RoleScopeType> roles,
        List<AssignmentEntry> assignments,
        List<SalaryEntry> salaries
    ) {
    }

    /**
     * One base salary, oldest first; it runs until the next one starts. A transfer with a new
     * salary uses the assignment's {@code monthsAgo}; a renegotiated salary needs no assignment.
     */
    record SalaryEntry(int monthsAgo, BigDecimal baseSalary, String reason) {
    }

    /** One primary assignment period; {@code workLocation} and {@code shift} default to the head office day shift. */
    record AssignmentEntry(
        int monthsAgo,
        String organizationUnit,
        String position,
        String workLocation,
        String shift,
        String manager
    ) {
    }

    /** An assignment period with its codes resolved and its start date. */
    private record ResolvedAssignment(
        LocalDate effectiveFrom,
        OrganizationUnit unit,
        WorkLocation location,
        JobPosition position,
        WorkShift shift,
        Employee manager
    ) {
    }

    private final AccountRepository accountRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeAssignmentRepository employeeAssignmentRepository;
    private final EmployeeSalaryHistoryRepository employeeSalaryHistoryRepository;
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
        EmployeeSalaryHistoryRepository employeeSalaryHistoryRepository,
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
        this.employeeSalaryHistoryRepository = employeeSalaryHistoryRepository;
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
        List<ResolvedAssignment> assignments = resolveAssignments(entry);
        ResolvedAssignment first = assignments.getFirst();
        ResolvedAssignment current = assignments.getLast();
        LocalDate startDate = first.effectiveFrom();
        Instant createdAt = startOf(startDate);

        Employee employee = employeeRepository.save(Employee.builder()
            .employeeCode(employeeCodeGenerator.next())
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
            .organizationUnit(scope == RoleScopeType.ORG_UNIT ? current.unit() : null)
            .workLocation(scope == RoleScopeType.LOCATION ? current.location() : null)
            .effectiveFrom(startDate)
            .grantedByAccount(account)
            .reason(SEED_REASON)
            .createdAt(createdAt)
            .build()));

        // Each period ends the day before the next one starts; the last stays open.
        for (int i = 0; i < assignments.size(); i++) {
            ResolvedAssignment assignment = assignments.get(i);
            LocalDate effectiveTo = i + 1 < assignments.size() ? assignments.get(i + 1).effectiveFrom().minusDays(1) : null;
            employeeAssignmentRepository.save(EmployeeAssignment.builder()
                .employee(employee)
                .organizationUnit(assignment.unit())
                .workLocation(assignment.location())
                .position(assignment.position())
                .shift(assignment.shift())
                .managerEmployee(assignment.manager())
                .employmentType(EmploymentType.FULL_TIME)
                .effectiveFrom(assignment.effectiveFrom())
                .effectiveTo(effectiveTo)
                .isPrimary(true)
                .reason(SEED_REASON)
                .createdByAccount(account)
                .createdAt(startOf(assignment.effectiveFrom()))
                .build());
        }

        seedSalaries(entry, employee, startDate);

        int attendanceDays = seedAttendanceWriter.write(employee, startDate, LocalDate.now(ZONE).minusDays(1),
            date -> overtimeApproverOn(assignments, date));
        log.info("Staff seed: '{}' created as {} with {} attendance days",
            entry.username(), employee.getEmployeeCode(), attendanceDays);
    }

    /** The direct manager of the period holding {@code date}, else the Director, as ApproverResolver decides today. */
    private Account overtimeApproverOn(List<ResolvedAssignment> assignments, LocalDate date) {
        Employee manager = assignments.stream()
            .filter(assignment -> !assignment.effectiveFrom().isAfter(date))
            .reduce((earlier, later) -> later)
            .map(ResolvedAssignment::manager)
            .orElse(null);
        if (manager != null) {
            return accountRepository.findByEmployeeId(manager.getId())
                .orElseThrow(() -> new IllegalStateException("Staff seed: manager " + manager.getEmployeeCode() + " has no account"));
        }
        return accountRoleAssignmentRepository.findAccountsHoldingRole("DIRECTOR", LocalDate.now(ZONE)).stream().findFirst()
            .orElseThrow(() -> new IllegalStateException("Staff seed: no Director to approve overtime"));
    }

    /**
     * Saves the salary history, each salary closed the day before the next. The first one must start
     * on the hire date, or payroll finds days without a salary. Entered by the Company Owner.
     */
    private void seedSalaries(StaffEntry entry, Employee employee, LocalDate hireDate) {
        List<SalaryEntry> salaries = entry.salaries();
        if (salaries == null || salaries.isEmpty()) {
            throw new IllegalStateException("Staff seed '" + entry.username() + "': at least one salary is required");
        }
        LocalDate today = LocalDate.now(ZONE);
        Account owner = accountRoleAssignmentRepository.findAccountsHoldingRole("COMPANY_OWNER", today).stream().findFirst()
            .orElseThrow(() -> new IllegalStateException("Staff seed: no Company Owner to enter salaries"));
        List<LocalDate> starts = salaries.stream().map(salary -> today.minusMonths(salary.monthsAgo())).toList();
        if (!starts.getFirst().equals(hireDate)) {
            throw new IllegalStateException("Staff seed '" + entry.username()
                + "': the first salary must start on the hire date (same monthsAgo as the first assignment)");
        }
        for (int i = 0; i < salaries.size(); i++) {
            SalaryEntry salary = salaries.get(i);
            if (i > 0 && !starts.get(i).isAfter(starts.get(i - 1))) {
                throw new IllegalStateException("Staff seed '" + entry.username()
                    + "': salaries must go from oldest to newest (monthsAgo decreasing)");
            }
            if (salary.baseSalary() == null || salary.baseSalary().signum() <= 0) {
                throw new IllegalStateException("Staff seed '" + entry.username() + "': baseSalary must be positive");
            }
            employeeSalaryHistoryRepository.save(EmployeeSalaryHistory.builder()
                .employee(employee)
                .baseSalary(salary.baseSalary())
                .effectiveFrom(starts.get(i))
                .effectiveTo(i + 1 < salaries.size() ? starts.get(i + 1).minusDays(1) : null)
                .approvedByAccount(owner)
                .reason(salary.reason())
                .createdAt(startOf(starts.get(i)))
                .build());
        }
    }

    private List<ResolvedAssignment> resolveAssignments(StaffEntry entry) {
        if (entry.assignments() == null || entry.assignments().isEmpty()) {
            throw new IllegalStateException("Staff seed '" + entry.username() + "': at least one assignment is required");
        }
        LocalDate today = LocalDate.now(ZONE);
        List<ResolvedAssignment> resolved = new ArrayList<>();
        for (AssignmentEntry item : entry.assignments()) {
            LocalDate effectiveFrom = today.minusMonths(item.monthsAgo());
            if (!resolved.isEmpty() && !effectiveFrom.isAfter(resolved.getLast().effectiveFrom())) {
                throw new IllegalStateException("Staff seed '" + entry.username()
                    + "': assignments must go from oldest to newest (monthsAgo decreasing)");
            }
            String locationCode = item.workLocation() != null ? item.workLocation() : "HO";
            String shiftCode = item.shift() != null ? item.shift() : "OFFICE_DAY";
            resolved.add(new ResolvedAssignment(
                effectiveFrom,
                organizationUnitRepository.findByCodeAndDeletedAtIsNull(item.organizationUnit())
                    .orElseThrow(() -> missing(entry, "organization unit", item.organizationUnit())),
                workLocationRepository.findByCodeAndDeletedAtIsNull(locationCode)
                    .orElseThrow(() -> missing(entry, "work location", locationCode)),
                jobPositionRepository.findByCodeAndDeletedAtIsNull(item.position())
                    .orElseThrow(() -> missing(entry, "job position", item.position())),
                workShiftRepository.findByCodeAndDeletedAtIsNull(shiftCode)
                    .orElseThrow(() -> missing(entry, "work shift", shiftCode)),
                item.manager() == null ? null : accountRepository.findByUsername(item.manager())
                    .map(Account::getEmployee)
                    .orElseThrow(() -> missing(entry, "manager", item.manager()))
            ));
        }
        return resolved;
    }

    private static Instant startOf(LocalDate date) {
        return date.atStartOfDay(ZONE).toInstant();
    }

    private static IllegalStateException missing(StaffEntry entry, String what, String code) {
        return new IllegalStateException("Staff seed '" + entry.username() + "': " + what + " '" + code + "' not found");
    }
}
