package io.github.evoforge.simulation.world.terrain.genesis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import io.github.evoforge.simulation.world.terrain.definition.V15TerrainDefinition;
import org.junit.jupiter.api.Test;

final class V14LandmassPlanTest {

    @Test
    void preservesExternalOceanAndDeterministicCompactSupport() {
        ContinuumWorldDomain domain = new ContinuumWorldDomain(64, 64);
        V15TerrainDefinition definition = V15TerrainDefinition.balanced();
        V12TerrainCalibration terrain = V12TerrainCalibration.compile(
                domain, definition, V12TerrainRecipe.balanced());

        V14LandmassPlan first = V14LandmassPlan.prepare(domain, 71_337L, definition, terrain);
        V14LandmassPlan second = V14LandmassPlan.prepare(domain, 71_337L, definition, terrain);

        assertTrue(first.supportCellCount() > 0L);
        assertTrue(first.supportCellCount() <= first.maximumLandCells());
        assertEquals(first.supportCellCount(), second.supportCellCount());

        for (int coordinate = 0; coordinate < 64; coordinate++) {
            assertFalse(first.supports(0, coordinate));
            assertFalse(first.supports(63, coordinate));
            assertFalse(first.supports(coordinate, 0));
            assertFalse(first.supports(coordinate, 63));
        }

        int[][] probes = {
                {7, 7}, {16, 20}, {31, 31}, {47, 22}, {55, 51}
        };
        for (int[] probe : probes) {
            assertEquals(first.supports(probe[0], probe[1]), second.supports(probe[0], probe[1]));
            assertEquals(
                    first.potentialPpmAt(probe[0], probe[1]),
                    second.potentialPpmAt(probe[0], probe[1]));
        }
    }

    @Test
    void localPotentialWindowsMatchPointExactRelaxationAtEdgesAndInterior() {
        ContinuumWorldDomain domain = new ContinuumWorldDomain(96, 80);
        V15TerrainDefinition definition = V15TerrainDefinition.balanced();
        V12TerrainCalibration terrain = V12TerrainCalibration.compile(
                domain, definition, V12TerrainRecipe.balanced());
        V14LandmassPlan plan = V14LandmassPlan.prepare(domain, -918_273_645L, definition, terrain);

        assertWindowMatchesPoints(plan, 0, 0, 13, 11);
        assertWindowMatchesPoints(plan, 83, 0, 13, 9);
        assertWindowMatchesPoints(plan, 0, 69, 15, 11);
        assertWindowMatchesPoints(plan, 81, 67, 15, 13);
        assertWindowMatchesPoints(plan, 27, 19, 31, 23);
    }

    private static void assertWindowMatchesPoints(
            V14LandmassPlan plan,
            int minX,
            int minY,
            int width,
            int height) {
        int[] actual = new int[Math.multiplyExact(width, height)];
        plan.fillPotentialWindow(minX, minY, width, height, actual);
        int cursor = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++, cursor++) {
                assertEquals(
                        plan.potentialPpmAt(minX + x, minY + y),
                        actual[cursor],
                        "local V14 window differs at " + (minX + x) + "," + (minY + y));
            }
        }
    }
}
