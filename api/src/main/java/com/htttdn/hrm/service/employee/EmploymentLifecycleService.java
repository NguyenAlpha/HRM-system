package com.htttdn.hrm.service.employee;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.dto.response.employee.EmployeeDetailResponse;
import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.EmployeeAssignment;
import com.htttdn.hrm.entity.enums.AccountStatus;
import com.htttdn.hrm.entity.enums.EmploymentStatus;
import com.htttdn.hrm.entity.enums.ResignationRequestStatus;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.repository.AccountRepository;
import com.htttdn.hrm.repository.AccountRoleAssignmentRepository;
import com.htttdn.hrm.repository.EmployeeAssignmentRepository;
import com.htttdn.hrm.repository.EmployeePayrollProfileRepository;
import com.htttdn.hrm.repository.EmployeeRepository;
import com.htttdn.hrm.repository.EmployeeSalaryHistoryRepository;
import com.htttdn.hrm.repository.EmployeeTaxDependentRepository;
import com.htttdn.hrm.repository.ResignationRequestRepository;
import com.htttdn.hrm.security.CurrentAccountProvider;
import com.htttdn.hrm.service.auth.RefreshTokenService;
import com.htttdn.hrm.service.payroll.PayrollPeriodInvalidator;
import com.htttdn.hrm.service.rbac.AccountRoleAssignmentCommandService;
import com.htttdn.hrm.service.rbac.EmployeeAccessScopeService;
import com.htttdn.hrm.service.rbac.SystemRoles;

import lombok.RequiredArgsConstructor;

/**
 * Kết thúc làm việc của nhân viên: kết thúc ngay, hẹn ngày nghỉ, hủy hẹn và hoàn tất khi đến hạn. Hồ sơ
 * nhân viên nằm ở {@link EmployeeService}.
 *
 * <p>Kiến thức nền: {@code terminationDate} là ngày làm việc cuối cùng — ngày đó vẫn đi làm và được trả
 * lương. Có hai cách kết thúc:
 * <ul>
 *   <li><b>ngay</b> (ngày cuối là hôm nay hoặc đã qua): đổi trạng thái sang {@code RESIGNED} /
 *       {@code TERMINATED} / {@code RETIRED}, đóng phân công và dữ liệu tính lương, khóa tài khoản, thu hồi
 *       role ({@link #endEmploymentAs});</li>
 *   <li><b>hẹn trước</b> (ngày cuối ở tương lai): chỉ ghi ngày cuối và trạng thái sẽ chuyển sang
 *       ({@code scheduledEndStatus}); nhân viên vẫn làm việc bình thường tới hết ngày đó, rồi
 *       {@link ScheduledEmploymentEndJob} hoàn tất ({@link #completeScheduledEnd}).</li>
 * </ul>
 * Đơn xin nghỉ việc đã duyệt cũng đi qua {@link #endEmploymentAs} (gọi từ {@link ResignationRequestService}).
 */
@Service
@Transactional
@RequiredArgsConstructor
public class EmploymentLifecycleService {

    private static final String EMPLOYEE_LIFECYCLE_MANAGE = "employee.lifecycle.manage";

    private final EmployeeRepository employeeRepository;
    private final EmployeeAssignmentRepository employeeAssignmentRepository;
    private final EmployeeSalaryHistoryRepository employeeSalaryHistoryRepository;
    private final EmployeePayrollProfileRepository employeePayrollProfileRepository;
    private final EmployeeTaxDependentRepository employeeTaxDependentRepository;
    private final AccountRepository accountRepository;
    private final AccountRoleAssignmentRepository accountRoleAssignmentRepository;
    private final AccountRoleAssignmentCommandService roleAssignmentCommandService;
    private final RefreshTokenService refreshTokenService;
    private final CurrentAccountProvider currentAccountProvider;
    private final EmployeeAccessScopeService employeeAccessScopeService;
    private final EmployeeMapper employeeMapper;
    private final PayrollPeriodInvalidator payrollPeriodInvalidator;
    private final ResignationRequestRepository resignationRequestRepository;
    private final Clock clock;

