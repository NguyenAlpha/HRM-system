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
import com.htttdn.hrm.entity.EmployeePayrollProfile;
import com.htttdn.hrm.entity.EmployeeSalaryHistory;
import com.htttdn.hrm.entity.EmployeeTaxDependent;
import com.htttdn.hrm.entity.JobPosition;
import com.htttdn.hrm.entity.OrganizationUnit;
import com.htttdn.hrm.entity.WorkLocation;
import com.htttdn.hrm.entity.WorkShift;
import com.htttdn.hrm.entity.enums.AccountStatus;
import com.htttdn.hrm.entity.enums.EducationLevel;
import com.htttdn.hrm.entity.enums.EmploymentStatus;
import com.htttdn.hrm.entity.enums.EmploymentType;
import com.htttdn.hrm.entity.enums.Gender;
import com.htttdn.hrm.entity.enums.LeaveRequestStatus;
import com.htttdn.hrm.entity.enums.LeaveType;
import com.htttdn.hrm.entity.enums.RoleAssignmentRequestStatus;
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
 * <p>{@code salaries} và {@code payrollProfiles} (hồ sơ bảo hiểm/thuế) là lịch sử theo thời gian như
 * phân công: từ cũ đến mới, mục đầu bắt đầu đúng ngày vào làm, mỗi mục kết thúc ngay trước mục sau.
 * {@code dependents} là người phụ thuộc giảm trừ, không bắt đầu trước ngày vào làm. Company Owner là
 * người nhập các dữ liệu lương này.
 *
 * <p>{@code manager} là username của quản lý trực tiếp; người đó phải đã có (ví dụ {@code owner})
 * hoặc đứng trước trong file. Phần tử có username đã tồn tại được bỏ qua, nên chạy lại seed không
 * tạo trùng. Seeder chạy sau Company Owner (order 510).
 *
 * <p>{@code leaves}, {@code absences} và {@code missingPunches} là đơn nghỉ và các ngày công khác
 * thường, ghi đè lên chấm công (xem {@link SeedLeaveWriter}); {@code roleRequests} là yêu cầu cấp
 * role HR gửi cho account này (xem {@link SeedRoleRequestWriter}).
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
        ProfileEntry profile,
        Map<String, RoleScopeType> roles,
        List<AssignmentEntry> assignments,
        List<SalaryEntry> salaries,
        List<PayrollProfileEntry> payrollProfiles,
        List<DependentEntry> dependents,
        List<LeaveEntry> leaves,
        List<AbsenceEntry> absences,
        List<Integer> missingPunches,
        List<RoleRequestEntry> roleRequests
    ) {
    }

    /**
     * A leave request of {@code days} work days (default 1) from {@code workDaysAgo}; {@code part} is
     * {@code MORNING} or {@code AFTERNOON} for half a day. {@code comment} is the approver's.
     */
    record LeaveEntry(
        int workDaysAgo,
        Integer days,
        String part,
        LeaveType type,
        LeaveRequestStatus status,
        String reason,
        String comment
    ) {
    }

    /** A day the employee did not come and HR recorded as an unauthorized absence. */
    record AbsenceEntry(int workDaysAgo, String note) {
    }

    /** A role HR asked for this account; {@code note} is the Owner's decision or the cancellation reason. */
    record RoleRequestEntry(
        String role,
        RoleScopeType scope,
        String requestedBy,
        RoleAssignmentRequestStatus status,
        int daysAgo,
        int effectiveFromDaysAgo,
        Integer effectiveToDaysAgo,
        String reason,
        String note
    ) {
    }

    /**
     * One base salary, oldest first; it runs until the next one starts. A transfer with a new
     * salary uses the assignment's {@code monthsAgo}; a renegotiated salary needs no assignment.
     */
    record SalaryEntry(int monthsAgo, BigDecimal baseSalary, String reason) {
    }

    /**
     * One insurance and tax profile, oldest first, like salaries. The employee is a tax resident and
     * pays social, health and unemployment insurance on {@code insuranceSalary}; {@code wageRegion} is 1-4.
     */
    record PayrollProfileEntry(int monthsAgo, BigDecimal insuranceSalary, short wageRegion) {
    }

    /**
     * Personal details, education, contact, tax and bank details, so the profile and the education
     * report are not empty. All optional; {@code nationalId} must be unique.
     */
    record ProfileEntry(
        Gender gender,
        LocalDate dateOfBirth,
        EducationLevel highestEducationLevel,
        String major,
        String institution,
        Short graduationYear,
        String nationalId,
        String phone,
        String personalEmail,
        String address,
        String taxCode,
        String bankName,
        String bankAccountNumber,
        String bankAccountHolder
    ) {
        static final ProfileEntry EMPTY = new ProfileEntry(
            null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    /** A dependent claimed for tax from {@code monthsAgo}, no earlier than the hire date. */
    record DependentEntry(String fullName, String identifier, int monthsAgo) {
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
    private final EmployeePayrollProfileRepository employeePayrollProfileRepository;
    private final EmployeeTaxDependentRepository employeeTaxDependentRepository;
    private final RoleRepository roleRepository;
    private final AccountRoleAssignmentRepository accountRoleAssignmentRepository;
    private final OrganizationUnitRepository organizationUnitRepository;
    private final WorkLocationRepository workLocationRepository;
    private final JobPositionRepository jobPositionRepository;
    private final WorkShiftRepository workShiftRepository;
    private final SeedAttendanceWriter seedAttendanceWriter;
    private final SeedLeaveWriter seedLeaveWriter;
    private final SeedRoleRequestWriter seedRoleRequestWriter;
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
        EmployeePayrollProfileRepository employeePayrollProfileRepository,
        EmployeeTaxDependentRepository employeeTaxDependentRepository,
        RoleRepository roleRepository,
        AccountRoleAssignmentRepository accountRoleAssignmentRepository,
        OrganizationUnitRepository organizationUnitRepository,
        WorkLocationRepository workLocationRepository,
        JobPositionRepository jobPositionRepository,
        WorkShiftRepository workShiftRepository,
        SeedAttendanceWriter seedAttendanceWriter,
        SeedLeaveWriter seedLeaveWriter,
        SeedRoleRequestWriter seedRoleRequestWriter,
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
        this.employeePayrollProfileRepository = employeePayrollProfileRepository;
        this.employeeTaxDependentRepository = employeeTaxDependentRepository;
        this.roleRepository = roleRepository;
        this.accountRoleAssignmentRepository = accountRoleAssignmentRepository;
        this.organizationUnitRepository = organizationUnitRepository;
        this.workLocationRepository = workLocationRepository;
        this.jobPositionRepository = jobPositionRepository;
        this.workShiftRepository = workShiftRepository;
        this.seedAttendanceWriter = seedAttendanceWriter;
        this.seedLeaveWriter = seedLeaveWriter;
        this.seedRoleRequestWriter = seedRoleRequestWriter;
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
        ProfileEntry profile = entry.profile() != null ? entry.profile() : ProfileEntry.EMPTY;

        Employee employee = employeeRepository.save(Employee.builder()
            .employeeCode(employeeCodeGenerator.next())
            .fullName(entry.fullName())
            .gender(profile.gender())
            .dateOfBirth(profile.dateOfBirth())
            .highestEducationLevel(profile.highestEducationLevel())
            .major(profile.major())
            .institution(profile.institution())
            .graduationYear(profile.graduationYear())
            .nationalId(profile.nationalId())
            .phone(profile.phone())
            .personalEmail(profile.personalEmail())
            .address(profile.address())
            .taxCode(profile.taxCode())
            .bankName(profile.bankName())
            .bankAccountNumber(profile.bankAccountNumber())
            .bankAccountHolder(profile.bankAccountHolder())
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

        Account owner = accountRoleAssignmentRepository.findAccountsHoldingRole("COMPANY_OWNER", LocalDate.now(ZONE))
            .stream().findFirst()
            .orElseThrow(() -> new IllegalStateException("Staff seed: no Company Owner to enter salaries"));
        seedSalaries(entry, employee, owner);
        seedPayrollProfiles(entry, employee, owner);
        seedDependents(entry, employee, owner);

        int attendanceDays = seedAttendanceWriter.write(employee, startDate, LocalDate.now(ZONE).minusDays(1),
            date -> overtimeApproverOn(assignments, date));
        int leaves = seedLeaveWriter.write(entry, employee, date -> overtimeApproverOn(assignments, date), reconciler(entry));
        seedRoleRequestWriter.write(entry, account, current.unit(), current.location(), owner);
        log.info("Staff seed: '{}' created as {} with {} attendance days and {} leave requests",
            entry.username(), employee.getEmployeeCode(), attendanceDays, leaves);
    }

    /** The HR Manager who records unauthorized absences; only needed when the entry has some. */
    private Account reconciler(StaffEntry entry) {
        if (entry.absences() == null || entry.absences().isEmpty()) {
            return null;
        }
        return accountRoleAssignmentRepository.findAccountsHoldingRole("HR_MANAGER", LocalDate.now(ZONE)).stream().findFirst()
            .orElseThrow(() -> new IllegalStateException("Staff seed: no HR Manager to record absences of '" + entry.username() + "'"));
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

    /** Saves the salary history, each salary closed the day before the next. */
    private void seedSalaries(StaffEntry entry, Employee employee, Account owner) {
        List<SalaryEntry> salaries = entry.salaries();
        List<LocalDate> starts = historyStarts(entry, "salaries", salaries == null ? null
            : salaries.stream().map(SalaryEntry::monthsAgo).toList(), employee.getHireDate());
        for (int i = 0; i < salaries.size(); i++) {
            SalaryEntry salary = salaries.get(i);
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

    /** Saves the insurance and tax profiles, each closed the day before the next. */
    private void seedPayrollProfiles(StaffEntry entry, Employee employee, Account owner) {
        List<PayrollProfileEntry> profiles = entry.payrollProfiles();
        List<LocalDate> starts = historyStarts(entry, "payrollProfiles", profiles == null ? null
            : profiles.stream().map(PayrollProfileEntry::monthsAgo).toList(), employee.getHireDate());
        for (int i = 0; i < profiles.size(); i++) {
            PayrollProfileEntry profile = profiles.get(i);
            if (profile.insuranceSalary() == null || profile.insuranceSalary().signum() <= 0
                || profile.wageRegion() < 1 || profile.wageRegion() > 4) {
                throw new IllegalStateException("Staff seed '" + entry.username()
                    + "': insuranceSalary must be positive and wageRegion 1-4");
            }
            employeePayrollProfileRepository.save(EmployeePayrollProfile.builder()
                .employee(employee)
                .effectiveFrom(starts.get(i))
                .effectiveTo(i + 1 < profiles.size() ? starts.get(i + 1).minusDays(1) : null)
                .taxResident(true)
                .socialInsurance(true)
                .healthInsurance(true)
                .unemploymentInsurance(true)
                .insuranceSalary(profile.insuranceSalary())
                .wageRegion(profile.wageRegion())
                .createdByAccount(owner)
                .createdAt(startOf(starts.get(i)))
                .build());
        }
    }

    private void seedDependents(StaffEntry entry, Employee employee, Account owner) {
        if (entry.dependents() == null) {
            return;
        }
        LocalDate today = LocalDate.now(ZONE);
        for (DependentEntry dependent : entry.dependents()) {
            LocalDate effectiveFrom = today.minusMonths(dependent.monthsAgo());
            if (dependent.fullName() == null || dependent.fullName().isBlank()
                || effectiveFrom.isBefore(employee.getHireDate())) {
                throw new IllegalStateException("Staff seed '" + entry.username()
                    + "': a dependent needs a fullName and cannot start before the hire date");
            }
            employeeTaxDependentRepository.save(EmployeeTaxDependent.builder()
                .employee(employee)
                .fullName(dependent.fullName())
                .identifier(dependent.identifier())
                .effectiveFrom(effectiveFrom)
                .createdByAccount(owner)
                .createdAt(startOf(effectiveFrom))
                .build());
        }
    }

    /**
     * Start dates of a dated history given as {@code monthsAgo}, oldest first. The first must be the
     * hire date, or payroll finds worked days the history does not cover.
     */
    private List<LocalDate> historyStarts(StaffEntry entry, String field, List<Integer> monthsAgo, LocalDate hireDate) {
        if (monthsAgo == null || monthsAgo.isEmpty()) {
            throw new IllegalStateException("Staff seed '" + entry.username() + "': " + field + " needs at least one item");
        }
        LocalDate today = LocalDate.now(ZONE);
        List<LocalDate> starts = monthsAgo.stream().map(today::minusMonths).toList();
        if (!starts.getFirst().equals(hireDate)) {
            throw new IllegalStateException("Staff seed '" + entry.username() + "': the first of " + field
                + " must start on the hire date (same monthsAgo as the first assignment)");
        }
        for (int i = 1; i < starts.size(); i++) {
            if (!starts.get(i).isAfter(starts.get(i - 1))) {
                throw new IllegalStateException("Staff seed '" + entry.username() + "': " + field
                    + " must go from oldest to newest (monthsAgo decreasing)");
            }
        }
        return starts;
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
