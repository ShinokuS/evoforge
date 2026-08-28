package io.github.evoforge.simulation.world.terrain.genesis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.continuum.field.ContinuumSampleWindow;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPage;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import io.github.evoforge.simulation.world.terrain.definition.V13MountainDefinition;
import io.github.evoforge.simulation.world.terrain.definition.V15TerrainDefinition;
import org.junit.jupiter.api.Test;

final class V15ContinuumLargeDomainTest {

    @Test
    void tenThousandSquareWorldUsesBoundedV15PlanAndMaterializesOnlyRequestedWindow() {
        ContinuumWorldDomain domain = new ContinuumWorldDomain(10_000, 10_000);
        V15ContinuumTerrainPlan plan = V15ContinuumTerrainPlan.prepare(
                domain,
                71_337L,
                V15TerrainDefinition.balanced(),
                V13MountainDefinition.balanced(),
                -96,
                96);

        assertEquals(domain, plan.domain());
        assertTrue(plan.usesScaledPlanning());
        assertEquals(V15ContinuumTerrainPlan.MAX_EXACT_PLANNING_AXIS, plan.planningDomain().width());
        assertEquals(V15ContinuumTerrainPlan.MAX_EXACT_PLANNING_AXIS, plan.planningDomain().height());
        assertEquals(domain, plan.elevationPages().domain());

        ContinuumSampleWindow requested = new ContinuumSampleWindow(4_980, 4_980, 32, 32, 1);
        ContinuumScalarPage page = plan.elevationPages().materialize(requested);
        assertEquals(requested, page.window());
    }

    @Test
    void squareWorldSizesReuseTheSameBoundedMacroPlan() {
        V15TerrainDefinition terrain = V15TerrainDefinition.balanced();
        V13MountainDefinition mountains = V13MountainDefinition.balanced();
        long seed = 9_913L;
        V15ContinuumTerrainPlan first = V15ContinuumTerrainPlan.prepare(
                new ContinuumWorldDomain(1_000, 1_000), seed, terrain, mountains, -96, 96);
        V15ContinuumTerrainPlan second = V15ContinuumTerrainPlan.prepare(
                new ContinuumWorldDomain(10_000, 10_000), seed, terrain, mountains, -96, 96);

        assertEquals(first.planningDomain(), second.planningDomain());
        assertSame(first.lakeBase(), second.lakeBase());
    }

    @Test
    void hundredThousandSquareDomainDoesNotOverflowMountainCalibrationArea() {
        ContinuumWorldDomain domain = new ContinuumWorldDomain(100_000, 100_000);
        V13MountainCalibration calibration = V13MountainCalibration.compile(
                domain,
                V13MountainDefinition.balanced(),
                V13MountainRecipe.balanced(),
                96);
        assertEquals(10_000_000_000L, calibration.area());
    }
}
