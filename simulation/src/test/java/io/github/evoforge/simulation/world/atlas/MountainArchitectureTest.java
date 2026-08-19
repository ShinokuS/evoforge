package io.github.evoforge.simulation.world.atlas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.definition.NormalizedValue;
import io.github.evoforge.simulation.world.genesis.GenerationRevision;
import io.github.evoforge.simulation.world.genesis.MountainIntent;
import io.github.evoforge.simulation.world.genesis.RngRevision;
import io.github.evoforge.simulation.world.genesis.WorldGenerationIntent;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.genesis.WorldSpec;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import org.junit.jupiter.api.Test;

final class MountainArchitectureTest {
    private static final WorldBounds BOUNDS = new WorldBounds(-128, 127, -128, 127, -12, 96);
    private static final MountainRecipe RECIPE = MountainRecipe.balanced();
    private static final MountainCalibrator CALIBRATOR = MountainCalibrator.standard();

    @Test
    void zeroAbundanceCalibratesToNoMountainCandidates() {
        MountainCalibration calibration = CALIBRATOR.calibrate(
                genesis(BOUNDS, mountains(0, 500_000, 500_000, 500_000, 500_000, false, 0)),
                RECIPE);

        assertEquals(0, calibration.candidateActivationPpm());
    }

    @Test
    void largerMountainScaleProducesWiderAndMoreWidelySpacedSystems() {
        MountainCalibration small = CALIBRATOR.calibrate(
                genesis(BOUNDS, mountains(700_000, 0, 100_000, 500_000, 1_000_000, false, 0)),
                RECIPE);
        MountainCalibration large = CALIBRATOR.calibrate(
                genesis(BOUNDS, mountains(700_000, 0, 900_000, 500_000, 1_000_000, false, 0)),
                RECIPE);

        assertTrue(large.typicalHalfWidthCells() > small.typicalHalfWidthCells());
        assertTrue(large.candidateSpacingCells() > small.candidateSpacingCells());
    }

    @Test
    void chaininessOnlyStretchesTheSameSmoothHillAlongItsLongAxis() {
        MountainCalibration massif = CALIBRATOR.calibrate(
                genesis(BOUNDS, mountains(700_000, 400_000, 500_000, 0, 600_000, false, 0)),
                RECIPE);
        MountainCalibration chain = CALIBRATOR.calibrate(
                genesis(BOUNDS, mountains(700_000, 400_000, 500_000, 1_000_000, 600_000, false, 0)),
                RECIPE);

        assertEquals(massif.typicalHalfWidthCells(), chain.typicalHalfWidthCells());
        assertTrue(chain.typicalLongAxisCells() > massif.typicalLongAxisCells());
        assertTrue(chain.typicalLongAxisCells() > chain.typicalHalfWidthCells());
    }

    @Test
    void slopeBudgetKeepsMountainLevelsBroadWithoutKnowingConcreteShapes() {
        MountainCalibration soft = CALIBRATOR.calibrate(
                genesis(BOUNDS, mountains(700_000, 700_000, 500_000, 500_000, 0, false, 0)),
                RECIPE);
        MountainCalibration balanced = CALIBRATOR.calibrate(
                genesis(BOUNDS, mountains(700_000, 700_000, 500_000, 500_000, 600_000, false, 0)),
                RECIPE);
        MountainCalibration sharp = CALIBRATOR.calibrate(
                genesis(BOUNDS, mountains(700_000, 700_000, 500_000, 500_000, 1_000_000, false, 0)),
                RECIPE);

        long cell = ElevationField.SUBUNITS_PER_CELL;
        assertTrue(soft.maximumCardinalRiseSubunits() <= cell / 4L,
                "soft mountains should spend roughly four horizontal cells per vertical level");
        assertTrue(balanced.maximumCardinalRiseSubunits() <= 350_000L,
                "balanced mountains should remain near three horizontal cells per vertical level");
        assertTrue(sharp.maximumCardinalRiseSubunits() <= cell / 2L,
                "even maximum sharpness must never collapse below two horizontal cells per level");
        assertTrue(soft.maximumCardinalRiseSubunits() < balanced.maximumCardinalRiseSubunits());
        assertTrue(balanced.maximumCardinalRiseSubunits() < sharp.maximumCardinalRiseSubunits());
    }

