package io.github.evoforge.simulation.world.atlas.hydrology;

import io.github.evoforge.simulation.world.spatial.WorldBounds;

/** Resolves world scale into exact external-boundary opening thresholds. */
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
        long limitingSpan = Math.min(width, height);
        long scaledSquared = Math.multiplyExact(
                limitingSpan,
                (long) recipe.boundaryContactSpanFactor());
        long scaledBoundaryContact = ceilSqrt(scaledSquared);
        long minimumBoundaryContact = Math.min(
                limitingSpan,
                Math.max((long) recipe.minimumBoundaryContactCells(), scaledBoundaryContact));
        return new StandingWaterExternalSinkCalibration(
                Math.toIntExact(minimumBoundaryContact),
                recipe.minimumClearanceCells());
    }

    private static long ceilSqrt(long value) {
        if (value <= 0L) throw new IllegalArgumentException("square-root input must be positive");
        long root = (long) Math.sqrt((double) value);
        while (root > value / root) root--;
        while (root < value / root || root * root < value) root++;
        return root;
    }
}
