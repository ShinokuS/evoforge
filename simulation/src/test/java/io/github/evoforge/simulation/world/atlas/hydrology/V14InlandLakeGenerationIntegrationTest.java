package io.github.evoforge.simulation.world.atlas.hydrology;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.definition.NormalizedValue;
import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.atlas.ElevationGenerationStage;
import io.github.evoforge.simulation.world.genesis.GenerationRevision;
import io.github.evoforge.simulation.world.genesis.RngRevision;
import io.github.evoforge.simulation.world.genesis.WorldGenerationIntent;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.genesis.WorldSpec;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import org.junit.jupiter.api.Test;

final class V14InlandLakeGenerationIntegrationTest {
    private static final long[] REPRESENTATIVE_SEEDS = {
            1L,
            71_337L,
            9_913L,
            -4_759_010_560_822_749_572L
    };

    @Test
    void maximumFragmentationDoesNotDisableIndependentInlandLakeFormation() {
        int worldsWithLakes = 0;
        int totalLakes = 0;
        for (long seed : REPRESENTATIVE_SEEDS) {
            ElevationField elevation = new ElevationGenerationStage().generate(
                    genesis(bounds(150), seed, 350_000, 1_000_000));
            WorldHydrologyTopology hydrology = WorldHydrologyTopologyStage.standard()
                    .generate(elevation);
            int lakes = hydrology.inlandLakes().lakeCount();
            totalLakes += lakes;
            if (lakes > 0) worldsWithLakes++;
        }

        assertTrue(
                totalLakes > 0,
                "Fragmentation=100% may causally change basin geometry but must not eliminate the independent lake mechanism across representative V14 worlds");
        assertTrue(
                worldsWithLakes >= 2,
                "lake formation should remain a recurring inland phenomenon rather than one accidental seed-specific artifact: worldsWithLakes="
                        + worldsWithLakes + " totalLakes=" + totalLakes);
    }

    private static WorldBounds bounds(int size) {
        int min = -size / 2;
        return new WorldBounds(min, min + size - 1, min, min + size - 1, -16, 96);
    }

    private static WorldGenesis genesis(
            WorldBounds bounds,
            long seed,
            int landPpm,
            int fragmentationPpm) {
        WorldGenerationIntent balanced = WorldGenerationIntent.balanced();
        WorldGenerationIntent intent = new WorldGenerationIntent(
                NormalizedValue.ofPartsPerMillion(landPpm),
                balanced.landmassScale(),
                NormalizedValue.ofPartsPerMillion(fragmentationPpm),
                balanced.relief(),
                balanced.localRelief(),
                balanced.landformScale(),
                balanced.ruggedness(),
                balanced.mountains());
        return new WorldGenesis(
                new WorldSpec(bounds),
                seed,
                GenerationRevision.V14,
                RngRevision.V1,
                intent);
    }
}
