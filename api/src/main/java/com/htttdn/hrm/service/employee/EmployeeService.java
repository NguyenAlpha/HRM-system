package com.htttdn.hrm.service.employee;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.htttdn.hrm.service.rbac.EmployeeAccessScopeService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.request.employee.AssignEmployeeRequest;
import com.htttdn.hrm.dto.request.employee.CreateEmployeeProfileRequest;
import com.htttdn.hrm.dto.request.employee.CreateEmployeeRequest;
import com.htttdn.hrm.dto.request.employee.EmployeeSearchCriteria;
import com.htttdn.hrm.dto.request.employee.SoftDeleteEmployeeRequest;
import com.htttdn.hrm.dto.request.employee.UpdateEmployeeRequest;
import com.htttdn.hrm.dto.request.employee.UpdateSelfProfileRequest;
import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.dto.response.employee.EmployeeAssignmentResponse;
import com.htttdn.hrm.dto.response.employee.EmployeeCreationResponse;
import com.htttdn.hrm.dto.response.employee.EmployeeDetailResponse;
import com.htttdn.hrm.dto.response.employee.EmployeeSummaryResponse;
import com.htttdn.hrm.dto.response.employee.SelfProfileResponse;
import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.EmployeeAssignment;
import com.htttdn.hrm.entity.enums.EmploymentStatus;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.exception.ResourceNotFoundException;
import com.htttdn.hrm.repository.AccountRepository;
import com.htttdn.hrm.repository.AttendanceRecordRepository;
import com.htttdn.hrm.repository.EmployeeAssignmentRepository;
import com.htttdn.hrm.repository.EmployeeRepository;
import com.htttdn.hrm.repository.EmployeeSalaryHistoryRepository;
import com.htttdn.hrm.repository.specification.EmployeeSpecifications;
import com.htttdn.hrm.repository.specification.SearchPatterns;
import com.htttdn.hrm.repository.LeaveRequestRepository;
import com.htttdn.hrm.repository.PayslipRepository;
import com.htttdn.hrm.security.CurrentAccountProvider;
import com.htttdn.hrm.util.Strings;

import lombok.RequiredArgsConstructor;

/**
 * Hồ sơ nhân viên: tạo, xem, danh sách, sửa, xác nhận hết thử việc, hồ sơ tự cập nhật và xóa mềm. Kết
 * thúc làm việc nằm ở {@link EmploymentLifecycleService}, phân công ở {@link EmployeeAssignmentService}.
 *
 * <p>Kiến thức nền:
 * <ul>
 *   <li>Mỗi thao tác cần một permission riêng ({@code @PreAuthorize}), rồi nhân viên đích phải nằm trong
 *       phạm vi của permission đó ({@code EmployeeAccessScopeService.requireEmployeeAccess}: toàn công ty,
 *       theo đơn vị, theo địa điểm, hoặc chỉ bản thân) — thiếu → 403.</li>
 *   <li>Nhân viên bị xóa mềm ({@code deletedAt}) coi như không tồn tại: {@code getRequired} trả 404.</li>
 *   <li>Email công việc cũng là email đăng nhập của tài khoản gắn với nhân viên, nên hai bên phải luôn
 *       khớp và không trùng (unique {@code uq_employees_work_email_lower}, {@code uq_accounts_email_lower}).</li>
 * </ul>
 */
@Service
@Transactional
@RequiredArgsConstructor
public class EmployeeService {

    private static final String EMPLOYEE_READ = "employee.read";
    private static final String EMPLOYEE_LIST_READ = "employee.list.read";
    private static final Set<String> SORTABLE_PROPERTIES = Set.of(
        "id", "employeeCode", "fullName", "hireDate", "employmentStatus"
    );
    private static final String EMPLOYEE_UPDATE = "employee.update";
    private static final String EMPLOYEE_PROBATION_CONFIRM = "employee.probation.confirm";
    private static final String EMPLOYEE_DELETE = "employee.delete";

    private final EmployeeRepository employeeRepository;
    private final EmployeeAssignmentRepository employeeAssignmentRepository;
    private final EmployeeSalaryHistoryRepository employeeSalaryHistoryRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final PayslipRepository payslipRepository;
    private final AccountRepository accountRepository;
    private final CurrentAccountProvider currentAccountProvider;
    private final EmployeeAccessScopeService employeeAccessScopeService;
    private final EmployeeAssignmentService employeeAssignmentService;
    private final EmployeeCodeGenerator employeeCodeGenerator;
    private final EmployeeMapper employeeMapper;
    private final Clock clock;

