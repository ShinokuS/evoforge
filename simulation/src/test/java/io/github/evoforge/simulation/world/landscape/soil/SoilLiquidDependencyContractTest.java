package io.github.evoforge.simulation.world.landscape.soil;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

final class SoilLiquidDependencyContractTest {

    private static final String WATER_IMPORT =
            "import io.github.evoforge.simulation.world.landscape.water.";

    @Test
    void genericSoilLiquidFoundationDoesNotDependOnWaterIntegration()
            throws IOException {

        Path root = mainJava().resolve(
                "io/github/evoforge/simulation/world/landscape/soil");
        assertTrue(Files.isDirectory(root), "missing source directory: " + root);

        try (var paths = Files.list(root)) {
            List<Path> genericFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith("SoilLiquid"))
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList();

            assertFalse(genericFiles.isEmpty(), "missing generic Soil liquid sources");
            for (Path javaFile : genericFiles) {
                String source = Files.readString(javaFile);
                assertFalse(
                        source.contains(WATER_IMPORT),
                        () -> javaFile + " contains forbidden Water integration dependency");
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
