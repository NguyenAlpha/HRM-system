package com.htttdn.hrm.config.seed;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.entity.JobPosition;
import com.htttdn.hrm.repository.JobPositionRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Auditing stamps the current time; SeedTimestamps moves seed data back to its own dates. */
@SpringBootTest(properties = {
    "company.seed.enabled=false",
    "admin.seed.enabled=false",
    "owner.seed.enabled=false",
    "staff.seed.enabled=false",
    "payroll.seed.enabled=false",
    "attendance.auto-prepare.enabled=false",
    "rate-limit.enabled=false"
})
@Transactional
class SeedTimestampsIntegrationTest {

    @Autowired private JobPositionRepository jobPositionRepository;
    @Autowired private SeedTimestamps seedTimestamps;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void auditingStampsNowOnInsertAndMovesOnlyUpdatedAtOnUpdate() {
        Instant before = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        JobPosition position = jobPositionRepository.saveAndFlush(newPosition());
        Instant createdAt = position.getCreatedAt();

        // Within the test's run, so not shifted by the Vietnam time zone either way.
        assertTrue(!createdAt.isBefore(before) && createdAt.isBefore(Instant.now().plusSeconds(1)), createdAt.toString());
        assertEquals(createdAt, position.getUpdatedAt());

        position.setTitle("Renamed");
        jobPositionRepository.saveAndFlush(position);

        assertEquals(createdAt, position.getCreatedAt());
        assertTrue(position.getUpdatedAt().isAfter(createdAt));
    }

    @Test
    void seedDatesReplaceTheAuditedOnesWhenApplied() {
        Instant created = Instant.parse("2021-03-01T01:00:00Z");
        Instant updated = created.plus(Duration.ofDays(30));
        Long id = jobPositionRepository.save(newPosition()).getId();
        JobPosition position = jobPositionRepository.findById(id).orElseThrow();
        seedTimestamps.set(position, created, null);
        // A later change after the dates were given: the last date given still wins.
        position.setTitle("Renamed");
        seedTimestamps.set(position, null, updated);
        assertEquals(created, seedTimestamps.createdAt(position));

        seedTimestamps.apply();

        assertEquals(created, column("created_at", id));
        assertEquals(updated, column("updated_at", id));
        assertEquals("Renamed", jobPositionRepository.findById(id).orElseThrow().getTitle());
    }

    private Instant column(String column, Long id) {
        return jdbc.queryForObject("SELECT " + column + " FROM job_positions WHERE id = ?", OffsetDateTime.class, id)
            .toInstant();
    }

    private static JobPosition newPosition() {
        return JobPosition.builder()
            .code("ST_" + UUID.randomUUID().toString().substring(0, 8))
            .title("Seed timestamps")
            .isManagerial(false)
            .isActive(true)
            .build();
    }
}