    /**
     * Tạo hồ sơ nhân viên mới (trạng thái {@code PROBATION}) cùng phân công chính đầu tiên, trong một
     * transaction — phân công lỗi thì hồ sơ cũng không được lưu.
     *
     * <p>Mã nhân viên do server sinh ({@link EmployeeCodeGenerator#next}: {@code NV} + số kế tiếp, khóa
     * advisory để hai lần tạo cùng lúc không trùng). Ngày tính thâm niên bắt đầu bằng ngày vào làm.
     *
     * @param request hồ sơ và phân công đầu tiên
     * @return hồ sơ vừa tạo và phân công đầu tiên
     * @throws BusinessException thiếu ngày sinh hoặc chưa đủ 17 tuổi (→ 400)
     * @throws ConflictException email công việc đã có nhân viên hoặc tài khoản dùng (→ 409 {@code EMAIL_TAKEN})
     */
    @PreAuthorize("hasAuthority('employee.create')")
    public EmployeeCreationResponse create(CreateEmployeeRequest request) {
        CreateEmployeeProfileRequest profile = request.employee();
        AssignEmployeeRequest assignmentRequest = request.initialAssignment();

        EmployeeBirthDateValidator.validate(profile.dateOfBirth(), true, LocalDate.now(clock));

        String workEmail = normalizeEmail(profile.workEmail());
        validateCreateUniqueness(workEmail);
        Employee employee = Employee.builder()
            .employeeCode(employeeCodeGenerator.next())
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
            .build();

        Employee savedEmployee = employeeRepository.save(employee);
        // createInitial kiểm tra ngày hiệu lực, quản lý không phải chính nhân viên, đơn vị/địa điểm/chức danh/ca
        // có thật và trong phạm vi employee.create của người tạo, rồi lưu phân công chính.
        EmployeeAssignmentResponse initialAssignment = employeeAssignmentService.createInitial(
            savedEmployee,
            assignmentRequest
        );
        return new EmployeeCreationResponse(
            employeeMapper.toDetailResponse(savedEmployee),
            initialAssignment
        );
    }

    /**
     * Hồ sơ chi tiết của một nhân viên trong phạm vi {@code employee.read}, kèm phân công hiện tại và tài
     * khoản.
     *
     * @throws com.htttdn.hrm.exception.ResourceNotFoundException không có hoặc đã xóa mềm (→ 404)
     * @throws com.htttdn.hrm.exception.ForbiddenException ngoài phạm vi (→ 403)
     */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('employee.read')")
    public EmployeeDetailResponse getById(Long id) {
        Employee employee = employeeRepository.getRequired(id);
        employeeAccessScopeService.requireEmployeeAccess(id, EMPLOYEE_READ);
        return employeeMapper.toDetailResponse(employee);
    }

    /** Hồ sơ của chính người đăng nhập; không cần employee.read vì không đọc hồ sơ người khác. */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('profile.self.read')")
    public EmployeeDetailResponse getSelf() {
        return employeeMapper.toDetailResponse(findSelf());
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('profile.self.read')")
    public SelfProfileResponse getSelfProfile() {
        return toSelfProfile(findSelf());
    }

    /**
     * The employee updates their own contact details and education; everything else stays with HR.
     *
     * <p>Ghi đè toàn bộ các trường này: trường bỏ trống → xóa giá trị cũ (null).
     *
     * @throws com.htttdn.hrm.exception.ResourceNotFoundException tài khoản không gắn nhân viên (→ 404)
     * @throws BusinessException năm tốt nghiệp ngoài khoảng 1950 tới năm nay (→ 400)
     */
    @PreAuthorize("hasAuthority('profile.self.update')")
    public SelfProfileResponse updateSelfProfile(UpdateSelfProfileRequest request) {
        Employee employee = findSelf();
        int currentYear = LocalDate.now(clock).getYear();
        if (request.graduationYear() != null
            && (request.graduationYear() < 1950 || request.graduationYear() > currentYear)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                "graduationYear must be between 1950 and " + currentYear, "graduationYear");
        }
        employee.setPhone(Strings.trimToNull(request.phone()));
        employee.setPersonalEmail(normalizeEmail(request.personalEmail()));
        employee.setAddress(Strings.trimToNull(request.address()));
        employee.setHighestEducationLevel(request.highestEducationLevel());
        employee.setMajor(Strings.trimToNull(request.major()));
        employee.setInstitution(Strings.trimToNull(request.institution()));
        employee.setGraduationYear(request.graduationYear());
        return toSelfProfile(employee);
    }

