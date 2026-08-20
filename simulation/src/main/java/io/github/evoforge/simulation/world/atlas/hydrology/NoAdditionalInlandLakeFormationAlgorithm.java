package io.github.evoforge.simulation.world.atlas.hydrology;

import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.util.Arrays;
import java.util.List;

/**
 * Standard Stage 2B policy after Z=0 lake-domain generation: hydrology analyzes terrain but does not
 * author a second standing-water mask from depression spill levels.
 */
final class NoAdditionalInlandLakeFormationAlgorithm implements InlandLakeFormationAlgorithm {
    static final NoAdditionalInlandLakeFormationAlgorithm INSTANCE =
            new NoAdditionalInlandLakeFormationAlgorithm();

    private NoAdditionalInlandLakeFormationAlgorithm() {
    }

    @Override
    public InlandLakeTopology generate(
            ElevationField elevation,
            DrainageBasinTopology basins,
            InlandLakeFormationRecipe recipe) {
        if (elevation == null || basins == null || recipe == null) {
            throw new IllegalArgumentException("inland lake formation inputs must not be null");
        }
        WorldBounds bounds = elevation.bounds();
        if (!bounds.equals(basins.bounds())) {
            throw new IllegalArgumentException("lake formation inputs must share world bounds");
        }
        int width = Math.toIntExact((long) bounds.maxX() - bounds.minX() + 1L);
        int height = Math.toIntExact((long) bounds.maxY() - bounds.minY() + 1L);
        int[] lakeIds = new int[Math.multiplyExact(width, height)];
        Arrays.fill(lakeIds, InlandLakeTopology.NO_LAKE);
        return new DenseInlandLakeTopology(bounds, lakeIds, List.of());
    }
}
