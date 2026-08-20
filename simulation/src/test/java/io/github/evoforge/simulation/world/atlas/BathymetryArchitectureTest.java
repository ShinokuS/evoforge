package io.github.evoforge.simulation.world.atlas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.genesis.GenerationRevision;
import io.github.evoforge.simulation.world.genesis.RngRevision;
import io.github.evoforge.simulation.world.genesis.WorldGenerationIntent;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.genesis.WorldSpec;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import org.junit.jupiter.api.Test;

final class BathymetryArchitectureTest {
    private static final BathymetryRecipe RECIPE = BathymetryRecipe.balanced();
    private static final BathymetryCalibrator CALIBRATOR = BathymetryCalibrator.standard();

    @Test
    void worldDepthBudgetGrowsWithHorizontalScaleWithoutExceedingNegativeZCapacity() {
        BathymetryCalibration small = CALIBRATOR.calibrate(
                genesis(new WorldBounds(-32, 31, -32, 31, -96, 96)),
                RECIPE);
        BathymetryCalibration large = CALIBRATOR.calibrate(
                genesis(new WorldBounds(-250, 249, -250, 249, -96, 96)),
                RECIPE);

        assertTrue(large.worldDepthCapSubunits() > small.worldDepthCapSubunits() * 5L);
        assertTrue(large.worldDepthCapSubunits() <= 96L * ElevationField.SUBUNITS_PER_CELL);
    }

    @Test
    void readableSlopeBudgetStaysBelowHalfACellPerCardinalStep() {
        BathymetryCalibration calibration = CALIBRATOR.calibrate(
                genesis(new WorldBounds(-128, 127, -128, 127, -96, 96)),
                RECIPE);

        assertEquals(420_000L, calibration.maximumCardinalFallSubunits());
        assertTrue(calibration.maximumCardinalFallSubunits() < ElevationField.SUBUNITS_PER_CELL / 2L);
    }

    @Test
    void coastalContextScalesWithWorldSizeWithinRecipeBounds() {
        BathymetryCalibration small = CALIBRATOR.calibrate(
                genesis(new WorldBounds(-32, 31, -32, 31, -96, 96)),
                RECIPE);
        BathymetryCalibration medium = CALIBRATOR.calibrate(
                genesis(new WorldBounds(-150, 149, -150, 149, -96, 96)),
                RECIPE);
        BathymetryCalibration large = CALIBRATOR.calibrate(
                genesis(new WorldBounds(-250, 249, -250, 249, -96, 96)),
                RECIPE);

        assertEquals(6, small.coastalContextRadiusCells());
        assertEquals(13, medium.coastalContextRadiusCells());
        assertEquals(18, large.coastalContextRadiusCells());
    }

    @Test
    void coastalFallBudgetAllowsSteepButNotMultiCellCliffContinuation() {
        BathymetryCalibration calibration = CALIBRATOR.calibrate(
                genesis(new WorldBounds(-150, 149, -150, 149, -96, 96)),
                RECIPE);

        assertEquals(20_000L, calibration.coastalMinimumFallSubunits());
        assertEquals(800_000L, calibration.coastalMaximumFallSubunits());
        assertTrue(calibration.coastalMaximumFallSubunits() > calibration.maximumCardinalFallSubunits());
        assertTrue(calibration.coastalMaximumFallSubunits() < ElevationField.SUBUNITS_PER_CELL);
    }

    @Test
    void verticalWorldFloorCapsOtherwiseDeeperBathymetry() {
        BathymetryCalibration shallowWorld = CALIBRATOR.calibrate(
                genesis(new WorldBounds(-250, 249, -250, 249, -7, 96)),
                RECIPE);

        assertEquals(7L * ElevationField.SUBUNITS_PER_CELL, shallowWorld.worldDepthCapSubunits());
        assertEquals(-7L * ElevationField.SUBUNITS_PER_CELL, shallowWorld.floorSubunits());
    }

    private static WorldGenesis genesis(WorldBounds bounds) {
        return new WorldGenesis(
                new WorldSpec(bounds),
                17L,
                GenerationRevision.V13,
                RngRevision.V1,
                WorldGenerationIntent.balanced());
    }
}
