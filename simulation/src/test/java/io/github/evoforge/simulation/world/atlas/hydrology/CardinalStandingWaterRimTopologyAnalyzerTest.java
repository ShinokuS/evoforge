package io.github.evoforge.simulation.world.atlas.hydrology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

final class CardinalStandingWaterRimTopologyAnalyzerTest {
    private final StandingWaterTopologyAnalyzer waterAnalyzer =
            new ConnectedStandingWaterTopologyAnalyzer();
    private final StandingWaterRimTopologyAnalyzer rimAnalyzer =
            new CardinalStandingWaterRimTopologyAnalyzer();

    @Test
    void singleInlandBodyProducesExactCardinalDryRimInRowMajorOrder() {
        ElevationField elevation = field(
                new WorldBounds(0, 4, 0, 4, -5, 8),
                5L, 5L, 5L, 5L, 5L,
                5L, 5L, 2L, 5L, 5L,
                5L, 3L, -1L, 4L, 5L,
                5L, 5L, 6L, 5L, 5L,
                5L, 5L, 5L, 5L, 5L);
        StandingWaterTopology water = waterAnalyzer.analyze(elevation);

        StandingWaterRimTopology rim = rimAnalyzer.analyze(elevation, water);
        List<StandingWaterRimCell> cells = rim.rimCells(0);

        assertEquals(4, cells.size());
        assertRim(cells.get(0), 2, 1, 2L);
        assertRim(cells.get(1), 1, 2, 3L);
        assertRim(cells.get(2), 3, 2, 4L);
        assertRim(cells.get(3), 2, 3, 6L);
        for (StandingWaterRimCell cell : cells) {
            assertEquals(1, cell.adjacentWaterEdgeCount());
            assertTrue(cell.hasDryContinuation());
            assertFalse(cell.touchesWorldBoundary());
        }
    }

    @Test
    void dryIslandInsideOneWaterBodyIsRimGeometryButNotADryContinuation() {
        ElevationField elevation = field(
                new WorldBounds(0, 2, 0, 2, -5, 8),
                -1L, -1L, -1L,
                -1L, 3L, -1L,
                -1L, -1L, -1L);
        StandingWaterTopology water = waterAnalyzer.analyze(elevation);

        List<StandingWaterRimCell> rim = rimAnalyzer.analyze(elevation, water).rimCells(0);

        assertEquals(1, rim.size());
        StandingWaterRimCell island = rim.get(0);
        assertEquals(1, island.x());
        assertEquals(1, island.y());
        assertEquals(4, island.adjacentWaterEdgeCount());
        assertEquals(0, island.dryNeighborCount());
        assertFalse(island.hasDryContinuation());
    }

    @Test
    void oneDryCellMayBorderTwoBodiesWithoutCollapsingTheirIdentity() {
        ElevationField elevation = field(
                new WorldBounds(0, 2, 0, 2, -5, 8),
                2L, 2L, 2L,
                -1L, 1L, -2L,
                2L, 2L, 2L);
        StandingWaterTopology water = waterAnalyzer.analyze(elevation);
        assertEquals(2, water.bodyCount());

        StandingWaterRimTopology rim = rimAnalyzer.analyze(elevation, water);
        StandingWaterRimCell leftRelation = find(rim.rimCells(0), 1, 1);
        StandingWaterRimCell rightRelation = find(rim.rimCells(1), 1, 1);

        assertEquals(0, leftRelation.bodyId());
        assertEquals(1, rightRelation.bodyId());
        assertEquals(1, leftRelation.adjacentWaterEdgeCount());
        assertEquals(1, rightRelation.adjacentWaterEdgeCount());
        assertEquals(2, leftRelation.dryNeighborCount());
        assertEquals(2, rightRelation.dryNeighborCount());
    }

    @Test
    void rimTopologyRequiresTheSameWorldBoundsAsStandingWaterTopology() {
        ElevationField source = field(new WorldBounds(0, 0, 0, 0, -2, 2), -1L);
        StandingWaterTopology water = waterAnalyzer.analyze(source);
        ElevationField other = field(new WorldBounds(0, 1, 0, 0, -2, 2), -1L, 1L);

        assertThrows(IllegalArgumentException.class, () -> rimAnalyzer.analyze(other, water));
    }

    @Test
    void returnedRimListsAreImmutableFacts() {
        ElevationField elevation = field(
                new WorldBounds(0, 2, 0, 2, -2, 2),
                1L, 1L, 1L,
                1L, -1L, 1L,
                1L, 1L, 1L);
        StandingWaterTopology water = waterAnalyzer.analyze(elevation);
        List<StandingWaterRimCell> rim = rimAnalyzer.analyze(elevation, water).rimCells(0);

        assertThrows(UnsupportedOperationException.class, () -> rim.add(rim.get(0)));
    }

    private static void assertRim(StandingWaterRimCell rim, int x, int y, long elevation) {
        assertEquals(x, rim.x());
        assertEquals(y, rim.y());
        assertEquals(elevation, rim.elevationSubunits());
    }

    private static StandingWaterRimCell find(List<StandingWaterRimCell> rim, int x, int y) {
        return rim.stream()
                .filter(cell -> cell.x() == x && cell.y() == y)
                .findFirst()
                .orElseThrow();
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