    @Test
    void maximumMountainHeightScalesWithHorizontalWorldSize() {
        WorldBounds smallBounds = new WorldBounds(-32, 31, -32, 31, -12, 96);
        WorldBounds largeBounds = new WorldBounds(-250, 249, -250, 249, -12, 96);
        MountainIntent high = mountains(700_000, 1_000_000, 500_000, 550_000, 600_000, false, 0);

        MountainCalibration small = CALIBRATOR.calibrate(genesis(smallBounds, high), RECIPE);
        MountainCalibration large = CALIBRATOR.calibrate(genesis(largeBounds, high), RECIPE);

        long cell = ElevationField.SUBUNITS_PER_CELL;
        assertTrue(small.typicalUpliftSubunits() < 15L * cell,
                "a 64x64 world must not receive a hundred-cell mountain");
        assertTrue(large.typicalUpliftSubunits() > 50L * cell,
                "a 500x500 world should have enough vertical budget for recognizably high mountains");
        assertTrue(large.typicalUpliftSubunits() > small.typicalUpliftSubunits() * 5L);
    }

    @Test
    void tallerMountainsAutomaticallyReserveMoreHorizontalRoom() {
        MountainCalibration low = CALIBRATOR.calibrate(
                genesis(BOUNDS, mountains(700_000, 100_000, 100_000, 500_000, 600_000, false, 0)),
                RECIPE);
        MountainCalibration high = CALIBRATOR.calibrate(
                genesis(BOUNDS, mountains(700_000, 1_000_000, 100_000, 500_000, 600_000, false, 0)),
                RECIPE);

        assertTrue(high.typicalUpliftSubunits() > low.typicalUpliftSubunits());
        assertTrue(high.typicalHalfWidthCells() > low.typicalHalfWidthCells());
    }

    @Test
    void coastalTransitionExpandsWithMountainHeightInsteadOfUsingFixedCutoff() {
        MountainCalibration low = CALIBRATOR.calibrate(
                genesis(BOUNDS, mountains(700_000, 100_000, 500_000, 500_000, 600_000, false, 0)),
                RECIPE);
        MountainCalibration high = CALIBRATOR.calibrate(
                genesis(BOUNDS, mountains(700_000, 1_000_000, 500_000, 500_000, 600_000, false, 0)),
                RECIPE);

        assertTrue(high.coastalTransitionCells() > low.coastalTransitionCells());
        assertTrue(high.shorelineUpliftSubunits() <= 3L * ElevationField.SUBUNITS_PER_CELL);
    }

    @Test
    void plateauProbabilityIsIgnoredWhenPlateausAreDisabled() {
        MountainCalibration disabled = CALIBRATOR.calibrate(
                genesis(BOUNDS, mountains(700_000, 500_000, 500_000, 500_000, 500_000, false, 1_000_000)),
                RECIPE);

        assertEquals(0, disabled.plateauProbabilityPpm());
    }

    private static WorldGenesis genesis(WorldBounds bounds, MountainIntent mountains) {
        return new WorldGenesis(
                new WorldSpec(bounds),
                17L,
                GenerationRevision.V13,
                RngRevision.V1,
                new WorldGenerationIntent(
                        normalized(600_000),
                        normalized(750_000),
                        normalized(250_000),
                        normalized(600_000),
                        normalized(450_000),
                        normalized(500_000),
                        normalized(350_000),
                        mountains));
    }

    private static MountainIntent mountains(
            int abundance,
            int height,
            int scale,
            int chaininess,
            int sharpness,
            boolean plateaus,
            int plateauProbability) {
        return new MountainIntent(
                normalized(abundance),
                normalized(height),
                normalized(scale),
                normalized(chaininess),
                normalized(sharpness),
                plateaus,
                normalized(plateauProbability));
    }

    private static NormalizedValue normalized(int ppm) {
        return NormalizedValue.ofPartsPerMillion(ppm);
    }
}
