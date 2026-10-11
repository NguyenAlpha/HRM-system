package com.htttdn.hrm.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.PayrollPeriod;
import com.htttdn.hrm.entity.Payslip;
import com.htttdn.hrm.exception.ResourceNotFoundException;
import com.htttdn.hrm.repository.PayrollPeriodRepository;
import com.htttdn.hrm.repository.PayslipRepository;

/** Writes every payslip of one payroll period to a single XLSX sheet, one row per employee. */
@Service
public class PayrollExportService {
    private static final String MONEY_FORMAT = "#,##0";

    private record Column(String header, Function<Payslip, Object> value, boolean money) {}

    private static final List<Column> COLUMNS = List.of(
        new Column("Mã NV", Payslip::getEmployeeCodeSnapshot, false),
        new Column("Họ tên", Payslip::getEmployeeNameSnapshot, false),
        new Column("Đơn vị", Payslip::getOrganizationUnitSnapshot, false),
        new Column("Địa điểm", Payslip::getWorkLocationSnapshot, false),
        new Column("Lương hợp đồng", Payslip::getContractualBaseSalary, true),
        new Column("Phút công chuẩn", Payslip::getScheduledWorkMinutes, false),
        new Column("Phút công được trả", Payslip::getPayableWorkMinutes, false),
        new Column("Phút tăng ca", Payslip::getApprovedOvertimeMinutes, false),
        new Column("Lương theo công", Payslip::getBaseSalaryPay, true),
        new Column("Phụ cấp", Payslip::getAllowancePay, true),
        new Column("Tiền tăng ca", Payslip::getOvertimePay, true),
        new Column("Tăng ca miễn thuế", Payslip::getTaxExemptOvertimePay, true),
        new Column("Thưởng, phụ cấp khác", Payslip::getOtherEarningsPay, true),
        new Column("Phụ cấp khác miễn thuế", Payslip::getTaxExemptOtherEarnings, true),
        new Column("Tổng thu nhập", Payslip::getGrossPay, true),
        new Column("BHXH", Payslip::getEmployeeSocialInsurance, true),
        new Column("BHYT", Payslip::getEmployeeHealthInsurance, true),
        new Column("BHTN", Payslip::getEmployeeUnemploymentInsurance, true),
        new Column("Thu nhập tính thuế", Payslip::getTaxableIncome, true),
        new Column("Thuế TNCN", Payslip::getPersonalIncomeTax, true),
        new Column("Khấu trừ khác", Payslip::getOtherDeductions, true),
        new Column("Thực nhận", Payslip::getNetPay, true)
    );

    private final PayrollPeriodRepository payrollPeriodRepository;
    private final PayslipRepository payslipRepository;
    private final EmployeeAccessScopeService employeeAccessScopeService;

    public PayrollExportService(PayrollPeriodRepository payrollPeriodRepository,
        PayslipRepository payslipRepository, EmployeeAccessScopeService employeeAccessScopeService) {
        this.payrollPeriodRepository = payrollPeriodRepository;
        this.payslipRepository = payslipRepository;
        this.employeeAccessScopeService = employeeAccessScopeService;
    }

    public record PayrollExport(String fileName, byte[] content) {}

    @PreAuthorize("hasAuthority('report.payroll.read')")
    @Transactional(readOnly = true)
    public PayrollExport exportPeriod(Long periodId) {
        employeeAccessScopeService.requireCompanyWide("report.payroll.read");
        PayrollPeriod period = payrollPeriodRepository.findById(periodId)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.PAYROLL_PERIOD_NOT_FOUND, "Payroll period not found: " + periodId));
        List<Payslip> payslips = payslipRepository.findByPayrollPeriodId(periodId).stream()
            .sorted(Comparator.comparing(Payslip::getEmployeeCodeSnapshot))
            .toList();
        String label = "%d-%02d".formatted(period.getYear(), period.getMonth());
        return new PayrollExport("bang-luong-" + label + ".xlsx", write("Bảng lương " + label, payslips));
    }

    private byte[] write(String sheetName, List<Payslip> payslips) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            Workbook workbook = new Workbook(out, "HRM", "1.0");
            Worksheet sheet = workbook.newWorksheet(sheetName);
            for (int col = 0; col < COLUMNS.size(); col++) {
                sheet.value(0, col, COLUMNS.get(col).header());
                sheet.style(0, col).bold().set();
            }
            for (int i = 0; i < payslips.size(); i++) {
                for (int col = 0; col < COLUMNS.size(); col++) {
                    Object value = COLUMNS.get(col).value().apply(payslips.get(i));
                    if (value instanceof Number number) {
                        sheet.value(i + 1, col, number);
                    } else if (value != null) {
                        sheet.value(i + 1, col, value.toString());
                    }
                }
            }
            writeTotals(sheet, payslips.size());
            sheet.freezePane(2, 1);
            workbook.finish();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return out.toByteArray();
    }

    /** Adds a SUM row under the money columns and applies the money format to them. */
    private void writeTotals(Worksheet sheet, int rowCount) {
        int totalRow = rowCount + 1;
        sheet.value(totalRow, 0, "Tổng cộng");
        sheet.style(totalRow, 0).bold().set();
        for (int col = 0; col < COLUMNS.size(); col++) {
            if (!COLUMNS.get(col).money()) {
                continue;
            }
            String letter = columnLetter(col);
            if (rowCount > 0) {
                sheet.formula(totalRow, col, "SUM(%s2:%s%d)".formatted(letter, letter, rowCount + 1));
            }
            sheet.range(1, col, totalRow, col).style().format(MONEY_FORMAT).set();
            sheet.style(totalRow, col).bold().format(MONEY_FORMAT).set();
        }
    }

    private static String columnLetter(int index) {
        StringBuilder letters = new StringBuilder();
        for (int n = index + 1; n > 0; n = (n - 1) / 26) {
            letters.insert(0, (char) ('A' + (n - 1) % 26));
        }
        return letters.toString();
    }
}
