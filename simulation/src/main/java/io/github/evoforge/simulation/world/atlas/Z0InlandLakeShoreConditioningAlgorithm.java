package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.spatial.WorldBounds;

/**
 * Materializes inland-water membership below Z=0 without rewriting surrounding dry terrain.
 *
 * <p>The lake domain is selected from terrain that is already a broad continental lowland. Dry
 * shoreline geometry therefore remains owned by the accepted terrain generator instead of being
 * flattened in a synthetic ring. Downstream V13 mountains already treat submerged cells as water,
 * and V14 bathymetry owns the final submerged depth profile.</p>
 */
final class Z0InlandLakeShoreConditioningAlgorithm implements InlandLakeShoreConditioningAlgorithm {
    static final Z0InlandLakeShoreConditioningAlgorithm INSTANCE =
            new Z0InlandLakeShoreConditioningAlgorithm();

    private Z0InlandLakeShoreConditioningAlgorithm() {
    }

    @Override
    public ElevationField condition(
            ElevationField continentalBase,
            InlandLakeDomain lakeDomain,
            V12LandformRecipe.CoastProfile coastProfile) {
        if (continentalBase == null || lakeDomain == null || coastProfile == null) {
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
        return new DenseElevationField(bounds, result);
    }

    private static boolean sameHorizontalBounds(WorldBounds first, WorldBounds second) {
        return first.minX() == second.minX()
                && first.maxX() == second.maxX()
                && first.minY() == second.minY()
                && first.maxY() == second.maxY();
    }
}
