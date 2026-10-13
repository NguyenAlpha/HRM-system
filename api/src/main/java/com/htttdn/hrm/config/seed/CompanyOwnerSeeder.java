package com.htttdn.hrm.config.seed;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.AccountRoleAssignment;
import com.htttdn.hrm.entity.AttendanceRecord;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.EmployeeAssignment;
import com.htttdn.hrm.entity.Role;
import com.htttdn.hrm.entity.enums.AccountStatus;
import com.htttdn.hrm.entity.enums.AttendanceStatus;
import com.htttdn.hrm.entity.enums.EmploymentStatus;
import com.htttdn.hrm.entity.enums.EmploymentType;
import com.htttdn.hrm.entity.enums.RoleScopeType;
import com.htttdn.hrm.repository.AccountRepository;
import com.htttdn.hrm.repository.AccountRoleAssignmentRepository;
import com.htttdn.hrm.repository.AttendanceRecordRepository;
import com.htttdn.hrm.repository.EmployeeAssignmentRepository;
import com.htttdn.hrm.repository.EmployeeRepository;
import com.htttdn.hrm.repository.JobPositionRepository;
import com.htttdn.hrm.repository.OrganizationUnitRepository;
import com.htttdn.hrm.repository.RoleRepository;
import com.htttdn.hrm.repository.WorkLocationRepository;
import com.htttdn.hrm.repository.WorkShiftRepository;
import com.htttdn.hrm.service.AttendanceCalendarService;
import com.htttdn.hrm.service.EmployeeCodeGenerator;

/**
 * Khởi tạo Company Owner mẫu: nhân viên, account đã kích hoạt, role {@code COMPANY_OWNER},
 * {@code DIRECTOR} và {@code EMPLOYEE}, cùng phân công chính tại Ban giám đốc / Trụ sở chính.
 *
 * <p>Dữ liệu giống kết quả của luồng bootstrap Company Owner trong Admin Console, nhưng account
 * được kích hoạt sẵn với mật khẩu cố định để dùng trong môi trường phát triển. Ngày tạo, ngày vào
 * làm, ngày cấp role và ngày bắt đầu phân công đều là 2 năm trước ngày chạy seed.
 *
 * <p>Owner có sẵn chấm công {@code PRESENT} cho mọi ngày làm việc từ ngày vào làm đến hôm qua:
 * phần lớn đúng giờ, cứ 10 ngày công có một ngày đi muộn 30 phút và một ngày về sớm 30 phút.
 * Từ hôm nay trở đi do job chuẩn bị chấm công tạo như mọi nhân viên khác.
 *
 * <p>Seeder chạy sau các seeder doanh nghiệp và RBAC (order 500). Nếu đã có Company Owner đang
 * hiệu lực thì bỏ qua để không vi phạm ràng buộc chỉ một Company Owner.
 */
@Component
@Order(500)
public class CompanyOwnerSeeder implements ApplicationRunner {

    private static final String USERNAME = "owner";
    private static final String PASSWORD = "password";
    private static final String EMAIL = "owner@hrm.local";
    private static final String FULL_NAME = "Nguyễn Văn An";
    private static final String COMPANY_OWNER_ROLE = "COMPANY_OWNER";
    private static final String DIRECTOR_ROLE = "DIRECTOR";
    private static final String EMPLOYEE_ROLE = "EMPLOYEE";
    private static final String SEED_REASON = "Seeded company owner";
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int LATE_OR_EARLY_MINUTES = 30;

    private static final Logger log = LoggerFactory.getLogger(CompanyOwnerSeeder.class);

    private final AccountRepository accountRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeAssignmentRepository employeeAssignmentRepository;
    private final RoleRepository roleRepository;
    private final AccountRoleAssignmentRepository accountRoleAssignmentRepository;
    private final OrganizationUnitRepository organizationUnitRepository;
    private final WorkLocationRepository workLocationRepository;
    private final JobPositionRepository jobPositionRepository;
    private final WorkShiftRepository workShiftRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final AttendanceCalendarService attendanceCalendarService;
    private final EmployeeCodeGenerator employeeCodeGenerator;
    private final PasswordEncoder passwordEncoder;
    private final boolean enabled;

