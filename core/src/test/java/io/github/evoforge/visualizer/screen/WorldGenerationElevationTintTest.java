package io.github.evoforge.visualizer.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import org.junit.jupiter.api.Test;

final class WorldGenerationElevationTintTest {
    private static final WorldBounds BOUNDS = new WorldBounds(-4, 4, -4, 4, -12, 12);

    @Test
    void zeroStrengthLeavesTerrainUntinted() {
        assertEquals(1f, WorldGenerationElevationTint.brightness(
                0L, BOUNDS, 0));
        assertEquals(1f, WorldGenerationElevationTint.brightness(
                8L * ElevationField.SUBUNITS_PER_CELL, BOUNDS, 0));
    }

    @Test
    void strongerTintDarkensLowTerrainMoreThanHighTerrain() {
        float low = WorldGenerationElevationTint.brightness(
                0L, BOUNDS, WorldGenerationElevationTint.SCALE);
        float high = WorldGenerationElevationTint.brightness(
                10L * ElevationField.SUBUNITS_PER_CELL,
                BOUNDS,
                WorldGenerationElevationTint.SCALE);

        assertTrue(low < high);
        assertTrue(high <= 1f);
    }

    @Test
    void tintStrengthInterpolatesBackTowardOriginalPresentation() {
        float full = WorldGenerationElevationTint.brightness(
                0L, BOUNDS, WorldGenerationElevationTint.SCALE);
        float half = WorldGenerationElevationTint.brightness(
                0L, BOUNDS, WorldGenerationElevationTint.SCALE / 2);

        assertTrue(full < half);
        assertTrue(half < 1f);
    }
}
