package io.github.evoforge.simulation.world.terrain.genesis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.continuum.field.ContinuumSampleWindow;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPage;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import io.github.evoforge.simulation.world.terrain.definition.V15TerrainDefinition;
import org.junit.jupiter.api.Test;

final class V14ContinuumBaseTerrainPlanTest {

    @Test
    void composesAcceptedLandmassRankReliefAndBoundedSlopePages() {
        ContinuumWorldDomain domain = new ContinuumWorldDomain(16, 16);
        V14ContinuumBaseTerrainPlan first = V14ContinuumBaseTerrainPlan.prepare(
                domain, 71_337L, V15TerrainDefinition.balanced(), 24);
        V14ContinuumBaseTerrainPlan second = V14ContinuumBaseTerrainPlan.prepare(
                domain, 71_337L, V15TerrainDefinition.balanced(), 24);
        ContinuumSampleWindow window = new ContinuumSampleWindow(0, 0, 16, 16, 1);
        ContinuumScalarPage firstPage = first.elevationPages().materialize(window);
        ContinuumScalarPage secondPage = second.elevationPages().materialize(window);

        long land = 0L;
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                long value = (long) firstPage.sample(x, y);
                assertEquals(value, (long) secondPage.sample(x, y));
                assertEquals(first.landRank().isLand(x, y), value > 0L);
                if (value > 0L) land++;
            }
        }
        assertEquals(first.landRank().landCount(), land);
        assertTrue(land > 0L);

        for (int i = 0; i < 16; i++) {
            assertTrue(firstPage.sample(i, 0) < 0d);
            assertTrue(firstPage.sample(i, 15) < 0d);
            assertTrue(firstPage.sample(0, i) < 0d);
            assertTrue(firstPage.sample(15, i) < 0d);
        }
    }
}
