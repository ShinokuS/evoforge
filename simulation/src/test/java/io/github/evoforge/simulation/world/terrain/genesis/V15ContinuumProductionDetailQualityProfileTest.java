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

/** Cell-scale quality gate against the accepted V15 finite oracle. */
@Tag("scale-profile")
final class V15ContinuumProductionDetailQualityProfileTest {
    private static final long SEED = -4_774_846_722_868_265_927L;
    private static final int SIDE = 500;
    private static final int PATCH_SIDE = 64;
    private static final int[][] PATCHES = {
            {24, 24},
            {188, 48},
            {56, 276},
            {314, 316}
    };

    @Test
    void unitResolutionTerracesAndLocalGradientsStayCloseToExactOracle() {
        ContinuumWorldDomain domain = new ContinuumWorldDomain(SIDE, SIDE);
        V15TerrainDefinition terrain = V15TerrainDefinition.balanced();
        V13MountainDefinition mountains = V13MountainDefinition.balanced();

        V15ContinuumTerrainPlan exact = V15ContinuumTerrainPlan.prepare(
                domain, SEED, terrain, mountains, -96, 96);
        V15ContinuumProductionTerrainPlan production = V15ContinuumProductionTerrainPlan.prepare(
                domain, SEED, terrain, mountains, -96, 96);

        DetailStats total = new DetailStats();
        for (int[] origin : PATCHES) {
            ContinuumSampleWindow window = new ContinuumSampleWindow(
                    origin[0], origin[1], PATCH_SIDE, PATCH_SIDE, 1L);
            ContinuumScalarPage exactPage = exact.elevationPages().materialize(window);
            ContinuumScalarPage productionPage = production.elevationPages().materialize(window);
            total.add(compare(exactPage, productionPage));
        }

        double discreteAgreement = ratio(total.discreteAgreement, total.bothDry);
        double contourIou = ratio(total.contourIntersection, total.contourUnion);
        double exactContourDensity = ratio(total.exactContourEdges, total.comparableEdges);
        double productionContourDensity = ratio(total.productionContourEdges, total.comparableEdges);
        double exactSingleStepDensity = ratio(total.exactSingleStepEdges, total.comparableEdges);
        double productionSingleStepDensity = ratio(total.productionSingleStepEdges, total.comparableEdges);
        double contourDensityRatio = productionContourDensity / exactContourDensity;
        double singleStepDensityRatio = productionSingleStepDensity / exactSingleStepDensity;
        double dryMaeCells = total.bothDry == 0L
                ? 0d
                : total.dryAbsErrorSubunits / total.bothDry / TerrainElevationField.SUBUNITS_PER_CELL;
        double gradientMaeCells = total.comparableEdges == 0L
                ? 0d
                : total.gradientAbsErrorSubunits / total.comparableEdges
                        / TerrainElevationField.SUBUNITS_PER_CELL;

        System.out.printf(
                Locale.ROOT,
                "v15-continuum-detail-quality side=%d patches=%d patchSide=%d cells=%d bothDry=%d "
                        + "discreteZAgreement=%.6f dryMaeCells=%.6f "
                        + "contourIoU=%.6f exactContourDensity=%.6f productionContourDensity=%.6f "
                        + "contourDensityRatio=%.6f exactSingleStepDensity=%.6f "
                        + "productionSingleStepDensity=%.6f singleStepDensityRatio=%.6f "
                        + "gradientMaeCells=%.6f exactMaxAdjacentStepCells=%.6f productionMaxAdjacentStepCells=%.6f%n",
                SIDE,
                PATCHES.length,
                PATCH_SIDE,
                (long) PATCHES.length * PATCH_SIDE * PATCH_SIDE,
                total.bothDry,
                discreteAgreement,
                dryMaeCells,
                contourIou,
                exactContourDensity,
                productionContourDensity,
                contourDensityRatio,
                exactSingleStepDensity,
                productionSingleStepDensity,
                singleStepDensityRatio,
                gradientMaeCells,
                total.exactMaxAdjacentStepSubunits / (double) TerrainElevationField.SUBUNITS_PER_CELL,
                total.productionMaxAdjacentStepSubunits / (double) TerrainElevationField.SUBUNITS_PER_CELL);

        assertTrue(total.bothDry > 0L);
        assertTrue(total.comparableEdges > 0L);
        assertTrue(discreteAgreement >= 0.96,
                "production V15 changed too many exact cell Z levels");
        assertTrue(dryMaeCells <= 0.08,
                "production V15 local dry relief moved too far from exact V15");
        assertTrue(contourIou >= 0.82,
                "production V15 local terrace/contour placement drifted too far");
        assertTrue(contourDensityRatio >= 0.90 && contourDensityRatio <= 1.10,
                "production V15 introduced or removed too many discrete terrace edges");
        assertTrue(singleStepDensityRatio >= 0.90 && singleStepDensityRatio <= 1.10,
                "production V15 introduced or removed too many one-cell elevation layers");
        assertTrue(gradientMaeCells <= 0.02,
                "production V15 local adjacent gradients drifted too far from exact V15");
    }

