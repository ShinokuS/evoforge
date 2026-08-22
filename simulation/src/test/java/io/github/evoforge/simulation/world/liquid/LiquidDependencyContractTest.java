package io.github.evoforge.simulation.world.liquid;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class LiquidDependencyContractTest {

    private static final String WATER_IMPORT =
            "import io.github.evoforge.simulation.world.liquid.water.";

    @Test
    void genericLiquidFoundationDoesNotDependOnWaterIntegration()
            throws IOException {

        Path mainJava = mainJava();
        Path ownerRoot = mainJava.resolve(
                "io/github/evoforge/simulation/world/liquid");
        Path root = Files.isDirectory(ownerRoot)
                ? ownerRoot
                : mainJava.resolve(
                        "io/github/evoforge/simulation/world/landscape/liquid");
        assertTrue(Files.isDirectory(root), "missing liquid source directory: " + root);

        try (var paths = Files.walk(root)) {
            for (Path javaFile : paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !isWaterSpecialization(root, path))
                    .toList()) {

                String source = Files.readString(javaFile);
                assertFalse(
                        source.contains(WATER_IMPORT),
                        () -> javaFile
                                + " contains forbidden dependency from generic Liquid onto Water specialization");
            }
        }
    }

    private static boolean isWaterSpecialization(Path root, Path file) {
        Path relative = root.relativize(file);
        return relative.getNameCount() > 1
                && relative.getName(0).toString().equals("water");
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
