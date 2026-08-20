package io.github.evoforge.simulation.world.atlas.hydrology;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.definition.NormalizedValue;
import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.atlas.V15InlandBasinTerrainGenerator;
import io.github.evoforge.simulation.world.genesis.GenerationRevision;
import io.github.evoforge.simulation.world.genesis.RngRevision;
import io.github.evoforge.simulation.world.genesis.WorldGenerationIntent;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.genesis.WorldSpec;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import org.junit.jupiter.api.Test;

final class V15InlandLakeGenerationIntegrationTest {

    @Test
    void finalTerrainBasinsFeedIndependentLakeFormation() {
        WorldGenesis genesis = genesis(300, 4_859_186_304_997_574_751L);
        ElevationField finalTerrain = V15InlandBasinTerrainGenerator.standard().generate(genesis);
        WorldHydrologyTopology hydrology = WorldHydrologyTopologyStage.standard().generate(finalTerrain);

        assertTrue(hydrology.drainageBasins().basinCount() > 0,
                "V15 macro lowlands should expose real closed depressions to Priority-Flood");
        assertTrue(hydrology.inlandLakes().lakeCount() > 0,
                "lake owner should be able to fill significant V15 terrain basins");
        assertTrue(hydrology.inlandLakes().lake(0).surfaceElevationSubunits() >= 0L);
    }

    private static WorldGenesis genesis(int size, long seed) {
        int min = -size / 2;
        WorldBounds bounds = new WorldBounds(min, min + size - 1, min, min + size - 1, -96, 96);
        WorldGenerationIntent balanced = WorldGenerationIntent.balanced();
        WorldGenerationIntent intent = new WorldGenerationIntent(
                NormalizedValue.ofPartsPerMillion(830_000),
                NormalizedValue.ofPartsPerMillion(750_000),
                NormalizedValue.ofPartsPerMillion(120_000),
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
