package io.github.evoforge.simulation.world.landscape;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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

    @Test
    void productionCodeUsesTerrainSystemOnlyBehindCurrentTerrainBoundary()
            throws IOException {

        Path mainJava = mainJava();
        Set<Path> allowedOwners = Set.of(
                uniqueSource(mainJava, "LandscapeSystem.java"),
                uniqueSource(mainJava, "TerrainSystem.java"));

        try (var paths = Files.walk(mainJava)) {
            List<Path> javaFiles =
                    paths.filter(Files::isRegularFile)
                            .filter(path -> path.toString().endsWith(".java"))
                            .toList();

            for (Path javaFile : javaFiles) {
                if (allowedOwners.contains(javaFile)) {
                    continue;
                }

                String source = withoutComments(
                        Files.readString(javaFile));

                assertFalse(
                        TERRAIN_SYSTEM_REFERENCE.matcher(source).find(),
                        () -> mainJava.relativize(javaFile)
                                + " bypasses the current Terrain mutation boundary by depending on TerrainSystem");
            }
        }
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

        throw new IllegalStateException(
                "cannot locate simulation main Java sources");
    }
}
