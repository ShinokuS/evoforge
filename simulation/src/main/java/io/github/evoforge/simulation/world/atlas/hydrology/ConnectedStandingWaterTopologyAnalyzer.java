package io.github.evoforge.simulation.world.atlas.hydrology;

import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Deterministic four-connected standing-water component analysis over accepted negative-Z footprint.
 *
 * <p>Diagonal corner contact does not merge water bodies. World-edge contact is recorded as a
 * geometric fact rather than interpreted here as ocean/sea semantics.</p>
 */
public final class ConnectedStandingWaterTopologyAnalyzer implements StandingWaterTopologyAnalyzer {
    private static final int[] DX = {-1, 1, 0, 0};
    private static final int[] DY = {0, 0, -1, 1};

    @Override
    public StandingWaterTopology analyze(ElevationField elevation) {
        if (elevation == null) throw new IllegalArgumentException("elevation must not be null");

        WorldBounds bounds = elevation.bounds();
        int width = Math.toIntExact((long) bounds.maxX() - bounds.minX() + 1L);
        int height = Math.toIntExact((long) bounds.maxY() - bounds.minY() + 1L);
        int area = Math.multiplyExact(width, height);
        int[] bodyIds = new int[area];
        Arrays.fill(bodyIds, StandingWaterTopology.NO_BODY);
        List<StandingWaterBody> bodies = new ArrayList<>();
        ArrayDeque<Integer> frontier = new ArrayDeque<>();

        for (int localY = 0; localY < height; localY++) {
            int y = bounds.minY() + localY;
            for (int localX = 0; localX < width; localX++) {
                int x = bounds.minX() + localX;
                int start = localY * width + localX;
                if (bodyIds[start] != StandingWaterTopology.NO_BODY
                        || elevation.elevationSubunitsAt(x, y) >= 0L) {
                    continue;
                }

                int bodyId = bodies.size();
                bodyIds[start] = bodyId;
                frontier.addLast(start);

                long cellCount = 0L;
                long shorelineEdges = 0L;
                boolean touchesWorldBoundary = false;
                int minX = x;
                int maxX = x;
                int minY = y;
                int maxY = y;

                while (!frontier.isEmpty()) {
                    int cell = frontier.removeFirst();
                    int cellLocalX = cell % width;
                    int cellLocalY = cell / width;
                    int cellX = bounds.minX() + cellLocalX;
                    int cellY = bounds.minY() + cellLocalY;
                    cellCount++;
                    minX = Math.min(minX, cellX);
                    maxX = Math.max(maxX, cellX);
                    minY = Math.min(minY, cellY);
                    maxY = Math.max(maxY, cellY);

                    for (int direction = 0; direction < DX.length; direction++) {
                        int nextLocalX = cellLocalX + DX[direction];
                        int nextLocalY = cellLocalY + DY[direction];
                        if (nextLocalX < 0 || nextLocalX >= width
                                || nextLocalY < 0 || nextLocalY >= height) {
                            touchesWorldBoundary = true;
                            continue;
                        }

                        int nextX = bounds.minX() + nextLocalX;
                        int nextY = bounds.minY() + nextLocalY;
                        int next = nextLocalY * width + nextLocalX;
                        if (elevation.elevationSubunitsAt(nextX, nextY) >= 0L) {
                            shorelineEdges++;
                            continue;
                        }
                        if (bodyIds[next] == StandingWaterTopology.NO_BODY) {
                            bodyIds[next] = bodyId;
                            frontier.addLast(next);
                        }
                    }
                }

                bodies.add(new StandingWaterBody(
                        bodyId,
                        cellCount,
                        shorelineEdges,
                        touchesWorldBoundary,
                        minX,
                        maxX,
                        minY,
                        maxY));
            }
        }

        return new DenseStandingWaterTopology(bounds, bodyIds, bodies);
    }
}
