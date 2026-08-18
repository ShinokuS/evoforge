package io.github.evoforge.visualizer.screen;

import com.badlogic.gdx.graphics.Color;
import io.github.evoforge.visualizer.visual.TerrainElevationColorRamp;

/** Generated-world adapter for the shared terrain elevation palette. */
final class WorldGenerationElevationTint {
    static final int DEFAULT_STRENGTH_PPM =
            TerrainElevationColorRamp.DEFAULT_PREVIEW_SENSITIVITY_PPM;
    static final int SCALE = TerrainElevationColorRamp.SCALE;

    private WorldGenerationElevationTint() {
    }

    static Color color(
            long elevationSubunits,
            WorldGenerationElevationRange range,
            int sensitivityPpm,
            Color out) {
        if (range == null) throw new IllegalArgumentException("elevation range must not be null");
        return TerrainElevationColorRamp.color(
                Math.max(range.minimumSubunits(), elevationSubunits),
                range.minimumSubunits(),
                range.maximumSubunits(),
                sensitivityPpm,
                out);
    }

    static Color shaderColor(
            long elevationSubunits,
            WorldGenerationElevationRange range,
            int sensitivityPpm,
            Color out) {
        if (range == null) throw new IllegalArgumentException("elevation range must not be null");
        return TerrainElevationColorRamp.shaderColor(
                Math.max(range.minimumSubunits(), elevationSubunits),
                range.minimumSubunits(),
                range.maximumSubunits(),
                sensitivityPpm,
                out);
    }
}
