package io.github.evoforge.simulation.world.atlas.hydrology;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.PriorityQueue;

/** Deterministic minimax routing over the standing-water spill graph toward boundary water. */
public final class MinimaxStandingWaterBoundaryRouteResolver
        implements StandingWaterBoundaryRouteResolver {
    private static final Comparator<RouteEntry> ROUTE_ORDER =
            Comparator.comparingLong(RouteEntry::barrier)
                    .thenComparingInt(RouteEntry::bodyId);

    @Override
    public StandingWaterBoundaryRouteTopology resolve(
            StandingWaterTopology standingWater,
            StandingWaterSpillTopology spills) {
        if (standingWater == null || spills == null) {
            throw new IllegalArgumentException("standing-water route inputs must not be null");
        }
        if (!standingWater.bounds().equals(spills.bounds())
                || standingWater.bodyCount() != spills.bodyCount()) {
            throw new IllegalArgumentException("standing-water route inputs must describe the same bodies");
        }

        int count = standingWater.bodyCount();
        long[] minimumBarrier = new long[count];
        int[] nextBody = new int[count];
        boolean[] settled = new boolean[count];
        Arrays.fill(minimumBarrier, Long.MAX_VALUE);
        Arrays.fill(nextBody, StandingWaterTopology.NO_BODY);
        PriorityQueue<RouteEntry> frontier = new PriorityQueue<>(ROUTE_ORDER);

        for (int bodyId = 0; bodyId < count; bodyId++) {
            if (standingWater.body(bodyId).touchesWorldBoundary()) {
                minimumBarrier[bodyId] = 0L;
                frontier.add(new RouteEntry(0L, bodyId));
            }
        }

        while (!frontier.isEmpty()) {
            RouteEntry current = frontier.remove();
            if (settled[current.bodyId()]
                    || minimumBarrier[current.bodyId()] != current.barrier()) {
                continue;
            }
            settled[current.bodyId()] = true;

            for (StandingWaterSpillConnection connection :
                    spills.connectionsForBody(current.bodyId())) {
                int neighbor = connection.otherBodyId(current.bodyId());
                if (settled[neighbor]) continue;
                long candidate = Math.max(current.barrier(), connection.barrierElevationSubunits());
                boolean better = candidate < minimumBarrier[neighbor];
                boolean deterministicTie = candidate == minimumBarrier[neighbor]
                        && (nextBody[neighbor] == StandingWaterTopology.NO_BODY
                        || current.bodyId() < nextBody[neighbor]);
                if (better || deterministicTie) {
                    minimumBarrier[neighbor] = candidate;
                    nextBody[neighbor] = current.bodyId();
                    frontier.add(new RouteEntry(candidate, neighbor));
                }
            }
        }

        List<StandingWaterBoundaryRoute> routes = new ArrayList<>(count);
        for (int bodyId = 0; bodyId < count; bodyId++) {
            boolean boundaryConnected = standingWater.body(bodyId).touchesWorldBoundary();
            if (boundaryConnected) {
                routes.add(new StandingWaterBoundaryRoute(
                        bodyId,
                        true,
                        OptionalInt.empty(),
                        OptionalLong.of(0L)));
            } else if (minimumBarrier[bodyId] == Long.MAX_VALUE) {
                routes.add(new StandingWaterBoundaryRoute(
                        bodyId,
                        false,
                        OptionalInt.empty(),
                        OptionalLong.empty()));
            } else {
                if (nextBody[bodyId] == StandingWaterTopology.NO_BODY) {
                    throw new IllegalStateException("reachable standing water has no next body");
                }
                routes.add(new StandingWaterBoundaryRoute(
                        bodyId,
                        false,
                        OptionalInt.of(nextBody[bodyId]),
                        OptionalLong.of(minimumBarrier[bodyId])));
            }
        }
        return new DenseStandingWaterBoundaryRouteTopology(standingWater.bounds(), routes);
    }

    private record RouteEntry(long barrier, int bodyId) {
    }
}
