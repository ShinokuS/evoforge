package io.github.evoforge.simulation.world.atlas.hydrology;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import org.junit.jupiter.api.Test;

final class BroadStandingWaterBodySelectorTest {

    @Test
    void isolatedPixelAndOneCellWideTraceRemainRawWaterButNotHydrologicBodies() {
        WorldBounds bounds = new WorldBounds(0, 7, 0, 5, -4, 4);
        ElevationField elevation = field(bounds, (x, y) ->
                (x == 0 && y == 0)
                        || (x == 2 && y >= 0 && y <= 3)
                        || (x >= 4 && x <= 5 && y >= 1 && y <= 2)
                        || (x >= 6 && x <= 7 && y >= 4 && y <= 5));
        StandingWaterTopology raw = new ConnectedStandingWaterTopologyAnalyzer().analyze(elevation);

        StandingWaterTopology selected = new BroadStandingWaterBodySelector().select(raw);

        assertEquals(4, raw.bodyCount());
        assertEquals(2, selected.bodyCount());
        assertEquals(StandingWaterTopology.NO_BODY, selected.bodyIdAt(0, 0));
        assertEquals(StandingWaterTopology.NO_BODY, selected.bodyIdAt(2, 2));
        assertEquals(0, selected.bodyIdAt(4, 1));
        assertEquals(0, selected.bodyIdAt(5, 2));
        assertEquals(1, selected.bodyIdAt(6, 4));
        assertEquals(1, selected.bodyIdAt(7, 5));
    }

    @Test
    void selectionPreservesAcceptedBodyGeometryAndReindexesDenseIds() {
        WorldBounds bounds = new WorldBounds(0, 5, 0, 3, -4, 4);
        ElevationField elevation = field(bounds, (x, y) ->
                (x == 0 && y == 0)
                        || (x >= 3 && x <= 4 && y >= 1 && y <= 2));
        StandingWaterTopology raw = new ConnectedStandingWaterTopologyAnalyzer().analyze(elevation);
        StandingWaterBody acceptedRaw = raw.body(1);

        StandingWaterTopology selected = new BroadStandingWaterBodySelector().select(raw);

        assertEquals(1, selected.bodyCount());
        StandingWaterBody accepted = selected.body(0);
        assertEquals(acceptedRaw.cellCount(), accepted.cellCount());
        assertEquals(acceptedRaw.shorelineEdgeCount(), accepted.shorelineEdgeCount());
        assertEquals(acceptedRaw.touchesWorldBoundary(), accepted.touchesWorldBoundary());
        assertEquals(acceptedRaw.minX(), accepted.minX());
        assertEquals(acceptedRaw.maxX(), accepted.maxX());
        assertEquals(acceptedRaw.minY(), accepted.minY());
        assertEquals(acceptedRaw.maxY(), accepted.maxY());
    }

    private static ElevationField field(WorldBounds bounds, WaterPredicate water) {
        return new ElevationField() {
            @Override
            public WorldBounds bounds() {
                return bounds;
            }

            @Override
            public int elevationAt(int x, int y) {
                return water.test(x, y) ? -1 : 1;
            }
        };
    }

    @FunctionalInterface
    private interface WaterPredicate {
        boolean test(int x, int y);
    }
}
