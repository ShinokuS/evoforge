package io.github.evoforge.simulation.world.terrain.field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.continuum.field.ContinuumSampleWindow;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPage;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import io.github.evoforge.simulation.world.terrain.definition.V15TerrainDefinition;
import io.github.evoforge.simulation.world.terrain.genesis.V12ContinuumSlopeCalibration;
import io.github.evoforge.simulation.world.terrain.genesis.V12LandRankPlan;
import io.github.evoforge.simulation.world.terrain.genesis.V12TerrainCalibration;
import io.github.evoforge.simulation.world.terrain.genesis.V12TerrainRecipe;
import org.junit.jupiter.api.Test;

final class V12HistoricalSlopePageSourceTest {

    @Test
    void boundedWindowMatchesWholeDomainHistoricalOracleOnDifficultV12Seed() {
        int size = 160;
        ContinuumWorldDomain domain = new ContinuumWorldDomain(size, size);
        V15TerrainDefinition definition = V15TerrainDefinition.balanced();
        V12TerrainRecipe recipe = V12TerrainRecipe.balanced();
        V12TerrainCalibration terrain = V12TerrainCalibration.compile(domain, definition, recipe);
        V12LandRankPlan land = V12LandRankPlan.prepareUnconstrained(
                domain, 71_337L, terrain, recipe);
        V12UnrelaxedLandElevationField unrelaxed = new V12UnrelaxedLandElevationField(
                domain, 71_337L, land, terrain, recipe, 96);
        V12ContinuumSlopeCalibration slope = V12ContinuumSlopeCalibration.compile(
                terrain, recipe, 96);
        V12HistoricalSlopePageSource source = new V12HistoricalSlopePageSource(
                domain, unrelaxed, slope, recipe);

        ContinuumScalarPage whole = source.materialize(
                new ContinuumSampleWindow(0, 0, size, size, 1));
        ContinuumSampleWindow firstWindow = new ContinuumSampleWindow(48, 48, 32, 32, 1);
        ContinuumSampleWindow secondWindow = new ContinuumSampleWindow(64, 48, 32, 32, 1);
        ContinuumScalarPage first = source.materialize(firstWindow);
        ContinuumScalarPage second = source.materialize(secondWindow);

        assertEquals(48, source.migrationHaloCells());
        for (int y = 0; y < firstWindow.height(); y++) {
            for (int x = 0; x < firstWindow.width(); x++) {
                int worldX = Math.toIntExact(firstWindow.xAt(x));
                int worldY = Math.toIntExact(firstWindow.yAt(y));
                assertEquals((long) whole.sample(worldX, worldY), (long) first.sample(x, y));
            }
        }
        for (int y = 0; y < secondWindow.height(); y++) {
            for (int x = 0; x < secondWindow.width(); x++) {
                int worldX = Math.toIntExact(secondWindow.xAt(x));
                int worldY = Math.toIntExact(secondWindow.yAt(y));
                assertEquals((long) whole.sample(worldX, worldY), (long) second.sample(x, y));
            }
        }
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 16; x++) {
                assertEquals((long) first.sample(x + 16, y), (long) second.sample(x, y));
            }
        }
    }

    @Test
    void materializationPreservesHistoricalLandWaterMembership() {
        ContinuumWorldDomain domain = new ContinuumWorldDomain(64, 64);
        V15TerrainDefinition definition = V15TerrainDefinition.balanced();
        V12TerrainRecipe recipe = V12TerrainRecipe.balanced();
        V12TerrainCalibration terrain = V12TerrainCalibration.compile(domain, definition, recipe);
        V12LandRankPlan land = V12LandRankPlan.prepareUnconstrained(domain, 913L, terrain, recipe);
        V12UnrelaxedLandElevationField unrelaxed = new V12UnrelaxedLandElevationField(
                domain, 913L, land, terrain, recipe, 96);
        V12ContinuumSlopeCalibration slope = V12ContinuumSlopeCalibration.compile(
                terrain, recipe, 96);
        V12HistoricalSlopePageSource source = new V12HistoricalSlopePageSource(
                domain, unrelaxed, slope, recipe);
        ContinuumScalarPage page = source.materialize(new ContinuumSampleWindow(0, 0, 64, 64, 1));

        long landCells = 0L;
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                boolean expectedLand = land.isLand(x, y);
                assertEquals(expectedLand, page.sample(x, y) > 0d);
                if (expectedLand) {
                    assertTrue(page.sample(x, y) <= slope.maximumLandHeightSubunits());
                    landCells++;
                }
            }
        }
        assertEquals(land.landCount(), landCells);
    }
}
