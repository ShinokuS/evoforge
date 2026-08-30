package io.github.evoforge.simulation.world.terrain.genesis;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.evoforge.simulation.definition.NormalizedValue;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import io.github.evoforge.simulation.world.terrain.definition.V15TerrainDefinition;
import io.github.evoforge.simulation.world.terrain.field.V12UnrelaxedLandElevationField;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * Exact checks for the two V12 operations that changed execution model during the Continuum port.
 *
 * <p>The oracle deliberately uses the historical dense sort and dense coast-distance transform.
 * Production code must stay addressable/bounded; test code is allowed to materialize a small world
 * in order to prove that the architectural rewrite did not change accepted semantics.</p>
 */
final class V12ContinuumAdaptationParityTest {
    private static final int PPM = 1_000_000;

    @Test
    void histogramRankPlanMatchesHistoricalDenseSortCellForCell() {
        ContinuumWorldDomain domain = new ContinuumWorldDomain(67, 53);
        V15TerrainDefinition definition = new V15TerrainDefinition(
                NormalizedValue.of(0.47),
                NormalizedValue.of(0.63),
                NormalizedValue.of(0.38),
                NormalizedValue.of(0.60),
                NormalizedValue.of(0.45),
                NormalizedValue.of(0.57),
                NormalizedValue.of(0.35));
        V12TerrainRecipe recipe = V12TerrainRecipe.balanced();
        V12TerrainCalibration calibration = V12TerrainCalibration.compile(domain, definition, recipe);

        for (long seed : new long[] {1L, 913L, -4_759_010_560_822_749_572L}) {
            boolean[] historical = historicalDenseLandMask(domain, seed, calibration, recipe);
            V12LandRankPlan continuum = V12LandRankPlan.prepareUnconstrained(
                    domain, seed, calibration, recipe);

            int index = 0;
            for (long y = 0L; y < domain.height(); y++) {
                for (long x = 0L; x < domain.width(); x++, index++) {
                    assertEquals(
                            historical[index],
                            continuum.isLand(x, y),
                            "land-rank parity failed at seed=" + seed + " x=" + x + " y=" + y);
                }
            }
        }
    }

    @Test
    void pointAddressableCoastInteriorityMatchesHistoricalDenseDistanceTransform() {
        ContinuumWorldDomain domain = new ContinuumWorldDomain(71, 59);
        V15TerrainDefinition definition = new V15TerrainDefinition(
                NormalizedValue.of(0.44),
                NormalizedValue.of(0.58),
                NormalizedValue.of(0.31),
                NormalizedValue.of(0.0),
                NormalizedValue.of(0.0),
                NormalizedValue.of(0.52),
                NormalizedValue.of(0.35));
        V12TerrainRecipe recipe = V12TerrainRecipe.balanced();
        V12TerrainCalibration calibration = V12TerrainCalibration.compile(domain, definition, recipe);
        long seed = 71_337L;
        boolean[] historicalLand = historicalDenseLandMask(domain, seed, calibration, recipe);
        int[] historicalInteriority = historicalDenseCoastalInteriority(
                historicalLand,
                calibration.width(),
                calibration.height(),
                recipe.coastTransitionCells());

        V12LandRankPlan land = V12LandRankPlan.prepareUnconstrained(
                domain, seed, calibration, recipe);
        int maximumLandHeightCells = 96;
        V12UnrelaxedLandElevationField field = new V12UnrelaxedLandElevationField(
                domain,
                seed,
                land,
                calibration,
                recipe,
                maximumLandHeightCells);
        long amplitude = maximumLandHeightCells * V12UnrelaxedLandElevationField.SUBUNITS_PER_CELL;

        int index = 0;
        for (long y = 0L; y < domain.height(); y++) {
            for (long x = 0L; x < domain.width(); x++, index++) {
                if (!historicalLand[index]) {
                    assertEquals(-1L, field.elevationSubunitsAt(x, y));
                    continue;
                }
                int interiorityPpm = historicalInteriority[index];
                long baseHeightPpm = recipe.coastBaseHeightPpm()
                        + (long) interiorityPpm * recipe.coastInteriorHeightPpm() / PPM;
                int heightPpm = LegacyV12Noise.clampPpm(baseHeightPpm);
                long expected = positiveNormalizedHeight(heightPpm, amplitude);
                assertEquals(
                        expected,
                        field.elevationSubunitsAt(x, y),
                        "coast-interiority parity failed at x=" + x + " y=" + y);
            }
        }
    }

