package io.github.evoforge.simulation.world.atlas.hydrology;

import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Deterministic cardinal dry-rim analysis around accepted standing-water components. */
public final class CardinalStandingWaterRimTopologyAnalyzer
        implements StandingWaterRimTopologyAnalyzer {
    private static final int[] DX = {-1, 1, 0, 0};
    private static final int[] DY = {0, 0, -1, 1};

    @Override
    public StandingWaterRimTopology analyze(
            ElevationField elevation,
            StandingWaterTopology standingWater) {
        if (elevation == null || standingWater == null) {
            throw new IllegalArgumentException("standing-water rim inputs must not be null");
        }
        WorldBounds bounds = elevation.bounds();
        if (!bounds.equals(standingWater.bounds())) {
            throw new IllegalArgumentException("standing-water rim inputs must share world bounds");
        }

        List<List<StandingWaterRimCell>> rimsByBody =
                new ArrayList<>(standingWater.bodyCount());
        for (int bodyId = 0; bodyId < standingWater.bodyCount(); bodyId++) {
            rimsByBody.add(new ArrayList<>());
        }

        int[] adjacentBodies = new int[4];
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                if (standingWater.isStandingWaterAt(x, y)) continue;

                Arrays.fill(adjacentBodies, StandingWaterTopology.NO_BODY);
                int adjacentBodyCount = 0;
                int dryNeighborCount = 0;
                for (int direction = 0; direction < DX.length; direction++) {
                    int nx = x + DX[direction];
                    int ny = y + DY[direction];
                    if (!standingWater.contains(nx, ny)) continue;

                    int bodyId = standingWater.bodyIdAt(nx, ny);
                    if (bodyId == StandingWaterTopology.NO_BODY) {
                        dryNeighborCount++;
                        continue;
                    }
                    boolean alreadyPresent = false;
                    for (int index = 0; index < adjacentBodyCount; index++) {
                        if (adjacentBodies[index] == bodyId) {
                            alreadyPresent = true;
                            break;
                        }
                    }
                    if (!alreadyPresent) {
                        adjacentBodies[adjacentBodyCount++] = bodyId;
                    }
                }

                if (adjacentBodyCount == 0) continue;
                boolean touchesWorldBoundary = x == bounds.minX() || x == bounds.maxX()
                        || y == bounds.minY() || y == bounds.maxY();
                long rimElevation = elevation.elevationSubunitsAt(x, y);
                if (rimElevation < 0L) {
                    throw new IllegalStateException(
                            "standing-water topology and elevation disagree on dry rim membership");
                }

                for (int bodyIndex = 0; bodyIndex < adjacentBodyCount; bodyIndex++) {
                    int bodyId = adjacentBodies[bodyIndex];
                    int adjacentWaterEdges = 0;
                    for (int direction = 0; direction < DX.length; direction++) {
                        int nx = x + DX[direction];
                        int ny = y + DY[direction];
                        if (standingWater.contains(nx, ny)
                                && standingWater.bodyIdAt(nx, ny) == bodyId) {
                            adjacentWaterEdges++;
                        }
                    }
                    rimsByBody.get(bodyId).add(new StandingWaterRimCell(
                            bodyId,
                            x,
                            y,
                            rimElevation,
                            adjacentWaterEdges,
                            dryNeighborCount,
                            touchesWorldBoundary));
                }
            }
        }

        return new DenseStandingWaterRimTopology(bounds, rimsByBody);
    }
}
