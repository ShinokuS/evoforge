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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Diagnostic evidence for the one intentional V12 semantic migration: whole-raster slope sweeps. */
@Tag("scale-profile")
final class V12SlopeMigrationProfileTest {
    private static final int SIZE = 64;
    private static final int MAX_HEIGHT_CELLS = 96;

    @Test
    void reportsDifferenceFromHistoricalDirectionalSweeps() {
        ContinuumWorldDomain domain = new ContinuumWorldDomain(SIZE, SIZE);
        V15TerrainDefinition definition = V15TerrainDefinition.balanced();
        V12TerrainRecipe recipe = V12TerrainRecipe.balanced();
        V12TerrainCalibration terrain = V12TerrainCalibration.compile(domain, definition, recipe);
        V12ContinuumSlopeCalibration slopes = V12ContinuumSlopeCalibration.compile(
                terrain, recipe, MAX_HEIGHT_CELLS);

        for (long seed : new long[] {41L, 913L, 71_337L}) {
            V12LandRankPlan land = V12LandRankPlan.prepareUnconstrained(
                    domain, seed, terrain, recipe);
            V12UnrelaxedLandElevationField unrelaxed = new V12UnrelaxedLandElevationField(
                    domain,
                    seed,
                    land,
                    terrain,
                    recipe,
                    MAX_HEIGHT_CELLS);

            long[] historical = new long[SIZE * SIZE];
            boolean[] landMask = new boolean[historical.length];
            int index = 0;
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++, index++) {
                    historical[index] = unrelaxed.elevationSubunitsAt(x, y);
                    landMask[index] = historical[index] > 0L;
                }
            }
            historicalDirectionalRelax(
                    historical,
                    landMask,
                    SIZE,
                    SIZE,
                    slopes.maximumStepSubunits(),
                    slopes.maximumLandHeightSubunits(),
                    recipe.relaxationPasses());

            V12SlopeLimitedPageSource continuum = new V12SlopeLimitedPageSource(domain, unrelaxed, slopes);
            ContinuumScalarPage page = continuum.materialize(
                    new ContinuumSampleWindow(0, 0, SIZE, SIZE, 1));

            long absoluteDifference = 0L;
            long maximumDifference = 0L;
            int comparedLand = 0;
            index = 0;
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++, index++) {
                    long modern = (long) page.sample(x, y);
                    assertEquals(landMask[index], modern > 0L);
                    if (!landMask[index]) continue;
                    long difference = Math.abs(modern - historical[index]);
                    absoluteDifference = Math.addExact(absoluteDifference, difference);
                    maximumDifference = Math.max(maximumDifference, difference);
                    comparedLand++;
                    if (x + 1 < SIZE && (long) page.sample(x + 1, y) > 0L) {
                        assertTrue(Math.abs((long) page.sample(x + 1, y) - modern)
                                <= slopes.maximumStepSubunits());
                    }
                    if (y + 1 < SIZE && (long) page.sample(x, y + 1) > 0L) {
                        assertTrue(Math.abs((long) page.sample(x, y + 1) - modern)
                                <= slopes.maximumStepSubunits());
                    }
                }
            }

            double meanCells = absoluteDifference
                    / (double) Math.max(1, comparedLand)
                    / TerrainElevationField.SUBUNITS_PER_CELL;
            double maximumCells = maximumDifference / (double) TerrainElevationField.SUBUNITS_PER_CELL;
            System.out.printf(
                    "V12 slope migration seed=%d land=%d halo=%d step=%.3fZ meanAbs=%.4fZ maxAbs=%.4fZ%n",
                    seed,
                    comparedLand,
                    slopes.exactHaloCells(),
                    slopes.maximumStepSubunits() / (double) TerrainElevationField.SUBUNITS_PER_CELL,
                    meanCells,
                    maximumCells);
        }
    }

    private static void historicalDirectionalRelax(
            long[] elevations,
            boolean[] land,
            int width,
            int height,
            long maximumStep,
            long maximumHeight,
            int passes) {
        for (int pass = 0; pass < passes; pass++) {
            boolean reverse = (pass & 1) != 0;
            if (!reverse) {
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int cell = y * width + x;
                        if (!land[cell]) continue;
                        if (x + 1 < width) {
                            relaxPair(elevations, land, cell, cell + 1, maximumStep, maximumHeight);
                        }
                        if (y + 1 < height) {
                            relaxPair(elevations, land, cell, cell + width, maximumStep, maximumHeight);
                        }
                    }
                }
            } else {
                for (int y = height - 1; y >= 0; y--) {
                    for (int x = width - 1; x >= 0; x--) {
                        int cell = y * width + x;
                        if (!land[cell]) continue;
                        if (x > 0) {
                            relaxPair(elevations, land, cell, cell - 1, maximumStep, maximumHeight);
                        }
                        if (y > 0) {
                            relaxPair(elevations, land, cell, cell - width, maximumStep, maximumHeight);
                        }
                    }
                }
            }
        }
    }

    private static void relaxPair(
            long[] elevations,
            boolean[] land,
            int first,
            int second,
            long maximumStep,
            long maximumHeight) {
        if (!land[first] || !land[second]) return;
        long difference = elevations[first] - elevations[second];
        long magnitude = Math.abs(difference);
        if (magnitude <= maximumStep) return;
        long excess = magnitude - maximumStep;
        long firstCorrection = (excess + 1L) / 2L;
        long secondCorrection = excess - firstCorrection;
        if (difference > 0L) {
            elevations[first] = clampLandHeight(elevations[first] - firstCorrection, maximumHeight);
            elevations[second] = clampLandHeight(elevations[second] + secondCorrection, maximumHeight);
        } else {
            elevations[first] = clampLandHeight(elevations[first] + firstCorrection, maximumHeight);
            elevations[second] = clampLandHeight(elevations[second] - secondCorrection, maximumHeight);
        }
    }

    private static long clampLandHeight(long value, long maximumHeight) {
        return Math.max(1L, Math.min(maximumHeight, value));
    }
}
