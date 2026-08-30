package io.github.evoforge.simulation.world.terrain.genesis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.continuum.field.ContinuumSampleWindow;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPage;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import io.github.evoforge.simulation.world.terrain.definition.V13MountainDefinition;
import io.github.evoforge.simulation.world.terrain.definition.V15TerrainDefinition;
import org.junit.jupiter.api.Test;

final class V15ContinuumProductionTerrainPlanTest {
    private static final long SEED = -4_774_846_722_868_265_927L;

    @Test
    void productionPlanIsRequestLocalDeterministicAndKeepsLakesOnActualLand() {
        ContinuumWorldDomain domain = new ContinuumWorldDomain(500, 500);
        V15ContinuumProductionTerrainPlan plan = V15ContinuumProductionTerrainPlan.prepare(
                domain,
                SEED,
                V15TerrainDefinition.balanced(),
                V13MountainDefinition.balanced(),
                -96,
                96);

        assertEquals(domain, plan.domain());
        assertEquals(domain, plan.elevationPages().domain());
        assertTrue(plan.continental().landRank().usesSampledRank());
        assertTrue(plan.lakes().lakeBodyCount() <= V15InlandLakeDomainRecipe.balanced().maximumLakeBodies());

        ContinuumSampleWindow first = new ContinuumSampleWindow(217, 231, 24, 20, 1);
        ContinuumSampleWindow overlap = new ContinuumSampleWindow(225, 236, 24, 20, 1);
        ContinuumScalarPage firstPage = plan.elevationPages().materialize(first);
        plan.elevationPages().materialize(overlap);
        ContinuumScalarPage repeated = plan.elevationPages().materialize(first);
        for (int y = 0; y < first.height(); y++) {
            for (int x = 0; x < first.width(); x++) {
                assertEquals(firstPage.sample(x, y), repeated.sample(x, y), 0.0d);
            }
        }

        for (long y = 2; y < domain.height(); y += 17) {
            for (long x = 3; x < domain.width(); x += 19) {
                boolean lake = plan.lakes().isLake(x, y);
                assertEquals(lake, plan.lakes().isLake(x, y));
                if (lake) {
                    assertTrue(plan.continental().unrelaxedElevation().elevationSubunitsAt(x, y) > 0L);
                    assertTrue(plan.lakes().normalizedRadius(x, y) <= 1.18d);
                }
            }
        }
        assertThrows(IllegalArgumentException.class, () -> plan.lakes().isLake(-1, 0));
    }

    @Test
    void coarseAndUnitBathymetryQueriesStayBoundedAndPreserveSigns() {
        ContinuumWorldDomain domain = new ContinuumWorldDomain(192, 192);
        V15ContinuumProductionTerrainPlan plan = V15ContinuumProductionTerrainPlan.prepare(
                domain,
                771_991L,
                V15TerrainDefinition.balanced(),
                V13MountainDefinition.balanced(),
                -96,
                96);

        ContinuumScalarPage unit = plan.elevationPages().materialize(
                new ContinuumSampleWindow(72, 76, 20, 18, 1));
        ContinuumScalarPage coarse = plan.elevationPages().materialize(
                new ContinuumSampleWindow(4, 7, 20, 18, 7));
        assertEquals(20, unit.window().width());
        assertEquals(18, unit.window().height());
        assertEquals(20, coarse.window().width());
        assertEquals(18, coarse.window().height());

        for (int y = 0; y < unit.window().height(); y++) {
            for (int x = 0; x < unit.window().width(); x++) {
                assertTrue(Double.isFinite(unit.sample(x, y)));
            }
        }
        for (int y = 0; y < coarse.window().height(); y++) {
            for (int x = 0; x < coarse.window().width(); x++) {
                assertTrue(Double.isFinite(coarse.sample(x, y)));
            }
        }
    }

    @Test
    void productionCalibrationDoesNotHaveHistoricalIntAreaLimit() {
        ContinuumWorldDomain huge = new ContinuumWorldDomain(100_000, 100_000);
        V14BathymetryRecipe recipe = V14BathymetryRecipe.balanced();
        V14ContinuumBathymetryCalibration calibration =
                V14ContinuumBathymetryCalibration.compile(huge, -96, recipe);

        assertEquals(100_000L, calibration.width());
        assertEquals(100_000L, calibration.height());
        assertTrue(calibration.floorSubunits() < 0L);
        assertTrue(calibration.maximumCardinalFallSubunits() > 0L);
        assertTrue(calibration.worldDepthCapSubunits() > 0L);
        assertTrue(calibration.coastalContextRadiusCells() >= recipe.coastalContextMinimumCells());
        assertTrue(calibration.coastalContextRadiusCells() <= recipe.coastalContextMaximumCells());

        assertThrows(
                IllegalArgumentException.class,
                () -> V14ContinuumBathymetryCalibration.compile(huge, 0, recipe));
        assertThrows(
                IllegalArgumentException.class,
                () -> V14ContinuumBathymetryCalibration.compile(null, -96, recipe));
        assertFalse(calibration.floorSubunits() == 0L);
    }
}
