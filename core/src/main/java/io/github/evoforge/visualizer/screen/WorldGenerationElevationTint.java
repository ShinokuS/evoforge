package io.github.evoforge.visualizer.screen;

import com.badlogic.gdx.math.MathUtils;
import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.spatial.WorldBounds;

/** Pure presentation mapping from generated elevation to terrain brightness. */
final class WorldGenerationElevationTint {
    static final int DEFAULT_STRENGTH_PPM = 450_000;
    static final int SCALE = 1_000_000;
    private static final float LOW_BRIGHTNESS = 0.62f;

    private WorldGenerationElevationTint() {
    }

    static float brightness(long elevationSubunits, WorldBounds bounds, int strengthPpm) {
        if (bounds == null) throw new IllegalArgumentException("world bounds must not be null");
        if (strengthPpm < 0 || strengthPpm > SCALE) {
            throw new IllegalArgumentException("tint strength must be normalized ppm");
        }
        if (strengthPpm == 0) return 1f;

        long maxLandSubunits = Math.max(
                ElevationField.SUBUNITS_PER_CELL,
                Math.multiplyExact((long) Math.max(1, bounds.maxZ()), ElevationField.SUBUNITS_PER_CELL));
        float normalized = MathUtils.clamp(
                (float) Math.max(0L, elevationSubunits) / (float) maxLandSubunits,
                0f,
                1f);
        float fullTint = LOW_BRIGHTNESS + (1f - LOW_BRIGHTNESS) * normalized;
        float strength = strengthPpm / (float) SCALE;
        return 1f + (fullTint - 1f) * strength;
    }
}
