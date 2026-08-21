package io.github.evoforge.simulation.world.atlas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.definition.NormalizedValue;
import io.github.evoforge.simulation.world.genesis.GenerationRevision;
import io.github.evoforge.simulation.world.genesis.RngRevision;
import io.github.evoforge.simulation.world.genesis.WorldGenerationIntent;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.genesis.WorldSpec;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import org.junit.jupiter.api.Test;

final class RegularizedGraphLandmassSilhouetteAlgorithmTest {

    @Test
    void fragmentationControlsSeparatedStructuralNuclei() {
        LandmassSilhouetteCalibration cohesive = calibrate(300, 0);
        LandmassSilhouetteCalibration fragmented = calibrate(300, 1_000_000);

        assertEquals(1, cohesive.landClusterCount(),
                "Fragmentation=0 must start from one cohesive geographic nucleus");
        assertTrue(fragmented.landClusterCount() >= 6,
                "Fragmentation=100% must start several independently separated geographic nuclei");
        assertTrue(fragmented.scaffoldSpacingCells() < cohesive.scaffoldSpacingCells(),
                "fragmented worlds need a finer structural graph for islands and straits");
    }

    @Test
    void standardAlgorithmIsTheAcceptedRegularizedGraphImplementation() {
        assertTrue(LandmassSilhouetteAlgorithm.standard()
                instanceof RegularizedGraphLandmassSilhouetteAlgorithm);
    }

    private static LandmassSilhouetteCalibration calibrate(int size, int fragmentationPpm) {
        WorldBounds bounds = new WorldBounds(
                -size / 2,
                -size / 2 + size - 1,
                -size / 2,
                -size / 2 + size - 1,
                -16,
                96);
        WorldGenerationIntent balanced = WorldGenerationIntent.balanced();
        WorldGenerationIntent intent = new WorldGenerationIntent(
                NormalizedValue.ofPartsPerMillion(500_000),
                balanced.landmassScale(),
                NormalizedValue.ofPartsPerMillion(fragmentationPpm),
                balanced.relief(),
                balanced.localRelief(),
                balanced.landformScale(),
                balanced.ruggedness(),
                balanced.mountains());
        WorldGenesis genesis = new WorldGenesis(
                new WorldSpec(bounds),
                71_337L,
                GenerationRevision.V14,
                RngRevision.V1,
                intent);
        V12LandformCalibration terrain = V12LandformCalibrator.standard()
                .calibrate(genesis, V12LandformRecipe.balanced());
        return LandmassSilhouetteCalibrator.standard()
                .calibrate(genesis, terrain, LandmassSilhouetteRecipe.balanced());
    }
}
