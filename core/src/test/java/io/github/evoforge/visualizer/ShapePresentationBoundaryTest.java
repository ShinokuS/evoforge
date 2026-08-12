package io.github.evoforge.visualizer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ShapePresentationBoundaryTest {

    private static final String RAMP_IMPORT =
            "import io.github.evoforge.simulation.world.mechanics.geometry.RampShape;";
    private static final String FULL_IMPORT =
            "import io.github.evoforge.simulation.world.mechanics.geometry.FullShape;";

    @Test
    void genericPresentationDoesNotKnowConcreteShapeTypes()
            throws IOException {

        Path mainJava = mainJava();
        for (String relative : List.of(
                "io/github/evoforge/visualizer/ZLevelVisualizer.java",
                "io/github/evoforge/visualizer/VisualizerState.java",
                "io/github/evoforge/visualizer/VisualizerCamera.java",
                "io/github/evoforge/visualizer/VisualizerInputController.java",
                "io/github/evoforge/visualizer/render/LandscapeRenderer.java",
                "io/github/evoforge/visualizer/render/VisualizerOverlayRenderer.java",
                "io/github/evoforge/visualizer/render/VisualizerHudRenderer.java",
                "io/github/evoforge/visualizer/visual/ProceduralLandscapePack.java")) {

            Path file = mainJava.resolve(relative);
            assertTrue(Files.isRegularFile(file), "missing source file: " + file);
            String source = Files.readString(file);
            assertFalse(
                    source.contains(RAMP_IMPORT),
                    () -> file + " imports concrete RampShape");
            assertFalse(
                    source.contains(FULL_IMPORT),
                    () -> file + " imports concrete FullShape");
        }
    }

    private static Path mainJava() {
        Path moduleLocal = Path.of("src/main/java");
        if (Files.isDirectory(moduleLocal)) {
            return moduleLocal;
        }

        Path repositoryRelative = Path.of("core/src/main/java");
        if (Files.isDirectory(repositoryRelative)) {
            return repositoryRelative;
        }

        throw new IllegalStateException(
                "cannot locate core main Java sources");
    }
}
