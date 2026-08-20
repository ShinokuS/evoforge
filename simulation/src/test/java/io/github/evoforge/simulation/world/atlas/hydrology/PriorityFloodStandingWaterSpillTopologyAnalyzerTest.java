package io.github.evoforge.simulation.world.atlas.hydrology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

final class PriorityFloodStandingWaterSpillTopologyAnalyzerTest {
    private final StandingWaterTopologyAnalyzer waterAnalyzer =
            new ConnectedStandingWaterTopologyAnalyzer();
    private final StandingWaterSpillTopologyAnalyzer spillAnalyzer =
            new PriorityFloodStandingWaterSpillTopologyAnalyzer();

    @Test
    void oneDimensionalConnectionUsesTheHighestBarrierOnTheOnlyAvailablePath() {
        ElevationField elevation = field(
                new WorldBounds(0, 4, 0, 0, -20, 20),
                -1L, 2L, 4L, 1L, -1L);
        StandingWaterTopology water = waterAnalyzer.analyze(elevation);

        StandingWaterSpillTopology spills = spillAnalyzer.analyze(elevation, water);

        assertEquals(2, spills.bodyCount());
        assertEquals(1, spills.connections().size());
        StandingWaterSpillConnection connection = spills.connections().get(0);
        assertEquals(0, connection.firstBodyId());
        assertEquals(1, connection.secondBodyId());
        assertEquals(4L, connection.barrierElevationSubunits());
        assertCardinalMeeting(connection);
    }

    @Test
    void priorityFloodFindsLowerDetourInsteadOfNearestHighRidge() {
        ElevationField elevation = field(
                new WorldBounds(0, 4, 0, 4, -20, 20),
                2L, 2L, 2L, 2L, 2L,
                2L, 9L, 9L, 9L, 2L,
                -1L, 9L, 9L, 9L, -1L,
                9L, 9L, 9L, 9L, 9L,
                9L, 9L, 9L, 9L, 9L);
        StandingWaterTopology water = waterAnalyzer.analyze(elevation);

        StandingWaterSpillConnection connection =
                spillAnalyzer.analyze(elevation, water).connections().get(0);

        assertEquals(2L, connection.barrierElevationSubunits(),
                "minimax flood must use the broad low pass around the high direct ridge");
    }

    @Test
    void acceptedBathymetricDepthDoesNotBecomeHydrologicBarrierHeight() {
        ElevationField elevation = field(
                new WorldBounds(0, 2, 0, 0, -30, 10),
                -20L, 3L, -1L);
        StandingWaterTopology water = waterAnalyzer.analyze(elevation);

        StandingWaterSpillConnection connection =
                spillAnalyzer.analyze(elevation, water).connections().get(0);

        assertEquals(3L, connection.barrierElevationSubunits(),
                "standing-water bodies are super-nodes at the datum, not routes through bottom depth");
    }

    @Test
    void worldWithoutStandingWaterProducesAnEmptySpillGraph() {
        ElevationField elevation = field(
                new WorldBounds(0, 2, 0, 1, -2, 8),
                1L, 2L, 3L,
                4L, 5L, 6L);
        StandingWaterTopology water = waterAnalyzer.analyze(elevation);

        StandingWaterSpillTopology spills = spillAnalyzer.analyze(elevation, water);

        assertEquals(0, spills.bodyCount());
        assertTrue(spills.connections().isEmpty());
    }

    @Test
    void spillGraphIsDeterministicAndExposesImmutablePerBodyConnections() {
        ElevationField elevation = field(
                new WorldBounds(0, 4, 0, 2, -5, 10),
                -1L, 2L, 4L, 2L, -1L,
                2L, 3L, 5L, 3L, 2L,
                -1L, 2L, 4L, 2L, 2L);
        StandingWaterTopology water = waterAnalyzer.analyze(elevation);

        StandingWaterSpillTopology first = spillAnalyzer.analyze(elevation, water);
        StandingWaterSpillTopology second = spillAnalyzer.analyze(elevation, water);

        assertEquals(first.connections(), second.connections());
        for (int bodyId = 0; bodyId < first.bodyCount(); bodyId++) {
            assertEquals(first.connectionsForBody(bodyId), second.connectionsForBody(bodyId));
            List<StandingWaterSpillConnection> bodyConnections = first.connectionsForBody(bodyId);
            if (!bodyConnections.isEmpty()) {
                assertThrows(UnsupportedOperationException.class,
                        () -> bodyConnections.add(bodyConnections.get(0)));
            }
        }
    }

    @Test
    void spillAnalysisRejectsMismatchedWorldBounds() {
        ElevationField source = field(
                new WorldBounds(0, 2, 0, 0, -3, 3),
                -1L, 1L, -1L);
        StandingWaterTopology water = waterAnalyzer.analyze(source);
        ElevationField other = field(
                new WorldBounds(0, 3, 0, 0, -3, 3),
                -1L, 1L, 1L, -1L);

        assertThrows(IllegalArgumentException.class, () -> spillAnalyzer.analyze(other, water));
    }

    private static void assertCardinalMeeting(StandingWaterSpillConnection connection) {
        int distance = Math.abs(connection.meetingFirstX() - connection.meetingSecondX())
                + Math.abs(connection.meetingFirstY() - connection.meetingSecondY());
        assertEquals(1, distance);
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
