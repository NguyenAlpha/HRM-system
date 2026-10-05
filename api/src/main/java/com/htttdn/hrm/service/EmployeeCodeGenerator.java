package com.htttdn.hrm.service;

import java.util.Map;
import java.util.Locale;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.exception.ResourceNotFoundException;
import com.htttdn.hrm.repository.JobPositionRepository;

@Service
@Transactional(propagation = Propagation.MANDATORY)
public class EmployeeCodeGenerator {

    private static final Map<String, String> PREFIX_BY_POSITION = Map.of(
        "DIRECTOR", "GD",
        "HR_SPECIALIST", "NS",
        "PAYROLL_ACCOUNTANT", "KT",
        "OPERATIONS_MANAGER", "VH",
        "BRANCH_MANAGER", "CN",
        "WAREHOUSE_SUPERVISOR", "KHO",
        "TEAM_LEAD", "TN",
        "GENERAL_STAFF", "NV"
    );

    private static final String NEXT_NUMBER_SQL = """
        INSERT INTO employee_code_counters (prefix, next_number)
        VALUES (?, 2)
        ON CONFLICT (prefix)
        DO UPDATE SET next_number = employee_code_counters.next_number + 1
        RETURNING next_number - 1
        """;

    private final JdbcTemplate jdbcTemplate;
    private final JobPositionRepository jobPositionRepository;

    public EmployeeCodeGenerator(JdbcTemplate jdbcTemplate, JobPositionRepository jobPositionRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.jobPositionRepository = jobPositionRepository;
    }

    public String forPosition(Long positionId) {
        String positionCode = jobPositionRepository.findByIdAndDeletedAtIsNull(positionId)
            .filter(position -> Boolean.TRUE.equals(position.getIsActive()))
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.JOB_POSITION_NOT_FOUND,
                "Active job position not found: " + positionId
            ))
            .getCode();
        return nextCode(PREFIX_BY_POSITION.getOrDefault(positionCode, "NV"));
    }

    public String forDirector() {
        return nextCode(PREFIX_BY_POSITION.get("DIRECTOR"));
    }

    private String nextCode(String prefix) {
        Long number = jdbcTemplate.queryForObject(NEXT_NUMBER_SQL, Long.class, prefix);
        if (number == null) {
            throw new IllegalStateException("Employee code counter did not return a number");
        }
        return prefix + String.format(Locale.ROOT, "%04d", number);
    }
}
