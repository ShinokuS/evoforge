package io.github.evoforge.simulation.world.terrain.genesis;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.continuum.field.ContinuumSampleWindow;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPage;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import io.github.evoforge.simulation.world.terrain.definition.V13MountainDefinition;
import io.github.evoforge.simulation.world.terrain.definition.V15TerrainDefinition;
import io.github.evoforge.simulation.world.terrain.field.TerrainElevationField;
import java.util.Arrays;
import java.util.Locale;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Quantitative comparison of bounded production V15 against the accepted exact finite oracle. */
@Tag("scale-profile")
final class V15ContinuumProductionQualityProfileTest {
    private static final long SEED = -4_774_846_722_868_265_927L;
    private static final int SIDE = 500;
    private static final int SAMPLE_STEP = 5;
    private static final int SAMPLE_SIDE = (SIDE - 1) / SAMPLE_STEP + 1;

    /* Regression gates deliberately sit below the accepted current profile, but above variants that
     * were visually rejected during the Continuum migration. They are morphology gates, not a claim
     * of cell-exact production equivalence to the finite historical oracle. */
    private static final double MIN_LAND_IOU = 0.98;
    private static final double MIN_COAST_EDGE_IOU = 0.80;
    private static final double MIN_LAKE_IOU = 0.60;
    private static final double MIN_MOUNTAIN_IOU = 0.90;
    private static final double MAX_LAND_FRACTION_DRIFT = 0.03;
    private static final double MAX_LAKE_FRACTION_DRIFT = 0.005;
    private static final double MAX_MOUNTAIN_FRACTION_DRIFT = 0.04;

