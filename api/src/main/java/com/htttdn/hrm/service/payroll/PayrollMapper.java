package com.htttdn.hrm.service.payroll;

import java.util.List;

import org.springframework.stereotype.Component;

import com.htttdn.hrm.dto.response.payroll.PayrollPeriodResponse;
import com.htttdn.hrm.dto.response.payroll.PayslipItemResponse;
import com.htttdn.hrm.dto.response.payroll.PayslipResponse;
import com.htttdn.hrm.entity.PayrollPeriod;
import com.htttdn.hrm.entity.Payslip;
import com.htttdn.hrm.repository.PayslipItemRepository;
import com.htttdn.hrm.repository.PayslipRepository;

import lombok.RequiredArgsConstructor;

/** Kỳ lương và phiếu lương ra response; dùng chung cho {@link PayrollService} và {@link PayrollQueryService}. */
@Component
@RequiredArgsConstructor
public class PayrollMapper {

    private final PayslipRepository payslipRepository;
    private final PayslipItemRepository payslipItemRepository;

    public PayrollPeriodResponse toResponse(PayrollPeriod period) {
        PayslipRepository.PeriodTotals totals = payslipRepository.totalsForPeriod(period.getId());
        return new PayrollPeriodResponse(
            period.getId(),
            period.getYear(),
            period.getMonth(),
            period.getPeriodStart(),
            period.getPeriodEnd(),
            period.getTaxPaymentDate(),
            period.getStatus(),
            period.getCalculatedByAccount() != null ? period.getCalculatedByAccount().getId() : null,
            period.getCalculatedAt(),
            period.getApprovedByAccount() != null ? period.getApprovedByAccount().getId() : null,
            period.getApprovedAt(),
            period.getPaidByAccount() != null ? period.getPaidByAccount().getId() : null,
            period.getPaidAt(),
            period.getLockedByAccount() != null ? period.getLockedByAccount().getId() : null,
            period.getLockedAt(),
            period.getReopenedByAccount() != null ? period.getReopenedByAccount().getId() : null,
            period.getReopenedAt(),
            period.getReopenReason(),
            totals.getPayslipCount(),
            totals.getTotalGrossPay(),
            totals.getTotalNetPay()
        );
    }

    public PayslipResponse toResponse(Payslip payslip) {
        List<PayslipItemResponse> items = payslipItemRepository.findByPayslipId(payslip.getId()).stream()
            .map(item -> new PayslipItemResponse(
                item.getId(),
                item.getComponentType(),
                item.getDescription(),
                item.getQuantity(),
                item.getUnitRate(),
                item.getMultiplier(),
                item.getAmount()
            ))
            .toList();

        return new PayslipResponse(
            payslip.getId(),
            payslip.getPayrollPeriod().getId(),
            payslip.getPayrollPeriod().getYear(),
            payslip.getPayrollPeriod().getMonth(),
            payslip.getEmployee().getId(),
            payslip.getEmployeeCodeSnapshot(),
            payslip.getEmployeeNameSnapshot(),
            payslip.getPositionSnapshot(),
            payslip.getWorkLocationSnapshot(),
            payslip.getOrganizationUnitSnapshot(),
            payslip.getContractualBaseSalary(),
            payslip.getScheduledWorkMinutes(),
            payslip.getPayableWorkMinutes(),
            payslip.getApprovedOvertimeMinutes(),
            payslip.getBaseSalaryPay(),
            payslip.getAllowancePay(),
            payslip.getOvertimePay(),
            payslip.getTaxExemptOvertimePay(),
            payslip.getOtherEarningsPay(),
            payslip.getTaxExemptOtherEarnings(),
            payslip.getGrossPay(),
            payslip.getInsuranceSalaryBase(),
            payslip.getUnemploymentInsuranceBase(),
            payslip.getEmployeeSocialInsurance(),
            payslip.getEmployeeHealthInsurance(),
            payslip.getEmployeeUnemploymentInsurance(),
            payslip.getTaxableIncome(),
            payslip.getPersonalIncomeTax(),
            payslip.getOtherDeductions(),
            payslip.getNetPay(),
            items
        );
    }
}
