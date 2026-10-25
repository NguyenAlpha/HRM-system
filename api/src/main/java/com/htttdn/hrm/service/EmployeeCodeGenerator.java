package com.htttdn.hrm.service;

import java.util.Locale;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * Cấp mã nhân viên theo một dãy chung {@code NV0001}, {@code NV0002}…: số lớn nhất đang có cộng một.
 * Nhân viên đã xóa mềm vẫn giữ mã nên mã không bị tái sử dụng.
 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
@RequiredArgsConstructor
public class EmployeeCodeGenerator {

    static final String PREFIX = "NV";

    // Held until the transaction ends, so two employees created at once cannot read the same maximum.
    private static final String LOCK_SQL = "SELECT pg_advisory_xact_lock(hashtext('employee_code'))";

    private static final String NEXT_NUMBER_SQL = """
        SELECT COALESCE(MAX(CAST(SUBSTRING(employee_code FROM 3) AS BIGINT)), 0) + 1
        FROM employees
        WHERE employee_code ~ '^NV[0-9]+$'
        """;

    private final JdbcTemplate jdbcTemplate;

    public String next() {
        jdbcTemplate.queryForList(LOCK_SQL);
        Long number = jdbcTemplate.queryForObject(NEXT_NUMBER_SQL, Long.class);
        if (number == null) {
            throw new IllegalStateException("Employee code query did not return a number");
        }
        return PREFIX + String.format(Locale.ROOT, "%04d", number);
    }
}
