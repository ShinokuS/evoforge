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
    void genericControlDoesNotDependOnWorldDomains()
            throws IOException {

        Path mainJava = mainJava();

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
    void worldDomainsDoNotDependOnControl()
            throws IOException {

        assertNoImport(
                mainJava().resolve(
                        "io/github/evoforge/simulation/world"),
                CONTROL_IMPORT);
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
