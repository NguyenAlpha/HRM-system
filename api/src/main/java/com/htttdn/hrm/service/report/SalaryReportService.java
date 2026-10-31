package com.htttdn.hrm.service.report;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.response.report.DistributionItemResponse;
import com.htttdn.hrm.dto.response.report.PayrollSalaryReportResponse;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.EmployeeSalaryHistory;
import com.htttdn.hrm.entity.enums.EmploymentStatus;
import com.htttdn.hrm.repository.EmployeeSalaryHistoryRepository;

import lombok.RequiredArgsConstructor;

import static com.htttdn.hrm.service.report.ReportSupport.item;

/**
 * Báo cáo phân bố lương cơ bản tại một ngày: trung bình, thấp nhất, cao nhất và số người theo khoảng lương.
 * Lương tổng hợp theo năm từ phiếu lương nằm ở {@code PayrollReportService}.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SalaryReportService {

    private static final String PAYROLL_REPORT_READ = "report.payroll.read";
    private static final BigDecimal TEN_MILLION = new BigDecimal("10000000");
    private static final BigDecimal FIFTEEN_MILLION = new BigDecimal("15000000");
    private static final BigDecimal TWENTY_MILLION = new BigDecimal("20000000");
    private static final BigDecimal THIRTY_MILLION = new BigDecimal("30000000");

    private final EmployeeSalaryHistoryRepository employeeSalaryHistoryRepository;
    private final ReportSupport reportSupport;

    /**
     * Lương cơ bản đang hiệu lực vào {@code requestedDate} của những người đang làm việc, trong phạm vi
     * {@code report.payroll.read} của người xem. Người chưa có lương vẫn được đếm, ở dòng "Chưa cấu hình".
     *
     * @param requestedDate ngày chốt số liệu (null → hôm nay)
     * @param organizationUnitId lọc theo đơn vị của phân công chính ngày đó (null → không lọc)
     * @param workLocationId lọc theo địa điểm (null → không lọc)
     * @param employmentStatus lọc theo trạng thái làm việc (null → không lọc)
     * @return thống kê lương; trung bình/thấp nhất/cao nhất là null khi không ai có lương
     * @throws com.htttdn.hrm.exception.BusinessException ngày ở tương lai (→ 400 {@code VALIDATION_ERROR})
     */
    @PreAuthorize("hasAuthority('report.payroll.read')")
    public PayrollSalaryReportResponse getSalaryReport(
        LocalDate requestedDate,
        Long organizationUnitId,
        Long workLocationId,
        EmploymentStatus employmentStatus
    ) {
        LocalDate asOfDate = reportSupport.asOfDate(requestedDate);
        List<Employee> employees = reportSupport.eligibleEmployees(
            PAYROLL_REPORT_READ, asOfDate, organizationUnitId, workLocationId, employmentStatus
        );
        long total = employees.size();
        List<Long> employeeIds = employees.stream().map(Employee::getId).toList();
        // Một query lấy lương hiệu lực của cả danh sách, không query từng người.
        List<EmployeeSalaryHistory> salaryHistories = employeeIds.isEmpty()
            ? List.of()
            : employeeSalaryHistoryRepository.findEffectiveForEmployees(employeeIds, asOfDate);

        Map<Long, BigDecimal> salaries = new HashMap<>();
        // excl_employee_salary_history_overlap: một người có nhiều nhất một mức lương hiệu lực mỗi ngày.
        for (EmployeeSalaryHistory salaryHistory : salaryHistories) {
            salaries.put(salaryHistory.getEmployee().getId(), salaryHistory.getBaseSalary());
        }

        Map<String, Long> salaryCounts = initializedSalaryCounts();
        BigDecimal totalSalary = BigDecimal.ZERO;
        BigDecimal minimum = null;
        BigDecimal maximum = null;
        for (Employee employee : employees) {
            BigDecimal salary = salaries.get(employee.getId());
            if (salary == null) {
                salaryCounts.compute("NOT_CONFIGURED", (key, value) -> value + 1);
                continue;
            }
            totalSalary = totalSalary.add(salary);
            minimum = minimum == null || salary.compareTo(minimum) < 0 ? salary : minimum;
            maximum = maximum == null || salary.compareTo(maximum) > 0 ? salary : maximum;
            String bucket = salaryBucket(salary);
            salaryCounts.compute(bucket, (key, value) -> value + 1);
        }

        long withSalary = salaries.size();
        long missingSalary = total - withSalary;
        BigDecimal average = withSalary == 0
            ? null
            : totalSalary.divide(BigDecimal.valueOf(withSalary), 2, RoundingMode.HALF_UP);
        List<DistributionItemResponse> distribution = List.of(
            item("UNDER_10M", "Dưới 10 triệu", salaryCounts.get("UNDER_10M"), total),
            item("FROM_10M_TO_15M", "10 – dưới 15 triệu", salaryCounts.get("FROM_10M_TO_15M"), total),
            item("FROM_15M_TO_20M", "15 – dưới 20 triệu", salaryCounts.get("FROM_15M_TO_20M"), total),
            item("FROM_20M_TO_30M", "20 – dưới 30 triệu", salaryCounts.get("FROM_20M_TO_30M"), total),
            item("FROM_30M", "Từ 30 triệu", salaryCounts.get("FROM_30M"), total),
            item("NOT_CONFIGURED", "Chưa cấu hình", missingSalary, total)
        );

        return new PayrollSalaryReportResponse(
            asOfDate,
            "VND",
            total,
            withSalary,
            average,
            minimum,
            maximum,
            distribution,
            missingSalary
        );
    }

    private String salaryBucket(BigDecimal salary) {
        if (salary.compareTo(TEN_MILLION) < 0) return "UNDER_10M";
        if (salary.compareTo(FIFTEEN_MILLION) < 0) return "FROM_10M_TO_15M";
        if (salary.compareTo(TWENTY_MILLION) < 0) return "FROM_15M_TO_20M";
        if (salary.compareTo(THIRTY_MILLION) < 0) return "FROM_20M_TO_30M";
        return "FROM_30M";
    }

    private Map<String, Long> initializedSalaryCounts() {
        Map<String, Long> counts = new HashMap<>();
        counts.put("UNDER_10M", 0L);
        counts.put("FROM_10M_TO_15M", 0L);
        counts.put("FROM_15M_TO_20M", 0L);
        counts.put("FROM_20M_TO_30M", 0L);
        counts.put("FROM_30M", 0L);
        counts.put("NOT_CONFIGURED", 0L);
        return counts;
    }
}
