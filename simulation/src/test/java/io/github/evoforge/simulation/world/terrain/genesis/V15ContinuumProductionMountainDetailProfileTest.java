package io.github.evoforge.simulation.world.terrain.genesis;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.continuum.field.ContinuumSampleWindow;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPage;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import io.github.evoforge.simulation.world.terrain.definition.V13MountainDefinition;
import io.github.evoforge.simulation.world.terrain.definition.V15TerrainDefinition;
import io.github.evoforge.simulation.world.terrain.field.TerrainElevationField;
import java.util.Locale;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Dense raw-uplift audit, intentionally upstream of bathymetry and presentation. */
@Tag("scale-profile")
final class V15ContinuumProductionMountainDetailProfileTest {
    private static final long SEED = -4_774_846_722_868_265_927L;
    private static final int SIDE = 500;
    private static final int PATCH_SIDE = 64;
    private static final double MIN_MASK_IOU = 0.95;
    private static final double MAX_COVERAGE_DRIFT = 0.03;
    private static final double MAX_UPLIFT_MAE_CELLS = 0.08;
    private static final double MIN_NEIGHBOUR_VARIATION_RATIO = 0.75;
    private static final double MAX_NEIGHBOUR_VARIATION_RATIO = 1.30;
    private static final double MAX_CURVATURE_RATIO = 1.60;
    private static final int MAX_EXTRA_ISOLATED_PEAKS = 1;
    private static final int[][] PATCHES = {
            {24, 24},
            {188, 48},
            {56, 276},
            {314, 316}
    };

