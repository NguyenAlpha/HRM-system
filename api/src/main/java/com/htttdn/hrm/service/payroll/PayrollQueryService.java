package com.htttdn.hrm.service.payroll;

import java.util.List;

import com.htttdn.hrm.service.rbac.EmployeeAccessScopeService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.dto.response.payroll.PayrollPeriodResponse;
import com.htttdn.hrm.dto.response.payroll.PayslipResponse;
import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.Payslip;
import com.htttdn.hrm.entity.enums.PayrollPeriodStatus;
import com.htttdn.hrm.exception.ForbiddenException;
import com.htttdn.hrm.exception.ResourceNotFoundException;
import com.htttdn.hrm.repository.PayrollPeriodRepository;
import com.htttdn.hrm.repository.PayslipRepository;
import com.htttdn.hrm.security.CurrentAccountProvider;

import lombok.RequiredArgsConstructor;

/**
 * Phần chỉ đọc của lương: kỳ lương, bảng lương của kỳ và phiếu lương. Thay đổi kỳ lương nằm ở
 * {@link PayrollService}.
 *
 * <p>{@code @Transactional(readOnly = true)} đặt trên class áp dụng cho mọi method public, nên không
 * cần lặp lại ở từng method.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PayrollQueryService {

    private final PayrollPeriodRepository payrollPeriodRepository;
    private final PayslipRepository payslipRepository;
    private final CurrentAccountProvider currentAccountProvider;
    private final EmployeeAccessScopeService employeeAccessScopeService;
    private final PayrollMapper payrollMapper;

    @PreAuthorize("hasAuthority('report.payroll.read')")
    public PayrollPeriodResponse getPeriodById(Long periodId) {
        employeeAccessScopeService.requireCompanyWide("report.payroll.read");
        return payrollMapper.toResponse(payrollPeriodRepository.getRequired(periodId));
    }

    @PreAuthorize("hasAuthority('report.payroll.read')")
    public Page<PayrollPeriodResponse> listPeriods(Pageable pageable) {
        employeeAccessScopeService.requireCompanyWide("report.payroll.read");
        return payrollPeriodRepository.findAll(pageable).map(payrollMapper::toResponse);
    }

    /** Every payslip of one period, by employee code: the payroll sheet reviewed before approval. */
    @PreAuthorize("hasAuthority('report.payroll.read')")
    public List<PayslipResponse> listPayslipsForPeriod(Long periodId) {
        employeeAccessScopeService.requireCompanyWide("report.payroll.read");
        payrollPeriodRepository.getRequired(periodId);
        return payslipRepository.findByPayrollPeriodIdOrderByEmployeeCodeSnapshotAsc(periodId).stream()
            .map(payrollMapper::toResponse)
            .toList();
    }

    @PreAuthorize("hasAnyAuthority('payroll.self.read', 'report.payroll.read')")
    public PayslipResponse getPayslip(Long payslipId) {
        Payslip payslip = payslipRepository.findById(payslipId)
            .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PAYSLIP_NOT_FOUND, "Payslip not found: " + payslipId));
        requirePayslipAccess(payslip.getEmployee().getId());
        if (!currentAccountProvider.hasAuthority("report.payroll.read")
            && !isReleased(payslip.getPayrollPeriod().getStatus())) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN, "Payslip has not been approved");
        }
        return payrollMapper.toResponse(payslip);
    }

    @PreAuthorize("hasAnyAuthority('payroll.self.read', 'report.payroll.read')")
    public Page<PayslipResponse> listPayslipsForEmployee(Long employeeId, Pageable pageable) {
        requirePayslipAccess(employeeId);
        if (currentAccountProvider.hasAuthority("report.payroll.read")) {
            return payslipRepository.findByEmployeeId(employeeId, pageable).map(payrollMapper::toResponse);
        }
        return payslipRepository.findByEmployeeIdAndPayrollPeriodStatusIn(employeeId,
            List.of(PayrollPeriodStatus.APPROVED, PayrollPeriodStatus.PAID, PayrollPeriodStatus.LOCKED),
            pageable).map(payrollMapper::toResponse);
    }

    private boolean isReleased(PayrollPeriodStatus status) {
        return status == PayrollPeriodStatus.APPROVED || status == PayrollPeriodStatus.PAID
            || status == PayrollPeriodStatus.LOCKED;
    }

    private void requirePayslipAccess(Long employeeId) {
        if (currentAccountProvider.hasAuthority("payroll.self.read")) {
            Account actor = currentAccountProvider.account();
            if (actor.getEmployee() != null && employeeId.equals(actor.getEmployee().getId())) {
                return;
            }
        }
        if (!currentAccountProvider.hasAuthority("report.payroll.read")) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN, "Payslip is outside the assigned scope");
        }
        employeeAccessScopeService.requireEmployeeAccess(employeeId, "report.payroll.read");
    }
}
