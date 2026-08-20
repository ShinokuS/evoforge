package io.github.evoforge.simulation.world.atlas.hydrology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import org.junit.jupiter.api.Test;

final class PriorityFloodDrainageBasinTopologyAnalyzerTest {
    private static final long CELL = ElevationField.SUBUNITS_PER_CELL;

    @Test
    void derivesAboveSeaClosedDepressionAndItsSpillLevelWithoutChangingTerrain() {
        ElevationField elevation = bowlElevation();
        StandingWaterTopology outlets = new BroadStandingWaterBodySelector().select(
                new ConnectedStandingWaterTopologyAnalyzer().analyze(elevation));

        DrainageBasinTopology basins =
                new PriorityFloodDrainageBasinTopologyAnalyzer().analyze(elevation, outlets);

        assertEquals(1, basins.basinCount());
        DrainageBasin basin = basins.basin(0);
        assertEquals(4L, basin.cellCount());
        assertEquals(3L * CELL, basin.spillElevationSubunits());
        assertEquals(2L * CELL, basin.maximumDepthSubunits());
        assertTrue(basins.isBasinAt(3, 3));
        assertTrue(basins.isBasinAt(4, 4));
        assertFalse(basins.isBasinAt(2, 2));
        assertEquals(1L * CELL, elevation.elevationSubunitsAt(3, 3));
    }

    static ElevationField bowlElevation() {
        WorldBounds bounds = new WorldBounds(0, 7, 0, 7, -4, 8);
        return new ElevationField() {
            @Override
            public WorldBounds bounds() {
                return bounds;
            }

            @Override
            public int elevationAt(int x, int y) {
                return Math.toIntExact(Math.floorDiv(elevationSubunitsAt(x, y), CELL));
            }

            @Override
            public long elevationSubunitsAt(int x, int y) {
                if (x <= 1 || x >= 6 || y <= 1 || y >= 6) return -1L * CELL;
                if ((x == 3 || x == 4) && (y == 3 || y == 4)) return 1L * CELL;
                return 3L * CELL;
            }
        };
    }
}
