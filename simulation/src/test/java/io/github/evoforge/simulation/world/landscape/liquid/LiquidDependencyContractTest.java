package io.github.evoforge.simulation.world.landscape.liquid;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class LiquidDependencyContractTest {

    private static final String WATER_IMPORT =
            "import io.github.evoforge.simulation.world.landscape.water.";

    @Test
    void genericLiquidFoundationDoesNotDependOnWaterIntegration()
            throws IOException {

        Path root = mainJava().resolve(
                "io/github/evoforge/simulation/world/landscape/liquid");
        assertTrue(Files.isDirectory(root), "missing liquid source directory: " + root);

        try (var paths = Files.walk(root)) {
            for (Path javaFile : paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList()) {

                String source = Files.readString(javaFile);
                assertFalse(
                        source.contains(WATER_IMPORT),
                        () -> javaFile
                                + " contains forbidden Water dependency "
                                + WATER_IMPORT);
            }
        }
    }

    private static Path mainJava() {
        Path moduleLocal = Path.of("src/main/java");
        if (Files.isDirectory(moduleLocal)) return moduleLocal;

        Path repositoryRelative = Path.of("simulation/src/main/java");
        if (Files.isDirectory(repositoryRelative)) return repositoryRelative;

        throw new IllegalStateException(
                "cannot locate simulation main Java sources");
    }
}
