package io.github.evoforge.simulation.world.atlas.hydrology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import org.junit.jupiter.api.Test;

final class InteriorClosedDepressionBasinMorphologyAlgorithmTest {
    private static final long CELL = ElevationField.SUBUNITS_PER_CELL;

    @Test
    void basinMorphologyCreatesClosedInteriorDepressionWithoutChangingContinentalMembership() {
        WorldBounds bounds = new WorldBounds(0, 20, 0, 20, -4, 8);
        ElevationField base = new ElevationField() {
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
                if (x == 0 || y == 0 || x == 20 || y == 20) return -CELL;
                return 4L * CELL + x * (CELL / 20L);
            }
        };

        LacustrineBasinTerrain terrain = LacustrineBasinMorphologyAlgorithm.standard().generate(
                base,
                LacustrineBasinMorphologyRecipe.balanced());

        assertTrue(terrain.imprintedBasinCount() > 0);
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                long before = base.elevationSubunitsAt(x, y);
                long after = terrain.elevation().elevationSubunitsAt(x, y);
                assertEquals(before < 0L, after < 0L, "continental membership changed at " + x + "," + y);
                if (before < 0L) assertEquals(before, after, "ocean bathymetry changed at " + x + "," + y);
            }
        }

        StandingWaterTopology outlets = new BroadStandingWaterBodySelector().select(
                new ConnectedStandingWaterTopologyAnalyzer().analyze(terrain.elevation()));
        DrainageBasinTopology basins = DrainageBasinTopologyAnalyzer.standard().analyze(
                terrain.elevation(),
                outlets);
        assertTrue(basins.basinCount() > 0);
    }
}
