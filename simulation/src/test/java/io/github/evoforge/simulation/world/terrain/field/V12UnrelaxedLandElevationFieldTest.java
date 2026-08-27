package io.github.evoforge.simulation.world.terrain.field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import io.github.evoforge.simulation.world.terrain.definition.V15TerrainDefinition;
import io.github.evoforge.simulation.world.terrain.genesis.V12LandRankPlan;
import io.github.evoforge.simulation.world.terrain.genesis.V12TerrainCalibration;
import io.github.evoforge.simulation.world.terrain.genesis.V12TerrainRecipe;
import org.junit.jupiter.api.Test;

final class V12UnrelaxedLandElevationFieldTest {

    @Test
    void isDeterministicAndPreservesLandWaterMembership() {
        ContinuumWorldDomain domain = new ContinuumWorldDomain(48, 48);
        V15TerrainDefinition definition = V15TerrainDefinition.balanced();
        V12TerrainRecipe recipe = V12TerrainRecipe.balanced();
        V12TerrainCalibration calibration = V12TerrainCalibration.compile(domain, definition, recipe);
        V12LandRankPlan land = V12LandRankPlan.prepareUnconstrained(domain, 913L, calibration, recipe);
        V12UnrelaxedLandElevationField first = new V12UnrelaxedLandElevationField(
                domain, 913L, land, calibration, recipe, 96);
        V12UnrelaxedLandElevationField second = new V12UnrelaxedLandElevationField(
                domain, 913L, land, calibration, recipe, 96);

        long positive = 0L;
        long negative = 0L;
        for (long y = 0L; y < domain.height(); y += 3L) {
            for (long x = 0L; x < domain.width(); x += 3L) {
                long a = first.elevationSubunitsAt(x, y);
                long b = second.elevationSubunitsAt(x, y);
                assertEquals(a, b);
                assertEquals(land.isLand(x, y), a > 0L);
                if (a > 0L) positive++; else negative++;
            }
        }
        assertTrue(positive > 0L);
        assertTrue(negative > 0L);
    }
}