    private static boolean[] historicalDenseLandMask(
            ContinuumWorldDomain domain,
            long seed,
            V12TerrainCalibration calibration,
            V12TerrainRecipe recipe) {
        int area = Math.toIntExact(calibration.area());
        long[] rankKeys = new long[area];
        LegacyV15Random random = new LegacyV15Random(seed);
        V15TerrainCoordinateFrame frame = V15TerrainCoordinateFrame.centered(domain);
        int index = 0;
        for (long y = 0L; y < domain.height(); y++) {
            long legacyY = frame.legacyY(y);
            for (long x = 0L; x < domain.width(); x++, index++) {
                long legacyX = frame.legacyX(x);
                int coherent = LegacyV12Noise.organicValueNoise(
                        random,
                        LegacyV12Noise.LANDMASS,
                        legacyX,
                        legacyY,
                        calibration.coherentLandmassScale(),
                        recipe);
                int fragmented = LegacyV12Noise.organicValueNoise(
                        random,
                        LegacyV12Noise.FRAGMENT,
                        legacyX,
                        legacyY,
                        calibration.fragmentedLandmassScale(),
                        recipe);
                int potential = (int) (((long) coherent * (PPM - calibration.fragmentationPpm())
                        + (long) fragmented * calibration.fragmentationPpm()) / PPM);
                rankKeys[index] = historicalRankKey(potential, index);
            }
        }
        Arrays.sort(rankKeys);

        boolean[] land = new boolean[area];
        int landCount = Math.toIntExact(calibration.landCount());
        for (int rank = 0; rank < landCount; rank++) {
            land[(int) rankKeys[rank]] = true;
        }
        return land;
    }

    private static int[] historicalDenseCoastalInteriority(
            boolean[] land,
            int width,
            int height,
            int transitionCells) {
        int[] distance = new int[land.length];
        int infinity = width + height + 1;
        boolean hasOcean = false;
        for (int index = 0; index < land.length; index++) {
            if (land[index]) {
                distance[index] = infinity;
            } else {
                distance[index] = 0;
                hasOcean = true;
            }
        }

        int[] result = new int[land.length];
        if (!hasOcean) {
            Arrays.fill(result, PPM);
            return result;
        }

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = y * width + x;
                if (!land[index]) continue;
                int best = distance[index];
                if (x > 0) best = Math.min(best, distance[index - 1] + 1);
                if (y > 0) best = Math.min(best, distance[index - width] + 1);
                distance[index] = best;
            }
        }
        for (int y = height - 1; y >= 0; y--) {
            for (int x = width - 1; x >= 0; x--) {
                int index = y * width + x;
                if (!land[index]) continue;
                int best = distance[index];
                if (x + 1 < width) best = Math.min(best, distance[index + 1] + 1);
                if (y + 1 < height) best = Math.min(best, distance[index + width] + 1);
                distance[index] = best;
            }
        }

        for (int index = 0; index < land.length; index++) {
            if (!land[index]) continue;
            long coordinate = Math.min(distance[index], transitionCells)
                    * (long) PPM / transitionCells;
            result[index] = LegacyV12Noise.smoothStepPpm(coordinate);
        }
        return result;
    }

    private static long historicalRankKey(int potential, int cellIndex) {
        long invertedPotential = (long) LegacyV12Noise.SAMPLE_MAX - potential;
        return (invertedPotential << 32) | (cellIndex & 0xffff_ffffL);
    }

    private static long positiveNormalizedHeight(int heightPpm, long amplitude) {
        if (amplitude <= 1L) return Math.max(1L, amplitude);
        return 1L + ((amplitude - 1L) * heightPpm) / PPM;
    }
}
