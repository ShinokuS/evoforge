package io.github.evoforge.simulation.kernel.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

final class ControlDependencyContractTest {

    private static final String WORLD_IMPORT =
            "import io.github.evoforge.simulation.world.";
    private static final String LEGACY_CONTROL_IMPORT =
            "import io.github.evoforge.simulation.control.";

    @Test
    void genericCommandInfrastructureDoesNotDependOnWorldDomains()
            throws IOException {

        assertNoImport(
                mainJava().resolve(
                        "io/github/evoforge/simulation/kernel/command"),
                WORLD_IMPORT);
    }

    @Test
    void worldDomainsDoNotDependOnLegacyControlPackages()
            throws IOException {

        assertNoImport(
                mainJava().resolve(
                        "io/github/evoforge/simulation/world"),
                LEGACY_CONTROL_IMPORT);
    }

    @Test
    void moveToOrchestrationDoesNotBypassMovementExecutionBoundary()
            throws IOException {

        Path source = uniqueSource(mainJava(), "MoveToSystem.java");
        String text = Files.readString(source);

        // MoveTo may consume read-only transform/pathfinding capabilities, but
        // authoritative physical mutation must still go through MovementSystem.
        assertFalse(text.matches("(?s).*import\\s+[^;]*\\.SpatialSystem;.*"));
        assertFalse(text.matches("(?s).*import\\s+[^;]*\\.OccupancySystem;.*"));
        assertFalse(text.matches("(?s).*import\\s+[^;]*\\.NavigationSystem;.*"));
    }

    private static Path uniqueSource(Path root, String fileName)
            throws IOException {

        try (var paths = Files.walk(root)) {
            List<Path> matches = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals(fileName))
                    .toList();
            assertEquals(1, matches.size(), "expected one source named " + fileName);
            return matches.getFirst();
        }
    }

    private static void assertNoImport(
            Path root,
            String forbiddenImport)
            throws IOException {

        assertTrue(Files.isDirectory(root), "missing source directory: " + root);

        try (var paths = Files.walk(root)) {
            List<Path> javaFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList();

            for (Path javaFile : javaFiles) {
                String source = Files.readString(javaFile);
                assertFalse(
                        source.contains(forbiddenImport),
                        () -> javaFile + " contains forbidden dependency " + forbiddenImport);
            }
        }
    }

    private static Path mainJava() {
        Path moduleLocal = Path.of("src/main/java");
        if (Files.isDirectory(moduleLocal)) return moduleLocal;

        Path repositoryRelative = Path.of("simulation/src/main/java");
        if (Files.isDirectory(repositoryRelative)) return repositoryRelative;

        throw new IllegalStateException("cannot locate simulation main Java sources");
    }
}
