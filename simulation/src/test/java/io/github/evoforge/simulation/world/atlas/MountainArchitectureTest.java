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
                genesis(mountains(0, 500_000, 500_000, 500_000, 500_000, false, 0)),
                RECIPE);

        assertEquals(0, calibration.candidateActivationPpm());
    }

    @Test
    void largerMountainScaleProducesWiderAndMoreWidelySpacedSystems() {
        MountainCalibration small = CALIBRATOR.calibrate(
                genesis(mountains(700_000, 300_000, 100_000, 500_000, 800_000, false, 0)),
                RECIPE);
        MountainCalibration large = CALIBRATOR.calibrate(
                genesis(mountains(700_000, 300_000, 900_000, 500_000, 800_000, false, 0)),
                RECIPE);

        assertTrue(large.typicalHalfWidthCells() > small.typicalHalfWidthCells());
        assertTrue(large.candidateSpacingCells() > small.candidateSpacingCells());
    }

    @Test
    void chaininessLengthensTheSameMountainSystemInsteadOfCreatingAnotherGeneratorType() {
        MountainCalibration massif = CALIBRATOR.calibrate(
                genesis(mountains(700_000, 400_000, 500_000, 0, 600_000, false, 0)),
                RECIPE);
        MountainCalibration chain = CALIBRATOR.calibrate(
                genesis(mountains(700_000, 400_000, 500_000, 1_000_000, 600_000, false, 0)),
                RECIPE);

        assertTrue(chain.ridgeHalfLengthCells() > massif.ridgeHalfLengthCells());
        assertTrue(chain.branchProbabilityPpm() > massif.branchProbabilityPpm());
    }

    @Test
    void tallSoftMountainsAutomaticallyWidenInsteadOfBecomingUnreadableNeedles() {
        MountainCalibration soft = CALIBRATOR.calibrate(
                genesis(mountains(700_000, 900_000, 100_000, 500_000, 0, false, 0)),
                RECIPE);
        MountainCalibration sharp = CALIBRATOR.calibrate(
                genesis(mountains(700_000, 900_000, 100_000, 500_000, 1_000_000, false, 0)),
                RECIPE);

        assertTrue(soft.typicalHalfWidthCells() > sharp.typicalHalfWidthCells());
        assertTrue(soft.typicalHalfWidthCells() > RECIPE.minimumHalfWidthCells());
    }

    @Test
    void plateauProbabilityIsIgnoredWhenPlateausAreDisabled() {
        MountainCalibration disabled = CALIBRATOR.calibrate(
                genesis(mountains(700_000, 500_000, 500_000, 500_000, 500_000, false, 1_000_000)),
                RECIPE);

        assertEquals(0, disabled.plateauProbabilityPpm());
    }

    private static WorldGenesis genesis(MountainIntent mountains) {
        return new WorldGenesis(
                new WorldSpec(BOUNDS),
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
