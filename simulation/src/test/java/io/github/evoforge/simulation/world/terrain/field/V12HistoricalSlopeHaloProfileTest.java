package io.github.evoforge.simulation.world.terrain.field;

import io.github.evoforge.simulation.definition.NormalizedValue;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import io.github.evoforge.simulation.world.terrain.definition.V15TerrainDefinition;
import io.github.evoforge.simulation.world.terrain.genesis.V12ContinuumSlopeCalibration;
import io.github.evoforge.simulation.world.terrain.genesis.V12LandRankPlan;
import io.github.evoforge.simulation.world.terrain.genesis.V12TerrainCalibration;
import io.github.evoforge.simulation.world.terrain.genesis.V12TerrainRecipe;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Profiles exact historical V12 sweeps when evaluated on a bounded page plus increasing halo. */
@Tag("scale-profile")
final class V12HistoricalSlopeHaloProfileTest {
    private static final int SIZE = 192;
    private static final int TARGET_SIZE = 32;
    private static final int MAX_HEIGHT_CELLS = 96;
    private static final int[][] TARGETS = {
            {48, 48},
            {80, 80},
            {112, 48}
    };
    private static final int[] HALOS = {16, 24, 32, 48};
    private static final long[] SEEDS = {41L, 913L, 71_337L};

    @Test
    void reportsConvergenceOfBoundedHistoricalSweeps() {
        profile("balanced", V15TerrainDefinition.balanced());
        profile("full-land", new V15TerrainDefinition(
                NormalizedValue.of(1.0),
                NormalizedValue.of(0.5),
                NormalizedValue.of(0.5),
                NormalizedValue.of(0.6),
                NormalizedValue.of(0.45),
                NormalizedValue.of(0.5),
                NormalizedValue.of(0.35)));
    }

    private static void profile(String label, V15TerrainDefinition definition) {
        ContinuumWorldDomain domain = new ContinuumWorldDomain(SIZE, SIZE);
        V12TerrainRecipe recipe = V12TerrainRecipe.balanced();
        V12TerrainCalibration terrain = V12TerrainCalibration.compile(domain, definition, recipe);
        V12ContinuumSlopeCalibration slopes = V12ContinuumSlopeCalibration.compile(
                terrain, recipe, MAX_HEIGHT_CELLS);

        for (long seed : SEEDS) {
            V12LandRankPlan land = V12LandRankPlan.prepareUnconstrained(domain, seed, terrain, recipe);
            V12UnrelaxedLandElevationField unrelaxed = new V12UnrelaxedLandElevationField(
                    domain, seed, land, terrain, recipe, MAX_HEIGHT_CELLS);

            long[] base = new long[SIZE * SIZE];
            boolean[] mask = new boolean[base.length];
            int index = 0;
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++, index++) {
                    base[index] = unrelaxed.elevationSubunitsAt(x, y);
                    mask[index] = base[index] > 0L;
                }
            }

            long[] reference = base.clone();
            historicalDirectionalRelax(
                    reference,
                    mask,
                    SIZE,
                    SIZE,
                    slopes.maximumStepSubunits(),
                    slopes.maximumLandHeightSubunits(),
                    recipe.relaxationPasses());

            for (int targetIndex = 0; targetIndex < TARGETS.length; targetIndex++) {
                int targetMinX = TARGETS[targetIndex][0];
                int targetMinY = TARGETS[targetIndex][1];
                for (int halo : HALOS) {
                    ProfileDifference difference = compareBounded(
                            base,
                            mask,
                            reference,
                            targetMinX,
                            targetMinY,
                            halo,
                            slopes,
                            recipe);
                    System.out.printf(
                            "V12 historical-halo profile=%s seed=%d target=%d,%d halo=%d targetLand=%d exact=%d meanAbs=%.6fZ maxAbs=%.6fZ%n",
                            label,
                            seed,
                            targetMinX,
                            targetMinY,
                            halo,
                            difference.comparedLand(),
                            difference.exact(),
                            difference.meanCells(),
                            difference.maximumCells());
                }
            }
        }
    }

    private static ProfileDifference compareBounded(
            long[] base,
            boolean[] mask,
            long[] reference,
            int targetMinX,
            int targetMinY,
            int halo,
            V12ContinuumSlopeCalibration slopes,
            V12TerrainRecipe recipe) {
        int minX = targetMinX - halo;
        int minY = targetMinY - halo;
        int width = TARGET_SIZE + halo * 2;
        int height = TARGET_SIZE + halo * 2;
        long[] bounded = new long[width * height];
        boolean[] boundedMask = new boolean[bounded.length];
        int local = 0;
        for (int y = 0; y < height; y++) {
            int worldY = minY + y;
            for (int x = 0; x < width; x++, local++) {
                int worldX = minX + x;
                int worldIndex = worldY * SIZE + worldX;
                bounded[local] = base[worldIndex];
                boundedMask[local] = mask[worldIndex];
            }
        }
        historicalDirectionalRelax(
                bounded,
                boundedMask,
                width,
                height,
                slopes.maximumStepSubunits(),
                slopes.maximumLandHeightSubunits(),
                recipe.relaxationPasses());

        long absoluteDifference = 0L;
        long maximumDifference = 0L;
        int comparedLand = 0;
        int exact = 0;
        for (int y = 0; y < TARGET_SIZE; y++) {
            for (int x = 0; x < TARGET_SIZE; x++) {
                int worldX = targetMinX + x;
                int worldY = targetMinY + y;
                int worldIndex = worldY * SIZE + worldX;
                if (!mask[worldIndex]) continue;
                int boundedIndex = (y + halo) * width + (x + halo);
                long delta = Math.abs(bounded[boundedIndex] - reference[worldIndex]);
                if (delta == 0L) exact++;
                absoluteDifference = Math.addExact(absoluteDifference, delta);
                maximumDifference = Math.max(maximumDifference, delta);
                comparedLand++;
            }
        }
        double meanCells = absoluteDifference
                / (double) Math.max(1, comparedLand)
                / TerrainElevationField.SUBUNITS_PER_CELL;
        double maximumCells = maximumDifference / (double) TerrainElevationField.SUBUNITS_PER_CELL;
        return new ProfileDifference(comparedLand, exact, meanCells, maximumCells);
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

    private record ProfileDifference(
            int comparedLand,
            int exact,
            double meanCells,
            double maximumCells) {}
}
