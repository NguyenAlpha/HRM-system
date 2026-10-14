package com.htttdn.hrm.service;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.request.employee.UpdateSelfProfileRequest;
import com.htttdn.hrm.dto.response.employee.SelfProfileResponse;
import com.htttdn.hrm.entity.enums.EducationLevel;
import com.htttdn.hrm.exception.BusinessException;

import jakarta.persistence.EntityManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** An employee keeps their contact details and education up to date; identity, tax and bank stay with HR. */
@SpringBootTest
@Transactional
class EmployeeSelfProfileIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;
    @Autowired private EmployeeService employeeService;

    private Long employeeId;
    private Long accountId;

    @BeforeEach
    void seed() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        employeeId = jdbc.queryForObject("""
            INSERT INTO employees(employee_code, full_name, hire_date, employment_status, national_id, bank_account_number, phone)
            VALUES (?, 'Self Profile', '2025-01-01', 'ACTIVE', '001090000001', '0123456789', '0900000000') RETURNING id
            """, Long.class, "SP" + suffix);
        accountId = jdbc.queryForObject("""
            INSERT INTO accounts(employee_id, username, email, password_hash, status) VALUES (?, ?, ?, 'x', 'ACTIVE') RETURNING id
            """, Long.class, employeeId, "sp_" + suffix, "sp_" + suffix + "@hrm.local");
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void theEmployeeUpdatesContactAndEducationOnly() {
        authenticate("profile.self.read", "profile.self.update");
        SelfProfileResponse saved = employeeService.updateSelfProfile(new UpdateSelfProfileRequest(
            " 0912345678 ", "Ca.Nhan@Mail.com", "12 Lê Lợi, Quận 1", EducationLevel.BACHELOR, "Kế toán", " ", (short) 2018));
        entityManager.flush();

        assertEquals("0912345678", saved.phone());
        assertEquals("ca.nhan@mail.com", saved.personalEmail());
        assertNull(saved.institution());
        assertEquals(saved, employeeService.getSelfProfile());
        Map<String, Object> row = jdbc.queryForMap(
            "SELECT full_name, national_id, bank_account_number, highest_education_level FROM employees WHERE id = ?", employeeId);
        assertEquals("Self Profile", row.get("full_name"));
        assertEquals("001090000001", row.get("national_id"));
        assertEquals("0123456789", row.get("bank_account_number"));
        assertEquals("BACHELOR", row.get("highest_education_level"));
    }

    @Test
    void updatingNeedsTheSelfUpdatePermissionAndAPlausibleGraduationYear() {
        UpdateSelfProfileRequest request = new UpdateSelfProfileRequest(null, null, null, null, null, null, (short) 2999);
        authenticate("profile.self.read");
        assertThrows(AuthorizationDeniedException.class, () -> employeeService.updateSelfProfile(request));
        authenticate("profile.self.read", "profile.self.update");
        assertThrows(BusinessException.class, () -> employeeService.updateSelfProfile(request));
    }

    private void authenticate(String... authorities) {
        Jwt jwt = Jwt.withTokenValue("self-profile-it").header("alg", "none").claim("accountId", accountId).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
            Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }
}