    public CompanyOwnerSeeder(
        AccountRepository accountRepository,
        EmployeeRepository employeeRepository,
        EmployeeAssignmentRepository employeeAssignmentRepository,
        RoleRepository roleRepository,
        AccountRoleAssignmentRepository accountRoleAssignmentRepository,
        OrganizationUnitRepository organizationUnitRepository,
        WorkLocationRepository workLocationRepository,
        JobPositionRepository jobPositionRepository,
        WorkShiftRepository workShiftRepository,
        AttendanceRecordRepository attendanceRecordRepository,
        AttendanceCalendarService attendanceCalendarService,
        EmployeeCodeGenerator employeeCodeGenerator,
        PasswordEncoder passwordEncoder,
        @Value("${owner.seed.enabled:true}") boolean enabled
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
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.attendanceCalendarService = attendanceCalendarService;
        this.employeeCodeGenerator = employeeCodeGenerator;
        this.passwordEncoder = passwordEncoder;
        this.enabled = enabled;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }

        Role ownerRole = findRequiredRole(COMPANY_OWNER_ROLE);
        Role directorRole = findRequiredRole(DIRECTOR_ROLE);
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

        ZonedDateTime seededAt = ZonedDateTime.now().minusYears(2);
        Instant createdAt = seededAt.toInstant();
        LocalDate startDate = seededAt.toLocalDate();

        Employee employee = employeeRepository.save(Employee.builder()
            .employeeCode(employeeCodeGenerator.forDirector())
            .fullName(FULL_NAME)
            .workEmail(EMAIL)
            .hireDate(startDate)
            .seniorityStartDate(startDate)
            .employmentStatus(EmploymentStatus.ACTIVE)
            .createdAt(createdAt)
            .updatedAt(createdAt)
            .build());

        Account account = accountRepository.save(Account.builder()
            .employee(employee)
            .username(USERNAME)
            .email(EMAIL)
            .passwordHash(passwordEncoder.encode(PASSWORD))
            .status(AccountStatus.ACTIVE)
            .failedLoginCount(0)
            .createdAt(createdAt)
            .updatedAt(createdAt)
            .build());

        assignRole(account, ownerRole, RoleScopeType.COMPANY, startDate, createdAt);
        assignRole(account, directorRole, RoleScopeType.COMPANY, startDate, createdAt);
        assignRole(account, employeeRole, RoleScopeType.SELF, startDate, createdAt);
        createPrimaryAssignment(employee, account, startDate, createdAt);
        int attendanceDays = createAttendance(employee, startDate, LocalDate.now(ZONE).minusDays(1));

        log.info("Company owner seed completed: '{}' created as {} with {} attendance days",
            USERNAME, employee.getEmployeeCode(), attendanceDays);
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
        accountRoleAssignmentRepository.save(AccountRoleAssignment.builder()
            .account(account)
            .role(role)
            .scopeType(scopeType)
            .effectiveFrom(effectiveFrom)
            .grantedByAccount(account)
            .reason(SEED_REASON)
            .createdAt(createdAt)
            .build());
    }

    private void createPrimaryAssignment(
        Employee employee,
        Account createdBy,
        LocalDate effectiveFrom,
        Instant createdAt
    ) {
        employeeAssignmentRepository.save(EmployeeAssignment.builder()
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
            .createdAt(createdAt)
            .build());
    }

    /**
     * Minutes follow {@code AttendanceService.recordPunches}: late minutes count after the shift's
     * grace period, and 30 minutes off either end of the shift stays clear of the lunch break.
     */
    private int createAttendance(Employee employee, LocalDate from, LocalDate to) {
        List<AttendanceCalendarService.ScheduledDay> days = attendanceCalendarService.scheduleFor(employee.getId(), from, to);
        for (int i = 0; i < days.size(); i++) {
            AttendanceCalendarService.ScheduledDay day = days.get(i);
            int lateBy = i % 10 == 3 ? LATE_OR_EARLY_MINUTES : 0;
            int earlyBy = i % 10 == 7 ? LATE_OR_EARLY_MINUTES : 0;
            int workedMinutes = day.minutes() - lateBy - earlyBy;
            Instant recordedAt = day.endAt();
            attendanceRecordRepository.save(AttendanceRecord.builder()
                .employee(employee)
                .workDate(day.workDate())
                .shift(day.shift())
                .scheduledStartAt(day.startAt())
                .scheduledEndAt(day.endAt())
                .scheduledMinutes(day.minutes())
                .checkInAt(day.startAt().plus(Duration.ofMinutes(lateBy)))
                .checkOutAt(day.endAt().minus(Duration.ofMinutes(earlyBy)))
                .workedMinutes(workedMinutes)
                .payableMinutes(workedMinutes)
                .lateMinutes(Math.max(0, lateBy - day.shift().getGraceLateMinutes()))
                .earlyLeaveMinutes(earlyBy)
                .overtimeMinutes(0)
                .overtimeMultiplier(BigDecimal.ONE)
                .status(AttendanceStatus.PRESENT)
                .createdAt(recordedAt)
                .updatedAt(recordedAt)
                .build());
        }
        return days.size();
    }
}