    @Test
    void profileGlobalMorphologyAgainstExact500Oracle() {
        ContinuumWorldDomain domain = new ContinuumWorldDomain(SIDE, SIDE);
        V15TerrainDefinition terrain = V15TerrainDefinition.balanced();
        V13MountainDefinition mountains = V13MountainDefinition.balanced();

        long started = System.nanoTime();
        V15ContinuumTerrainPlan exact = V15ContinuumTerrainPlan.prepare(
                domain, SEED, terrain, mountains, -96, 96);
        long exactReady = System.nanoTime();
        V15ContinuumProductionTerrainPlan production = V15ContinuumProductionTerrainPlan.prepare(
                domain, SEED, terrain, mountains, -96, 96);
        long productionReady = System.nanoTime();

        ContinuumSampleWindow sample = new ContinuumSampleWindow(
                0L, 0L, SAMPLE_SIDE, SAMPLE_SIDE, SAMPLE_STEP);
        ContinuumScalarPage exactFinal = exact.elevationPages().materialize(sample);
        ContinuumScalarPage exactCoastal = exact.coastalBathymetryPages().materialize(sample);
        ContinuumScalarPage exactDeep = exact.deepBathymetryPages().materialize(sample);
        ContinuumScalarPage productionFinal = production.elevationPages().materialize(sample);
        ContinuumScalarPage exactLakeBase = exact.lakeBase().elevationPages().materialize(sample);
        ContinuumScalarPage productionLakeBase = production.lakeBasePages().materialize(sample);
        long sampled = System.nanoTime();

        long samples = (long) SAMPLE_SIDE * SAMPLE_SIDE;
        long landAgreement = 0L;
        long exactLand = 0L;
        long productionLand = 0L;
        long landIntersection = 0L;
        long landUnion = 0L;
        long exactLake = 0L;
        long productionLake = 0L;
        long lakeIntersection = 0L;
        long lakeUnion = 0L;
        long exactMountain = 0L;
        long productionMountain = 0L;
        long mountainIntersection = 0L;
        long mountainUnion = 0L;
        double dryAbsError = 0d;
        long dryComparable = 0L;
        double waterAbsError = 0d;
        double coastalAbsError = 0d;
        double deepStructureMagnitude = 0d;
        double exactWaterDepthSum = 0d;
        double productionWaterDepthSum = 0d;
        long waterComparable = 0L;
        double[] exactWaterDepths = new double[Math.toIntExact(samples)];
        double[] productionWaterDepths = new double[Math.toIntExact(samples)];
        int waterDepthCount = 0;

        boolean[][] exactLandMask = new boolean[SAMPLE_SIDE][SAMPLE_SIDE];
        boolean[][] productionLandMask = new boolean[SAMPLE_SIDE][SAMPLE_SIDE];
        boolean[][] exactLakeMask = new boolean[SAMPLE_SIDE][SAMPLE_SIDE];
        boolean[][] productionLakeMask = new boolean[SAMPLE_SIDE][SAMPLE_SIDE];
        double subunitsPerCell = TerrainElevationField.SUBUNITS_PER_CELL;
        for (int y = 0; y < SAMPLE_SIDE; y++) {
            long worldY = sample.yAt(y);
            for (int x = 0; x < SAMPLE_SIDE; x++) {
                long worldX = sample.xAt(x);
                double exactValue = exactFinal.sample(x, y);
                double productionValue = productionFinal.sample(x, y);
                boolean exactDry = exactValue > 0d;
                boolean productionDry = productionValue > 0d;
                exactLandMask[y][x] = exactDry;
                productionLandMask[y][x] = productionDry;
                if (exactDry == productionDry) landAgreement++;
                if (exactDry) exactLand++;
                if (productionDry) productionLand++;
                if (exactDry && productionDry) landIntersection++;
                if (exactDry || productionDry) landUnion++;

                boolean exactLakeCell = exact.lakeBase().lakeDomain().isLake(
                        Math.toIntExact(worldX), Math.toIntExact(worldY));
                boolean productionLakeCell = production.lakes().isLake(worldX, worldY);
                exactLakeMask[y][x] = exactLakeCell;
                productionLakeMask[y][x] = productionLakeCell;
                if (exactLakeCell) exactLake++;
                if (productionLakeCell) productionLake++;
                if (exactLakeCell && productionLakeCell) lakeIntersection++;
                if (exactLakeCell || productionLakeCell) lakeUnion++;

                boolean exactMountainCell = exactDry
                        && exactValue > exactLakeBase.sample(x, y) + 0.5d;
                boolean productionMountainCell = productionDry
                        && productionValue > productionLakeBase.sample(x, y) + 0.5d;
                if (exactMountainCell) exactMountain++;
                if (productionMountainCell) productionMountain++;
                if (exactMountainCell && productionMountainCell) mountainIntersection++;
                if (exactMountainCell || productionMountainCell) mountainUnion++;

                if (exactDry && productionDry) {
                    dryAbsError += Math.abs(exactValue - productionValue);
                    dryComparable++;
                } else if (!exactDry && !productionDry && !exactLakeCell && !productionLakeCell) {
                    double exactCoastalValue = exactCoastal.sample(x, y);
                    double exactDeepValue = exactDeep.sample(x, y);
                    waterAbsError += Math.abs(exactValue - productionValue);
                    coastalAbsError += Math.abs(exactCoastalValue - productionValue);
                    deepStructureMagnitude += Math.abs(exactDeepValue - exactCoastalValue);
                    double exactDepth = -exactValue / subunitsPerCell;
                    double productionDepth = -productionValue / subunitsPerCell;
                    exactWaterDepthSum += exactDepth;
                    productionWaterDepthSum += productionDepth;
                    exactWaterDepths[waterDepthCount] = exactDepth;
                    productionWaterDepths[waterDepthCount] = productionDepth;
                    waterDepthCount++;
                    waterComparable++;
                }
            }
        }

        Arrays.sort(exactWaterDepths, 0, waterDepthCount);
        Arrays.sort(productionWaterDepths, 0, waterDepthCount);
        EdgeAgreement edges = edgeAgreement(exactLandMask, productionLandMask);
        ComponentSummary exactLakeComponents = componentSummary(exactLakeMask);
        ComponentSummary productionLakeComponents = componentSummary(productionLakeMask);

        double landIou = ratio(landIntersection, landUnion);
        double coastEdgeIou = ratio(edges.intersection(), edges.union());
        double lakeIou = ratio(lakeIntersection, lakeUnion);
        double mountainIou = ratio(mountainIntersection, mountainUnion);
        double exactLandFraction = ratio(exactLand, samples);
        double productionLandFraction = ratio(productionLand, samples);
        double exactLakeFraction = ratio(exactLake, samples);
        double productionLakeFraction = ratio(productionLake, samples);
        double exactMountainFraction = ratio(exactMountain, samples);
        double productionMountainFraction = ratio(productionMountain, samples);

        System.out.printf(
                Locale.ROOT,
                "v15-continuum-quality-profile side=%d step=%d samples=%d "
                        + "exactPrepareMs=%.3f productionPrepareMs=%.3f sampleMs=%.3f "
                        + "landAgreement=%.6f landIoU=%.6f exactLandFraction=%.6f productionLandFraction=%.6f "
                        + "coastEdgeAgreement=%.6f coastEdgeIoU=%.6f "
                        + "lakeIoU=%.6f exactLakeFraction=%.6f productionLakeFraction=%.6f "
                        + "exactLakeComponents=%d productionLakeComponents=%d exactLargestLakeFraction=%.6f productionLargestLakeFraction=%.6f "
                        + "mountainIoU=%.6f exactMountainFraction=%.6f productionMountainFraction=%.6f "
                        + "dryMaeCells=%.6f waterMaeCells=%.6f coastalOnlyMaeCells=%.6f "
                        + "exactDeepAdjustmentCells=%.6f exactWaterMeanDepthCells=%.6f productionWaterMeanDepthCells=%.6f "
                        + "exactWaterP50=%.6f productionWaterP50=%.6f exactWaterP90=%.6f productionWaterP90=%.6f%n",
                SIDE,
                SAMPLE_STEP,
                samples,
                (exactReady - started) / 1_000_000d,
                (productionReady - exactReady) / 1_000_000d,
                (sampled - productionReady) / 1_000_000d,
                ratio(landAgreement, samples),
                landIou,
                exactLandFraction,
                productionLandFraction,
                ratio(edges.agreement(), edges.total()),
                coastEdgeIou,
                lakeIou,
                exactLakeFraction,
                productionLakeFraction,
                exactLakeComponents.components(),
                productionLakeComponents.components(),
                ratio(exactLakeComponents.largestCells(), samples),
                ratio(productionLakeComponents.largestCells(), samples),
                mountainIou,
                exactMountainFraction,
                productionMountainFraction,
                dryComparable == 0L ? 0d : dryAbsError / dryComparable / subunitsPerCell,
                waterComparable == 0L ? 0d : waterAbsError / waterComparable / subunitsPerCell,
                waterComparable == 0L ? 0d : coastalAbsError / waterComparable / subunitsPerCell,
                waterComparable == 0L ? 0d : deepStructureMagnitude / waterComparable / subunitsPerCell,
                waterComparable == 0L ? 0d : exactWaterDepthSum / waterComparable,
                waterComparable == 0L ? 0d : productionWaterDepthSum / waterComparable,
                quantile(exactWaterDepths, waterDepthCount, 0.50),
                quantile(productionWaterDepths, waterDepthCount, 0.50),
                quantile(exactWaterDepths, waterDepthCount, 0.90),
                quantile(productionWaterDepths, waterDepthCount, 0.90));

        assertAtLeast("land IoU", landIou, MIN_LAND_IOU);
        assertAtLeast("coast-edge IoU", coastEdgeIou, MIN_COAST_EDGE_IOU);
        assertAtLeast("lake IoU", lakeIou, MIN_LAKE_IOU);
        assertAtLeast("mountain IoU", mountainIou, MIN_MOUNTAIN_IOU);
        assertWithin(
                "land fraction",
                productionLandFraction,
                exactLandFraction,
                MAX_LAND_FRACTION_DRIFT);
        assertWithin(
                "lake fraction",
                productionLakeFraction,
                exactLakeFraction,
                MAX_LAKE_FRACTION_DRIFT);
        assertWithin(
                "mountain fraction",
                productionMountainFraction,
                exactMountainFraction,
                MAX_MOUNTAIN_FRACTION_DRIFT);
    }

