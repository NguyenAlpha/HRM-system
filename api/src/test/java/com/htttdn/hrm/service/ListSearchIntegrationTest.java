package com.htttdn.hrm.service;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.request.account.AccountSearchCriteria;
import com.htttdn.hrm.dto.request.employee.EmployeeSearchCriteria;
import com.htttdn.hrm.dto.response.account.AccountResponse;
import com.htttdn.hrm.dto.response.employee.EmployeeSummaryResponse;
import com.htttdn.hrm.entity.enums.AccountStatus;
import com.htttdn.hrm.entity.enums.EmploymentStatus;
import com.htttdn.hrm.exception.BusinessException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Keyword search, filters and sorting of the employee and account lists against the real database.
 * Every row carries a random marker and every query searches for it, so other data never matches.
 */
@SpringBootTest
@Transactional
class ListSearchIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private EmployeeService employeeService;
    @Autowired private AccountAdminService accountAdminService;

    private String marker;
    private Long actorId;
    private Long unitId;

    @BeforeEach
    void seed() {
        marker = "srch" + UUID.randomUUID().toString().substring(0, 8);
        actorId = insertAccount("actor_" + UUID.randomUUID().toString().substring(0, 8), "ACTIVE", null);
        // employee.list.read is resolved from the actor's role assignments, so give it a company-wide HR role.
        assignRole(actorId, "HR_MANAGER");

        unitId = insertId("INSERT INTO organization_units(code, name, unit_type) VALUES (?, 'Search unit', 'DEPARTMENT') RETURNING id",
            marker.toUpperCase() + "_U");
        Long locationId = insertId("""
            INSERT INTO work_locations(code, name, location_type, address) VALUES (?, 'Search', 'HEAD_OFFICE', 'HN') RETURNING id
            """, marker.toUpperCase() + "_L");
        Long positionId = insertId("INSERT INTO job_positions(code, title) VALUES (?, 'Search role') RETURNING id",
            marker.toUpperCase() + "_P");

        Long alpha = insertEmployee("A", "Alpha " + marker, "ACTIVE");
        insertEmployee("B", "Bravo " + marker, "PROBATION");
        insertEmployee("C", "Charlie " + marker, "ACTIVE");
        jdbc.update("""
            INSERT INTO employee_assignments(employee_id, organization_unit_id, work_location_id, position_id,
                employment_type, effective_from, created_by_account_id)
            VALUES (?, ?, ?, ?, 'FULL_TIME', '2026-01-01', ?)
            """, alpha, unitId, locationId, positionId, actorId);

        Long hrAccount = insertAccount(marker + "_hr", "ACTIVE", alpha);
        assignRole(hrAccount, "HR_MANAGER");
        insertAccount(marker + "_off", "DISABLED", null);
        insertAccount(marker + "_new", "PENDING", null);
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void employeeListSearchesFiltersAndSorts() {
        authenticate("employee.list.read");
        PageRequest byName = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "fullName"));

        assertEquals(List.of("Charlie", "Bravo", "Alpha"), firstNames(employees(new EmployeeSearchCriteria(marker, null, null, null), byName)));
        assertEquals(List.of("Alpha"), firstNames(employees(new EmployeeSearchCriteria(marker.toUpperCase() + "A", null, null, null), byName)));
        assertEquals(List.of("Charlie", "Alpha"), firstNames(employees(new EmployeeSearchCriteria(marker, EmploymentStatus.ACTIVE, null, null), byName)));
        assertEquals(List.of("Alpha"), firstNames(employees(new EmployeeSearchCriteria(marker, null, unitId, null), byName)));
        assertThrows(BusinessException.class, () -> employeeService.list(
            new EmployeeSearchCriteria(marker, null, null, null), PageRequest.of(0, 20, Sort.by("nationalId"))));
    }

    @Test
    void accountListSearchesFiltersAndSorts() {
        authenticate("account.read");
        PageRequest byUsername = PageRequest.of(0, 20, Sort.by("username"));

        assertEquals(List.of(marker + "_hr", marker + "_new", marker + "_off"),
            usernames(new AccountSearchCriteria(marker, null, null), byUsername));
        assertEquals(List.of(marker + "_off"), usernames(new AccountSearchCriteria(marker, AccountStatus.DISABLED, null), byUsername));
        assertEquals(List.of(marker + "_hr"), usernames(new AccountSearchCriteria(marker, null, "HR_MANAGER"), byUsername));
        // The linked employee's name is searchable too.
        assertEquals(List.of(marker + "_hr"), usernames(new AccountSearchCriteria("alpha " + marker, null, null), byUsername));
        // Each row carries the linked employee's name so the list can show who the account belongs to.
        assertEquals(java.util.Arrays.asList("Alpha " + marker, null, null),
            accountAdminService.list(new AccountSearchCriteria(marker, null, null), byUsername).getContent().stream()
                .map(AccountResponse::employeeFullName).toList());
        // Each row lists the roles the account holds, so the web can tell protected accounts apart.
        assertEquals(List.of(List.of("HR_MANAGER"), List.of(), List.of()),
            accountAdminService.list(new AccountSearchCriteria(marker, null, null), byUsername).getContent().stream()
                .map(AccountResponse::roleCodes).toList());
        // LIKE wildcards in the keyword are matched literally.
        assertEquals(List.of(), usernames(new AccountSearchCriteria(marker + "%", null, null), byUsername));
        assertThrows(BusinessException.class, () -> accountAdminService.list(
            new AccountSearchCriteria(marker, null, null), PageRequest.of(0, 20, Sort.by("passwordHash"))));
    }

    private List<EmployeeSummaryResponse> employees(EmployeeSearchCriteria criteria, PageRequest page) {
        return employeeService.list(criteria, page).getContent();
    }

    private static List<String> firstNames(List<EmployeeSummaryResponse> employees) {
        return employees.stream().map(employee -> employee.fullName().split(" ")[0]).toList();
    }

    private List<String> usernames(AccountSearchCriteria criteria, PageRequest page) {
        return accountAdminService.list(criteria, page).getContent().stream().map(AccountResponse::username).toList();
    }

    private Long insertEmployee(String codeSuffix, String fullName, String status) {
        return insertId("""
            INSERT INTO employees(employee_code, full_name, hire_date, employment_status)
            VALUES (?, ?, '2026-01-01', ?) RETURNING id
            """, marker.toUpperCase() + codeSuffix, fullName, status);
    }

    private Long insertAccount(String username, String status, Long employeeId) {
        return insertId("""
            INSERT INTO accounts(username, email, password_hash, status, employee_id)
            VALUES (?, ?, CASE WHEN ? = 'PENDING' THEN NULL ELSE 'x' END, ?, ?) RETURNING id
            """, username, username + "@hrm.local", status, status, employeeId);
    }

    private void assignRole(Long accountId, String roleCode) {
        jdbc.update("""
            INSERT INTO account_role_assignments(account_id, role_id, scope_type, effective_from, granted_by_account_id)
            SELECT ?, id, 'COMPANY', '2026-01-01', ? FROM roles WHERE code = ?
            """, accountId, accountId, roleCode);
    }

    private Long insertId(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private void authenticate(String... authorities) {
        Jwt jwt = Jwt.withTokenValue("search-it").header("alg", "none").claim("accountId", actorId).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
            Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }
}
