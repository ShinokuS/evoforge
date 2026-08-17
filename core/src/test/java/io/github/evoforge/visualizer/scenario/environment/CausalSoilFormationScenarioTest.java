package io.github.evoforge.visualizer.scenario.environment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.landscape.soil.SoilProperties;
import io.github.evoforge.visualizer.scenario.ScenarioSession;
import org.junit.jupiter.api.Test;

final class CausalSoilFormationScenarioTest {

    @Test
    void sameTerrainMaterialRespondsDifferentlyAfterCausalFormationAndThenDries() {
        ScenarioSession session = new CausalSoilFormationScenario().create();

        assertEquals(
                session.runtime().view().terrain().find(
                        CausalSoilFormationScenario.RIDGE_X,
                        CausalSoilFormationScenario.CENTER_Y,
                        CausalSoilFormationScenario.SURFACE_Z),
                session.runtime().view().terrain().find(
                        CausalSoilFormationScenario.BASIN_X,
                        CausalSoilFormationScenario.CENTER_Y,
                        CausalSoilFormationScenario.SURFACE_Z),
                "acceptance cells must share one runtime Terrain material identity");

        SoilProperties ridge = session.runtime().view().soilProperties().find(
                CausalSoilFormationScenario.RIDGE_X,
                CausalSoilFormationScenario.CENTER_Y,
                CausalSoilFormationScenario.SURFACE_Z);
        SoilProperties basin = session.runtime().view().soilProperties().find(
                CausalSoilFormationScenario.BASIN_X,
                CausalSoilFormationScenario.CENTER_Y,
                CausalSoilFormationScenario.SURFACE_Z);

        assertTrue(
                ridge.permeability() > basin.permeability(),
                "convex exposure should develop faster infiltration than the concave accumulation site");
        assertTrue(
                ridge.capacity() < basin.capacity(),
                "finer accumulated soil should expose more pore capacity in the calibrated profile");

        assertEquals(0L, ridgeWater(session).retained());
        assertEquals(0L, basinWater(session).retained());
        assertEquals(0L, ridgeWater(session).free());
        assertEquals(0L, basinWater(session).free());

        session.runtime().stepper().advance();

        CausalSoilFormationScenario.CellWater ridgeWater = ridgeWater(session);
        CausalSoilFormationScenario.CellWater basinWater = basinWater(session);
        assertTrue(
                ridgeWater.retained() > basinWater.retained(),
                "the faster developed soil must absorb more of the same first rain pulse");
        assertTrue(
                basinWater.free() > ridgeWater.free(),
                "the slower concave soil must leave more free Water without any Puddle generator");

        while (session.runtime().time().tick() < 40L) {
            session.runtime().stepper().advance();
        }

        assertEquals(0L, ridgeWater(session).retained(), "dry phase must empty retained ridge Water");
        assertEquals(0L, basinWater(session).retained(), "dry phase must empty retained basin Water");
        assertEquals(0L, ridgeWater(session).free(), "dry phase must empty free ridge Water");
        assertEquals(0L, basinWater(session).free(), "dry phase must empty free basin Water");
    }

    private static CausalSoilFormationScenario.CellWater ridgeWater(ScenarioSession session) {
        return CausalSoilFormationScenario.waterAt(
                session.runtime(),
                CausalSoilFormationScenario.RIDGE_X,
                CausalSoilFormationScenario.CENTER_Y);
    }

    private static CausalSoilFormationScenario.CellWater basinWater(ScenarioSession session) {
        return CausalSoilFormationScenario.waterAt(
                session.runtime(),
                CausalSoilFormationScenario.BASIN_X,
                CausalSoilFormationScenario.CENTER_Y);
    }
}
