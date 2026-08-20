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

final class BathymetryMorphologyElevationGenerationTest {
    private static final BathymetryRecipe RECIPE = BathymetryRecipe.balanced();
    private static final BathymetryCalibrator CALIBRATOR = BathymetryCalibrator.standard();
    private static final BathymetryElevationAlgorithm ALGORITHM = new BathymetryMorphologyAlgorithm();

    @Test
    void preservesLandExactlyAndKeepsTheExistingSubmergedFootprint() {
        WorldBounds bounds = new WorldBounds(0, 10, 0, 10, -96, 96);
        ElevationField base = squareWaterBody(bounds, 1, -8L * ElevationField.SUBUNITS_PER_CELL);
        ElevationField result = generate(bounds, base);

        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                long before = base.elevationSubunitsAt(x, y);
                long after = result.elevationSubunitsAt(x, y);
                assertEquals(before < 0L, after < 0L, "submerged membership must not change");
                if (before >= 0L) {
                    assertEquals(before, after, "land elevation must be copied exactly");
                }
            }
        }
    }

    @Test
    void shorelineIsShallowAndInteriorBecomesProgressivelyDeeper() {
        WorldBounds bounds = new WorldBounds(0, 10, 0, 10, -96, 96);
        ElevationField result = generate(bounds, squareWaterBody(bounds, 1, -1L));

        long edgeDepth = -result.elevationSubunitsAt(1, 5);
        long middleDepth = -result.elevationSubunitsAt(3, 5);
        long centerDepth = -result.elevationSubunitsAt(5, 5);

        assertTrue(edgeDepth < middleDepth);
        assertTrue(middleDepth < centerDepth);
        assertTrue(edgeDepth < ElevationField.SUBUNITS_PER_CELL / 10L,
                "the first submerged column should remain a shallow littoral margin");
    }

    @Test
    void widerWaterBodiesNaturallySupportGreaterDepth() {
        WorldBounds smallBounds = new WorldBounds(0, 8, 0, 8, -96, 96);
        WorldBounds largeBounds = new WorldBounds(0, 32, 0, 32, -96, 96);

        ElevationField small = generate(smallBounds, squareWaterBody(smallBounds, 1, -1L));
        ElevationField large = generate(largeBounds, squareWaterBody(largeBounds, 1, -1L));

        long smallCenterDepth = -small.elevationSubunitsAt(4, 4);
        long largeCenterDepth = -large.elevationSubunitsAt(16, 16);
        assertTrue(largeCenterDepth > smallCenterDepth * 3L);
    }

    @Test
    void steepBroadLandReliefCanCausallyDeepenAnOceanCoast() {
        WorldBounds bounds = new WorldBounds(0, 63, 0, 63, -96, 96);
        ElevationField flatBase = openOceanAgainstLand(bounds, 16, 0L, -1L);
        ElevationField steepBase = openOceanAgainstLand(
                bounds,
                16,
                6L * ElevationField.SUBUNITS_PER_CELL,
                -1L);

        ElevationField flat = generate(bounds, flatBase);
        ElevationField steep = generate(bounds, steepBase);
        long flatFirstDepth = -flat.elevationSubunitsAt(16, 32);
        long steepFirstDepth = -steep.elevationSubunitsAt(16, 32);
        long steepThirdDepth = -steep.elevationSubunitsAt(18, 32);

        assertTrue(steepFirstDepth > flatFirstDepth + ElevationField.SUBUNITS_PER_CELL / 2L,
                "sustained high land relief should permit a visibly faster coastal descent");
        assertTrue(steepThirdDepth > steepFirstDepth,
                "the steep coastal continuation should still deepen coherently into the sea");
    }

    @Test
    void coastalReliefInfluenceFormsBroadSegmentsRatherThanIsolatedCellNoise() {
        WorldBounds bounds = new WorldBounds(0, 79, 0, 79, -96, 96);
        ElevationField flatBase = segmentedCoast(bounds, 20, -1, -1, 0L, -1L);
        ElevationField reliefBase = segmentedCoast(
                bounds,
                20,
                25,
                54,
                6L * ElevationField.SUBUNITS_PER_CELL,
                -1L);
        ElevationField flat = generate(bounds, flatBase);
        ElevationField relief = generate(bounds, reliefBase);

        boolean[] changed = new boolean[80];
        int changedCount = 0;
        int segments = 0;
        boolean previous = false;
        for (int y = 0; y < 80; y++) {
            long flatDepth = -flat.elevationSubunitsAt(20, y);
            long reliefDepth = -relief.elevationSubunitsAt(20, y);
            changed[y] = reliefDepth > flatDepth + ElevationField.SUBUNITS_PER_CELL / 20L;
            if (changed[y]) changedCount++;
            if (changed[y] && !previous) segments++;
            previous = changed[y];
        }

        assertTrue(changedCount >= 20, "a broad landform must affect a broad coastal segment");
        assertTrue(segments <= 2, "coastal character must not fragment into one-cell noise");
        for (int y = 1; y < 79; y++) {
            if (changed[y]) {
                assertTrue(changed[y - 1] || changed[y + 1],
                        "no changed coastal cell may exist as an isolated one-cell feature");
            }
        }
    }

    @Test
    void narrowOceanCorridorRemainsShallowEvenBesideHighRelief() {
        WorldBounds bounds = new WorldBounds(0, 39, 0, 39, -96, 96);
        ElevationField base = narrowBoundaryConnectedSea(
                bounds,
                18,
                21,
                8L * ElevationField.SUBUNITS_PER_CELL,
                -1L);
        ElevationField result = generate(bounds, base);

        long maximumDepth = 0L;
        for (int y = 0; y < 40; y++) {
            for (int x = 18; x <= 21; x++) {
                maximumDepth = Math.max(maximumDepth, -result.elevationSubunitsAt(x, y));
            }
        }

        assertTrue(maximumDepth < ElevationField.SUBUNITS_PER_CELL,
                "a narrow sea must stay shallow when horizontal room cannot support clean depth");
    }

    @Test
    void steepOceanCoastStaysMonotoneAndAvoidsMultiCellCardinalCliffs() {
        WorldBounds bounds = new WorldBounds(0, 63, 0, 63, -96, 96);
        ElevationField result = generate(
                bounds,
                openOceanAgainstLand(
                        bounds,
                        16,
                        8L * ElevationField.SUBUNITS_PER_CELL,
                        -1L));

        long allowed = ElevationField.SUBUNITS_PER_CELL;
        for (int x = 16; x < 47; x++) {
            long currentDepth = -result.elevationSubunitsAt(x, 32);
            long nextDepth = -result.elevationSubunitsAt(x + 1, 32);
            assertTrue(nextDepth >= currentDepth,
                    "causal coastal morphology must not create an underwater ridge while moving offshore");
            assertTrue(nextDepth - currentDepth <= allowed,
                    "causal coastal continuation may be steep but not a multi-cell cliff");
        }
    }

    @Test
    void generatedDepthNeverCrossesTheWorldFloor() {
        WorldBounds bounds = new WorldBounds(0, 64, 0, 64, -3, 96);
        ElevationField result = generate(bounds, squareWaterBody(bounds, 1, -1L));
        long floor = -3L * ElevationField.SUBUNITS_PER_CELL;

        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                assertTrue(result.elevationSubunitsAt(x, y) >= floor);
            }
        }
        assertEquals(floor, result.elevationSubunitsAt(32, 32));
    }

    @Test
    void cardinalUnderwaterSlopesRespectTheCalibratedReadableFallBudget() {
        WorldBounds bounds = new WorldBounds(0, 40, 0, 40, -96, 96);
        ElevationField result = generate(bounds, squareWaterBody(bounds, 1, -1L));
        BathymetryCalibration calibration = CALIBRATOR.calibrate(genesis(bounds), RECIPE);
        long allowed = calibration.maximumCardinalFallSubunits() + 2_000L;

        for (int y = 1; y < 40; y++) {
            for (int x = 1; x < 40; x++) {
                long current = result.elevationSubunitsAt(x, y);
                if (current >= 0L) continue;
                if (result.elevationSubunitsAt(x + 1, y) < 0L) {
                    assertTrue(Math.abs(current - result.elevationSubunitsAt(x + 1, y)) <= allowed);
                }
                if (result.elevationSubunitsAt(x, y + 1) < 0L) {
                    assertTrue(Math.abs(current - result.elevationSubunitsAt(x, y + 1)) <= allowed);
                }
            }
        }
    }

    @Test
    void sameInputsProduceIdenticalBathymetry() {
        WorldBounds bounds = new WorldBounds(0, 24, 0, 24, -96, 96);
        ElevationField base = squareWaterBody(bounds, 2, -1L);

        ElevationField first = generate(bounds, base);
        ElevationField second = generate(bounds, base);

        assertArrayEquals(values(first), values(second));
    }

    private static ElevationField generate(WorldBounds bounds, ElevationField base) {
        WorldGenesis genesis = genesis(bounds);
        BathymetryCalibration calibration = CALIBRATOR.calibrate(genesis, RECIPE);
        return ALGORITHM.generate(genesis, base, calibration, RECIPE);
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

    private static ElevationField openOceanAgainstLand(
            WorldBounds bounds,
            int landColumns,
            long landElevation,
            long waterElevation) {
        int width = Math.toIntExact((long) bounds.maxX() - bounds.minX() + 1L);
        int height = Math.toIntExact((long) bounds.maxY() - bounds.minY() + 1L);
        long[] values = new long[Math.multiplyExact(width, height)];
        int index = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                values[index++] = x < landColumns ? landElevation : waterElevation;
            }
        }
        return new DenseElevationField(bounds, values);
    }

    private static ElevationField segmentedCoast(
            WorldBounds bounds,
            int landColumns,
            int reliefStartY,
            int reliefEndY,
            long reliefElevation,
            long waterElevation) {
        int width = Math.toIntExact((long) bounds.maxX() - bounds.minX() + 1L);
        int height = Math.toIntExact((long) bounds.maxY() - bounds.minY() + 1L);
        long[] values = new long[Math.multiplyExact(width, height)];
        int index = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (x >= landColumns) {
                    values[index++] = waterElevation;
                } else {
                    boolean highRelief = y >= reliefStartY && y <= reliefEndY;
                    values[index++] = highRelief ? reliefElevation : 0L;
                }
            }
        }
        return new DenseElevationField(bounds, values);
    }

    private static ElevationField narrowBoundaryConnectedSea(
            WorldBounds bounds,
            int waterStartX,
            int waterEndX,
            long landElevation,
            long waterElevation) {
        int width = Math.toIntExact((long) bounds.maxX() - bounds.minX() + 1L);
        int height = Math.toIntExact((long) bounds.maxY() - bounds.minY() + 1L);
        long[] values = new long[Math.multiplyExact(width, height)];
        int index = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                boolean water = x >= waterStartX && x <= waterEndX;
                values[index++] = water ? waterElevation : landElevation;
            }
        }
        return new DenseElevationField(bounds, values);
    }

    private static WorldGenesis genesis(WorldBounds bounds) {
        return new WorldGenesis(
                new WorldSpec(bounds),
                41L,
                GenerationRevision.V13,
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
