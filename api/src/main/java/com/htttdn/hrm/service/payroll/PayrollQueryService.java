package com.htttdn.hrm.service.payroll;

import java.util.List;
import java.util.Set;

import com.htttdn.hrm.service.rbac.EmployeeAccessScopeService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.dto.response.payroll.PayrollPeriodResponse;
import com.htttdn.hrm.dto.response.payroll.PayslipResponse;
import com.htttdn.hrm.entity.Payslip;
import com.htttdn.hrm.entity.enums.PayrollPeriodStatus;
import com.htttdn.hrm.exception.ForbiddenException;
import com.htttdn.hrm.repository.PayrollPeriodRepository;
import com.htttdn.hrm.repository.PayslipRepository;
import com.htttdn.hrm.repository.specification.SearchPatterns;
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

    private static final Set<String> PERIOD_SORTABLE = Set.of("periodStart", "year", "month", "status");
    private static final Set<String> PAYSLIP_SORTABLE = Set.of("payrollPeriod.periodStart");

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
        return payrollPeriodRepository.findAll(SearchPatterns.requireSortable(pageable, PERIOD_SORTABLE))
            .map(payrollMapper::toResponse);
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
        Payslip payslip = payslipRepository.getRequired(payslipId);
        requirePayslipAccess(payslip.getEmployee().getId());
        if (!currentAccountProvider.hasAuthority("report.payroll.read")
            && !payslip.getPayrollPeriod().getStatus().isReleased()) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN, "Payslip has not been approved");
        }
        return payrollMapper.toResponse(payslip);
    }

    @PreAuthorize("hasAnyAuthority('payroll.self.read', 'report.payroll.read')")
    public Page<PayslipResponse> listPayslipsForEmployee(Long employeeId, Pageable pageable) {
        SearchPatterns.requireSortable(pageable, PAYSLIP_SORTABLE);
        requirePayslipAccess(employeeId);
        if (currentAccountProvider.hasAuthority("report.payroll.read")) {
            return payslipRepository.findByEmployeeId(employeeId, pageable).map(payrollMapper::toResponse);
        }
        return payslipRepository.findByEmployeeIdAndPayrollPeriodStatusIn(employeeId,
            PayrollPeriodStatus.RELEASED, pageable).map(payrollMapper::toResponse);
    }

    private void requirePayslipAccess(Long employeeId) {
        if (currentAccountProvider.hasAuthority("payroll.self.read")) {
            if (currentAccountProvider.account().belongsTo(employeeId)) {
                return;
            }
        }
        if (!currentAccountProvider.hasAuthority("report.payroll.read")) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN, "Payslip is outside the assigned scope");
        }
        employeeAccessScopeService.requireEmployeeAccess(employeeId, "report.payroll.read");
    }
}
