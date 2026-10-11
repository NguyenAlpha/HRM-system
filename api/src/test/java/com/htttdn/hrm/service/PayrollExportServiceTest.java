package com.htttdn.hrm.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.PayrollPeriod;
import com.htttdn.hrm.entity.Payslip;
import com.htttdn.hrm.exception.ForbiddenException;
import com.htttdn.hrm.exception.ResourceNotFoundException;
import com.htttdn.hrm.repository.PayrollPeriodRepository;
import com.htttdn.hrm.repository.PayslipRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayrollExportServiceTest {
    @Mock private PayrollPeriodRepository payrollPeriodRepository;
    @Mock private PayslipRepository payslipRepository;
    @Mock private EmployeeAccessScopeService employeeAccessScopeService;

    @Test
    void exportWritesOneRowPerPayslipSortedByCodeAndATotalRow() throws IOException {
        PayrollPeriod period = PayrollPeriod.builder().id(3L).year((short) 2026).month((short) 1).build();
        when(payrollPeriodRepository.findById(3L)).thenReturn(Optional.of(period));
        when(payslipRepository.findByPayrollPeriodId(3L)).thenReturn(List.of(
            payslip("NV002", "Trần Thị B", "9000000"),
            payslip("NV001", "Nguyễn Văn A", "11000000")
        ));

        var export = service().exportPeriod(3L);

        assertEquals("bang-luong-2026-01.xlsx", export.fileName());
        Map<String, String> parts = unzip(export.content());
        String sheet = parts.get("xl/worksheets/sheet1.xml");
        String strings = parts.getOrDefault("xl/sharedStrings.xml", "") + sheet;
        assertTrue(strings.indexOf("NV001") < strings.indexOf("NV002"), "rows are sorted by employee code");
        assertTrue(strings.contains("Nguyễn Văn A"));
        assertTrue(strings.contains("Tổng cộng"));
        // Net pay is the 22nd column (V); the total row sums rows 2..3.
        assertTrue(sheet.contains("SUM(V2:V3)"), sheet);
    }

    @Test
    void exportRequiresCompanyWideScope() {
        doThrow(new ForbiddenException(ErrorCode.FORBIDDEN, "scope"))
            .when(employeeAccessScopeService).requireCompanyWide("report.payroll.read");

        assertThrows(ForbiddenException.class, () -> service().exportPeriod(3L));
        verifyNoInteractions(payslipRepository);
    }

    @Test
    void exportRejectsUnknownPeriod() {
        when(payrollPeriodRepository.findById(9L)).thenReturn(Optional.empty());

        var exception = assertThrows(ResourceNotFoundException.class, () -> service().exportPeriod(9L));
        assertEquals(ErrorCode.PAYROLL_PERIOD_NOT_FOUND, exception.getErrorCode());
    }

    private static Payslip payslip(String code, String name, String netPay) {
        BigDecimal zero = BigDecimal.ZERO;
        return Payslip.builder()
            .employeeCodeSnapshot(code).employeeNameSnapshot(name)
            .organizationUnitSnapshot("Phòng Nhân sự").workLocationSnapshot("Trụ sở")
            .contractualBaseSalary(new BigDecimal("10000000"))
            .scheduledWorkMinutes(10560).payableWorkMinutes(10560).approvedOvertimeMinutes(0)
            .baseSalaryPay(new BigDecimal("10000000")).allowancePay(zero).overtimePay(zero)
            .taxExemptOvertimePay(zero).grossPay(new BigDecimal("10000000"))
            .employeeSocialInsurance(zero).employeeHealthInsurance(zero).employeeUnemploymentInsurance(zero)
            .otherEarningsPay(zero).taxExemptOtherEarnings(zero).otherDeductions(zero)
            .taxableIncome(zero).personalIncomeTax(zero).netPay(new BigDecimal(netPay))
            .build();
    }

    /** fastexcel streams entries with data descriptors, so read through the central directory. */
    private static Map<String, String> unzip(byte[] content) throws IOException {
        Path file = Files.write(Files.createTempFile("payroll-export", ".xlsx"), content);
        Map<String, String> parts = new HashMap<>();
        try (ZipFile zip = new ZipFile(file.toFile())) {
            for (ZipEntry entry : Collections.list(zip.entries())) {
                String xml = new String(zip.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8);
                // fastexcel writes non-ASCII characters as numeric entities such as &#x1ec5;.
                parts.put(entry.getName(), Pattern.compile("&#x([0-9a-fA-F]+);").matcher(xml)
                    .replaceAll(match -> Character.toString(Integer.parseInt(match.group(1), 16))));
            }
        } finally {
            Files.deleteIfExists(file);
        }
        return parts;
    }

    private PayrollExportService service() {
        return new PayrollExportService(payrollPeriodRepository, payslipRepository, employeeAccessScopeService);
    }
}
