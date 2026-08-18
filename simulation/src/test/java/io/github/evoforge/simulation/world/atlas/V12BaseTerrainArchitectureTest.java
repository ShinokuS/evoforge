package io.github.evoforge.simulation.world.atlas;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.evoforge.simulation.definition.NormalizedValue;
import io.github.evoforge.simulation.world.genesis.GenerationRevision;
import io.github.evoforge.simulation.world.genesis.RngRevision;
import io.github.evoforge.simulation.world.genesis.WorldGenerationIntent;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.genesis.WorldSpec;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import org.junit.jupiter.api.Test;

final class V12BaseTerrainArchitectureTest {
    private static final WorldBounds BOUNDS = new WorldBounds(-32, 31, -32, 31, -12, 12);

    @Test
    void semanticIntentIsResolvedIntoExactOperatingParametersBeforeGeneration() {
        WorldGenesis genesis = genesis(17L);
        V12LandformCalibration calibration = V12LandformCalibrator.standard()
                .calibrate(genesis, V12LandformRecipe.balanced());

        assertEquals(64, calibration.width());
        assertEquals(64, calibration.height());
        assertEquals(4_096, calibration.area());
        assertEquals(2_458, calibration.landCount());
        assertEquals(49, calibration.coherentLandmassScale());
        assertEquals(12, calibration.fragmentedLandmassScale());
        assertEquals(250_000, calibration.fragmentationPpm());
        assertEquals(42, calibration.landformSpacing());
        assertEquals(84, calibration.upliftScale());
        assertEquals(63, calibration.ridgeScale());
        assertEquals(21, calibration.rollingScale());
        assertEquals(14, calibration.rollingDetailScale());
        assertEquals(600_000, calibration.reliefPpm());
        assertEquals(450_000, calibration.localReliefPpm());
        assertEquals(350_000, calibration.ruggednessPpm());
        assertEquals(
                (long) ElevationField.SUBUNITS_PER_CELL * 327_000L / 1_000_000L,
                calibration.maximumReadableStepSubunits());
    }

    @Test
    void calibrationPolicyDoesNotDependOnWorldSeed() {
        V12LandformCalibrator calibrator = V12LandformCalibrator.standard();
        V12LandformRecipe recipe = V12LandformRecipe.balanced();

        assertEquals(
                calibrator.calibrate(genesis(1L), recipe),
                calibrator.calibrate(genesis(999_999L), recipe));
    }

    @Test
    void revisionRouterAndReplaceableV12GeneratorRemainBitIdentical() {
        WorldGenesis genesis = genesis(913L);
        ElevationField routed = new ElevationGenerationStage().generate(genesis);
        ElevationField direct = V12BaseTerrainGenerator.standard().generate(genesis);

        assertEquals(routed.bounds(), direct.bounds());
        for (int y = BOUNDS.minY(); y <= BOUNDS.maxY(); y++) {
            for (int x = BOUNDS.minX(); x <= BOUNDS.maxX(); x++) {
                assertEquals(
                        routed.elevationSubunitsAt(x, y),
                        direct.elevationSubunitsAt(x, y),
                        "V12 route must be a compatibility facade over the replaceable generator");
            }
        }
    }

    private static WorldGenesis genesis(long seed) {
        return new WorldGenesis(
                new WorldSpec(BOUNDS),
                seed,
                GenerationRevision.V12,
                RngRevision.V1,
                new WorldGenerationIntent(
                        NormalizedValue.ofPartsPerMillion(600_000),
                        NormalizedValue.ofPartsPerMillion(750_000),
                        NormalizedValue.ofPartsPerMillion(250_000),
                        NormalizedValue.ofPartsPerMillion(600_000),
                        NormalizedValue.ofPartsPerMillion(450_000),
                        NormalizedValue.ofPartsPerMillion(500_000),
                        NormalizedValue.ofPartsPerMillion(350_000)));
    }
}
