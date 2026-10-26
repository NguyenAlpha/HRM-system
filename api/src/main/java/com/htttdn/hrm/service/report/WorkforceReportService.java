package com.htttdn.hrm.service.report;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.response.report.DistributionItemResponse;
import com.htttdn.hrm.dto.response.report.HrWorkforceReportResponse;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.enums.EducationLevel;
import com.htttdn.hrm.entity.enums.EmploymentStatus;

import lombok.RequiredArgsConstructor;

import static com.htttdn.hrm.service.report.ReportSupport.item;

/** Báo cáo cơ cấu nhân sự tại một ngày: phân bố theo trình độ học vấn và theo thâm niên. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class WorkforceReportService {

    private static final String HR_REPORT_READ = "report.hr.read";

    private final ReportSupport reportSupport;

    /**
     * Cơ cấu nhân sự của những người đang làm việc vào {@code requestedDate}, trong phạm vi
     * {@code report.hr.read} của người xem.
     *
     * @param requestedDate ngày chốt số liệu (null → hôm nay)
     * @param organizationUnitId lọc theo đơn vị của phân công chính ngày đó (null → không lọc)
     * @param workLocationId lọc theo địa điểm (null → không lọc)
     * @param employmentStatus lọc theo trạng thái làm việc (null → không lọc)
     * @return tổng số người, thâm niên bình quân (năm, 1 chữ số), phân bố trình độ (kèm dòng "Chưa cập
     *         nhật") và phân bố thâm niên
     * @throws com.htttdn.hrm.exception.BusinessException ngày ở tương lai (→ 400 {@code VALIDATION_ERROR})
     */
    @PreAuthorize("hasAuthority('report.hr.read')")
    public HrWorkforceReportResponse getWorkforceReport(
        LocalDate requestedDate,
        Long organizationUnitId,
        Long workLocationId,
        EmploymentStatus employmentStatus
    ) {
        LocalDate asOfDate = reportSupport.asOfDate(requestedDate);
        List<Employee> employees = reportSupport.eligibleEmployees(
            HR_REPORT_READ, asOfDate, organizationUnitId, workLocationId, employmentStatus
        );
        long total = employees.size();

        Map<EducationLevel, Long> educationCounts = new EnumMap<>(EducationLevel.class);
        for (EducationLevel level : EducationLevel.values()) {
            educationCounts.put(level, 0L);
        }
        long missingEducation = 0;
        long totalSeniorityMonths = 0;
        Map<String, Long> seniorityCounts = initializedSeniorityCounts();

        for (Employee employee : employees) {
            if (employee.getHighestEducationLevel() == null) {
                missingEducation++;
            } else {
                educationCounts.compute(employee.getHighestEducationLevel(), (key, value) -> value + 1);
            }

            long months = seniorityMonths(employee, asOfDate);
            totalSeniorityMonths += months;
            String bucket = seniorityBucket(months);
            seniorityCounts.compute(bucket, (key, value) -> value + 1);
        }

        List<DistributionItemResponse> education = new ArrayList<>();
        for (EducationLevel level : EducationLevel.values()) {
            education.add(item(level.name(), educationLabel(level), educationCounts.get(level), total));
        }
        education.add(item("NOT_UPDATED", "Chưa cập nhật", missingEducation, total));

        List<DistributionItemResponse> seniority = List.of(
            item("UNDER_ONE_YEAR", "Dưới 1 năm", seniorityCounts.get("UNDER_ONE_YEAR"), total),
            item("ONE_TO_THREE_YEARS", "1 – dưới 3 năm", seniorityCounts.get("ONE_TO_THREE_YEARS"), total),
            item("THREE_TO_FIVE_YEARS", "3 – dưới 5 năm", seniorityCounts.get("THREE_TO_FIVE_YEARS"), total),
            item("FIVE_TO_TEN_YEARS", "5 – dưới 10 năm", seniorityCounts.get("FIVE_TO_TEN_YEARS"), total),
            item("TEN_YEARS_OR_MORE", "Từ 10 năm", seniorityCounts.get("TEN_YEARS_OR_MORE"), total)
        );

        BigDecimal averageYears = total == 0
            ? BigDecimal.ZERO.setScale(1)
            : BigDecimal.valueOf(totalSeniorityMonths)
                .divide(BigDecimal.valueOf(total * 12L), 1, RoundingMode.HALF_UP);

        return new HrWorkforceReportResponse(
            asOfDate, total, averageYears, education, seniority, missingEducation
        );
    }

    /** Số tháng thâm niên tròn từ {@code seniorityStartDate} đến ngày chốt (hoặc ngày nghỉ việc nếu sớm hơn). */
    private long seniorityMonths(Employee employee, LocalDate asOfDate) {
        return Math.max(0, ChronoUnit.MONTHS.between(employee.getSeniorityStartDate(), employee.employedTo(asOfDate)));
    }

    private String seniorityBucket(long months) {
        if (months < 12) return "UNDER_ONE_YEAR";
        if (months < 36) return "ONE_TO_THREE_YEARS";
        if (months < 60) return "THREE_TO_FIVE_YEARS";
        if (months < 120) return "FIVE_TO_TEN_YEARS";
        return "TEN_YEARS_OR_MORE";
    }

    private Map<String, Long> initializedSeniorityCounts() {
        Map<String, Long> counts = new HashMap<>();
        counts.put("UNDER_ONE_YEAR", 0L);
        counts.put("ONE_TO_THREE_YEARS", 0L);
        counts.put("THREE_TO_FIVE_YEARS", 0L);
        counts.put("FIVE_TO_TEN_YEARS", 0L);
        counts.put("TEN_YEARS_OR_MORE", 0L);
        return counts;
    }

    private String educationLabel(EducationLevel level) {
        return switch (level) {
            case HIGH_SCHOOL -> "Trung học phổ thông";
            case COLLEGE -> "Cao đẳng";
            case BACHELOR -> "Đại học";
            case MASTER -> "Thạc sĩ";
            case DOCTORATE -> "Tiến sĩ";
        };
    }
}
