package com.htttdn.hrm.config.seed;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;

import com.htttdn.hrm.entity.Permission;
import com.htttdn.hrm.repository.PermissionRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Keeps the permission catalogue in {@link PermissionSeeder} and the code that checks permissions in step:
 * every code the catalogue offers is checked somewhere, and every code the main sources name is in the catalogue.
 *
 * <p>Permission codes are found as quoted dotted strings ({@code "employee.read"}, {@code 'employee.read'} in
 * {@code @PreAuthorize}). Repositories are skipped (JPQL paths such as {@code account.employee}), and so are
 * {@code @ConfigurationProperties} prefixes such as {@code staff.seed}.
 */
@ExtendWith(MockitoExtension.class)
class PermissionCatalogueTest {

    private static final Path MAIN_SOURCES = Path.of("src/main/java/com/htttdn/hrm");
    private static final Pattern DOTTED_LITERAL = Pattern.compile("[\"']([a-z_]+(?:\\.[a-z_]+)+)[\"']");
    /** Dotted strings in the main sources that are not permission codes. */
    private static final Set<String> NOT_PERMISSIONS = Set.of("http.server.requests");

    @Mock private PermissionRepository permissionRepository;
    @Mock private ApplicationArguments applicationArguments;

    @Test
    void everyCataloguedPermissionIsCheckedOutsideTheSeeders() {
        Map<String, Set<Path>> used = codesInSources();
        Set<String> unchecked = catalogue().stream()
            .filter(code -> used.getOrDefault(code, Set.of()).stream()
                .allMatch(file -> file.startsWith(MAIN_SOURCES.resolve("config/seed"))))
            .collect(Collectors.toCollection(TreeSet::new));

        assertEquals(Set.of(), unchecked, "Permissions nothing checks; use or remove them");
    }

    @Test
    void everyPermissionNamedInTheCodeIsCatalogued() {
        Set<String> catalogue = catalogue();
        Map<String, Set<Path>> unknown = new TreeMap<>(codesInSources());
        unknown.keySet().removeAll(catalogue);

        assertEquals(Map.of(), unknown, "Codes missing from PermissionSeeder; catalogue them or fix the typo");
    }

    /** The codes {@link PermissionSeeder} creates on an empty database. */
    private Set<String> catalogue() {
        when(permissionRepository.findByCode(any())).thenReturn(Optional.empty());
        new PermissionSeeder(permissionRepository).run(applicationArguments);

        ArgumentCaptor<Permission> captor = ArgumentCaptor.forClass(Permission.class);
        verify(permissionRepository, atLeastOnce()).save(captor.capture());
        return captor.getAllValues().stream().map(Permission::getCode).collect(Collectors.toSet());
    }

    /** Each dotted string in the main sources, with the files naming it. */
    private static Map<String, Set<Path>> codesInSources() {
        Map<String, Set<Path>> codes = new TreeMap<>();
        try (Stream<Path> files = Files.walk(MAIN_SOURCES)) {
            files.filter(file -> file.toString().endsWith(".java"))
                .filter(file -> !file.startsWith(MAIN_SOURCES.resolve("repository")))
                .filter(file -> !file.endsWith("PermissionSeeder.java"))
                .forEach(file -> lines(file)
                    .filter(line -> !line.contains("@ConfigurationProperties"))
                    .forEach(line -> {
                        Matcher matcher = DOTTED_LITERAL.matcher(line);
                        while (matcher.find()) {
                            if (!NOT_PERMISSIONS.contains(matcher.group(1))) {
                                codes.computeIfAbsent(matcher.group(1), key -> new TreeSet<>()).add(file);
                            }
                        }
                    }));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return codes;
    }

    private static Stream<String> lines(Path file) {
        try {
            return Files.readAllLines(file).stream();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
