package io.github.evoforge.simulation.world.terrain.genesis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.github.evoforge.simulation.world.continuum.field.ContinuumSampleWindow;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPage;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import io.github.evoforge.simulation.world.terrain.definition.V13MountainDefinition;
import io.github.evoforge.simulation.world.terrain.definition.V15TerrainDefinition;
import org.junit.jupiter.api.Test;

final class V15ContinuumLargeDomainTest {
    @Test
    void worldPastFormerThresholdUsesItsActualV15Domain() {
        ContinuumWorldDomain domain = new ContinuumWorldDomain(320, 320);
        V15ContinuumTerrainPlan plan = V15ContinuumTerrainPlan.prepare(
                domain, 71_337L, V15TerrainDefinition.balanced(),
                V13MountainDefinition.balanced(), -96, 96);

        assertEquals(domain, plan.domain());
        assertEquals(domain, plan.planningDomain());
        assertFalse(plan.usesScaledPlanning());
        assertEquals(domain, plan.lakeBase().elevationPages().domain());
        assertEquals(domain, plan.mountainPages().domain());
        assertEquals(domain, plan.elevationPages().domain());

        ContinuumSampleWindow requested = new ContinuumSampleWindow(152, 152, 8, 8, 1);
        ContinuumScalarPage page = plan.elevationPages().materialize(requested);
        assertEquals(requested, page.window());
    }

    @Test
    void hundredThousandSquareDomainDoesNotOverflowMountainCalibrationArea() {
        ContinuumWorldDomain domain = new ContinuumWorldDomain(100_000, 100_000);
        V13MountainCalibration calibration = V13MountainCalibration.compile(
                domain, V13MountainDefinition.balanced(),
                V13MountainRecipe.balanced(), 96);
        assertEquals(10_000_000_000L, calibration.area());
    }
}
