package io.github.evoforge.simulation.world.atlas.hydrology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

final class MinimaxStandingWaterBoundaryRouteResolverTest {
    private final StandingWaterBoundaryRouteResolver resolver =
            new MinimaxStandingWaterBoundaryRouteResolver();
    private static final WorldBounds BOUNDS = new WorldBounds(0, 4, 0, 4, -10, 10);

    @Test
    void selectedExternalSinkIsTerminalWithZeroBarrierAndNoNextBody() {
        StandingWaterTopology water = topology(body(0, true));

        StandingWaterBoundaryRoute route = resolver.resolve(
                water,
                spillTopology(1),
                sinks(true)).route(0);

        assertTrue(route.externalSink());
        assertTrue(route.reachesExternalSink());
        assertTrue(route.nextBodyId().isEmpty());
        assertEquals(0L, route.minimumBarrierElevationSubunits().orElseThrow());
    }

    @Test
    void boundaryTouchingNonSinkCanRouteTowardLargerExternalWater() {
        StandingWaterTopology water = topology(
                body(0, true),
                body(1, true));
        StandingWaterSpillTopology spills = spillTopology(2, connection(0, 1, 3L));

        StandingWaterBoundaryRouteTopology routes = resolver.resolve(
                water,
                spills,
                sinks(false, true));

        assertFalse(routes.route(0).externalSink());
        assertEquals(1, routes.route(0).nextBodyId().orElseThrow(),
                "touching the world edge must not make a small body terminal");
        assertEquals(3L, routes.route(0).minimumBarrierElevationSubunits().orElseThrow());
        assertTrue(routes.route(1).externalSink());
    }

    @Test
    void inlandBodyUsesLowerMinimaxChainInsteadOfHigherDirectConnection() {
        StandingWaterTopology water = topology(
                body(0, true),
                body(1, false),
                body(2, false));
        StandingWaterSpillTopology spills = spillTopology(
                3,
                connection(0, 2, 8L),
                connection(0, 1, 5L),
                connection(1, 2, 4L));

        StandingWaterBoundaryRouteTopology routes = resolver.resolve(
                water,
                spills,
                sinks(true, false, false));

        assertEquals(0, routes.route(1).nextBodyId().orElseThrow());
        assertEquals(5L, routes.route(1).minimumBarrierElevationSubunits().orElseThrow());
        assertEquals(1, routes.route(2).nextBodyId().orElseThrow());
        assertEquals(5L, routes.route(2).minimumBarrierElevationSubunits().orElseThrow(),
                "2 -> 1 -> 0 has minimax barrier 5 and must beat direct barrier 8");
    }

    @Test
    void worldEdgeContactWithoutAnyExternalSinkLeavesPotentialRoutesClosed() {
        StandingWaterTopology water = topology(
                body(0, true),
                body(1, false));
        StandingWaterSpillTopology spills = spillTopology(2, connection(0, 1, 2L));

        StandingWaterBoundaryRouteTopology routes = resolver.resolve(
                water,
                spills,
                sinks(false, false));

        for (int bodyId = 0; bodyId < 2; bodyId++) {
            StandingWaterBoundaryRoute route = routes.route(bodyId);
            assertFalse(route.externalSink());
            assertFalse(route.reachesExternalSink());
            assertTrue(route.nextBodyId().isEmpty());
            assertTrue(route.minimumBarrierElevationSubunits().isEmpty());
        }
    }

    @Test
    void equalBarrierRoutesRemainAcyclicAndAlwaysReachEarlierSettledExternalTree() {
        StandingWaterTopology water = topology(
                body(0, true),
                body(1, false),
                body(2, false));
        StandingWaterSpillTopology spills = spillTopology(
                3,
                connection(0, 1, 5L),
                connection(0, 2, 5L),
                connection(1, 2, 5L));
        StandingWaterBoundaryRouteTopology routes = resolver.resolve(
                water,
                spills,
                sinks(true, false, false));

        for (int start = 0; start < routes.bodyCount(); start++) {
            int current = start;
            int steps = 0;
            while (!routes.route(current).externalSink()) {
                assertTrue(routes.route(current).nextBodyId().isPresent());
                current = routes.route(current).nextBodyId().orElseThrow();
                steps++;
                assertTrue(steps < routes.bodyCount(), "route next-pointers must not form a cycle");
            }
        }
    }

    @Test
    void resolverRejectsMismatchedBodyDomains() {
        StandingWaterTopology water = topology(body(0, true), body(1, false));

        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(
                water,
                spillTopology(2),
                new DenseStandingWaterExternalSinkTopology(BOUNDS, new boolean[] {true})));
    }

    private static StandingWaterExternalSinkTopology sinks(boolean... values) {
        return new DenseStandingWaterExternalSinkTopology(BOUNDS, values);
    }

    private static StandingWaterBody body(int id, boolean boundary) {
        return new StandingWaterBody(id, 1L, 4L, boundary, id, id, 0, 0);
    }

    private static StandingWaterSpillConnection connection(int first, int second, long barrier) {
        return new StandingWaterSpillConnection(first, second, barrier, first, 0, second, 0);
    }

    private static StandingWaterSpillTopology spillTopology(
            int bodyCount,
            StandingWaterSpillConnection... connections) {
        return new DenseStandingWaterSpillTopology(BOUNDS, bodyCount, Arrays.asList(connections));
    }

    private static StandingWaterTopology topology(StandingWaterBody... bodies) {
        List<StandingWaterBody> bodyList = List.of(bodies);
        return new StandingWaterTopology() {
            @Override
            public WorldBounds bounds() {
                return BOUNDS;
            }

            @Override
            public int bodyCount() {
                return bodyList.size();
            }

            @Override
            public int bodyIdAt(int x, int y) {
                throw new UnsupportedOperationException("route resolver does not consume cell labels");
            }

            @Override
            public StandingWaterBody body(int id) {
                return bodyList.get(id);
            }
        };
    }
}
