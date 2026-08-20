package io.github.evoforge.simulation.world.atlas.hydrology;

import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Measures broad standing-water scale without assigning lake/sea/ocean semantics.
 *
 * <p>Interior clearance is cardinal distance from in-world non-body terrain. World-edge contact is
 * deliberately not treated as shoreline: an external body may continue beyond the finite preview.
 * Boundary openness is measured separately as the longest contiguous run along one world side;
 * disjoint contacts and contacts on different sides are never added together.</p>
 */
public final class CardinalStandingWaterMorphologyAnalyzer implements StandingWaterMorphologyAnalyzer {
    static final CardinalStandingWaterMorphologyAnalyzer INSTANCE =
            new CardinalStandingWaterMorphologyAnalyzer();
    private static final int[] DX = {-1, 1, 0, 0};
    private static final int[] DY = {0, 0, -1, 1};

    public CardinalStandingWaterMorphologyAnalyzer() {
    }

    @Override
    public StandingWaterMorphologyTopology analyze(StandingWaterTopology standingWater) {
        if (standingWater == null) {
            throw new IllegalArgumentException("standing-water topology must not be null");
        }

        WorldBounds bounds = standingWater.bounds();
        int width = Math.toIntExact((long) bounds.maxX() - bounds.minX() + 1L);
        int height = Math.toIntExact((long) bounds.maxY() - bounds.minY() + 1L);
        int area = Math.multiplyExact(width, height);
        int count = standingWater.bodyCount();
        int[] distance = new int[area];
        Arrays.fill(distance, -1);
        long[] boundaryEdges = new long[count];
        int[] maximumBoundaryRun = measureMaximumBoundaryRuns(standingWater, bounds);
        ArrayDeque<Integer> frontier = new ArrayDeque<>();

        for (int localY = 0; localY < height; localY++) {
            int y = bounds.minY() + localY;
            for (int localX = 0; localX < width; localX++) {
                int x = bounds.minX() + localX;
                int bodyId = standingWater.bodyIdAt(x, y);
                if (bodyId == StandingWaterTopology.NO_BODY) continue;

                boolean touchesInWorldNonBody = false;
                for (int direction = 0; direction < DX.length; direction++) {
                    int nextLocalX = localX + DX[direction];
                    int nextLocalY = localY + DY[direction];
                    if (nextLocalX < 0 || nextLocalX >= width
                            || nextLocalY < 0 || nextLocalY >= height) {
                        boundaryEdges[bodyId]++;
                        continue;
                    }
                    int nextX = bounds.minX() + nextLocalX;
                    int nextY = bounds.minY() + nextLocalY;
                    if (standingWater.bodyIdAt(nextX, nextY) != bodyId) {
                        touchesInWorldNonBody = true;
                    }
                }

                if (touchesInWorldNonBody) {
                    int index = localY * width + localX;
                    distance[index] = 1;
                    frontier.addLast(index);
                }
            }
        }

        while (!frontier.isEmpty()) {
            int cell = frontier.removeFirst();
            int localX = cell % width;
            int localY = cell / width;
            int bodyId = standingWater.bodyIdAt(bounds.minX() + localX, bounds.minY() + localY);
            int nextDistance = distance[cell] + 1;
            for (int direction = 0; direction < DX.length; direction++) {
                int nextLocalX = localX + DX[direction];
                int nextLocalY = localY + DY[direction];
                if (nextLocalX < 0 || nextLocalX >= width
                        || nextLocalY < 0 || nextLocalY >= height) {
                    continue;
                }
                int next = nextLocalY * width + nextLocalX;
                if (distance[next] >= 0) continue;
                int nextBodyId = standingWater.bodyIdAt(
                        bounds.minX() + nextLocalX,
                        bounds.minY() + nextLocalY);
                if (nextBodyId != bodyId) continue;
                distance[next] = nextDistance;
                frontier.addLast(next);
            }
        }

        int[] maximumClearance = new int[count];
        for (int localY = 0; localY < height; localY++) {
            int y = bounds.minY() + localY;
            for (int localX = 0; localX < width; localX++) {
                int x = bounds.minX() + localX;
                int bodyId = standingWater.bodyIdAt(x, y);
                if (bodyId == StandingWaterTopology.NO_BODY) continue;
                int measured = distance[localY * width + localX];
                if (measured > maximumClearance[bodyId]) {
                    maximumClearance[bodyId] = measured;
                }
            }
        }

        List<StandingWaterMorphology> morphology = new ArrayList<>(count);
        for (int bodyId = 0; bodyId < count; bodyId++) {
            StandingWaterBody body = standingWater.body(bodyId);
            int clearance = maximumClearance[bodyId];
            if (clearance <= 0) {
                int spanX = body.maxX() - body.minX() + 1;
                int spanY = body.maxY() - body.minY() + 1;
                clearance = Math.max(1, (Math.min(spanX, spanY) + 1) / 2);
            }
            morphology.add(new StandingWaterMorphology(
                    bodyId,
                    clearance,
                    boundaryEdges[bodyId],
                    maximumBoundaryRun[bodyId]));
        }
        return new DenseStandingWaterMorphologyTopology(bounds, morphology);
    }

    private static int[] measureMaximumBoundaryRuns(
            StandingWaterTopology standingWater,
            WorldBounds bounds) {
        int[] maximum = new int[standingWater.bodyCount()];
        measureHorizontalSide(standingWater, bounds.minY(), bounds, maximum);
        if (bounds.maxY() != bounds.minY()) {
            measureHorizontalSide(standingWater, bounds.maxY(), bounds, maximum);
        }
        measureVerticalSide(standingWater, bounds.minX(), bounds, maximum);
        if (bounds.maxX() != bounds.minX()) {
            measureVerticalSide(standingWater, bounds.maxX(), bounds, maximum);
        }
        return maximum;
    }

    private static void measureHorizontalSide(
            StandingWaterTopology standingWater,
            int y,
            WorldBounds bounds,
            int[] maximum) {
        int currentBody = StandingWaterTopology.NO_BODY;
        int currentRun = 0;
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            int bodyId = standingWater.bodyIdAt(x, y);
            if (bodyId == currentBody && bodyId != StandingWaterTopology.NO_BODY) {
                currentRun++;
                continue;
            }
            recordRun(currentBody, currentRun, maximum);
            currentBody = bodyId;
            currentRun = bodyId == StandingWaterTopology.NO_BODY ? 0 : 1;
        }
        recordRun(currentBody, currentRun, maximum);
    }

    private static void measureVerticalSide(
            StandingWaterTopology standingWater,
            int x,
            WorldBounds bounds,
            int[] maximum) {
        int currentBody = StandingWaterTopology.NO_BODY;
        int currentRun = 0;
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            int bodyId = standingWater.bodyIdAt(x, y);
            if (bodyId == currentBody && bodyId != StandingWaterTopology.NO_BODY) {
                currentRun++;
                continue;
            }
            recordRun(currentBody, currentRun, maximum);
            currentBody = bodyId;
            currentRun = bodyId == StandingWaterTopology.NO_BODY ? 0 : 1;
        }
        recordRun(currentBody, currentRun, maximum);
    }

    private static void recordRun(int bodyId, int run, int[] maximum) {
        if (bodyId == StandingWaterTopology.NO_BODY || run <= 0) return;
        maximum[bodyId] = Math.max(maximum[bodyId], run);
    }
}
