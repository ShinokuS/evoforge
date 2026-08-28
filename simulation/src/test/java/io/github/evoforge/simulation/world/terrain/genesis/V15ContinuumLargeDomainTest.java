package io.github.evoforge.simulation.world.terrain.genesis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.continuum.field.ContinuumSampleWindow;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPage;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import io.github.evoforge.simulation.world.terrain.definition.V13MountainDefinition;
import io.github.evoforge.simulation.world.terrain.definition.V15TerrainDefinition;
import org.junit.jupiter.api.Test;

final class V15ContinuumLargeDomainTest {

    @Test
    void fiveThousandSquareWorldUsesBoundedV15PlanAndMaterializesOnlyRequestedWindow() {
        ContinuumWorldDomain domain = new ContinuumWorldDomain(5_000, 5_000);
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

        ContinuumSampleWindow requested = new ContinuumSampleWindow(2_480, 2_480, 32, 32, 1);
        ContinuumScalarPage page = plan.elevationPages().materialize(requested);
        assertEquals(requested, page.window());
    }
}
