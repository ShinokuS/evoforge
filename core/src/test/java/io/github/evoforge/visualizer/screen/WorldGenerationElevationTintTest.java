package io.github.evoforge.visualizer.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.graphics.Color;
import io.github.evoforge.simulation.world.atlas.ElevationField;
import org.junit.jupiter.api.Test;

final class WorldGenerationElevationTintTest {
    private static final WorldGenerationElevationRange RANGE =
            new WorldGenerationElevationRange(0L, 10L * ElevationField.SUBUNITS_PER_CELL);

    @Test
    void zeroSensitivityUsesOneNeutralColorAcrossZ() {
        Color low = WorldGenerationElevationTint.color(0L, RANGE, 0, new Color());
        Color high = WorldGenerationElevationTint.color(
                8L * ElevationField.SUBUNITS_PER_CELL,
                RANGE,
                0,
                new Color());

        assertEquals(Color.rgba8888(low), Color.rgba8888(high));
    }

    @Test
    void maximumSensitivityMapsLowAndHighLandToClearlyDifferentGreenShades() {
        Color low = WorldGenerationElevationTint.color(
                0L,
                RANGE,
                WorldGenerationElevationTint.SCALE,
                new Color());
        Color high = WorldGenerationElevationTint.color(
                10L * ElevationField.SUBUNITS_PER_CELL,
                RANGE,
                WorldGenerationElevationTint.SCALE,
                new Color());

        assertTrue(low.g < high.g);
        assertTrue(low.r < high.r);
        assertTrue(low.g > low.r, "low color must remain green, not grayscale shading");
        assertTrue(high.g > high.r, "high color must remain green, not grayscale shading");
        assertTrue(high.r - low.r > 0.45f, "full palette should visibly separate low and high land");
    }

    @Test
    void strongerSensitivitySeparatesTheSameZDifferenceMoreClearly() {
        Color lowWeak = WorldGenerationElevationTint.color(
                2L * ElevationField.SUBUNITS_PER_CELL,
                RANGE,
                200_000,
                new Color());
        Color highWeak = WorldGenerationElevationTint.color(
                3L * ElevationField.SUBUNITS_PER_CELL,
                RANGE,
                200_000,
                new Color());
        Color lowStrong = WorldGenerationElevationTint.color(
                2L * ElevationField.SUBUNITS_PER_CELL,
                RANGE,
                1_000_000,
                new Color());
        Color highStrong = WorldGenerationElevationTint.color(
                3L * ElevationField.SUBUNITS_PER_CELL,
                RANGE,
                1_000_000,
                new Color());

        float weakDifference = colorDistance(lowWeak, highWeak);
        float strongDifference = colorDistance(lowStrong, highStrong);
        assertTrue(strongDifference > weakDifference);
    }

    @Test
    void texturedTerrainUsesNeutralShaderColorAtZeroSensitivity() {
        Color low = WorldGenerationElevationTint.shaderColor(0L, RANGE, 0, new Color());
        Color high = WorldGenerationElevationTint.shaderColor(
                RANGE.maximumSubunits(),
                RANGE,
                0,
                new Color());

        assertEquals(0.5f, low.r, 0.0001f);
        assertEquals(Color.rgba8888(low), Color.rgba8888(high));
    }

    private static float colorDistance(Color first, Color second) {
        return Math.abs(second.g - first.g)
                + Math.abs(second.r - first.r)
                + Math.abs(second.b - first.b);
    }
}
