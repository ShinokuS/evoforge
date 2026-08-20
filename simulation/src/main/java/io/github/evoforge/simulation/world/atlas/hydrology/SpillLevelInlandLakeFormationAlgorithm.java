package io.github.evoforge.simulation.world.atlas.hydrology;

import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Fills significant closed depression basins to their analytical spill elevation.
 *
 * <p>This algorithm owns only lake membership and water-surface level. It never lowers terrain,
 * never changes the continental domain and does not create river channels.
 */
public final class SpillLevelInlandLakeFormationAlgorithm implements InlandLakeFormationAlgorithm {

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
        int area = Math.multiplyExact(width, height);
        boolean[] selected = new boolean[basins.basinCount()];

        for (int basinId = 0; basinId < basins.basinCount(); basinId++) {
            DrainageBasin basin = basins.basin(basinId);
            selected[basinId] = basin.cellCount() >= recipe.minimumCells()
                    && basin.maximumDepthSubunits() >= recipe.minimumMaximumDepthSubunits()
                    && (!recipe.requireTwoByTwoInterior()
                    || hasTwoByTwoInterior(basins, basinId, bounds));
        }

        int[] remap = new int[basins.basinCount()];
        Arrays.fill(remap, InlandLakeTopology.NO_LAKE);
        List<InlandLake> lakes = new ArrayList<>();
        for (int basinId = 0; basinId < basins.basinCount(); basinId++) {
            if (!selected[basinId]) continue;
            DrainageBasin basin = basins.basin(basinId);
            int lakeId = lakes.size();
            remap[basinId] = lakeId;
            lakes.add(new InlandLake(
                    lakeId,
                    basinId,
                    basin.cellCount(),
                    basin.spillElevationSubunits(),
                    basin.maximumDepthSubunits(),
                    basin.minX(),
                    basin.maxX(),
                    basin.minY(),
                    basin.maxY()));
        }

        int[] lakeIds = new int[area];
        Arrays.fill(lakeIds, InlandLakeTopology.NO_LAKE);
        for (int localY = 0; localY < height; localY++) {
            int y = bounds.minY() + localY;
            for (int localX = 0; localX < width; localX++) {
                int x = bounds.minX() + localX;
                int basinId = basins.basinIdAt(x, y);
                if (basinId == DrainageBasinTopology.NO_BASIN) continue;
                int lakeId = remap[basinId];
                if (lakeId != InlandLakeTopology.NO_LAKE) {
                    lakeIds[localY * width + localX] = lakeId;
                }
            }
        }

        return new DenseInlandLakeTopology(bounds, lakeIds, lakes);
    }

    private static boolean hasTwoByTwoInterior(
            DrainageBasinTopology basins,
            int basinId,
            WorldBounds bounds) {
        for (int y = bounds.minY(); y < bounds.maxY(); y++) {
            for (int x = bounds.minX(); x < bounds.maxX(); x++) {
                if (basins.basinIdAt(x, y) == basinId
                        && basins.basinIdAt(x + 1, y) == basinId
                        && basins.basinIdAt(x, y + 1) == basinId
                        && basins.basinIdAt(x + 1, y + 1) == basinId) {
                    return true;
                }
            }
        }
        return false;
    }
}