    /**
     * Nhân viên gắn với tài khoản đang đăng nhập. Tài khoản không gắn nhân viên (VD admin hệ thống) hoặc
     * nhân viên đã xóa mềm → 404 {@code EMPLOYEE_NOT_FOUND}.
     */
    private Employee findSelf() {
        Long accountId = currentAccountProvider.accountId();
        return accountRepository.findById(accountId)
            .map(Account::getEmployee)
            .filter(linked -> linked.getDeletedAt() == null)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.EMPLOYEE_NOT_FOUND, "No employee is linked to account " + accountId
            ));
    }

    private static SelfProfileResponse toSelfProfile(Employee employee) {
        return new SelfProfileResponse(employee.getPhone(), employee.getPersonalEmail(), employee.getAddress(),
            employee.getHighestEducationLevel(), employee.getMajor(), employee.getInstitution(), employee.getGraduationYear());
    }

    /**
     * Danh sách nhân viên trong phạm vi {@code employee.list.read}, lọc theo từ khóa (mã, tên, email),
     * trạng thái, đơn vị/chức danh của phân công hiện tại; phân trang.
     *
     * @param criteria bộ lọc; trường null/rỗng → không lọc theo trường đó
     * @param pageable trang và cách sắp xếp, chỉ cho các cột trong {@code SORTABLE_PROPERTIES}
     * @throws BusinessException sắp xếp theo cột không cho phép (→ 400, thay vì 500 từ JPA)
     */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('employee.list.read')")
    public Page<EmployeeSummaryResponse> list(EmployeeSearchCriteria criteria, Pageable pageable) {
        Specification<Employee> specification = employeeAccessScopeService.accessibleEmployees(EMPLOYEE_LIST_READ);
        for (Specification<Employee> filter : Arrays.asList(
            EmployeeSpecifications.matchesKeyword(criteria.keyword()),
            EmployeeSpecifications.hasEmploymentStatus(criteria.employmentStatus()),
            EmployeeSpecifications.currentlyAssignedTo(criteria.organizationUnitId(), criteria.positionId(), LocalDate.now(clock))
        )) {
            if (filter != null) {
                specification = specification.and(filter);
            }
        }
        Page<Employee> employees = employeeRepository.findAll(
            specification,
            SearchPatterns.requireSortable(pageable, SORTABLE_PROPERTIES)
        );
        // Tài khoản và phân công của cả trang lấy bằng 2 query (không query từng dòng → tránh N+1).
        Map<Long, Account> accountsByEmployeeId = findAccountsByEmployeeId(employees.getContent());
        Map<Long, EmployeeAssignment> assignmentsByEmployeeId = findCurrentAssignmentsByEmployeeId(
            employees.getContent()
        );
        return employees.map(employee -> employeeMapper.toSummaryResponse(
            employee,
            assignmentsByEmployeeId.get(employee.getId()),
            accountsByEmployeeId.get(employee.getId())
        ));
    }

    /**
     * HR sửa hồ sơ nhân viên: thông tin cá nhân, học vấn, liên hệ và email công việc. Trạng thái, ngày
     * vào làm và phân công không sửa ở đây.
     *
     * @throws com.htttdn.hrm.exception.ResourceNotFoundException không có hoặc đã xóa mềm (→ 404)
     * @throws com.htttdn.hrm.exception.ForbiddenException ngoài phạm vi (→ 403)
     * @throws BusinessException nhân viên chưa đủ 17 tuổi (→ 400)
     * @throws ConflictException email bị trùng hoặc xóa email khi đã có tài khoản (→ 409)
     */
    @PreAuthorize("hasAuthority('employee.update')")
    public EmployeeDetailResponse update(Long id, UpdateEmployeeRequest request) {
        Employee employee = employeeRepository.getRequired(id);
        employeeAccessScopeService.requireEmployeeAccess(id, EMPLOYEE_UPDATE);

        EmployeeBirthDateValidator.validate(request.dateOfBirth(), false, LocalDate.now(clock));

        Instant now = Instant.now(clock);
        updateWorkEmail(employee, request.workEmail(), now);

        employee.setFullName(request.fullName());
        employee.setDateOfBirth(request.dateOfBirth());
        employee.setGender(request.gender());
        employee.setHighestEducationLevel(request.highestEducationLevel());
        employee.setMajor(request.major());
        employee.setInstitution(request.institution());
        employee.setGraduationYear(request.graduationYear());
        employee.setPhone(request.phone());
        return employeeMapper.toDetailResponse(employee);
    }

    /**
     * Xác nhận hết thử việc: {@code PROBATION} → {@code ACTIVE}.
     *
     * @throws ConflictException nhân viên không ở trạng thái thử việc (→ 409
     *         {@code EMPLOYMENT_STATUS_TRANSITION_NOT_ALLOWED})
     */
    @PreAuthorize("hasAuthority('employee.probation.confirm')")
    public EmployeeDetailResponse confirmEmployment(Long id) {
        Employee employee = employeeRepository.getRequired(id);
        employeeAccessScopeService.requireEmployeeAccess(id, EMPLOYEE_PROBATION_CONFIRM);
        if (employee.getEmploymentStatus() != EmploymentStatus.PROBATION) {
            throw new ConflictException(
                ErrorCode.EMPLOYMENT_STATUS_TRANSITION_NOT_ALLOWED,
                "Only employees in PROBATION status can be confirmed"
            );
        }

        employee.setEmploymentStatus(EmploymentStatus.ACTIVE);
        return employeeMapper.toDetailResponse(employee);
    }

    /**
     * Đổi email công việc và đồng bộ sang email đăng nhập của tài khoản (nếu có), sau khi kiểm tra không
     * trùng ở cả bảng nhân viên lẫn bảng tài khoản. Kiểm tra trước để trả lỗi có {@code field} cho form;
     * unique index vẫn là chốt chặn cuối khi hai request đến cùng lúc.
     */
    private void updateWorkEmail(Employee employee, String requestedWorkEmail, Instant now) {
        String workEmail = normalizeEmail(requestedWorkEmail);
        if (workEmail != null
            && employeeRepository.existsByWorkEmailIgnoreCaseAndIdNot(workEmail, employee.getId())) {
            throw new ConflictException(ErrorCode.CONFLICT, "Work email is already taken", "workEmail");
        }

        // Tài khoản cần email để đăng nhập/nhận thư kích hoạt → không cho xóa email khi đã có tài khoản.
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
        }
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Xóa mềm hồ sơ tạo nhầm: chỉ được khi nhân viên chưa có dữ liệu nghiệp vụ nào. Người đã làm việc thì
     * phải kết thúc làm việc ({@link EmploymentLifecycleService#endEmployment}) để giữ lịch sử.
     *
     * @throws com.htttdn.hrm.exception.ResourceNotFoundException không có hoặc đã xóa mềm (→ 404)
     * @throws com.htttdn.hrm.exception.ForbiddenException ngoài phạm vi (→ 403)
     * @throws ConflictException đã có tài khoản, lương, đơn nghỉ, chấm công, phiếu lương hoặc hơn một
     *         phân công (→ 409)
     */
    @PreAuthorize("hasAuthority('employee.delete')")
    public void softDelete(Long id, SoftDeleteEmployeeRequest request) {
        Employee employee = employeeRepository.getRequired(id);
        employeeAccessScopeService.requireEmployeeAccess(id, EMPLOYEE_DELETE);
        ensureEmployeeHasNoBusinessHistory(id);

        employee.setDeletedAt(Instant.now(clock));
        employee.setDeletedByAccount(currentAccountProvider.account());
        employee.setDeletionReason(request.deletionReason());
    }

    /**
     * Email công việc mới không được trùng nhân viên nào (kể cả đã xóa mềm — unique index phủ mọi dòng)
     * hay tài khoản nào, vì sau này nó thành email đăng nhập.
     */
    private void validateCreateUniqueness(String workEmail) {
        if (workEmail != null && (employeeRepository.existsByWorkEmailIgnoreCase(workEmail)
            || accountRepository.existsByEmailIgnoreCase(workEmail))) {
            throw new ConflictException(ErrorCode.EMAIL_TAKEN, "Work email is already taken", "employee.workEmail");
        }
    }

    private void ensureEmployeeHasNoBusinessHistory(Long employeeId) {
        // The create endpoint always writes one assignment, so a single row proves nothing;
        // a second one means somebody deliberately transferred or promoted the employee.
        boolean hasHistory = accountRepository.existsByEmployeeId(employeeId)
            || employeeAssignmentRepository.countByEmployeeId(employeeId) > 1
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

    /** Tài khoản (chưa xóa) của các nhân viên trong trang, gom thành {@code Map<employeeId, Account>}. */
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

    /** Phân công chính đang hiệu lực hôm nay của các nhân viên trong trang, gom thành {@code Map<employeeId, phân công>}. */
    private Map<Long, EmployeeAssignment> findCurrentAssignmentsByEmployeeId(List<Employee> employees) {
        if (employees.isEmpty()) {
            return Map.of();
        }

        List<Long> employeeIds = employees.stream().map(Employee::getId).toList();
        Map<Long, EmployeeAssignment> assignmentsByEmployeeId = new HashMap<>();
        employeeAssignmentRepository.findCurrentPrimaryByEmployeeIds(employeeIds, LocalDate.now(clock))
            .forEach(assignment -> assignmentsByEmployeeId.put(assignment.getEmployee().getId(), assignment));
        return assignmentsByEmployeeId;
    }
}
