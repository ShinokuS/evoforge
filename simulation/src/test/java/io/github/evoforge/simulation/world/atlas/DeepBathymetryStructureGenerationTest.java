package io.github.evoforge.simulation.world.atlas;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.genesis.GenerationRevision;
import io.github.evoforge.simulation.world.genesis.RngRevision;
import io.github.evoforge.simulation.world.genesis.WorldGenerationIntent;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.genesis.WorldSpec;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import org.junit.jupiter.api.Test;

final class DeepBathymetryStructureGenerationTest {
    private static final BathymetryRecipe RECIPE = BathymetryRecipe.balanced();
    private static final BathymetryCalibrator CALIBRATOR = BathymetryCalibrator.standard();
    private static final BathymetryElevationAlgorithm DEEP = new DeepBathymetryStructureAlgorithm();

    @Test
    void largeDeepInteriorBreaksSingleDistanceBowlWithBroadBasinsAndHighs() {
        WorldBounds bounds = new WorldBounds(0, 120, 0, 120, -96, 96);
        ElevationField base = acceptedSquareBowl(bounds, 20);
        ElevationField result = generateDeep(bounds, base);
        BathymetryCalibration calibration = CALIBRATOR.calibrate(genesis(bounds), RECIPE);
        int protectedBand = Math.max(
                RECIPE.interiorStructure().minimumCoreRadiusCells(),
                calibration.coastalContextRadiusCells());

        long maximumSameClearanceSpread = 0L;
        int clearlyDeeper = 0;
        int clearlyShallower = 0;
        int maximumClearance = 60;
        for (int clearance = protectedBand + 1; clearance < maximumClearance; clearance++) {
            long minimumDepth = Long.MAX_VALUE;
            long maximumDepth = Long.MIN_VALUE;
            for (int y = 1; y < 120; y++) {
                for (int x = 1; x < 120; x++) {
                    if (squareClearance(x, y, 121, 121) != clearance) continue;
                    long before = depth(base, x, y);
                    long after = depth(result, x, y);
                    minimumDepth = Math.min(minimumDepth, after);
                    maximumDepth = Math.max(maximumDepth, after);
                    if (after > before + ElevationField.SUBUNITS_PER_CELL / 4L) clearlyDeeper++;
                    if (after + ElevationField.SUBUNITS_PER_CELL / 4L < before) clearlyShallower++;
                }
            }
            if (minimumDepth != Long.MAX_VALUE) {
                maximumSameClearanceSpread = Math.max(
                        maximumSameClearanceSpread,
                        maximumDepth - minimumDepth);
            }
        }

        assertTrue(clearlyDeeper >= 40,
                "large deep water should contain broad regions deeper than the old bowl at equal clearance");
        assertTrue(clearlyShallower >= 40,
                "large deep water should contain broad local highs rather than only extra depressions");
        assertTrue(maximumSameClearanceSpread > ElevationField.SUBUNITS_PER_CELL,
                "equal shoreline clearance must no longer imply one mathematically forced depth");
    }

    @Test
    void acceptedCoastalBandAndMembershipRemainBitIdentical() {
        WorldBounds bounds = new WorldBounds(0, 120, 0, 120, -96, 96);
        ElevationField base = acceptedSquareBowl(bounds, 20);
        ElevationField result = generateDeep(bounds, base);
        BathymetryCalibration calibration = CALIBRATOR.calibrate(genesis(bounds), RECIPE);
        int protectedBand = Math.max(
                RECIPE.interiorStructure().minimumCoreRadiusCells(),
                calibration.coastalContextRadiusCells());

        for (int y = 0; y <= 120; y++) {
            for (int x = 0; x <= 120; x++) {
                long before = base.elevationSubunitsAt(x, y);
                long after = result.elevationSubunitsAt(x, y);
                assertEquals(before < 0L, after < 0L, "deep structure must not change water membership");
                if (before >= 0L || squareClearance(x, y, 121, 121) <= protectedBand) {
                    assertEquals(before, after,
                            "land and the accepted world-scaled coastal band must remain bit-identical");
                }
            }
        }
    }

    @Test
    void shallowAndNarrowWaterBodiesStayBitIdentical() {
        WorldBounds shallowBounds = new WorldBounds(0, 48, 0, 48, -96, 96);
        ElevationField shallow = squareWaterBody(
                shallowBounds,
                1,
                -2L * ElevationField.SUBUNITS_PER_CELL);
        assertArrayEquals(values(shallow), values(generateDeep(shallowBounds, shallow)));

        WorldBounds narrowBounds = new WorldBounds(0, 79, 0, 79, -96, 96);
        ElevationField narrow = narrowSea(
                narrowBounds,
                37,
                42,
                -12L * ElevationField.SUBUNITS_PER_CELL);
        assertArrayEquals(values(narrow), values(generateDeep(narrowBounds, narrow)));
    }

