package com.htttdn.hrm.service.employee;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.repository.EmployeeRepository;

import jakarta.persistence.EntityManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A record created by mistake and soft-deleted gives back its national ID and work email, so the right record can be
 * created with them; a record not deleted still keeps both. Runs on an empty or seeded database inside a rolled-back
 * transaction.
 */
@SpringBootTest
@Transactional
class SoftDeletedEmployeeIdentityIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;
    @Autowired private Clock clock;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private EmployeeProvisioningService provisioningService;

    @Test
    void theRightRecordReusesTheNationalIdAndEmailOfOneDeletedByMistake() {
        String marker = UUID.randomUUID().toString().substring(0, 8);
        String nationalId = "079" + marker;
        String email = "real." + marker + "@company.vn";
        employee(nationalId, email, true);

        Long rightId = provisioningService.createActiveMinimalEmployee(new EmployeeProvisioningService.MinimalEmployeeCommand(
            "Người Thật", email.toUpperCase(), null, LocalDate.now(clock), "workEmail")).getId();
        entityManager.flush();

        assertFalse(employeeRepository.existsByNationalIdAndIdNotAndDeletedAtIsNull(nationalId, rightId));
        jdbc.update("UPDATE employees SET national_id = ? WHERE id = ?", nationalId, rightId);
        assertEquals(nationalId, jdbc.queryForObject("SELECT national_id FROM employees WHERE id = ?", String.class, rightId));
    }

    @Test
    void aRecordNotDeletedKeepsItsNationalIdAndEmail() {
        String marker = UUID.randomUUID().toString().substring(0, 8);
        String nationalId = "079" + marker;
        String email = "live." + marker + "@company.vn";
        employee(nationalId, email, false);
        Long otherId = employee(null, null, false);

        assertTrue(employeeRepository.existsByNationalIdAndIdNotAndDeletedAtIsNull(nationalId, otherId));
        ConflictException taken = assertThrows(ConflictException.class,
            () -> provisioningService.createActiveMinimalEmployee(new EmployeeProvisioningService.MinimalEmployeeCommand(
                "Người Khác", email, null, LocalDate.now(clock), "workEmail")));
        assertEquals(ErrorCode.EMAIL_TAKEN, taken.getErrorCode());
        // The database refuses it as well, for writes that skip the service.
        assertThrows(DuplicateKeyException.class,
            () -> jdbc.update("UPDATE employees SET national_id = ? WHERE id = ?", nationalId, otherId));
    }

    private Long employee(String nationalId, String email, boolean deleted) {
        return jdbc.queryForObject("""
            INSERT INTO employees(employee_code, full_name, hire_date, seniority_start_date, employment_status,
                national_id, work_email, deleted_at, deletion_reason)
            VALUES (?, 'Hồ Sơ', '2025-01-01', '2025-01-01', 'ACTIVE', ?, ?, ?, ?) RETURNING id
            """, Long.class, "SDI" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(), nationalId, email,
            deleted ? Timestamp.from(clock.instant()) : null, deleted ? "Tạo nhầm" : null);
    }
}
