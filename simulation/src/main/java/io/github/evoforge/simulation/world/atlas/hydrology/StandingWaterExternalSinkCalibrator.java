package io.github.evoforge.simulation.world.atlas.hydrology;

import io.github.evoforge.simulation.definition.NormalizedValue;
import io.github.evoforge.simulation.world.spatial.WorldBounds;

/** Resolves world scale into exact morphology thresholds for external drainage sinks. */
@FunctionalInterface
public interface StandingWaterExternalSinkCalibrator {
    StandingWaterExternalSinkCalibration calibrate(
            WorldBounds bounds,
            StandingWaterExternalSinkRecipe recipe);

    static StandingWaterExternalSinkCalibrator standard() {
        return StandardStandingWaterExternalSinkCalibrator.INSTANCE;
    }
}

final class StandardStandingWaterExternalSinkCalibrator
        implements StandingWaterExternalSinkCalibrator {
    static final StandardStandingWaterExternalSinkCalibrator INSTANCE =
            new StandardStandingWaterExternalSinkCalibrator();

    private StandardStandingWaterExternalSinkCalibrator() {
    }

    @Override
    public StandingWaterExternalSinkCalibration calibrate(
            WorldBounds bounds,
            StandingWaterExternalSinkRecipe recipe) {
        if (bounds == null || recipe == null) {
            throw new IllegalArgumentException("external-sink calibration inputs must not be null");
        }
        long width = (long) bounds.maxX() - bounds.minX() + 1L;
        long height = (long) bounds.maxY() - bounds.minY() + 1L;
        long area = Math.multiplyExact(width, height);
        long limitingSpan = Math.min(width, height);

        int minimumArea = Math.max(
                recipe.minimumAreaCells(),
                Math.toIntExact(ceilPpm(area, recipe.minimumWorldAreaPpm())));
        int minimumClearance = Math.max(
                recipe.minimumClearanceCells(),
                Math.toIntExact(ceilPpm(limitingSpan, recipe.minimumWorldClearancePpm())));
        return new StandingWaterExternalSinkCalibration(minimumArea, minimumClearance);
    }

    private static long ceilPpm(long value, int ppm) {
        long scale = NormalizedValue.SCALE;
        long whole = value / scale;
        long remainder = value % scale;
        long numerator = Math.multiplyExact(remainder, (long) ppm);
        return Math.addExact(
                Math.multiplyExact(whole, (long) ppm),
                (numerator + scale - 1L) / scale);
    }
}