    private static EdgeAgreement edgeAgreement(boolean[][] exact, boolean[][] production) {
        long agreement = 0L;
        long total = 0L;
        long intersection = 0L;
        long union = 0L;
        int height = exact.length;
        int width = exact[0].length;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (x + 1 < width) {
                    boolean exactEdge = exact[y][x] != exact[y][x + 1];
                    boolean productionEdge = production[y][x] != production[y][x + 1];
                    if (exactEdge == productionEdge) agreement++;
                    if (exactEdge && productionEdge) intersection++;
                    if (exactEdge || productionEdge) union++;
                    total++;
                }
                if (y + 1 < height) {
                    boolean exactEdge = exact[y][x] != exact[y + 1][x];
                    boolean productionEdge = production[y][x] != production[y + 1][x];
                    if (exactEdge == productionEdge) agreement++;
                    if (exactEdge && productionEdge) intersection++;
                    if (exactEdge || productionEdge) union++;
                    total++;
                }
            }
        }
        return new EdgeAgreement(agreement, total, intersection, union);
    }

    private static ComponentSummary componentSummary(boolean[][] mask) {
        int height = mask.length;
        int width = mask[0].length;
        boolean[][] visited = new boolean[height][width];
        int[] queueX = new int[Math.multiplyExact(width, height)];
        int[] queueY = new int[queueX.length];
        int components = 0;
        int largest = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!mask[y][x] || visited[y][x]) continue;
                components++;
                int head = 0;
                int tail = 0;
                queueX[tail] = x;
                queueY[tail++] = y;
                visited[y][x] = true;
                int cells = 0;
                while (head < tail) {
                    int cx = queueX[head];
                    int cy = queueY[head++];
                    cells++;
                    if (cx > 0) tail = enqueue(mask, visited, queueX, queueY, tail, cx - 1, cy);
                    if (cx + 1 < width) tail = enqueue(mask, visited, queueX, queueY, tail, cx + 1, cy);
                    if (cy > 0) tail = enqueue(mask, visited, queueX, queueY, tail, cx, cy - 1);
                    if (cy + 1 < height) tail = enqueue(mask, visited, queueX, queueY, tail, cx, cy + 1);
                }
                largest = Math.max(largest, cells);
            }
        }
        return new ComponentSummary(components, largest);
    }

    private static int enqueue(
            boolean[][] mask,
            boolean[][] visited,
            int[] queueX,
            int[] queueY,
            int tail,
            int x,
            int y) {
        if (!mask[y][x] || visited[y][x]) return tail;
        visited[y][x] = true;
        queueX[tail] = x;
        queueY[tail] = y;
        return tail + 1;
    }

    private static void assertAtLeast(String name, double actual, double minimum) {
        assertTrue(
                actual >= minimum,
                () -> name + " regressed below accepted production floor: "
                        + actual + " < " + minimum);
    }

    private static void assertWithin(
            String name,
            double actual,
            double reference,
            double maximumAbsoluteDrift) {
        assertTrue(
                Math.abs(actual - reference) <= maximumAbsoluteDrift,
                () -> name + " drifted too far from exact oracle: production="
                        + actual + " exact=" + reference + " allowed=" + maximumAbsoluteDrift);
    }

    private static double ratio(long numerator, long denominator) {
        return denominator == 0L ? 1d : numerator / (double) denominator;
    }

    private static double quantile(double[] sorted, int size, double fraction) {
        if (size <= 0) return 0d;
        int index = Math.max(0, Math.min(size - 1, (int) StrictMath.floor((size - 1) * fraction)));
        return sorted[index];
    }

    private record EdgeAgreement(long agreement, long total, long intersection, long union) {}
    private record ComponentSummary(int components, int largestCells) {}
}
