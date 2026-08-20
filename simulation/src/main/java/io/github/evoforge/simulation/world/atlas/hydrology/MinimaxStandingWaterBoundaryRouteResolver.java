package io.github.evoforge.simulation.world.atlas.hydrology;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.PriorityQueue;

/** Deterministic minimax routing over the spill graph toward oceanic standing water. */
public final class MinimaxStandingWaterBoundaryRouteResolver
        implements StandingWaterBoundaryRouteResolver {
    private static final Comparator<RouteEntry> ROUTE_ORDER =
            Comparator.comparingLong(RouteEntry::barrier)
                    .thenComparingInt(RouteEntry::bodyId);

    @Override
    public StandingWaterBoundaryRouteTopology resolve(
            StandingWaterTopology standingWater,
            StandingWaterSpillTopology spills,
            StandingWaterDomainTopology domains) {
        if (standingWater == null || spills == null || domains == null) {
            throw new IllegalArgumentException("standing-water route inputs must not be null");
        }
        if (!standingWater.bounds().equals(spills.bounds())
                || !standingWater.bounds().equals(domains.bounds())
                || standingWater.bodyCount() != spills.bodyCount()
                || standingWater.bodyCount() != domains.bodyCount()) {
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
            if (domains.isOceanic(bodyId)) {
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

            for (StandingWaterSpillConnection connection : spills.connectionsForBody(current.bodyId())) {
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
            boolean oceanic = domains.isOceanic(bodyId);
            if (oceanic) {
                routes.add(new StandingWaterBoundaryRoute(
                        bodyId,
                        true,
                        true,
                        OptionalInt.empty(),
                        OptionalLong.of(0L)));
            } else if (minimumBarrier[bodyId] == Long.MAX_VALUE) {
                routes.add(new StandingWaterBoundaryRoute(
                        bodyId,
                        false,
                        false,
                        OptionalInt.empty(),
                        OptionalLong.empty()));
            } else {
                if (nextBody[bodyId] == StandingWaterTopology.NO_BODY) {
                    throw new IllegalStateException("reachable inland standing water has no next body");
                }
                routes.add(new StandingWaterBoundaryRoute(
                        bodyId,
                        false,
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