    @Test
    void rawMountainUpliftStaysCanonicalAndDoesNotGainNoise() {
        ContinuumWorldDomain domain = new ContinuumWorldDomain(SIDE, SIDE);
        V15TerrainDefinition terrain = V15TerrainDefinition.balanced();
        V13MountainDefinition mountains = V13MountainDefinition.balanced();
        V15ContinuumTerrainPlan exact = V15ContinuumTerrainPlan.prepare(
                domain, SEED, terrain, mountains, -96, 96);
        V15ContinuumProductionTerrainPlan production = V15ContinuumProductionTerrainPlan.prepare(
                domain, SEED, terrain, mountains, -96, 96);

        long cells = 0L;
        long exactPositive = 0L;
        long productionPositive = 0L;
        long maskIntersection = 0L;
        long maskUnion = 0L;
        long exactLocalMaxima = 0L;
        long productionLocalMaxima = 0L;
        long exactIsolated = 0L;
        long productionIsolated = 0L;
        double upliftAbsError = 0d;
        double exactNeighbourVariation = 0d;
        double productionNeighbourVariation = 0d;
        double exactCurvature = 0d;
        double productionCurvature = 0d;
        long neighbourPairs = 0L;
        long curvatureSamples = 0L;
        double subunits = TerrainElevationField.SUBUNITS_PER_CELL;

        for (int[] origin : PATCHES) {
            ContinuumSampleWindow window = new ContinuumSampleWindow(
                    origin[0], origin[1], PATCH_SIDE, PATCH_SIDE, 1L);
            ContinuumScalarPage exactBase = exact.lakeBase().elevationPages().materialize(window);
            ContinuumScalarPage exactMountains = exact.mountainPages().materialize(window);
            ContinuumScalarPage productionBase = production.lakeBasePages().materialize(window);
            ContinuumScalarPage productionMountains = production.mountainPages().materialize(window);

            double[][] exactUplift = uplift(exactBase, exactMountains);
            double[][] productionUplift = uplift(productionBase, productionMountains);
            for (int y = 0; y < PATCH_SIDE; y++) {
                for (int x = 0; x < PATCH_SIDE; x++) {
                    double exactValue = exactUplift[y][x];
                    double productionValue = productionUplift[y][x];
                    boolean exactMountain = exactValue > 0.5d;
                    boolean productionMountain = productionValue > 0.5d;
                    cells++;
                    if (exactMountain) exactPositive++;
                    if (productionMountain) productionPositive++;
                    if (exactMountain && productionMountain) maskIntersection++;
                    if (exactMountain || productionMountain) maskUnion++;
                    upliftAbsError += Math.abs(exactValue - productionValue) / subunits;

                    if (x + 1 < PATCH_SIDE) {
                        exactNeighbourVariation += Math.abs(exactValue - exactUplift[y][x + 1]) / subunits;
                        productionNeighbourVariation += Math.abs(productionValue - productionUplift[y][x + 1]) / subunits;
                        neighbourPairs++;
                    }
                    if (y + 1 < PATCH_SIDE) {
                        exactNeighbourVariation += Math.abs(exactValue - exactUplift[y + 1][x]) / subunits;
                        productionNeighbourVariation += Math.abs(productionValue - productionUplift[y + 1][x]) / subunits;
                        neighbourPairs++;
                    }
                    if (x > 0 && x + 1 < PATCH_SIDE) {
                        exactCurvature += Math.abs(exactUplift[y][x - 1] - 2d * exactValue + exactUplift[y][x + 1]) / subunits;
                        productionCurvature += Math.abs(productionUplift[y][x - 1] - 2d * productionValue + productionUplift[y][x + 1]) / subunits;
                        curvatureSamples++;
                    }
                    if (y > 0 && y + 1 < PATCH_SIDE) {
                        exactCurvature += Math.abs(exactUplift[y - 1][x] - 2d * exactValue + exactUplift[y + 1][x]) / subunits;
                        productionCurvature += Math.abs(productionUplift[y - 1][x] - 2d * productionValue + productionUplift[y + 1][x]) / subunits;
                        curvatureSamples++;
                    }
                    if (x > 0 && x + 1 < PATCH_SIDE && y > 0 && y + 1 < PATCH_SIDE) {
                        if (localMaximum(exactUplift, x, y)) exactLocalMaxima++;
                        if (localMaximum(productionUplift, x, y)) productionLocalMaxima++;
                        if (isolated(exactUplift, x, y)) exactIsolated++;
                        if (isolated(productionUplift, x, y)) productionIsolated++;
                    }
                }
            }
        }

        double maskIou = ratio(maskIntersection, maskUnion);
        double exactCoverage = ratio(exactPositive, cells);
        double productionCoverage = ratio(productionPositive, cells);
        double upliftMae = upliftAbsError / cells;
        double exactVariation = exactNeighbourVariation / neighbourPairs;
        double productionVariation = productionNeighbourVariation / neighbourPairs;
        double variationRatio = productionVariation / Math.max(1e-12d, exactVariation);
        double exactCurvatureMean = exactCurvature / curvatureSamples;
        double productionCurvatureMean = productionCurvature / curvatureSamples;
        double curvatureRatio = productionCurvatureMean / Math.max(1e-12d, exactCurvatureMean);

        System.out.printf(
                Locale.ROOT,
                "v15-continuum-mountain-detail side=%d patches=%d patchSide=%d cells=%d "
                        + "maskIoU=%.6f exactCoverage=%.6f productionCoverage=%.6f upliftMaeCells=%.6f "
                        + "exactLocalMaxima=%d productionLocalMaxima=%d exactIsolated=%d productionIsolated=%d "
                        + "exactNeighbourVariation=%.6f productionNeighbourVariation=%.6f variationRatio=%.6f "
                        + "exactCurvature=%.6f productionCurvature=%.6f curvatureRatio=%.6f%n",
                SIDE,
                PATCHES.length,
                PATCH_SIDE,
                cells,
                maskIou,
                exactCoverage,
                productionCoverage,
                upliftMae,
                exactLocalMaxima,
                productionLocalMaxima,
                exactIsolated,
                productionIsolated,
                exactVariation,
                productionVariation,
                variationRatio,
                exactCurvatureMean,
                productionCurvatureMean,
                curvatureRatio);

        assertTrue(maskIou >= MIN_MASK_IOU, "raw mountain mask drifted from exact V13");
        assertTrue(Math.abs(productionCoverage - exactCoverage) <= MAX_COVERAGE_DRIFT,
                "raw mountain coverage drifted from exact V13");
        assertTrue(upliftMae <= MAX_UPLIFT_MAE_CELLS, "raw mountain uplift became too different");
        assertTrue(variationRatio >= MIN_NEIGHBOUR_VARIATION_RATIO
                        && variationRatio <= MAX_NEIGHBOUR_VARIATION_RATIO,
                "raw mountain neighbour variation gained/lost too much high-frequency structure");
        assertTrue(curvatureRatio <= MAX_CURVATURE_RATIO,
                "raw mountain curvature became materially noisier than exact V13");
        assertTrue(productionIsolated <= exactIsolated + MAX_EXTRA_ISOLATED_PEAKS,
                "production V13 introduced isolated one-cell mountain spikes");
    }

    private static double[][] uplift(ContinuumScalarPage base, ContinuumScalarPage mountains) {
        double[][] result = new double[PATCH_SIDE][PATCH_SIDE];
        for (int y = 0; y < PATCH_SIDE; y++) {
            for (int x = 0; x < PATCH_SIDE; x++) {
                result[y][x] = Math.max(0d, mountains.sample(x, y) - base.sample(x, y));
            }
        }
        return result;
    }

    private static boolean localMaximum(double[][] uplift, int x, int y) {
        double value = uplift[y][x];
        if (value <= 0.5d) return false;
        boolean strictlyHigher = false;
        for (int oy = -1; oy <= 1; oy++) {
            for (int ox = -1; ox <= 1; ox++) {
                if (ox == 0 && oy == 0) continue;
                double neighbour = uplift[y + oy][x + ox];
                if (neighbour > value) return false;
                if (neighbour < value) strictlyHigher = true;
            }
        }
        return strictlyHigher;
    }

    private static boolean isolated(double[][] uplift, int x, int y) {
        if (uplift[y][x] <= 0.5d) return false;
        return uplift[y][x - 1] <= 0.5d
                && uplift[y][x + 1] <= 0.5d
                && uplift[y - 1][x] <= 0.5d
                && uplift[y + 1][x] <= 0.5d;
    }

    private static double ratio(long numerator, long denominator) {
        return denominator == 0L ? 1d : numerator / (double) denominator;
    }
}
