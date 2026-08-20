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
    private static final double MILLI_SCALE = 1_000d;

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
        double rootSpan = StrictMath.sqrt((double) limitingSpan);

        long areaCap = ceilPpm(area, recipe.areaThresholdCapPpm());
        long sublinearArea = ceilFinite(
                (recipe.areaRootScaleMilli() / MILLI_SCALE)
                        * limitingSpan
                        * rootSpan,
                "external-sink area threshold");
        long minimumArea = Math.max(
                recipe.minimumAreaCells(),
                Math.min(areaCap, sublinearArea));

        long minimumClearance = Math.max(
                recipe.minimumClearanceCells(),
                ceilPpm(limitingSpan, recipe.minimumWorldClearancePpm()));

        long sublinearBoundaryRun = ceilFinite(
                (recipe.boundaryRunRootScaleMilli() / MILLI_SCALE) * rootSpan,
                "external-sink boundary-run threshold");
        long minimumBoundaryRun = Math.max(
                recipe.minimumBoundaryRunCells(),
                sublinearBoundaryRun);

        return new StandingWaterExternalSinkCalibration(
                Math.toIntExact(minimumArea),
                Math.toIntExact(minimumClearance),
                Math.toIntExact(minimumBoundaryRun));
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

    private static long ceilFinite(double value, String label) {
        if (!Double.isFinite(value) || value <= 0d || value > Long.MAX_VALUE) {
            throw new IllegalArgumentException(label + " must be finite and positive");
        }
        return (long) StrictMath.ceil(value);
    }
}
