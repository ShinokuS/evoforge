package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.spatial.WorldBounds;

/**
 * Materializes an already accepted inland-lake footprint below Z=0.
 *
 * <p>The lake-domain stage is responsible for choosing a naturally compatible lowland. Materialization
 * therefore never manufactures a dry terrace/collar around the water: every non-lake terrain cell
 * remains bit-identical. This keeps the accepted surrounding relief intact and avoids a synthetic
 * one-Z ridge tracing the shoreline.</p>
 */
final class Z0InlandLakeShoreConditioningAlgorithm implements InlandLakeShoreConditioningAlgorithm {
    static final Z0InlandLakeShoreConditioningAlgorithm INSTANCE =
            new Z0InlandLakeShoreConditioningAlgorithm();

    private Z0InlandLakeShoreConditioningAlgorithm() {
    }

    @Override
    public ElevationField condition(
            ElevationField continentalBase,
            InlandLakeDomain lakeDomain) {
        if (continentalBase == null || lakeDomain == null) {
            throw new IllegalArgumentException("lake shoreline inputs must not be null");
        }
        WorldBounds bounds = continentalBase.bounds();
        if (!sameHorizontalBounds(bounds, lakeDomain.bounds())) {
            throw new IllegalArgumentException("lake domain must match continental base horizontal bounds");
        }
        if (bounds.minZ() >= 0 || bounds.maxZ() <= 0) {
            throw new IllegalArgumentException("Z=0 inland lakes require elevation headroom below and above sea level");
        }

        int width = Math.toIntExact((long) bounds.maxX() - bounds.minX() + 1L);
        int height = Math.toIntExact((long) bounds.maxY() - bounds.minY() + 1L);
        long[] result = new long[Math.multiplyExact(width, height)];
        int index = 0;
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                long original = continentalBase.elevationSubunitsAt(x, y);
                if (lakeDomain.isLakeIndex(index)) {
                    if (original <= ElevationGenerationStage.SEA_LEVEL_SUBUNITS) {
                        throw new IllegalStateException("inland lake domain overlapped existing standing water");
                    }
                    result[index++] = -1L;
                } else {
                    result[index++] = original;
                }
            }
        }
        return DenseElevationField.takeOwnership(bounds, result);
    }

    private static boolean sameHorizontalBounds(WorldBounds first, WorldBounds second) {
        return first.minX() == second.minX()
                && first.maxX() == second.maxX()
                && first.minY() == second.minY()
                && first.maxY() == second.maxY();
    }
}
