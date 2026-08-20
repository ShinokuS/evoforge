package io.github.evoforge.simulation.world.atlas.hydrology;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

final class ConnectedStandingWaterTopologyAnalyzerTest {

    private final StandingWaterTopologyAnalyzer analyzer =
            new ConnectedStandingWaterTopologyAnalyzer();

    @Test
    void diagonalContactRemainsSeparateAndBodyIdsFollowDeterministicRowMajorDiscovery() {
        ElevationField elevation = field(
                new WorldBounds(0, 2, 0, 2, -4, 4),
                -1L, 1L, 1L,
                1L, -1L, 1L,
                1L, 1L, 1L);

        StandingWaterTopology topology = analyzer.analyze(elevation);

        assertEquals(2, topology.bodyCount());
        assertEquals(0, topology.bodyIdAt(0, 0));
        assertEquals(1, topology.bodyIdAt(1, 1));
        assertEquals(StandingWaterTopology.NO_BODY, topology.bodyIdAt(1, 0));
        assertTrue(topology.body(0).touchesWorldBoundary());
        assertFalse(topology.body(1).touchesWorldBoundary());
    }

    @Test
    void cardinalContactFormsOneBodyWithExactGeometryAndShorelineEdges() {
        ElevationField elevation = field(
                new WorldBounds(0, 3, 0, 2, -4, 4),
                1L, 1L, 1L, 1L,
                1L, -2L, -3L, 1L,
                1L, 1L, 1L, 1L);

        StandingWaterTopology topology = analyzer.analyze(elevation);
        StandingWaterBody body = topology.body(0);

        assertEquals(1, topology.bodyCount());
        assertEquals(2L, body.cellCount());
        assertEquals(6L, body.shorelineEdgeCount());
        assertFalse(body.touchesWorldBoundary());
        assertEquals(1, body.minX());
        assertEquals(2, body.maxX());
        assertEquals(1, body.minY());
        assertEquals(1, body.maxY());
    }

    @Test
    void worldEdgeContactIsRecordedSeparatelyFromInWorldShorelineLength() {
        ElevationField elevation = field(
                new WorldBounds(0, 1, 0, 1, -4, 4),
                -1L, 1L,
                1L, 1L);

        StandingWaterBody body = analyzer.analyze(elevation).body(0);

        assertTrue(body.touchesWorldBoundary());
        assertEquals(2L, body.shorelineEdgeCount(),
                "outside-world edges are boundary connectivity, not invented land shoreline");
    }

    @Test
    void preciseNegativeSubunitIsWaterWhileSeaLevelZeroRemainsLandMembershipBoundary() {
        ElevationField elevation = field(
                new WorldBounds(0, 1, 0, 0, -2, 2),
                -1L, 0L);

        StandingWaterTopology topology = analyzer.analyze(elevation);

        assertTrue(topology.isStandingWaterAt(0, 0));
        assertFalse(topology.isStandingWaterAt(1, 0));
        assertEquals(1, topology.bodyCount());
    }

    @Test
    void analysisIsReadOnlyAndDeterministic() {
        long[] source = {
                1L, -2L, -2L, 1L,
                1L, -3L, 1L, -1L,
                1L, 1L, 1L, -1L
        };
        long[] before = source.clone();
        ElevationField elevation = field(
                new WorldBounds(-2, 1, 5, 7, -6, 6),
                source);

        StandingWaterTopology first = analyzer.analyze(elevation);
        StandingWaterTopology second = analyzer.analyze(elevation);

        assertArrayEquals(before, source);
        assertEquals(first.bodyCount(), second.bodyCount());
        for (int id = 0; id < first.bodyCount(); id++) {
            assertEquals(first.body(id), second.body(id));
        }
        for (int y = first.bounds().minY(); y <= first.bounds().maxY(); y++) {
            for (int x = first.bounds().minX(); x <= first.bounds().maxX(); x++) {
                assertEquals(first.bodyIdAt(x, y), second.bodyIdAt(x, y));
            }
        }
    }

    @Test
    void topologyRejectsCoordinatesAndBodyIdsOutsideItsFactDomain() {
        StandingWaterTopology topology = analyzer.analyze(field(
                new WorldBounds(0, 0, 0, 0, -2, 2),
                -1L));

        assertThrows(IllegalArgumentException.class, () -> topology.bodyIdAt(1, 0));
        assertThrows(IllegalArgumentException.class, () -> topology.body(-1));
        assertThrows(IllegalArgumentException.class, () -> topology.body(1));
    }

    private static ElevationField field(WorldBounds bounds, long... values) {
        int width = Math.toIntExact((long) bounds.maxX() - bounds.minX() + 1L);
        int height = Math.toIntExact((long) bounds.maxY() - bounds.minY() + 1L);
        if (values.length != Math.multiplyExact(width, height)) {
            throw new IllegalArgumentException("test elevation values must cover XY bounds");
        }
        long[] elevation = Arrays.copyOf(values, values.length);
        return new ElevationField() {
            @Override
            public WorldBounds bounds() {
                return bounds;
            }

            @Override
            public int elevationAt(int x, int y) {
                return Math.toIntExact(Math.floorDiv(
                        elevationSubunitsAt(x, y), SUBUNITS_PER_CELL));
            }

            @Override
            public long elevationSubunitsAt(int x, int y) {
                if (!contains(x, y)) throw new IllegalArgumentException("coordinate outside test field");
                int localX = x - bounds.minX();
                int localY = y - bounds.minY();
                return elevation[localY * width + localX];
            }
        };
    }
}