    private static DetailStats compare(
            ContinuumScalarPage exact,
            ContinuumScalarPage production) {
        DetailStats result = new DetailStats();
        int width = exact.window().width();
        int height = exact.window().height();
        boolean[][] comparable = new boolean[height][width];
        int[][] exactZ = new int[height][width];
        int[][] productionZ = new int[height][width];
        double[][] exactValues = new double[height][width];
        double[][] productionValues = new double[height][width];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double e = exact.sample(x, y);
                double p = production.sample(x, y);
                exactValues[y][x] = e;
                productionValues[y][x] = p;
                boolean bothDry = e > 0d && p > 0d;
                comparable[y][x] = bothDry;
                if (!bothDry) continue;
                result.bothDry++;
                exactZ[y][x] = discreteZ(e);
                productionZ[y][x] = discreteZ(p);
                if (exactZ[y][x] == productionZ[y][x]) result.discreteAgreement++;
                result.dryAbsErrorSubunits += Math.abs(e - p);
            }
        }

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (x + 1 < width) {
                    compareEdge(result, comparable, exactZ, productionZ,
                            exactValues, productionValues, x, y, x + 1, y);
                }
                if (y + 1 < height) {
                    compareEdge(result, comparable, exactZ, productionZ,
                            exactValues, productionValues, x, y, x, y + 1);
                }
            }
        }
        return result;
    }

    private static void compareEdge(
            DetailStats result,
            boolean[][] comparable,
            int[][] exactZ,
            int[][] productionZ,
            double[][] exactValues,
            double[][] productionValues,
            int ax,
            int ay,
            int bx,
            int by) {
        if (!comparable[ay][ax] || !comparable[by][bx]) return;
        result.comparableEdges++;

        int exactStep = Math.abs(exactZ[ay][ax] - exactZ[by][bx]);
        int productionStep = Math.abs(productionZ[ay][ax] - productionZ[by][bx]);
        boolean exactContour = exactStep != 0;
        boolean productionContour = productionStep != 0;
        if (exactContour) result.exactContourEdges++;
        if (productionContour) result.productionContourEdges++;
        if (exactContour && productionContour) result.contourIntersection++;
        if (exactContour || productionContour) result.contourUnion++;
        if (exactStep == 1) result.exactSingleStepEdges++;
        if (productionStep == 1) result.productionSingleStepEdges++;

        double exactGradient = exactValues[by][bx] - exactValues[ay][ax];
        double productionGradient = productionValues[by][bx] - productionValues[ay][ax];
        result.gradientAbsErrorSubunits += Math.abs(exactGradient - productionGradient);
        result.exactMaxAdjacentStepSubunits = Math.max(
                result.exactMaxAdjacentStepSubunits, Math.abs(exactGradient));
        result.productionMaxAdjacentStepSubunits = Math.max(
                result.productionMaxAdjacentStepSubunits, Math.abs(productionGradient));
    }

    private static int discreteZ(double value) {
        return Math.toIntExact((long) StrictMath.floor(value / TerrainElevationField.SUBUNITS_PER_CELL));
    }

    private static double ratio(long numerator, long denominator) {
        return denominator == 0L ? 1d : numerator / (double) denominator;
    }

    private static final class DetailStats {
        long bothDry;
        long discreteAgreement;
        double dryAbsErrorSubunits;
        long comparableEdges;
        long exactContourEdges;
        long productionContourEdges;
        long contourIntersection;
        long contourUnion;
        long exactSingleStepEdges;
        long productionSingleStepEdges;
        double gradientAbsErrorSubunits;
        double exactMaxAdjacentStepSubunits;
        double productionMaxAdjacentStepSubunits;

        void add(DetailStats other) {
            bothDry += other.bothDry;
            discreteAgreement += other.discreteAgreement;
            dryAbsErrorSubunits += other.dryAbsErrorSubunits;
            comparableEdges += other.comparableEdges;
            exactContourEdges += other.exactContourEdges;
            productionContourEdges += other.productionContourEdges;
            contourIntersection += other.contourIntersection;
            contourUnion += other.contourUnion;
            exactSingleStepEdges += other.exactSingleStepEdges;
            productionSingleStepEdges += other.productionSingleStepEdges;
            gradientAbsErrorSubunits += other.gradientAbsErrorSubunits;
            exactMaxAdjacentStepSubunits = Math.max(
                    exactMaxAdjacentStepSubunits, other.exactMaxAdjacentStepSubunits);
            productionMaxAdjacentStepSubunits = Math.max(
                    productionMaxAdjacentStepSubunits, other.productionMaxAdjacentStepSubunits);
        }
    }
}