    /**
     * HR kết thúc làm việc của một nhân viên: ngay nếu ngày cuối là hôm nay/đã qua, hẹn trước nếu ở tương
     * lai (gọi lại với ngày khác sẽ thay lịch hẹn cũ).
     *
     * @param employeeId nhân viên
     * @param endStatus trạng thái sau khi nghỉ: {@code RESIGNED}, {@code TERMINATED} hoặc {@code RETIRED}
     * @param terminationDate ngày làm việc cuối cùng
     * @param terminationReason lý do, ghi vào hồ sơ và vào lý do thu hồi role
     * @return hồ sơ sau khi kết thúc / hẹn kết thúc
     * @throws BusinessException trạng thái không phải trạng thái nghỉ việc, ngày cuối trước ngày vào làm
     *         hoặc trước ngày bắt đầu phân công hiện tại (→ 400)
     * @throws com.htttdn.hrm.exception.ResourceNotFoundException không có hoặc đã xóa mềm (→ 404)
     * @throws com.htttdn.hrm.exception.ForbiddenException ngoài phạm vi {@code employee.lifecycle.manage} (→ 403)
     * @throws ConflictException nhân viên đã nghỉ việc rồi (→ 409 {@code EMPLOYMENT_ALREADY_ENDED}), hoặc đang là Company Owner
     *         (→ 409 {@code COMPANY_OWNER_TRANSFER_REQUIRED})
     */
    @PreAuthorize("hasAuthority('employee.lifecycle.manage')")
    public EmployeeDetailResponse endEmployment(
        Long employeeId, EmploymentStatus endStatus, LocalDate terminationDate, String terminationReason
    ) {
        if (!isEmploymentEnded(endStatus)) {
            throw new BusinessException(
                ErrorCode.VALIDATION_ERROR, "endStatus must be RESIGNED, TERMINATED or RETIRED", "endStatus"
            );
        }
        Employee employee = employeeRepository.getRequired(employeeId);
        employeeAccessScopeService.requireEmployeeAccess(employeeId, EMPLOYEE_LIFECYCLE_MANAGE);
        if (terminationDate.isBefore(employee.getHireDate())) {
            throw new BusinessException(
                ErrorCode.VALIDATION_ERROR, "terminationDate must not be before hireDate", "terminationDate"
            );
        }
        if (isEmploymentEnded(employee.getEmploymentStatus())) {
            throw new ConflictException(ErrorCode.EMPLOYMENT_ALREADY_ENDED, "Employee employment has already ended");
        }

        // The last working day is still worked and paid, so a later one only schedules the end; it
        // replaces any end already scheduled.
        if (terminationDate.isAfter(LocalDate.now(clock))) {
            scheduleEnd(employee, endStatus, terminationDate, terminationReason, currentAccountProvider.account());
        } else {
            // A last day already past may cut short a month payroll has paid in full: periods holding their
            // payslip after that day go back to DRAFT, or refuse the end once approved (409 PAYROLL_PERIOD_LOCKED).
            // A day scheduled earlier was already used by payroll, so it changes nothing.
            if (!terminationDate.equals(employee.getTerminationDate())) {
                payrollPeriodInvalidator.invalidateEmployeeFrom(employeeId, terminationDate.plusDays(1));
            }
            endEmploymentAs(employee, endStatus, terminationDate, terminationReason, currentAccountProvider.account());
        }
        return employeeMapper.toDetailResponse(employee);
    }

