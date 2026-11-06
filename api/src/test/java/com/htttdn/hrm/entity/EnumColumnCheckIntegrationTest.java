package com.htttdn.hrm.entity;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import jakarta.persistence.Column;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every enum column of an entity has a CHECK in the database that allows exactly the Java constants, and the column
 * is wide enough for the longest one. {@code ddl-auto=validate} looks at column names and types only, so a constant
 * added to an enum without the migration would otherwise surface as a 409 CONFLICT the first time it is saved.
 */
@SpringBootTest
class EnumColumnCheckIntegrationTest {

    /** Columns whose CHECK allows fewer values than the enum on purpose, with the values it allows. */
    private static final Map<String, Set<String>> NARROWER_ON_PURPOSE = Map.of(
        // The status an employment will end with: only the ended ones.
        "employees.scheduled_end_status", Set.of("RESIGNED", "TERMINATED", "RETIRED")
    );

    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void everyEnumColumnHasACheckThatMatchesTheJavaConstants() {
        List<String> problems = new ArrayList<>();
        int checked = 0;
        for (var entity : entityManager.getMetamodel().getEntities()) {
            Class<?> type = entity.getJavaType();
            Table table = type.getAnnotation(Table.class);
            String tableName = table != null && !table.name().isEmpty() ? table.name() : snake(type.getSimpleName());
            for (Field field : fieldsOf(type)) {
                if (!field.isAnnotationPresent(Enumerated.class)) {
                    continue;
                }
                checked++;
                Column column = field.getAnnotation(Column.class);
                String columnName = column != null && !column.name().isEmpty() ? column.name() : snake(field.getName());
                String key = tableName + "." + columnName;
                Set<String> constants = new TreeSet<>(Arrays.stream(field.getType().getEnumConstants())
                    .map(constant -> ((Enum<?>) constant).name()).toList());

                Set<String> allowed = allowedValues(tableName, columnName);
                Set<String> expected = NARROWER_ON_PURPOSE.getOrDefault(key, constants);
                if (allowed == null) {
                    problems.add(key + ": no CHECK");
                } else if (!allowed.equals(expected)) {
                    problems.add(key + ": CHECK allows " + new TreeSet<>(allowed) + ", Java has " + expected);
                }

                Integer length = jdbc.queryForObject("""
                    SELECT character_maximum_length FROM information_schema.columns
                    WHERE table_schema = current_schema() AND table_name = ? AND column_name = ?
                    """, Integer.class, tableName, columnName);
                int longest = constants.stream().mapToInt(String::length).max().orElse(0);
                if (length == null || length < longest) {
                    problems.add(key + ": VARCHAR(" + length + ") is shorter than " + longest);
                }
            }
        }

        assertTrue(checked > 0, "no @Enumerated field found");
        assertEquals(List.of(), problems);
    }

    /** Values the table's CHECKs allow for the column, or null when no CHECK names it. */
    private Set<String> allowedValues(String table, String column) {
        List<String> definitions = jdbc.queryForList("""
            SELECT pg_get_constraintdef(c.oid) FROM pg_constraint c JOIN pg_class t ON t.oid = c.conrelid
            WHERE c.contype = 'c' AND t.relname = ? AND t.relnamespace = current_schema()::regnamespace
            """, String.class, table);
        // "(col)::text = ANY ((ARRAY['A'::character varying, …])::text[])" and "(col)::text = 'A'::text"
        Pattern anyOf = Pattern.compile("\\(" + column + "\\)::text = ANY \\(\\(ARRAY\\[([^\\]]*)\\]");
        Pattern single = Pattern.compile("\\(" + column + "\\)::text = '([^']*)'::text");
        Pattern quoted = Pattern.compile("'([^']*)'");
        Set<String> values = null;
        for (String definition : definitions) {
            Matcher list = anyOf.matcher(definition);
            while (list.find()) {
                values = values == null ? new HashSet<>() : values;
                Matcher value = quoted.matcher(list.group(1));
                while (value.find()) {
                    values.add(value.group(1));
                }
            }
            Matcher one = single.matcher(definition);
            while (one.find()) {
                values = values == null ? new HashSet<>() : values;
                values.add(one.group(1));
            }
        }
        return values;
    }

    private static List<Field> fieldsOf(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            fields.addAll(Arrays.asList(current.getDeclaredFields()));
        }
        return fields;
    }

    private static String snake(String name) {
        return name.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    }
}
