package com.htttdn.hrm.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.EmployeeSalaryHistory;
import com.htttdn.hrm.entity.enums.EducationLevel;
import com.htttdn.hrm.entity.enums.EmploymentStatus;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.repository.EmployeeRepository;
import com.htttdn.hrm.repository.EmployeeSalaryHistoryRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock private EmployeeRepository employeeRepository;
    @Mock private EmployeeSalaryHistoryRepository employeeSalaryHistoryRepository;
    @Mock private EmployeeAccessScopeService employeeAccessScopeService;

    @Test
    void workforceReportGroupsEducationSeniorityAndMissingData() {
        LocalDate asOfDate = LocalDate.of(2026, 1, 1);
        Employee bachelor = employee(1L, LocalDate.of(2025, 1, 1), EducationLevel.BACHELOR);
        Employee missingEducation = employee(2L, LocalDate.of(2020, 1, 1), null);
        stubEmployees("report.hr.read", asOfDate, List.of(bachelor, missingEducation));

        var report = service().getWorkforceReport(asOfDate, null, null, null);

        assertEquals(2, report.totalEmployees());
        assertEquals(new BigDecimal("3.5"), report.averageSeniorityYears());
        assertEquals(1, report.missingEducation());
        assertEquals(1, count(report.educationDistribution(), "BACHELOR"));
        assertEquals(new BigDecimal("50.00"), percentage(report.educationDistribution(), "BACHELOR"));
        assertEquals(1, count(report.educationDistribution(), "NOT_UPDATED"));
        assertEquals(1, count(report.seniorityDistribution(), "ONE_TO_THREE_YEARS"));
        assertEquals(1, count(report.seniorityDistribution(), "FIVE_TO_TEN_YEARS"));
    }

    @Test
    void salaryReportUsesEffectiveBasicSalaryAndKeepsMissingEmployeesVisible() {
        LocalDate asOfDate = LocalDate.of(2026, 1, 1);
        Employee lowSalary = employee(1L, LocalDate.of(2025, 1, 1), EducationLevel.COLLEGE);
        Employee higherSalary = employee(2L, LocalDate.of(2024, 1, 1), EducationLevel.BACHELOR);
        Employee missingSalary = employee(3L, LocalDate.of(2023, 1, 1), EducationLevel.MASTER);
        stubEmployees(
            "report.payroll.read", asOfDate, List.of(lowSalary, higherSalary, missingSalary)
        );
        when(employeeSalaryHistoryRepository.findEffectiveForEmployees(anyList(), eq(asOfDate)))
            .thenReturn(List.of(
                salary(lowSalary, "9000000"),
                salary(higherSalary, "20000000")
            ));

        var report = service().getSalaryReport(asOfDate, null, null, null);

        assertEquals(3, report.totalEmployees());
        assertEquals(2, report.employeesWithBasicSalary());
        assertEquals(1, report.missingBasicSalary());
        assertEquals(new BigDecimal("14500000.00"), report.averageBasicSalary());
        assertEquals(new BigDecimal("9000000"), report.minimumBasicSalary());
        assertEquals(new BigDecimal("20000000"), report.maximumBasicSalary());
        assertEquals(1, count(report.salaryDistribution(), "UNDER_10M"));
        assertEquals(1, count(report.salaryDistribution(), "FROM_20M_TO_30M"));
        assertEquals(1, count(report.salaryDistribution(), "NOT_CONFIGURED"));
    }

    @Test
    void salaryReportReturnsNullStatisticsWhenNoSalaryIsConfigured() {
        LocalDate asOfDate = LocalDate.of(2026, 1, 1);
        Employee employee = employee(1L, LocalDate.of(2025, 1, 1), EducationLevel.COLLEGE);
        stubEmployees("report.payroll.read", asOfDate, List.of(employee));
        when(employeeSalaryHistoryRepository.findEffectiveForEmployees(anyList(), eq(asOfDate)))
            .thenReturn(List.of());

        var report = service().getSalaryReport(asOfDate, null, null, null);

        assertNull(report.averageBasicSalary());
        assertNull(report.minimumBasicSalary());
        assertNull(report.maximumBasicSalary());
        assertEquals(1, report.missingBasicSalary());
    }

    @Test
    void reportRejectsFutureDate() {
        assertThrows(
            BusinessException.class,
            () -> service().getWorkforceReport(LocalDate.now().plusDays(1), null, null, null)
        );
    }

    @SuppressWarnings("unchecked")
    private void stubEmployees(String permission, LocalDate date, List<Employee> employees) {
        var accessScope = new EmployeeAccessScopeService.EmployeeAccessScope(
            true, null, java.util.Set.of(), java.util.Set.of()
        );
        when(employeeAccessScopeService.resolve(permission)).thenReturn(accessScope);
        when(employeeRepository.findAll(any(Specification.class))).thenReturn(employees);
    }

    private Employee employee(Long id, LocalDate hireDate, EducationLevel educationLevel) {
        return Employee.builder()
            .id(id)
            .employeeCode("EMP" + id)
            .fullName("Employee " + id)
            .hireDate(hireDate)
            .seniorityStartDate(hireDate)
            .highestEducationLevel(educationLevel)
            .employmentStatus(EmploymentStatus.ACTIVE)
            .build();
    }

    private EmployeeSalaryHistory salary(Employee employee, String amount) {
        return EmployeeSalaryHistory.builder()
            .employee(employee)
            .baseSalary(new BigDecimal(amount))
            .effectiveFrom(LocalDate.of(2025, 1, 1))
            .build();
    }

    private long count(
        List<com.htttdn.hrm.dto.response.report.DistributionItemResponse> items,
        String key
    ) {
        return items.stream().filter(item -> item.key().equals(key)).findFirst().orElseThrow().count();
    }

    private BigDecimal percentage(
        List<com.htttdn.hrm.dto.response.report.DistributionItemResponse> items,
        String key
    ) {
        return items.stream().filter(item -> item.key().equals(key)).findFirst().orElseThrow().percentage();
    }

    private ReportService service() {
        return new ReportService(
            employeeRepository,
            employeeSalaryHistoryRepository,
            employeeAccessScopeService
        );
    }
}
