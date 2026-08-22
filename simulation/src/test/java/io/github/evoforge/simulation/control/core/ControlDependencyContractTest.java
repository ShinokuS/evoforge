package io.github.evoforge.simulation.control.core;

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
    private static final String CONTROL_IMPORT =
            "import io.github.evoforge.simulation.control.";

    @Test
    void genericCommandInfrastructureDoesNotDependOnWorldDomains()
            throws IOException {

        Path mainJava = mainJava();
        Path kernelCommand = mainJava.resolve(
                "io/github/evoforge/simulation/kernel/command");

        if (Files.isDirectory(kernelCommand)) {
            assertNoImport(kernelCommand, WORLD_IMPORT);
            return;
        }

        // Transitional pre-ADR-025 locations. Remove this branch once the
        // owner-first package migration itself has been committed.
        assertNoImport(
                mainJava.resolve(
                        "io/github/evoforge/simulation/control/core"),
                WORLD_IMPORT);
        assertNoImport(
                mainJava.resolve(
                        "io/github/evoforge/simulation/control/sync"),
                WORLD_IMPORT);
    }

    @Test
    void worldDomainsDoNotDependOnLegacyControlPackages()
            throws IOException {

        assertNoImport(
                mainJava().resolve(
                        "io/github/evoforge/simulation/world"),
                CONTROL_IMPORT);
    }

    @Test
    void moveToOrchestrationDoesNotBypassMovementExecutionBoundary()
            throws IOException {

        Path source = mainJava().resolve(
                "io/github/evoforge/simulation/world/mechanics/movement/MoveToSystem.java");
        assertTrue(Files.isRegularFile(source), "missing source file: " + source);

        String text = Files.readString(source);
        assertFalse(text.contains(
                "import io.github.evoforge.simulation.world.spatial.SpatialSystem;"));
        assertFalse(text.contains(
                "import io.github.evoforge.simulation.world.navigation."));
        assertFalse(text.contains(
                "import io.github.evoforge.simulation.world.mechanics.occupancy."));
        assertFalse(text.contains(
                "import io.github.evoforge.simulation.world.mechanics.traversal."));
    }

    private static void assertNoImport(
            Path root,
            String forbiddenImport)
            throws IOException {

        assertTrue(
                Files.isDirectory(root),
                "missing source directory: " + root);

        try (var paths = Files.walk(root)) {
            List<Path> javaFiles =
                    paths.filter(Files::isRegularFile)
                            .filter(path -> path.toString().endsWith(".java"))
                            .toList();

            for (Path javaFile : javaFiles) {
                String source = Files.readString(javaFile);
                assertFalse(
                        source.contains(forbiddenImport),
                        () -> javaFile
                                + " contains forbidden dependency "
                                + forbiddenImport);
            }
        }
    }

    private static Path mainJava() {
        Path moduleLocal = Path.of("src/main/java");
        if (Files.isDirectory(moduleLocal)) {
            return moduleLocal;
        }

        Path repositoryRelative =
                Path.of("simulation/src/main/java");
        if (Files.isDirectory(repositoryRelative)) {
            return repositoryRelative;
        }

        throw new IllegalStateException(
                "cannot locate simulation main Java sources");
    }
}
