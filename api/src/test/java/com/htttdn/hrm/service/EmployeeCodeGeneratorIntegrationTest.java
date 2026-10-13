package com.htttdn.hrm.service;

import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Transactional
class EmployeeCodeGeneratorIntegrationTest {

    @Autowired private EmployeeCodeGenerator generator;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void nextCodeFollowsTheHighestCodeIncludingDeletedEmployees() {
        // Far above any seeded code, so the test holds on a seeded or an empty database.
        long highest = ThreadLocalRandom.current().nextLong(5_000_000, 9_000_000);
        insertEmployee("NV" + highest, true);
        // Codes outside the NV + digits series do not count.
        insertEmployee("NVX" + (highest + 50), false);
        insertEmployee("GD" + (highest + 50), false);

        assertEquals("NV" + (highest + 1), generator.next());
    }

    private void insertEmployee(String code, boolean deleted) {
        jdbcTemplate.update("""
            INSERT INTO employees (employee_code, full_name, hire_date, seniority_start_date, employment_status, deleted_at)
            VALUES (?, 'Code test', CURRENT_DATE, CURRENT_DATE, 'ACTIVE', CASE WHEN ? THEN now() END)
            """, code, deleted);
    }
}
