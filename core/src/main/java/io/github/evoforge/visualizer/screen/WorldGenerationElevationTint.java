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
    private static final float DEEPEST_BRIGHTNESS = 0.28f;

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

        // Negative Z extends the existing land ramp downward instead of starting from an unrelated
        // water color. The shallowest submerged terrain therefore begins at the same darkest
        // positive-land reference and can only become darker with depth.
        TerrainElevationColorRamp.color(
                range.minimumSubunits(),
                range.minimumSubunits(),
                range.maximumSubunits(),
                sensitivityPpm,
                out);
        float darkness = submergedDarkness(elevationSubunits, range, sensitivityPpm);
        float brightness = MathUtils.lerp(1f, DEEPEST_BRIGHTNESS, darkness);
        return out.set(
                out.r * brightness,
                out.g * brightness,
                out.b * brightness,
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

        float landFloorPosition = TerrainElevationColorRamp.position(
                range.minimumSubunits(),
                range.minimumSubunits(),
                range.maximumSubunits(),
                sensitivityPpm);
        float darkness = submergedDarkness(elevationSubunits, range, sensitivityPpm);

        // Existing shader consumers encode a normal palette coordinate as grayscale (r == g == b).
        // V14 preview keeps red/green at the accepted land-floor palette position and uses only the
        // extra blue distance above that value to encode additional negative-Z darkening. The shared
        // shader can therefore support submerged depth without changing ordinary land callers.
        float encodedDepth = MathUtils.lerp(landFloorPosition, 1f, darkness);
        return out.set(landFloorPosition, landFloorPosition, encodedDepth, 1f);
    }

    private static float submergedDarkness(
            long elevationSubunits,
            WorldGenerationElevationRange range,
            int sensitivityPpm) {
        validateSensitivity(sensitivityPpm);
        long minimumWater = range.minimumWaterSubunits();
        if (sensitivityPpm == 0 || elevationSubunits >= 0L || minimumWater >= 0L) return 0f;

        float normalizedDepth = MathUtils.clamp(
                (float) elevationSubunits / (float) minimumWater,
                0f,
                1f);
        float contrast = sensitivityPpm / (float) SCALE * MAX_CONTRAST;
        int bands = 3 + Math.round(45f * contrast);
        float quantizedDepth = Math.round(normalizedDepth * (bands - 1f)) / (bands - 1f);
        return MathUtils.clamp(quantizedDepth * contrast, 0f, 1f);
    }

    private static void validateSensitivity(int sensitivityPpm) {
        if (sensitivityPpm < 0 || sensitivityPpm > SCALE) {
            throw new IllegalArgumentException("elevation color sensitivity must be normalized ppm");
        }
    }
}
