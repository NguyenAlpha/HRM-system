package com.htttdn.hrm.service.report;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.dto.response.report.TurnoverReportResponse;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.exception.BusinessException;

import lombok.RequiredArgsConstructor;

/** Báo cáo biến động nhân sự của một năm: quân số đầu/cuối, số vào, số nghỉ và tỉ lệ nghỉ việc theo tháng. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TurnoverReportService {

    private static final String HR_REPORT_READ = "report.hr.read";

    private final ReportSupport reportSupport;
    private final Clock clock;

    /**
     * Biến động nhân sự của một năm. Phạm vi được xét tại từng ngày cần đếm (cuối tháng, ngày nghỉ việc)
     * vì phân công của người đã nghỉ đã đóng; nhờ vậy role theo đơn vị vẫn đếm được người rời đơn vị mình.
     *
     * <p>Năm hiện tại chỉ tính tới hết tháng này. Người vào làm được đếm nếu thuộc phạm vi ở cuối tháng vào
     * làm (hoặc ngày nghỉ việc, nếu nghỉ ngay trong tháng đó).
     *
     * @return từng tháng và cả năm, kèm danh sách sự kiện vào/nghỉ theo ngày rồi theo mã nhân viên
     * @throws BusinessException năm ở tương lai (→ 400 {@code VALIDATION_ERROR})
     */
    @PreAuthorize("hasAuthority('report.hr.read')")
    public TurnoverReportResponse getTurnoverReport(int year) {
        LocalDate today = LocalDate.now(clock);
        if (year > today.getYear()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "year must not be in the future", "year");
        }
        int lastMonth = year == today.getYear() ? today.getMonthValue() : 12;
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEnd = yearStart.withMonth(lastMonth).withDayOfMonth(yearStart.withMonth(lastMonth).lengthOfMonth());

        ReportSupport.Population population = reportSupport.population(HR_REPORT_READ, yearStart.minusDays(1), yearEnd);

        List<TurnoverReportResponse.TurnoverMonth> months = new ArrayList<>();
        List<TurnoverReportResponse.TurnoverEvent> events = new ArrayList<>();
        for (int month = 1; month <= lastMonth; month++) {
            LocalDate first = LocalDate.of(year, month, 1);
            LocalDate last = first.withDayOfMonth(first.lengthOfMonth());
            LocalDate previousEnd = first.minusDays(1);
            long start = population.headcount(previousEnd);
            long end = population.headcount(last);
            long hires = 0;
            long terminations = 0;
            for (Employee employee : population.candidates()) {
                LocalDate hired = employee.getHireDate();
                LocalDate ended = employee.getTerminationDate();
                if (!hired.isBefore(first) && !hired.isAfter(last)) {
                    LocalDate seenOn = ended != null && ended.isBefore(last) ? ended : last;
                    if (population.visible(employee, seenOn)) {
                        hires++;
                        events.add(event(employee, "HIRE", hired));
                    }
                }
                if (ended != null && !ended.isBefore(first) && !ended.isAfter(last)
                    && population.visible(employee, ended)) {
                    terminations++;
                    events.add(event(employee, "TERMINATION", ended));
                }
            }
            months.add(new TurnoverReportResponse.TurnoverMonth(month, start, hires, terminations, end, rate(terminations, start, end)));
        }

        long headcountStart = months.getFirst().headcountStart();
        long headcountEnd = months.getLast().headcountEnd();
        long hires = months.stream().mapToLong(TurnoverReportResponse.TurnoverMonth::hires).sum();
        long terminations = months.stream().mapToLong(TurnoverReportResponse.TurnoverMonth::terminations).sum();
        events.sort(Comparator.comparing(TurnoverReportResponse.TurnoverEvent::date).thenComparing(TurnoverReportResponse.TurnoverEvent::employeeCode));
        return new TurnoverReportResponse(year, headcountStart, headcountEnd, hires, terminations,
            rate(terminations, headcountStart, headcountEnd), months, events);
    }

    /** Số nghỉ việc chia quân số bình quân (đầu + cuối) / 2, tính theo phần trăm. */
    private BigDecimal rate(long terminations, long start, long end) {
        if (start + end == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return BigDecimal.valueOf(terminations * 200)
            .divide(BigDecimal.valueOf(start + end), 2, RoundingMode.HALF_UP);
    }

    /** Một sự kiện vào/nghỉ; chỉ sự kiện nghỉ việc mới kèm trạng thái và lý do nghỉ. */
    private TurnoverReportResponse.TurnoverEvent event(Employee employee, String type, LocalDate date) {
        boolean termination = "TERMINATION".equals(type);
        return new TurnoverReportResponse.TurnoverEvent(employee.getId(), employee.getEmployeeCode(), employee.getFullName(), type, date,
            termination ? employee.getEmploymentStatus() : null, termination ? employee.getTerminationReason() : null);
    }
}
