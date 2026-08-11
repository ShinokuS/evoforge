package io.github.evoforge.simulation.world.landscape;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

final class LandscapeMutationBoundaryContractTest {

    private static final Pattern TERRAIN_SYSTEM_REFERENCE =
            Pattern.compile("\\bTerrainSystem\\b");

    private static final Pattern BLOCK_COMMENT =
            Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);

    private static final Pattern LINE_COMMENT =
            Pattern.compile("//.*$", Pattern.MULTILINE);

    private static final Set<Path> ALLOWED_OWNERS = Set.of(
            Path.of(
                    "io/github/evoforge/simulation/world/landscape/LandscapeSystem.java"),
            Path.of(
                    "io/github/evoforge/simulation/world/landscape/terrain/TerrainSystem.java"));

    @Test
    void productionCodeUsesTerrainSystemOnlyBehindLandscapeBoundary()
            throws IOException {

        Path mainJava = mainJava();

        try (var paths = Files.walk(mainJava)) {
            List<Path> javaFiles =
                    paths.filter(Files::isRegularFile)
                            .filter(path -> path.toString().endsWith(".java"))
                            .toList();

            for (Path javaFile : javaFiles) {
                Path relative = mainJava.relativize(javaFile);
                if (ALLOWED_OWNERS.contains(relative)) {
                    continue;
                }

                String source = withoutComments(
                        Files.readString(javaFile));

                assertFalse(
                        TERRAIN_SYSTEM_REFERENCE.matcher(source).find(),
                        () -> relative
                                + " bypasses LandscapeMutations by depending on TerrainSystem");
            }
        }
    }

    private static String withoutComments(String source) {
        return LINE_COMMENT.matcher(
                BLOCK_COMMENT.matcher(source)
                        .replaceAll(""))
                .replaceAll("");
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

        assertTrue(false, "cannot locate simulation main Java sources");
        throw new AssertionError();
    }
}
