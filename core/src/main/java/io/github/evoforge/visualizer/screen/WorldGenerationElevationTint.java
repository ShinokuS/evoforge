package io.github.evoforge.visualizer.screen;

import com.badlogic.gdx.graphics.Color;
import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import io.github.evoforge.visualizer.visual.TerrainElevationColorRamp;

/** Generated-world adapter for the shared terrain elevation color ramp. */
final class WorldGenerationElevationTint {
    static final int DEFAULT_STRENGTH_PPM =
            TerrainElevationColorRamp.DEFAULT_PREVIEW_SENSITIVITY_PPM;
    static final int SCALE = TerrainElevationColorRamp.SCALE;

    private WorldGenerationElevationTint() {
    }

    static Color color(
            long elevationSubunits,
            WorldBounds bounds,
            int sensitivityPpm,
            Color out) {
        if (bounds == null) throw new IllegalArgumentException("world bounds must not be null");

        long maximum = Math.max(
                ElevationField.SUBUNITS_PER_CELL,
                Math.multiplyExact(
                        (long) Math.max(1, bounds.maxZ()),
                        ElevationField.SUBUNITS_PER_CELL));
        return TerrainElevationColorRamp.color(
                Math.max(0L, elevationSubunits),
                0L,
                maximum,
                sensitivityPpm,
                out);
    }
}