    /**
     * Hủy lịch hẹn kết thúc làm việc: xóa ngày cuối, lý do và trạng thái hẹn; nhân viên tiếp tục làm như
     * chưa từng hẹn. Đơn nghỉ việc đã duyệt đặt lịch này cũng bị hủy, nếu không nó vẫn kết thúc hợp đồng.
     *
     * @throws ConflictException không có lịch hẹn nào (→ 409 {@code EMPLOYMENT_END_NOT_SCHEDULED})
     */
    @PreAuthorize("hasAuthority('employee.lifecycle.manage')")
    public EmployeeDetailResponse cancelScheduledEnd(Long employeeId) {
        Employee employee = employeeRepository.getRequired(employeeId);
        employeeAccessScopeService.requireEmployeeAccess(employeeId, EMPLOYEE_LIFECYCLE_MANAGE);
        if (employee.getScheduledEndStatus() == null) {
            throw new ConflictException(ErrorCode.EMPLOYMENT_END_NOT_SCHEDULED, "No employment end is scheduled");
        }
        employee.setTerminationDate(null);
        employee.setTerminationReason(null);
        employee.setScheduledEndStatus(null);
        employee.setScheduledEndByAccount(null);
        resignationRequestRepository.findApprovedNotCompletedForUpdate(employeeId)
            .ifPresent(request -> request.setStatus(ResignationRequestStatus.CANCELLED));
        return employeeMapper.toDetailResponse(employee);
    }

    /**
     * Ends a scheduled employment once its last working day has passed, acting as whoever scheduled
     * it. Run by {@link ScheduledEmploymentEndJob}; an end cancelled in the meantime is skipped.
     *
     * <p>Job chỉ đưa vào các nhân viên có ngày cuối trước hôm nay ({@code findScheduledEndsDue}), mỗi người
     * một transaction riêng; lỗi của một người được job ghi log rồi bỏ qua.
     */
    public void completeScheduledEnd(Long employeeId) {
        // Khóa ghi rồi đọc lại: HR có thể vừa hủy lịch hẹn (hoặc xóa nhân viên) sau khi job lấy danh sách.
        Employee employee = employeeRepository.findByIdForUpdate(employeeId).orElse(null);
        if (employee == null || employee.getScheduledEndStatus() == null) {
            return;
        }
        endEmploymentAs(employee, employee.getScheduledEndStatus(), employee.getTerminationDate(),
            employee.getTerminationReason(), employee.getScheduledEndByAccount());
    }

    /**
     * Records the last working day and the status to take after it, leaving the employee working until
     * then. Payroll, attendance and leave already stop at that day. Used by {@link #endEmployment} and,
     * without the caller's permission checks, by an approved resignation, where {@code actor} is the approver.
     */
    public void scheduleEnd(
        Employee employee, EmploymentStatus endStatus, LocalDate terminationDate, String terminationReason, Account actor
    ) {
        requireNotCompanyOwner(employee);
        employeeAssignmentRepository.findFirstByEmployeeIdAndIsPrimaryTrueAndEffectiveToIsNull(employee.getId())
            .ifPresent(assignment -> requireNotBeforeAssignment(assignment, terminationDate));
        employee.setTerminationDate(terminationDate);
        employee.setTerminationReason(terminationReason);
        employee.setScheduledEndStatus(endStatus);
        employee.setScheduledEndByAccount(actor);
    }

    /**
     * Ends the employment without the caller's permission checks: closes the primary assignment,
     * disables the account and revokes its business roles. Used by {@link #endEmployment} and by an
     * approved resignation once its last working day has passed, where {@code actor} is the approver.
     *
     * <p>Các bước: đổi trạng thái + xóa lịch hẹn → đóng phân công chính ở ngày cuối → đóng lương, hồ sơ
     * thuế, người phụ thuộc → khóa tài khoản ({@code DISABLED}), thu hồi mọi refresh token và thu hồi role
     * nghiệp vụ. Access token đang dùng vẫn chạy tới khi hết hạn (request không kiểm tra lại trạng thái
     * tài khoản), sau đó không làm mới được nữa.
     *
     * @param actor người ghi nhận việc thu hồi role: HR thao tác, người hẹn, hoặc người duyệt đơn nghỉ việc
     * @throws BusinessException ngày cuối trước ngày bắt đầu phân công hiện tại (→ 400)
     * @throws ConflictException nhân viên đang là Company Owner (→ 409 {@code COMPANY_OWNER_TRANSFER_REQUIRED})
     */
    public void endEmploymentAs(
        Employee employee, EmploymentStatus endStatus, LocalDate terminationDate, String terminationReason, Account actor
    ) {
        requireNotCompanyOwner(employee);
        Long employeeId = employee.getId();
        employee.setEmploymentStatus(endStatus);
        employee.setTerminationDate(terminationDate);
        employee.setTerminationReason(terminationReason);
        employee.setScheduledEndStatus(null);
        employee.setScheduledEndByAccount(null);

        employeeAssignmentRepository.findFirstByEmployeeIdAndIsPrimaryTrueAndEffectiveToIsNull(employeeId)
            .ifPresent(assignment -> closeAssignmentAtTermination(assignment, terminationDate));
        closePayrollInputsAtTermination(employeeId, terminationDate);
        accountRepository.findByEmployeeId(employeeId).ifPresent(account -> {
            account.setStatus(AccountStatus.DISABLED);
            refreshTokenService.revokeAll(account.getId());
            roleAssignmentCommandService.revokeHeldRoles(
                account.getId(), terminationDate, actor, "Kết thúc làm việc: " + terminationReason.trim());
        });
    }

