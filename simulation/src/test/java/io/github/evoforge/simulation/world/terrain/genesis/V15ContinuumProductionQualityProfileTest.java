package io.github.evoforge.simulation.world.terrain.genesis;

import io.github.evoforge.simulation.world.continuum.field.ContinuumSampleWindow;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPage;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import io.github.evoforge.simulation.world.terrain.definition.V13MountainDefinition;
import io.github.evoforge.simulation.world.terrain.definition.V15TerrainDefinition;
import io.github.evoforge.simulation.world.terrain.field.TerrainElevationField;
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
        long waterComparable = 0L;

        boolean[][] exactLandMask = new boolean[SAMPLE_SIDE][SAMPLE_SIDE];
        boolean[][] productionLandMask = new boolean[SAMPLE_SIDE][SAMPLE_SIDE];
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
                } else if (!exactDry && !productionDry) {
                    waterAbsError += Math.abs(exactValue - productionValue);
                    waterComparable++;
                }
            }
        }

        EdgeAgreement edges = edgeAgreement(exactLandMask, productionLandMask);
        double subunitsPerCell = TerrainElevationField.SUBUNITS_PER_CELL;
        System.out.printf(
                Locale.ROOT,
                "v15-continuum-quality-profile side=%d step=%d samples=%d "
                        + "exactPrepareMs=%.3f productionPrepareMs=%.3f sampleMs=%.3f "
                        + "landAgreement=%.6f landIoU=%.6f exactLandFraction=%.6f productionLandFraction=%.6f "
                        + "coastEdgeAgreement=%.6f coastEdgeIoU=%.6f "
                        + "lakeIoU=%.6f exactLakeFraction=%.6f productionLakeFraction=%.6f "
                        + "mountainIoU=%.6f exactMountainFraction=%.6f productionMountainFraction=%.6f "
                        + "dryMaeCells=%.6f waterMaeCells=%.6f%n",
                SIDE,
                SAMPLE_STEP,
                samples,
                (exactReady - started) / 1_000_000d,
                (productionReady - exactReady) / 1_000_000d,
                (sampled - productionReady) / 1_000_000d,
                ratio(landAgreement, samples),
                ratio(landIntersection, landUnion),
                ratio(exactLand, samples),
                ratio(productionLand, samples),
                ratio(edges.agreement(), edges.total()),
                ratio(edges.intersection(), edges.union()),
                ratio(lakeIntersection, lakeUnion),
                ratio(exactLake, samples),
                ratio(productionLake, samples),
                ratio(mountainIntersection, mountainUnion),
                ratio(exactMountain, samples),
                ratio(productionMountain, samples),
                dryComparable == 0L ? 0d : dryAbsError / dryComparable / subunitsPerCell,
                waterComparable == 0L ? 0d : waterAbsError / waterComparable / subunitsPerCell);
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

    private static double ratio(long numerator, long denominator) {
        return denominator == 0L ? 1d : numerator / (double) denominator;
    }

    private record EdgeAgreement(long agreement, long total, long intersection, long union) {}
}
