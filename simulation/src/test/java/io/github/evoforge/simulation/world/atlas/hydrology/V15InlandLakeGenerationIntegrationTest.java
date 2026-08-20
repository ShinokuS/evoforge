package io.github.evoforge.simulation.world.atlas.hydrology;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

final class V15InlandLakeGenerationIntegrationTest {

    @Test
    void generatedInlandLakesAreRealZ0StandingWaterRatherThanHydrologyOverlays() {
        WorldGenesis genesis = genesis(300, 4_859_186_304_997_574_751L, 120_000);
        ElevationField terrain = new ElevationGenerationStage().generate(genesis);
        WorldHydrologyTopology hydrology = WorldHydrologyTopologyStage.standard().generate(terrain);

        StandingWaterHydrologyTopology standing = hydrology.standingWaterTopology();
        assertTrue(standing.domains().oceanicBodyCount() > 0,
                "generated world must retain its boundary-connected ocean");
        assertTrue(standing.domains().inlandBodyCount() > 0,
                "V15 should expose at least one terrain-authored inland standing-water body");
        assertEquals(0, hydrology.inlandLakes().lakeCount(),
                "Priority-Flood must not add a second spill-level water mask");
    }

    @Test
    void highFragmentationMayReduceInteriorButDoesNotDisableIndependentLakeOwnership() {
        int worldsWithInlandWater = 0;
        long[] seeds = {71_337L, 991_337L, 4_859_186_304_997_574_751L, -8_201_337L};
        for (long seed : seeds) {
            ElevationField terrain = new ElevationGenerationStage().generate(genesis(300, seed, 1_000_000));
            StandingWaterHydrologyTopology standing = StandingWaterHydrologyTopologyStage.standard()
                    .generate(terrain);
            if (standing.domains().inlandBodyCount() > 0) worldsWithInlandWater++;
        }
        assertTrue(worldsWithInlandWater >= 2,
                "fragmentation may causally reduce continental interior, but must not switch lake generation off");
    }

    private static WorldGenesis genesis(int size, long seed, int fragmentationPpm) {
        int min = -size / 2;
        WorldBounds bounds = new WorldBounds(min, min + size - 1, min, min + size - 1, -96, 96);
        WorldGenerationIntent balanced = WorldGenerationIntent.balanced();
        WorldGenerationIntent intent = new WorldGenerationIntent(
                NormalizedValue.ofPartsPerMillion(830_000),
                NormalizedValue.ofPartsPerMillion(750_000),
                NormalizedValue.ofPartsPerMillion(fragmentationPpm),
                NormalizedValue.ofPartsPerMillion(600_000),
                NormalizedValue.ofPartsPerMillion(450_000),
                balanced.landformScale(),
                balanced.ruggedness(),
                balanced.mountains());
        return new WorldGenesis(
                new WorldSpec(bounds),
                seed,
                GenerationRevision.V15,
                RngRevision.V1,
                intent);
    }
}
