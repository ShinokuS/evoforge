package io.github.evoforge.simulation.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Repository-level fitness checks for ADR-026 semantic capability boundaries.
 *
 * <p>These checks intentionally cover only laws that can be inferred from source topology. Whether a
 * brand-new concept is semantically independent still requires the mandatory reuse test in AGENTS.md.
 */
final class SemanticCapabilityArchitectureContractTest {

    private static final String MECHANICS_IMPORT =
            "import io.github.evoforge.simulation.mechanics.";
    private static final String AGENTS_IMPORT =
            "import io.github.evoforge.simulation.agents.";

    private static final Set<String> CONSUMER_NEUTRAL_CAPABILITY_NAMES = Set.of(
            "occupancy",
            "navigation",
            "pathfinding",
            "geometry",
            "visibility",
            "placement",
            "admission");

    private static final Set<String> FORBIDDEN_ROOT_PACKAGES = Set.of(
            "capabilities",
            "common",
            "generation",
            "helpers",
            "misc",
            "physics",
            "services",
            "shared",
            "storage",
            "util",
            "utils");

    @Test
    void reusableWorldSemanticsDoNotDependOnHigherLevelConsumers() throws IOException {
        Path world = simulationPackageRoot().resolve("world");
        assertTrue(Files.isDirectory(world), "missing semantic world root: " + world);

        assertNoImport(world, MECHANICS_IMPORT);
        assertNoImport(world, AGENTS_IMPORT);
    }

    @Test
    void mechanicsCannotRecreateKnownConsumerNeutralCapabilities() throws IOException {
        Path mechanics = simulationPackageRoot().resolve("mechanics");
        assertTrue(Files.isDirectory(mechanics), "missing mechanics root: " + mechanics);

        try (var paths = Files.walk(mechanics)) {
            List<Path> forbidden = paths
                    .filter(Files::isDirectory)
                    .filter(path -> !path.equals(mechanics))
                    .filter(path -> CONSUMER_NEUTRAL_CAPABILITY_NAMES.contains(
                            path.getFileName().toString()))
                    .toList();

            assertTrue(
                    forbidden.isEmpty(),
                    () -> "consumer-neutral capability placed under mechanics: " + forbidden);
        }
    }

    @Test
    void legacyWorldMechanicsTreeCannotReappear() {
        Path legacy = simulationPackageRoot().resolve("world/mechanics");
        assertFalse(
                Files.exists(legacy),
                () -> "legacy consumer-owned world capability tree reappeared: " + legacy);
    }

    @Test
    void genericTechnicalRootPackagesCannotReappear() {
        Path root = simulationPackageRoot();
        List<Path> forbidden = FORBIDDEN_ROOT_PACKAGES.stream()
                .map(root::resolve)
                .filter(Files::exists)
                .sorted()
                .toList();

        assertTrue(
                forbidden.isEmpty(),
                () -> "forbidden global technical/dumping-root package exists: " + forbidden);
    }

    private static void assertNoImport(Path root, String forbiddenImport) throws IOException {
        try (var paths = Files.walk(root)) {
            List<Path> violations = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> contains(path, forbiddenImport))
                    .toList();

            assertTrue(
                    violations.isEmpty(),
                    () -> "lower-level semantic code depends on a higher-level consumer via "
                            + forbiddenImport + ": " + violations);
        }
    }

    private static boolean contains(Path path, String needle) {
        try {
            return Files.readString(path).contains(needle);
        } catch (IOException exception) {
            throw new IllegalStateException("cannot read source file: " + path, exception);
        }
    }

    private static Path simulationPackageRoot() {
        Path moduleLocal = Path.of("src/main/java/io/github/evoforge/simulation");
        if (Files.isDirectory(moduleLocal)) {
            return moduleLocal;
        }

        Path repositoryRelative =
                Path.of("simulation/src/main/java/io/github/evoforge/simulation");
        if (Files.isDirectory(repositoryRelative)) {
            return repositoryRelative;
        }

        throw new IllegalStateException("cannot locate simulation main Java package root");
    }
}
