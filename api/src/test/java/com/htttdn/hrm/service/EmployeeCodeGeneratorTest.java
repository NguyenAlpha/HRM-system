package com.htttdn.hrm.service;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import com.htttdn.hrm.entity.JobPosition;
import com.htttdn.hrm.repository.JobPositionRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeCodeGeneratorTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private JobPositionRepository jobPositionRepository;

    @Test
    void directorAndGeneralStaffUseIndependentPrefixes() {
        when(jobPositionRepository.findByIdAndDeletedAtIsNull(1L))
            .thenReturn(Optional.of(position("DIRECTOR")));
        when(jobPositionRepository.findByIdAndDeletedAtIsNull(2L))
            .thenReturn(Optional.of(position("GENERAL_STAFF")));
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq("GD"))).thenReturn(1L, 2L);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq("NV"))).thenReturn(1L);

        EmployeeCodeGenerator generator = new EmployeeCodeGenerator(jdbcTemplate, jobPositionRepository);

        assertEquals("GD0001", generator.forPosition(1L));
        assertEquals("NV0001", generator.forPosition(2L));
        assertEquals("GD0002", generator.forPosition(1L));
    }

    @Test
    void companyOwnerGetsDirectorPrefixAndUnknownPositionGetsStaffPrefix() {
        when(jobPositionRepository.findByIdAndDeletedAtIsNull(3L))
            .thenReturn(Optional.of(position("CUSTOM_POSITION")));
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq("GD"))).thenReturn(5L);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq("NV"))).thenReturn(12L);

        EmployeeCodeGenerator generator = new EmployeeCodeGenerator(jdbcTemplate, jobPositionRepository);

        assertEquals("GD0005", generator.forDirector());
        assertEquals("NV0012", generator.forPosition(3L));
        verify(jdbcTemplate).queryForObject(anyString(), eq(Long.class), eq("GD"));
    }

    private JobPosition position(String code) {
        return JobPosition.builder().code(code).isActive(true).build();
    }
}
