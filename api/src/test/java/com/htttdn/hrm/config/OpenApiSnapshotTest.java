package com.htttdn.hrm.config;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * The web app generates its TypeScript types from {@code apps/web/openapi.json}. Failing here
 * means a DTO or endpoint changed without refreshing that snapshot, so the web types are stale.
 *
 * <p>Refresh with {@code ./mvnw.cmd test -Dtest=OpenApiSnapshotTest -Dopenapi.snapshot.update=true},
 * then run {@code npm run gen:api} in {@code apps/web} and commit both files.
 */
@SpringBootTest(properties = {
    "company.seed.enabled=false",
    "rbac.seed.enabled=false",
    "admin.seed.enabled=false",
    "owner.seed.enabled=false",
    "staff.seed.enabled=false"
})
@AutoConfigureMockMvc
class OpenApiSnapshotTest {

    /** Surefire runs with the api module as working directory. */
    private static final Path SNAPSHOT = Path.of("..", "apps", "web", "openapi.json");

    @Autowired private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void committedSnapshotMatchesCurrentSpec() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode current = objectMapper.readTree(body);

        if (Boolean.getBoolean("openapi.snapshot.update")) {
            String pretty = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(current);
            Files.writeString(SNAPSHOT, pretty.replace("\r\n", "\n") + "\n", StandardCharsets.UTF_8);
            return;
        }

        assertThat(Files.exists(SNAPSHOT)).as("missing %s", SNAPSHOT).isTrue();
        JsonNode committed = objectMapper.readTree(Files.readString(SNAPSHOT, StandardCharsets.UTF_8));
        // Report only what changed; printing both 200 KB documents would bury the instructions.
        assertThat(changedEntries(current, committed))
            .as("""
                apps/web/openapi.json is out of date with the API.
                Run: ./mvnw.cmd test -Dtest=OpenApiSnapshotTest -Dopenapi.snapshot.update=true
                then in apps/web: npm run gen:api, and commit openapi.json and lib/api-schema.ts.
                Changed""")
            .isEmpty();
    }

    /** Paths and schemas that differ, plus any other top-level section that differs. */
    private static List<String> changedEntries(JsonNode current, JsonNode committed) {
        List<String> changed = new ArrayList<>();
        Set<String> sections = new TreeSet<>(current.propertyNames());
        sections.addAll(committed.propertyNames());
        for (String section : sections) {
            if (section.equals("paths")) {
                diffKeys("path ", current.path("paths"), committed.path("paths"), changed);
            } else if (section.equals("components")) {
                diffKeys("schema ", current.path("components").path("schemas"),
                    committed.path("components").path("schemas"), changed);
                if (!current.path("components").path("securitySchemes")
                    .equals(committed.path("components").path("securitySchemes"))) {
                    changed.add("components.securitySchemes");
                }
            } else if (!current.path(section).equals(committed.path(section))) {
                changed.add(section);
            }
        }
        return changed;
    }

    private static void diffKeys(String label, JsonNode current, JsonNode committed, List<String> changed) {
        Set<String> keys = new TreeSet<>(current.propertyNames());
        keys.addAll(committed.propertyNames());
        keys.stream()
            .filter(key -> !current.path(key).equals(committed.path(key)))
            .forEach(key -> changed.add(label + key));
    }
}
