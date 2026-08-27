package io.github.evoforge.simulation.world.terrain.genesis;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import io.github.evoforge.simulation.world.terrain.definition.V15TerrainDefinition;
import org.junit.jupiter.api.Test;

final class V12LandRankPlanTest {

    @Test
    void reproducesExactAuthoredLandCountWithoutWorldMask() {
        ContinuumWorldDomain domain = new ContinuumWorldDomain(64, 64);
        V15TerrainDefinition definition = V15TerrainDefinition.balanced();
        V12TerrainRecipe recipe = V12TerrainRecipe.balanced();
        V12TerrainCalibration calibration = V12TerrainCalibration.compile(domain, definition, recipe);
        V12LandRankPlan plan = V12LandRankPlan.prepareUnconstrained(domain, 41L, calibration, recipe);

        long count = 0L;
        for (long y = 0L; y < domain.height(); y++) {
            for (long x = 0L; x < domain.width(); x++) {
                if (plan.isLand(x, y)) count++;
            }
        }

        assertEquals(calibration.landCount(), plan.landCount());
        assertEquals(calibration.landCount(), count);
    }

    @Test
    void continuumAddressesMapToTheCenteredCoordinatesOfTheOldVisualizer() {
        ContinuumWorldDomain domain = new ContinuumWorldDomain(64, 48);
        V15TerrainCoordinateFrame frame = V15TerrainCoordinateFrame.centered(domain);

        assertEquals(-32L, frame.legacyX(0L));
        assertEquals(31L, frame.legacyX(63L));
        assertEquals(-24L, frame.legacyY(0L));
        assertEquals(23L, frame.legacyY(47L));
        assertEquals(0L, frame.cellIndex(0L, 0L));
        assertEquals(64L * 47L + 63L, frame.cellIndex(63L, 47L));
    }
}
