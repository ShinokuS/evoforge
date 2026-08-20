package io.github.evoforge.visualizer.screen;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import io.github.evoforge.visualizer.visual.TerrainElevationColorRamp;

/** Generated-world adapter for the shared terrain elevation palette plus submerged depth shading. */
final class WorldGenerationElevationTint {
    static final int DEFAULT_STRENGTH_PPM =
            TerrainElevationColorRamp.DEFAULT_PREVIEW_SENSITIVITY_PPM;
    static final int SCALE = TerrainElevationColorRamp.SCALE;

    private static final float MAX_CONTRAST = 2f;
    private static final Color SHALLOW_WATER = new Color(0.16f, 0.24f, 0.30f, 1f);
    private static final Color DEEP_WATER = new Color(0.045f, 0.075f, 0.10f, 1f);

    private WorldGenerationElevationTint() {
    }

    static Color color(
            long elevationSubunits,
            WorldGenerationElevationRange range,
            int sensitivityPpm,
            Color out) {
        if (range == null) throw new IllegalArgumentException("elevation range must not be null");
        if (out == null) throw new IllegalArgumentException("output color must not be null");
        if (elevationSubunits >= 0L) {
            return TerrainElevationColorRamp.color(
                    Math.max(range.minimumSubunits(), elevationSubunits),
                    range.minimumSubunits(),
                    range.maximumSubunits(),
                    sensitivityPpm,
                    out);
        }

        float palettePosition = submergedPalettePosition(elevationSubunits, range, sensitivityPpm);
        float darkness = MathUtils.clamp((0.5f - palettePosition) * 2f, 0f, 1f);
        return out.set(
                MathUtils.lerp(SHALLOW_WATER.r, DEEP_WATER.r, darkness),
                MathUtils.lerp(SHALLOW_WATER.g, DEEP_WATER.g, darkness),
                MathUtils.lerp(SHALLOW_WATER.b, DEEP_WATER.b, darkness),
                1f);
    }

    static Color shaderColor(
            long elevationSubunits,
            WorldGenerationElevationRange range,
            int sensitivityPpm,
            Color out) {
        if (range == null) throw new IllegalArgumentException("elevation range must not be null");
        if (out == null) throw new IllegalArgumentException("output color must not be null");
        if (elevationSubunits >= 0L) {
            return TerrainElevationColorRamp.shaderColor(
                    Math.max(range.minimumSubunits(), elevationSubunits),
                    range.minimumSubunits(),
                    range.maximumSubunits(),
                    sensitivityPpm,
                    out);
        }

        float position = submergedPalettePosition(elevationSubunits, range, sensitivityPpm);
        return out.set(position, position, position, 1f);
    }

    private static float submergedPalettePosition(
            long elevationSubunits,
            WorldGenerationElevationRange range,
            int sensitivityPpm) {
        validateSensitivity(sensitivityPpm);
        long minimumWater = range.minimumWaterSubunits();
        if (sensitivityPpm == 0 || elevationSubunits >= 0L || minimumWater >= 0L) return 0.5f;

        float normalizedDepth = MathUtils.clamp(
                (float) elevationSubunits / (float) minimumWater,
                0f,
                1f);
        float contrast = sensitivityPpm / (float) SCALE * MAX_CONTRAST;
        int bands = 3 + Math.round(45f * contrast);
        float quantizedDepth = Math.round(normalizedDepth * (bands - 1f)) / (bands - 1f);
        return MathUtils.clamp(
                0.5f - quantizedDepth * 0.5f * contrast,
                0f,
                0.5f);
    }

    private static void validateSensitivity(int sensitivityPpm) {
        if (sensitivityPpm < 0 || sensitivityPpm > SCALE) {
            throw new IllegalArgumentException("elevation color sensitivity must be normalized ppm");
        }
    }
}
