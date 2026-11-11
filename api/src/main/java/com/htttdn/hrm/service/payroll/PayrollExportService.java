package com.htttdn.hrm.service.payroll;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import com.htttdn.hrm.service.rbac.EmployeeAccessScopeService;
import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.entity.PayrollPeriod;
import com.htttdn.hrm.entity.Payslip;
import com.htttdn.hrm.repository.PayrollPeriodRepository;
import com.htttdn.hrm.repository.PayslipRepository;
import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.exception.ConflictException;

import lombok.RequiredArgsConstructor;

/**
 * Writes payslips to a single XLSX sheet with a total row: every payslip of one payroll period, one row
 * per employee, or one employee's payslips of a year, one row per month.
 */
@Service
@RequiredArgsConstructor
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

    /** The yearly sheet leads with the month; the other columns are the period sheet's. */
    private static final List<Column> YEAR_COLUMNS = Stream.concat(
        Stream.of(new Column("Tháng", payslip -> "%02d/%d".formatted(
            payslip.getPayrollPeriod().getMonth(), payslip.getPayrollPeriod().getYear()), false)),
        COLUMNS.stream()).toList();

    private final PayrollPeriodRepository payrollPeriodRepository;
    private final PayslipRepository payslipRepository;
    private final EmployeeAccessScopeService employeeAccessScopeService;
    private final PayrollQueryService payrollQueryService;

    public record PayrollExport(String fileName, byte[] content) {}

    @PreAuthorize("hasAuthority('report.payroll.read')")
    @Transactional(readOnly = true)
    public PayrollExport exportPeriod(Long periodId) {
        employeeAccessScopeService.requireCompanyWide("report.payroll.read");
        PayrollPeriod period = payrollPeriodRepository.getRequired(periodId);
        List<Payslip> payslips = payslipRepository.findByPayrollPeriodId(periodId).stream()
            .sorted(Comparator.comparing(Payslip::getEmployeeCodeSnapshot))
            .toList();
        String label = "%d-%02d".formatted(period.getYear(), period.getMonth());
        return new PayrollExport("bang-luong-" + label + ".xlsx", write("Bảng lương " + label, COLUMNS, payslips));
    }

    /**
     * Bảng chi lương của kỳ đã duyệt: mỗi nhân viên một dòng với tài khoản nhận (chụp lúc tính kỳ), số thực nhận
     * và nội dung chuyển khoản không dấu. Kế toán tải file này lên internet banking doanh nghiệp để ngân hàng chuyển
     * lương; hệ thống không tự chuyển tiền. Nhân viên chưa khai tài khoản vẫn có dòng, các ô tài khoản để trống.
     *
     * @throws ConflictException kỳ chưa duyệt (→ 409 {@code CONFLICT})
     */
    @PreAuthorize("hasAuthority('report.payroll.read')")
    @Transactional(readOnly = true)
    public PayrollExport exportBankTransfer(Long periodId) {
        employeeAccessScopeService.requireCompanyWide("report.payroll.read");
        PayrollPeriod period = payrollPeriodRepository.getRequired(periodId);
        if (!period.getStatus().isReleased()) {
            throw new ConflictException(ErrorCode.CONFLICT, "Only an approved period can be paid out");
        }
        List<Payslip> payslips = payslipRepository.findByPayrollPeriodId(periodId).stream()
            .sorted(Comparator.comparing(Payslip::getEmployeeCodeSnapshot))
            .toList();
        String month = "%02d/%d".formatted(period.getMonth(), period.getYear());
        List<Column> columns = List.of(
            new Column("STT", payslip -> payslips.indexOf(payslip) + 1, false),
            new Column("Mã NV", Payslip::getEmployeeCodeSnapshot, false),
            new Column("Họ tên", Payslip::getEmployeeNameSnapshot, false),
            new Column("Ngân hàng", Payslip::getBankNameSnapshot, false),
            new Column("Số tài khoản", Payslip::getBankAccountNumberSnapshot, false),
            new Column("Chủ tài khoản", Payslip::getBankAccountHolderSnapshot, false),
            // Đồng không có đơn vị lẻ, ngân hàng chỉ nhận số nguyên; phiếu lương vẫn giữ số lẻ.
            new Column("Số tiền", payslip -> payslip.getNetPay().setScale(0, RoundingMode.HALF_UP), true),
            new Column("Nội dung", payslip -> "Luong T%s %s".formatted(month, payslip.getEmployeeCodeSnapshot()), false)
        );
        String label = "%d-%02d".formatted(period.getYear(), period.getMonth());
        return new PayrollExport("chi-luong-" + label + ".xlsx", write("Chi lương " + label, columns, payslips));
    }

    /**
     * One employee's payslips of a year, a row per month: the same payslips their payslip list shows the
     * caller, so an employee's own sheet holds released months only.
     */
    @PreAuthorize("hasAnyAuthority('payroll.self.read', 'report.payroll.read')")
    @Transactional(readOnly = true)
    public PayrollExport exportEmployeeYear(Long employeeId, short year) {
        List<Payslip> payslips = payrollQueryService.listPayslipEntitiesForEmployeeYear(employeeId, year);
        String code = payslips.isEmpty() ? String.valueOf(employeeId) : payslips.getFirst().getEmployeeCodeSnapshot();
        return new PayrollExport("bang-luong-%s-%d.xlsx".formatted(code, year),
            write("Bảng lương " + year, YEAR_COLUMNS, payslips));
    }

    private byte[] write(String sheetName, List<Column> columns, List<Payslip> payslips) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            Workbook workbook = new Workbook(out, "HRM", "1.0");
            Worksheet sheet = workbook.newWorksheet(sheetName);
            for (int col = 0; col < columns.size(); col++) {
                sheet.value(0, col, columns.get(col).header());
                sheet.style(0, col).bold().set();
            }
            for (int i = 0; i < payslips.size(); i++) {
                for (int col = 0; col < columns.size(); col++) {
                    Object value = columns.get(col).value().apply(payslips.get(i));
                    if (value instanceof Number number) {
                        sheet.value(i + 1, col, number);
                    } else if (value != null) {
                        sheet.value(i + 1, col, value.toString());
                    }
                }
            }
            writeTotals(sheet, columns, payslips.size());
            sheet.freezePane(2, 1);
            workbook.finish();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return out.toByteArray();
    }

    /** Adds a SUM row under the money columns and applies the money format to them. */
    private void writeTotals(Worksheet sheet, List<Column> columns, int rowCount) {
        int totalRow = rowCount + 1;
        sheet.value(totalRow, 0, "Tổng cộng");
        sheet.style(totalRow, 0).bold().set();
        for (int col = 0; col < columns.size(); col++) {
            if (!columns.get(col).money()) {
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
