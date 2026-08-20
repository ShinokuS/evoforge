package io.github.evoforge.simulation.world.atlas.hydrology;

import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Multi-source Priority-Flood that derives minimum-barrier connectivity between standing-water
 * components without changing terrain.
 *
 * <p>Every accepted water component is treated as one hydrologic source at the common sea-level
 * datum rather than routing through its bathymetric bottom. Dry cells are claimed by the source
 * that reaches them with the lowest minimax barrier. Where different source regions meet, the
 * lowest discovered barrier for that body pair becomes a graph edge.</p>
 */
public final class PriorityFloodStandingWaterSpillTopologyAnalyzer
        implements StandingWaterSpillTopologyAnalyzer {
    private static final int[] DX = {-1, 1, 0, 0};
    private static final int[] DY = {0, 0, -1, 1};

    private static final Comparator<FloodEntry> FLOOD_ORDER =
            Comparator.comparingLong(FloodEntry::level)
                    .thenComparingInt(FloodEntry::ownerBodyId)
                    .thenComparingInt(FloodEntry::index);

    @Override
    public StandingWaterSpillTopology analyze(
            ElevationField elevation,
            StandingWaterTopology standingWater) {
        if (elevation == null || standingWater == null) {
            throw new IllegalArgumentException("standing-water spill inputs must not be null");
        }
        WorldBounds bounds = elevation.bounds();
        if (!bounds.equals(standingWater.bounds())) {
            throw new IllegalArgumentException("standing-water spill inputs must share world bounds");
        }

        int width = Math.toIntExact((long) bounds.maxX() - bounds.minX() + 1L);
        int height = Math.toIntExact((long) bounds.maxY() - bounds.minY() + 1L);
        int area = Math.multiplyExact(width, height);
        int[] owner = new int[area];
        long[] floodLevel = new long[area];
        boolean[] fixedWaterSource = new boolean[area];
        Arrays.fill(owner, StandingWaterTopology.NO_BODY);
        Arrays.fill(floodLevel, Long.MAX_VALUE);

        PriorityQueue<FloodEntry> frontier = new PriorityQueue<>(FLOOD_ORDER);
        for (int localY = 0; localY < height; localY++) {
            int y = bounds.minY() + localY;
            for (int localX = 0; localX < width; localX++) {
                int x = bounds.minX() + localX;
                int bodyId = standingWater.bodyIdAt(x, y);
                if (bodyId == StandingWaterTopology.NO_BODY) continue;
                int index = localY * width + localX;
                owner[index] = bodyId;
                floodLevel[index] = 0L;
                fixedWaterSource[index] = true;
                frontier.add(new FloodEntry(0L, bodyId, index));
            }
        }

        Map<Long, ConnectionCandidate> bestConnections = new HashMap<>();
        while (!frontier.isEmpty()) {
            FloodEntry current = frontier.remove();
            if (owner[current.index()] != current.ownerBodyId()
                    || floodLevel[current.index()] != current.level()) {
                continue;
            }

            int localX = current.index() % width;
            int localY = current.index() / width;
            for (int direction = 0; direction < DX.length; direction++) {
                int nx = localX + DX[direction];
                int ny = localY + DY[direction];
                if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue;
                int neighbor = ny * width + nx;

                long terrain = elevation.elevationSubunitsAt(
                        bounds.minX() + nx,
                        bounds.minY() + ny);
                long candidateLevel = Math.max(current.level(), Math.max(0L, terrain));
                int neighborOwner = owner[neighbor];

                if (neighborOwner == StandingWaterTopology.NO_BODY) {
                    owner[neighbor] = current.ownerBodyId();
                    floodLevel[neighbor] = candidateLevel;
                    frontier.add(new FloodEntry(candidateLevel, current.ownerBodyId(), neighbor));
                    continue;
                }

                if (neighborOwner != current.ownerBodyId()) {
                    long barrier = Math.max(current.level(), floodLevel[neighbor]);
                    recordConnection(
                            bestConnections,
                            current.ownerBodyId(),
                            current.index(),
                            neighborOwner,
                            neighbor,
                            barrier);
                }

                if (fixedWaterSource[neighbor]) continue;
                boolean betterLevel = candidateLevel < floodLevel[neighbor];
                boolean deterministicTie = candidateLevel == floodLevel[neighbor]
                        && current.ownerBodyId() < neighborOwner;
                if (betterLevel || deterministicTie) {
                    owner[neighbor] = current.ownerBodyId();
                    floodLevel[neighbor] = candidateLevel;
                    frontier.add(new FloodEntry(candidateLevel, current.ownerBodyId(), neighbor));
                }
            }
        }

        List<StandingWaterSpillConnection> connections = new ArrayList<>(bestConnections.size());
        for (ConnectionCandidate candidate : bestConnections.values()) {
            int firstLocalX = candidate.firstIndex() % width;
            int firstLocalY = candidate.firstIndex() / width;
            int secondLocalX = candidate.secondIndex() % width;
            int secondLocalY = candidate.secondIndex() / width;
            connections.add(new StandingWaterSpillConnection(
                    candidate.firstBodyId(),
                    candidate.secondBodyId(),
                    candidate.barrierElevationSubunits(),
                    bounds.minX() + firstLocalX,
                    bounds.minY() + firstLocalY,
                    bounds.minX() + secondLocalX,
                    bounds.minY() + secondLocalY));
        }
        connections.sort(Comparator
                .comparingInt(StandingWaterSpillConnection::firstBodyId)
                .thenComparingInt(StandingWaterSpillConnection::secondBodyId));
        return new DenseStandingWaterSpillTopology(
                bounds,
                standingWater.bodyCount(),
                connections);
    }

    private static void recordConnection(
            Map<Long, ConnectionCandidate> bestConnections,
            int currentBody,
            int currentIndex,
            int neighborBody,
            int neighborIndex,
            long barrier) {
        if (currentBody == neighborBody) return;
        int firstBody;
        int secondBody;
        int firstIndex;
        int secondIndex;
        if (currentBody < neighborBody) {
            firstBody = currentBody;
            secondBody = neighborBody;
            firstIndex = currentIndex;
            secondIndex = neighborIndex;
        } else {
            firstBody = neighborBody;
            secondBody = currentBody;
            firstIndex = neighborIndex;
            secondIndex = currentIndex;
        }
        long key = ((long) firstBody << 32) | (secondBody & 0xffff_ffffL);
        ConnectionCandidate candidate = new ConnectionCandidate(
                firstBody,
                secondBody,
                barrier,
                firstIndex,
                secondIndex);
        ConnectionCandidate previous = bestConnections.get(key);
        if (previous == null || candidate.isBetterThan(previous)) {
            bestConnections.put(key, candidate);
        }
    }

    private record FloodEntry(long level, int ownerBodyId, int index) {
    }

    private record ConnectionCandidate(
            int firstBodyId,
            int secondBodyId,
            long barrierElevationSubunits,
            int firstIndex,
            int secondIndex) {
        boolean isBetterThan(ConnectionCandidate other) {
            if (barrierElevationSubunits != other.barrierElevationSubunits) {
                return barrierElevationSubunits < other.barrierElevationSubunits;
            }
            if (firstIndex != other.firstIndex) return firstIndex < other.firstIndex;
            return secondIndex < other.secondIndex;
        }
    }
}
