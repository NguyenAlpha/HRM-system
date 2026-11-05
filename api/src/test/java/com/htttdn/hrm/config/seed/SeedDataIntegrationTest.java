package com.htttdn.hrm.config.seed;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.entity.AccountRoleAssignment;
import com.htttdn.hrm.repository.AccountRepository;
import com.htttdn.hrm.repository.AccountRoleAssignmentRepository;
import com.htttdn.hrm.service.rbac.RoleAssignmentPolicy;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs every seeder on a new, empty database, as {@code docker compose down -v} does. The seeders check their data
 * the way the API does (role scope, annual leave balance, payroll periods) and stop the start-up when it breaks a
 * rule, so a seed file that falls out of step with the rules fails here rather than on the next fresh dev database.
 *
 * <p>The database is created next to the one in {@code spring.datasource.url} and dropped afterwards.
 */
@SpringBootTest(properties = {
    "company.seed.enabled=true",
    "admin.seed.enabled=true",
    "owner.seed.enabled=true",
    "staff.seed.enabled=true",
    "payroll.seed.enabled=true",
    "attendance.auto-prepare.enabled=false",
    "rate-limit.enabled=false"
})
@DirtiesContext
class SeedDataIntegrationTest {

    private static final String SERVER_URL = System.getenv().getOrDefault("DB_URL", "jdbc:postgresql://localhost:5432/hrm");
    private static final String USERNAME = System.getenv().getOrDefault("DB_USERNAME", "hrm-user");
    private static final String PASSWORD = System.getenv().getOrDefault("DB_PASSWORD", "hrm-password");
    private static final String DATABASE = "hrm_seed_" + UUID.randomUUID().toString().replace("-", "");

    @Autowired private AccountRepository accountRepository;
    @Autowired private AccountRoleAssignmentRepository accountRoleAssignmentRepository;
    @Autowired private RoleAssignmentPolicy roleAssignmentPolicy;

    @DynamicPropertySource
    static void emptyDatabase(DynamicPropertyRegistry registry) throws SQLException {
        execute("CREATE DATABASE " + DATABASE);
        registry.add("spring.datasource.url", () -> SERVER_URL.substring(0, SERVER_URL.lastIndexOf('/') + 1) + DATABASE);
    }

    @AfterAll
    static void dropDatabase() throws SQLException {
        execute("DROP DATABASE IF EXISTS " + DATABASE + " WITH (FORCE)");
    }

    @Test
    @Transactional
    void everySeededRoleAssignmentHasAScopeTheRoleAllows() {
        // The staff seed ran: the start-up got past every seeder.
        assertTrue(accountRepository.findByUsername("employee").isPresent());

        List<AccountRoleAssignment> assignments = accountRoleAssignmentRepository.findAll();
        assertTrue(!assignments.isEmpty());
        for (AccountRoleAssignment assignment : assignments) {
            assertDoesNotThrow(() -> roleAssignmentPolicy.validateScope(assignment.getRole(), assignment.getScopeType()),
                () -> assignment.getAccount().getUsername() + " holds " + assignment.getRole().getCode()
                    + " on " + assignment.getScopeType());
        }
    }

    private static void execute(String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(SERVER_URL, USERNAME, PASSWORD);
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
