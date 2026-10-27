package com.htttdn.hrm.service.rbac;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.EmployeeAssignment;
import com.htttdn.hrm.entity.OrganizationUnit;
import com.htttdn.hrm.entity.WorkLocation;
import com.htttdn.hrm.entity.enums.RoleScopeType;
import com.htttdn.hrm.exception.ForbiddenException;
import com.htttdn.hrm.repository.AccountRepository;
import com.htttdn.hrm.repository.EmployeeRepository;
import com.htttdn.hrm.repository.OrganizationUnitRepository;
import com.htttdn.hrm.repository.WorkLocationRepository;
import com.htttdn.hrm.security.CurrentAccountProvider;
import com.htttdn.hrm.service.rbac.AccountAuthorizationService.AuthorizationScope;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

import lombok.RequiredArgsConstructor;

/**
 * Lớp kiểm tra thứ hai sau {@code @PreAuthorize}: permission đã có, nhưng có áp dụng cho <i>dữ liệu này</i>
 * không.
 *
 * <p>Mỗi lần gán role mang một phạm vi: {@code SELF} (bản thân), {@code ORG_UNIT} (đơn vị và mọi đơn vị
 * con), {@code LOCATION} (địa điểm và mọi địa điểm con) hoặc {@code COMPANY} (toàn công ty). Phạm vi tính
 * theo từng permission: {@link #resolve} lấy phạm vi của các lần gán có permission đó
 * ({@link AccountAuthorizationService#getScopes}) rồi hợp (OR) lại thành một {@link EmployeeAccessScope}.
 * Một nhân viên thuộc đơn vị/địa điểm nào là theo <b>phân công chính</b> ({@code isPrimary}) đang hiệu lực.
 *
 * <p>Các service nghiệp vụ dùng một trong các dạng:
 * <ul>
 *   <li>{@link #requireCompanyWide}: thao tác chung cả công ty (tạo kỳ lương, ngày lễ…)</li>
 *   <li>{@link #requireEmployeeAccess}: thao tác trên một nhân viên</li>
 *   <li>{@link #requireDestinationAccess}: chọn đơn vị/địa điểm đích (phân công, gán role)</li>
 *   <li>{@link #accessibleEmployees}: lọc danh sách ngay trong query (phân trang đúng)</li>
 * </ul>
 * Lọc ở tầng ứng dụng thay vì Row-Level Security của PostgreSQL: xem ADR
 * {@code docs/adr/0003-data-scope-in-application.md}. Hệ quả: query nào quên ghép phạm vi sẽ lộ dữ liệu.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class EmployeeAccessScopeService {

    private final AccountAuthorizationService accountAuthorizationService;
    private final CurrentAccountProvider currentAccountProvider;
    private final AccountRepository accountRepository;
    private final EmployeeRepository employeeRepository;
    private final OrganizationUnitRepository organizationUnitRepository;
    private final WorkLocationRepository workLocationRepository;
    private final Clock clock;

    /**
     * Phạm vi của người đang đăng nhập (lấy {@code accountId} từ JWT) cho một permission.
     *
     * @param permissionCode mã permission đang xét, VD {@code employee.read}
     * @return phạm vi đã hợp; "rỗng" (không company, không self, không đơn vị/địa điểm) nếu không có
     *         permission ở lần gán nào → {@link EmployeeAccessScope#toSpecification} không khớp ai
     */
    public EmployeeAccessScope resolve(String permissionCode) {
        return resolve(currentAccountProvider.accountId(), permissionCode);
    }

    /**
     * Tài khoản bất kỳ (không nhất thiết là người đang đăng nhập) có thấy nhân viên này với permission
     * này không, xét theo phân công hôm nay.
     *
     * <p>Dùng khi hệ thống tự chọn người nhận, VD {@code NotificationService} chỉ gửi thông báo cho người
     * quản lý có phạm vi chứa nhân viên, và seeder chọn HR duyệt đơn.
     *
     * @param accountId      tài khoản cần xét
     * @param employeeId     nhân viên cần xem
     * @param permissionCode mã permission
     * @return {@code true} nếu nhân viên chưa xóa mềm và nằm trong phạm vi
     */
    public boolean canAccountAccessEmployee(Long accountId, Long employeeId, String permissionCode) {
        EmployeeAccessScope scope = resolve(accountId, permissionCode);
        return employeeRepository.exists(notDeleted()
            .and(scope.toSpecification(LocalDate.now(clock)))
            .and(hasId(employeeId)));
    }

    /**
     * Hợp mọi phạm vi của các lần gán có permission thành một {@link EmployeeAccessScope}.
     *
     * <p>Các bước: lấy phạm vi từng lần gán ({@link AccountAuthorizationService#getScopes}, đọc database)
     * → có {@code COMPANY} là {@code companyWide} → có {@code SELF} thì lấy id nhân viên gắn với tài khoản
     * → gộp đơn vị/địa điểm kèm toàn bộ cấp con.
     */
    private EmployeeAccessScope resolve(Long accountId, String permissionCode) {
        List<AuthorizationScope> assignedScopes = accountAuthorizationService.getScopes(accountId, permissionCode);

        boolean companyWide = assignedScopes.stream()
            .anyMatch(scope -> scope.scopeType() == RoleScopeType.COMPANY);
        // Tài khoản không gắn nhân viên (VD tài khoản hệ thống) → null: phạm vi SELF không khớp ai.
        Long selfEmployeeId = assignedScopes.stream()
            .anyMatch(scope -> scope.scopeType() == RoleScopeType.SELF)
                ? accountRepository.findById(accountId)
                    .map(Account::getEmployee)
                    .map(Employee::getId)
                    .orElse(null)
                : null;

        // Mỗi phạm vi ORG_UNIT/LOCATION mở rộng xuống mọi cấp con, rồi gộp chung (Set bỏ trùng khi hai lần gán
        // chồng lên cùng nhánh cây). Mỗi nút cây tốn 1 query, chấp nhận được vì cây tổ chức nhỏ.
        Set<Long> organizationUnitIds = new LinkedHashSet<>();
        Set<Long> workLocationIds = new LinkedHashSet<>();
        for (AuthorizationScope scope : assignedScopes) {
            if (scope.organizationUnitId() != null) {
                organizationUnitIds.addAll(descendantOrganizationUnitIds(scope.organizationUnitId()));
            }
            if (scope.workLocationId() != null) {
                workLocationIds.addAll(descendantWorkLocationIds(scope.workLocationId()));
            }
        }

        return new EmployeeAccessScope(companyWide, selfEmployeeId, organizationUnitIds, workLocationIds);
    }

    /**
     * Chặn thao tác không gắn với nhân viên/đơn vị cụ thể nào (tạo kỳ lương, khai báo ngày lễ, xuất báo cáo
     * toàn công ty…) nếu người đang đăng nhập không có permission ở phạm vi {@code COMPANY}.
     *
     * <p>Đi kèm {@code @PreAuthorize("hasAuthority(...)")} trên method gọi: annotation chỉ biết có permission,
     * method này kiểm tra phạm vi. Ví dụ kế toán chỉ có {@code payroll.calculate} ở phạm vi một đơn vị qua
     * được annotation nhưng bị chặn ở đây.
     *
     * @param permissionCode mã permission cần có ở phạm vi toàn công ty
     * @throws ForbiddenException không có lần gán {@code COMPANY} nào chứa permission (→ 403 {@code FORBIDDEN})
     */
    public void requireCompanyWide(String permissionCode) {
        if (!resolve(permissionCode).companyWide()) {
            throw new ForbiddenException(
                ErrorCode.FORBIDDEN,
                "This operation requires COMPANY scope for " + permissionCode
            );
        }
    }

    /**
     * Chặn thao tác trên một nhân viên nằm ngoài phạm vi của người đang đăng nhập (xét theo phân công hôm nay).
     *
     * <p>Kiểm tra bằng một query {@code EXISTS} với cùng {@link Specification} dùng cho danh sách, nên chi tiết
     * và danh sách luôn nhất quán.
     *
     * @param employeeId     nhân viên cần thao tác
     * @param permissionCode mã permission của thao tác
     * @throws ForbiddenException nhân viên ngoài phạm vi, đã xóa mềm hoặc không tồn tại (→ 403 {@code FORBIDDEN};
     *         nhân viên không tồn tại cũng là 403, không phải 404)
     */
    public void requireEmployeeAccess(Long employeeId, String permissionCode) {
        EmployeeAccessScope scope = resolve(permissionCode);
        Specification<Employee> specification = notDeleted()
            .and(scope.toSpecification(LocalDate.now(clock)))
            .and(hasId(employeeId));
        if (!employeeRepository.exists(specification)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN, "Employee is outside the assigned scope");
        }
    }

    /**
     * Chặn việc đưa người/quyền tới một đơn vị hoặc địa điểm ngoài phạm vi: phân công nhân viên sang đơn vị
     * khác ({@code EmployeeAssignmentService}) hay gán role có phạm vi đơn vị/địa điểm
     * ({@link RoleAssignmentRequestService}).
     *
     * <p>Đạt nếu phạm vi là {@code COMPANY}, hoặc đơn vị <i>hoặc</i> địa điểm đích nằm trong phạm vi
     * (kể cả cấp con). Phạm vi {@code SELF} không cho đích nào. Không có đích (cả hai {@code null}, VD gán role
     * phạm vi {@code SELF}/{@code COMPANY}) thì chỉ phạm vi {@code COMPANY} mới đạt.
     *
     * @param organizationUnitId đơn vị đích, {@code null} nếu không có
     * @param workLocationId     địa điểm đích, {@code null} nếu không có
     * @param permissionCode     mã permission của thao tác
     * @throws ForbiddenException đích ngoài phạm vi (→ 403 {@code FORBIDDEN})
     */
    public void requireDestinationAccess(Long organizationUnitId, Long workLocationId, String permissionCode) {
        if (!resolve(permissionCode).allowsDestination(organizationUnitId, workLocationId)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN, "Assignment destination is outside the assigned scope");
        }
    }

    /**
     * Điều kiện "nhân viên chưa xóa mềm và trong phạm vi hôm nay" để ghép (AND) vào query danh sách/báo cáo.
     *
     * <p>Lọc ngay trong SQL thay vì lọc sau khi đọc, để phân trang và tổng số bản ghi đúng.
     *
     * @param permissionCode mã permission của màn hình/báo cáo
     * @return {@link Specification} trên {@link Employee}; không khớp ai nếu không có permission
     */
    public Specification<Employee> accessibleEmployees(String permissionCode) {
        return notDeleted().and(resolve(permissionCode).toSpecification(LocalDate.now(clock)));
    }

    /**
     * Ids of the given employees that were in the caller's scope on their last day up to {@code date}:
     * the termination date for someone who left earlier, the hire date for someone not yet started.
     * A scope by unit or location follows assignments, which close when an employee leaves, so
     * reports that cover past periods check each person on a day they were still assigned.
     */
    public Set<Long> visibleOnLastDay(String permissionCode, Collection<Employee> employees, LocalDate date) {
        EmployeeAccessScope scope = resolve(permissionCode);
        // Toàn công ty thấy hết, không cần query.
        if (scope.companyWide()) {
            return employees.stream().map(Employee::getId).collect(Collectors.toSet());
        }
        // Ngày cuối còn làm tính đến date (không sớm hơn ngày vào làm) → nhóm theo ngày đó.
        Map<LocalDate, List<Long>> idsByDay = employees.stream().collect(Collectors.groupingBy(
            employee -> employee.employedFrom(employee.employedTo(date)),
            Collectors.mapping(Employee::getId, Collectors.toList())));
        Set<Long> visible = new LinkedHashSet<>();
        // 1 query cho mỗi ngày khác nhau (không phải mỗi nhân viên): phần lớn nhân viên còn làm chung một ngày.
        idsByDay.forEach((day, ids) -> employeeRepository.findAll(notDeleted()
                .and(scope.toSpecification(day))
                .and((root, query, builder) -> root.get("id").in(ids)))
            .forEach(employee -> visible.add(employee.getId())));
        return visible;
    }

    /**
     * Id của đơn vị gốc và mọi đơn vị con cháu chưa xóa mềm, duyệt theo chiều rộng.
     *
     * <p>{@code result.add} trả {@code false} khi đã gặp id → bỏ qua, nên không lặp vô hạn nếu dữ liệu cây
     * lỡ có vòng. Đơn vị đã tắt ({@code isActive = false}) vẫn được tính vì chỉ lọc {@code deletedAt}.
     */
    private Set<Long> descendantOrganizationUnitIds(Long rootId) {
        Set<Long> result = new LinkedHashSet<>();
        ArrayDeque<Long> pending = new ArrayDeque<>();
        pending.add(rootId);
        while (!pending.isEmpty()) {
            Long currentId = pending.removeFirst();
            if (!result.add(currentId)) {
                continue;
            }
            organizationUnitRepository.findByParentUnitIdAndDeletedAtIsNull(currentId).stream()
                .map(OrganizationUnit::getId)
                .forEach(pending::addLast);
        }
        return result;
    }

    /** Như {@link #descendantOrganizationUnitIds}, cho cây địa điểm làm việc. */
    private Set<Long> descendantWorkLocationIds(Long rootId) {
        Set<Long> result = new LinkedHashSet<>();
        ArrayDeque<Long> pending = new ArrayDeque<>();
        pending.add(rootId);
        while (!pending.isEmpty()) {
            Long currentId = pending.removeFirst();
            if (!result.add(currentId)) {
                continue;
            }
            workLocationRepository.findByParentLocationIdAndDeletedAtIsNull(currentId).stream()
                .map(WorkLocation::getId)
                .forEach(pending::addLast);
        }
        return result;
    }

    private Specification<Employee> notDeleted() {
        return (root, query, criteriaBuilder) -> criteriaBuilder.isNull(root.get("deletedAt"));
    }

    private Specification<Employee> hasId(Long employeeId) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("id"), employeeId);
    }

    /**
     * Phạm vi đã hợp của một người cho một permission.
     *
     * @param companyWide         có lần gán {@code COMPANY} → bỏ qua mọi giới hạn khác
     * @param selfEmployeeId      nhân viên của chính tài khoản khi có lần gán {@code SELF}, ngược lại {@code null}
     * @param organizationUnitIds đơn vị được xem, đã gồm mọi cấp con
     * @param workLocationIds     địa điểm được xem, đã gồm mọi cấp con
     */
    public record EmployeeAccessScope(
        boolean companyWide,
        Long selfEmployeeId,
        Set<Long> organizationUnitIds,
        Set<Long> workLocationIds
    ) {
        public EmployeeAccessScope {
            organizationUnitIds = Set.copyOf(organizationUnitIds);
            workLocationIds = Set.copyOf(workLocationIds);
        }

        /** Đơn vị hoặc địa điểm đích có nằm trong phạm vi không (xem {@link #requireDestinationAccess}). */
        public boolean allowsDestination(Long organizationUnitId, Long workLocationId) {
            // Kiểm tra null trước: contains(null) trên set bất biến (Set.copyOf) ném NullPointerException.
            return companyWide
                || organizationUnitId != null && organizationUnitIds.contains(organizationUnitId)
                || workLocationId != null && workLocationIds.contains(workLocationId);
        }

        /**
         * Đổi phạm vi thành điều kiện SQL trên {@link Employee}, xét phân công tại {@code date}.
         *
         * <p>Dạng SQL sinh ra: {@code id = :self OR EXISTS (phân công chính hiệu lực tại date thuộc
         * đơn vị trong :units hoặc địa điểm trong :locations)}. Nhân viên chưa có phân công chính tại
         * {@code date} chỉ khớp qua nhánh {@code SELF}.
         *
         * @param date ngày xét phân công: hôm nay cho thao tác thường, ngày trong quá khứ cho báo cáo
         * @return luôn đúng khi {@code companyWide}; luôn sai khi phạm vi rỗng
         */
        public Specification<Employee> toSpecification(LocalDate date) {
            return (root, query, criteriaBuilder) -> {
                // conjunction() = điều kiện luôn đúng (WHERE 1=1).
                if (companyWide) {
                    return criteriaBuilder.conjunction();
                }

                List<Predicate> predicates = new java.util.ArrayList<>();
                if (selfEmployeeId != null) {
                    predicates.add(criteriaBuilder.equal(root.get("id"), selfEmployeeId));
                }
                if (!organizationUnitIds.isEmpty() || !workLocationIds.isEmpty()) {
                    // Subquery EXISTS trên employee_assignments: chỉ phân công chính (isPrimary), hiệu lực tại
                    // date, thuộc đơn vị hoặc địa điểm trong phạm vi.
                    Subquery<Long> assignmentQuery = query.subquery(Long.class);
                    Root<EmployeeAssignment> assignment = assignmentQuery.from(EmployeeAssignment.class);
                    List<Predicate> assignmentPredicates = new java.util.ArrayList<>();
                    assignmentPredicates.add(criteriaBuilder.equal(
                        assignment.get("employee").get("id"), root.get("id")
                    ));
                    assignmentPredicates.add(criteriaBuilder.isTrue(assignment.get("isPrimary")));
                    assignmentPredicates.add(criteriaBuilder.lessThanOrEqualTo(
                        assignment.get("effectiveFrom"), date
                    ));
                    assignmentPredicates.add(criteriaBuilder.or(
                        criteriaBuilder.isNull(assignment.get("effectiveTo")),
                        criteriaBuilder.greaterThanOrEqualTo(assignment.get("effectiveTo"), date)
                    ));

                    List<Predicate> scopedAssignments = new java.util.ArrayList<>();
                    if (!organizationUnitIds.isEmpty()) {
                        scopedAssignments.add(assignment.get("organizationUnit").get("id").in(organizationUnitIds));
                    }
                    if (!workLocationIds.isEmpty()) {
                        scopedAssignments.add(assignment.get("workLocation").get("id").in(workLocationIds));
                    }
                    assignmentPredicates.add(criteriaBuilder.or(scopedAssignments.toArray(Predicate[]::new)));
                    assignmentQuery.select(assignment.get("id"))
                        .where(assignmentPredicates.toArray(Predicate[]::new));
                    predicates.add(criteriaBuilder.exists(assignmentQuery));
                }

                // Không có nhánh nào (không có permission) → disjunction() = điều kiện luôn sai, không khớp ai.
                return predicates.isEmpty()
                    ? criteriaBuilder.disjunction()
                    : criteriaBuilder.or(predicates.toArray(Predicate[]::new));
            };
        }
    }
}