    @Test
    void structuredBathymetryKeepsWorldFloorAndReadableCardinalSlopeBudget() {
        WorldBounds bounds = new WorldBounds(0, 120, 0, 120, -96, 96);
        ElevationField membership = squareWaterBody(bounds, 1, -1L);
        WorldGenesis genesis = genesis(bounds);
        BathymetryCalibration calibration = CALIBRATOR.calibrate(genesis, RECIPE);
        ElevationField result = StructuredBathymetryAlgorithm.standard()
                .generate(genesis, membership, calibration, RECIPE);
        long allowed = calibration.maximumCardinalFallSubunits() + 2_000L;
        long floor = calibration.floorSubunits();

        for (int y = 0; y <= 120; y++) {
            for (int x = 0; x <= 120; x++) {
                long current = result.elevationSubunitsAt(x, y);
                assertTrue(current >= floor, "deep structures must stay above the reserved world floor");
                if (current >= 0L) continue;
                if (x < 120 && result.elevationSubunitsAt(x + 1, y) < 0L) {
                    assertTrue(Math.abs(current - result.elevationSubunitsAt(x + 1, y)) <= allowed,
                            "deep structural relief must respect the existing cardinal slope budget");
                }
                if (y < 120 && result.elevationSubunitsAt(x, y + 1) < 0L) {
                    assertTrue(Math.abs(current - result.elevationSubunitsAt(x, y + 1)) <= allowed,
                            "deep structural relief must respect the existing cardinal slope budget");
                }
            }
        }
    }

    @Test
    void sameInputReplaysDeepStructuresExactly() {
        WorldBounds bounds = new WorldBounds(0, 120, 0, 120, -96, 96);
        ElevationField base = acceptedSquareBowl(bounds, 20);

        ElevationField first = generateDeep(bounds, base);
        ElevationField second = generateDeep(bounds, base);

        assertArrayEquals(values(first), values(second));
    }

    private static ElevationField generateDeep(WorldBounds bounds, ElevationField base) {
        WorldGenesis genesis = genesis(bounds);
        BathymetryCalibration calibration = CALIBRATOR.calibrate(genesis, RECIPE);
        return DEEP.generate(genesis, base, calibration, RECIPE);
    }

    private static ElevationField acceptedSquareBowl(WorldBounds bounds, int maximumDepthCells) {
        int width = Math.toIntExact((long) bounds.maxX() - bounds.minX() + 1L);
        int height = Math.toIntExact((long) bounds.maxY() - bounds.minY() + 1L);
        int maximumClearance = Math.min(width, height) / 2;
        long[] values = new long[Math.multiplyExact(width, height)];
        int index = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int clearance = squareClearance(x, y, width, height);
                if (clearance == 0) {
                    values[index++] = ElevationField.SUBUNITS_PER_CELL;
                    continue;
                }
                long depthCellsSubunits = ElevationField.SUBUNITS_PER_CELL
                        + (maximumDepthCells - 1L)
                                * ElevationField.SUBUNITS_PER_CELL
                                * clearance
                                / maximumClearance;
                values[index++] = -depthCellsSubunits;
            }
        }
        return new DenseElevationField(bounds, values);
    }

    private static int squareClearance(int x, int y, int width, int height) {
        return Math.min(Math.min(x, width - 1 - x), Math.min(y, height - 1 - y));
    }

    private static ElevationField squareWaterBody(WorldBounds bounds, int landBorder, long waterElevation) {
        int width = Math.toIntExact((long) bounds.maxX() - bounds.minX() + 1L);
        int height = Math.toIntExact((long) bounds.maxY() - bounds.minY() + 1L);
        long[] values = new long[Math.multiplyExact(width, height)];
        int index = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                boolean water = x >= landBorder
                        && x < width - landBorder
                        && y >= landBorder
                        && y < height - landBorder;
                values[index++] = water ? waterElevation : ElevationField.SUBUNITS_PER_CELL;
            }
        }
        return new DenseElevationField(bounds, values);
    }

    private static ElevationField narrowSea(
            WorldBounds bounds,
            int waterStartX,
            int waterEndX,
            long waterElevation) {
        int width = Math.toIntExact((long) bounds.maxX() - bounds.minX() + 1L);
        int height = Math.toIntExact((long) bounds.maxY() - bounds.minY() + 1L);
        long[] values = new long[Math.multiplyExact(width, height)];
        int index = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                boolean water = x >= waterStartX && x <= waterEndX;
                values[index++] = water ? waterElevation : ElevationField.SUBUNITS_PER_CELL;
            }
        }
        return new DenseElevationField(bounds, values);
    }

    private static long depth(ElevationField field, int x, int y) {
        return -field.elevationSubunitsAt(x, y);
    }

    private static WorldGenesis genesis(WorldBounds bounds) {
        return new WorldGenesis(
                new WorldSpec(bounds),
                41L,
                GenerationRevision.V14,
                RngRevision.V1,
                WorldGenerationIntent.balanced());
    }

    private static long[] values(ElevationField field) {
        WorldBounds bounds = field.bounds();
        int width = Math.toIntExact((long) bounds.maxX() - bounds.minX() + 1L);
        int height = Math.toIntExact((long) bounds.maxY() - bounds.minY() + 1L);
        long[] values = new long[Math.multiplyExact(width, height)];
        int index = 0;
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                values[index++] = field.elevationSubunitsAt(x, y);
            }
        }
        return values;
    }
}
