package com.htttdn.hrm.service;

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
    void allocationAdvancesOnlyTheSelectedPrefix() {
        Long nextDirectorNumber = jdbcTemplate.queryForObject(
            "SELECT COALESCE((SELECT next_number FROM employee_code_counters WHERE prefix = 'GD'), 1)",
            Long.class
        );
        Long nextStaffNumber = jdbcTemplate.queryForObject(
            "SELECT COALESCE((SELECT next_number FROM employee_code_counters WHERE prefix = 'NV'), 1)",
            Long.class
        );

        assertEquals("GD" + String.format("%04d", nextDirectorNumber), generator.forDirector());
        assertEquals("GD" + String.format("%04d", nextDirectorNumber + 1), generator.forDirector());
        assertEquals(nextStaffNumber, jdbcTemplate.queryForObject(
            "SELECT COALESCE((SELECT next_number FROM employee_code_counters WHERE prefix = 'NV'), 1)",
            Long.class
        ));
    }
}
