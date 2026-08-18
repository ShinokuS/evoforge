package io.github.evoforge.visualizer.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.graphics.Color;
import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import org.junit.jupiter.api.Test;

final class WorldGenerationElevationTintTest {
    private static final WorldBounds BOUNDS = new WorldBounds(-4, 4, -4, 4, -12, 12);

    @Test
    void zeroSensitivityUsesOneNeutralGreenAcrossZ() {
        Color low = WorldGenerationElevationTint.color(0L, BOUNDS, 0, new Color());
        Color high = WorldGenerationElevationTint.color(
                8L * ElevationField.SUBUNITS_PER_CELL,
                BOUNDS,
                0,
                new Color());

        assertEquals(Color.rgba8888(low), Color.rgba8888(high));
    }

    @Test
    void maximumSensitivityMapsLowAndHighLandToDifferentGreenShades() {
        Color low = WorldGenerationElevationTint.color(
                0L,
                BOUNDS,
                WorldGenerationElevationTint.SCALE,
                new Color());
        Color high = WorldGenerationElevationTint.color(
                10L * ElevationField.SUBUNITS_PER_CELL,
                BOUNDS,
                WorldGenerationElevationTint.SCALE,
                new Color());

        assertTrue(low.g < high.g);
        assertTrue(low.r < high.r);
        assertTrue(low.g > low.r, "low color must remain green, not grayscale shading");
        assertTrue(high.g > high.r, "high color must remain green, not grayscale shading");
    }

    @Test
    void strongerSensitivitySeparatesTheSameZDifferenceMoreClearly() {
        Color lowWeak = WorldGenerationElevationTint.color(
                2L * ElevationField.SUBUNITS_PER_CELL,
                BOUNDS,
                200_000,
                new Color());
        Color highWeak = WorldGenerationElevationTint.color(
                3L * ElevationField.SUBUNITS_PER_CELL,
                BOUNDS,
                200_000,
                new Color());
        Color lowStrong = WorldGenerationElevationTint.color(
                2L * ElevationField.SUBUNITS_PER_CELL,
                BOUNDS,
                1_000_000,
                new Color());
        Color highStrong = WorldGenerationElevationTint.color(
                3L * ElevationField.SUBUNITS_PER_CELL,
                BOUNDS,
                1_000_000,
                new Color());

        float weakDifference = Math.abs(highWeak.g - lowWeak.g)
                + Math.abs(highWeak.r - lowWeak.r)
                + Math.abs(highWeak.b - lowWeak.b);
        float strongDifference = Math.abs(highStrong.g - lowStrong.g)
                + Math.abs(highStrong.r - lowStrong.r)
                + Math.abs(highStrong.b - lowStrong.b);
        assertTrue(strongDifference > weakDifference);
    }
}