    /**
     * The Company Owner hands ownership over first: no workflow revokes COMPANY_OWNER, so ending their
     * employment would leave the company with an owner nobody can sign in as. Checked when an end is
     * scheduled and again when it happens, since ownership may move in between.
     */
    private void requireNotCompanyOwner(Employee employee) {
        boolean owner = accountRepository.findByEmployeeId(employee.getId())
            .map(account -> accountRoleAssignmentRepository
                .existsByAccountIdAndRoleCodeAndRevokedAtIsNull(account.getId(), SystemRoles.COMPANY_OWNER))
            .orElse(false);
        if (owner) {
            throw new ConflictException(
                ErrorCode.COMPANY_OWNER_TRANSFER_REQUIRED,
                "Transfer company ownership before ending the Company Owner's employment"
            );
        }
    }

    /**
     * Ends the salary, payroll profile and dependents still running past the last working day there,
     * so none reads as current. Payroll already stops at that day, so no payslip changes. A record
     * starting after the day is left as it is: it never applied.
     */
    private void closePayrollInputsAtTermination(Long employeeId, LocalDate terminationDate) {
        employeeSalaryHistoryRepository.findByEmployeeIdOrderByEffectiveFromDesc(employeeId).stream()
            .filter(salary -> runsPast(salary.getEffectiveFrom(), salary.getEffectiveTo(), terminationDate))
            .forEach(salary -> salary.setEffectiveTo(terminationDate));
        employeePayrollProfileRepository.findByEmployeeIdForUpdate(employeeId).stream()
            .filter(profile -> runsPast(profile.getEffectiveFrom(), profile.getEffectiveTo(), terminationDate))
            .forEach(profile -> profile.setEffectiveTo(terminationDate));
        employeeTaxDependentRepository.findByEmployeeIdOrderByEffectiveFromDescIdDesc(employeeId).stream()
            .filter(dependent -> runsPast(dependent.getEffectiveFrom(), dependent.getEffectiveTo(), terminationDate))
            .forEach(dependent -> dependent.setEffectiveTo(terminationDate));
    }

    private static boolean runsPast(LocalDate from, LocalDate to, LocalDate terminationDate) {
        return !from.isAfter(terminationDate) && (to == null || to.isAfter(terminationDate));
    }

    private void closeAssignmentAtTermination(EmployeeAssignment assignment, LocalDate terminationDate) {
        requireNotBeforeAssignment(assignment, terminationDate);
        assignment.setEffectiveTo(terminationDate);
    }

    private void requireNotBeforeAssignment(EmployeeAssignment assignment, LocalDate terminationDate) {
        if (terminationDate.isBefore(assignment.getEffectiveFrom())) {
            throw new BusinessException(
                ErrorCode.VALIDATION_ERROR,
                "terminationDate must not be before the current assignment start date",
                "terminationDate"
            );
        }
    }

    private boolean isEmploymentEnded(EmploymentStatus status) {
        return status == EmploymentStatus.RESIGNED
            || status == EmploymentStatus.TERMINATED
            || status == EmploymentStatus.RETIRED;
    }
}
