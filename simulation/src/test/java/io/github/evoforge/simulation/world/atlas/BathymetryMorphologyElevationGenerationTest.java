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
