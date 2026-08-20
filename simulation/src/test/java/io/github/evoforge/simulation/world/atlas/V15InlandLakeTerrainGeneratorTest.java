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

final class V15InlandLakeTerrainGeneratorTest {

    @Test
    void v15AddsOnlyInteriorStandingWaterToAcceptedV14Footprint() {
        WorldGenesis genesis = genesis(300, 4_859_186_304_997_574_751L, GenerationRevision.V15);
        ElevationField v14 = V14BathymetryTerrainGenerator.standard().generate(genesis);
        ElevationField v15 = V15InlandLakeTerrainGenerator.standard().generate(genesis);

        int newWater = 0;
        WorldBounds bounds = v15.bounds();
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                long before = v14.elevationSubunitsAt(x, y);
                long after = v15.elevationSubunitsAt(x, y);
                if (before < 0L) {
                    assertTrue(after < 0L, "V15 must never dry accepted V14 standing water");
                } else if (after < 0L) {
                    newWater++;
                    assertTrue(x > bounds.minX() && x < bounds.maxX()
                                    && y > bounds.minY() && y < bounds.maxY(),
                            "inland lake must not be a boundary rewrite");
                }
            }
        }
        assertTrue(newWater >= 100,
                "representative high-land V15 world should contain a visually meaningful inland-water footprint");
    }

    @Test
    void sameGenesisReplaysZ0LakeTerrainExactly() {
        WorldGenesis genesis = genesis(180, 71_337L, GenerationRevision.V15);
        assertFieldsEqual(
                V15InlandLakeTerrainGenerator.standard().generate(genesis),
                V15InlandLakeTerrainGenerator.standard().generate(genesis));
    }

    @Test
    void elevationStageRoutesV15ToLakeDomainPipelineWhileV14RemainsAccepted() {
        WorldGenesis v15 = genesis(160, 991_337L, GenerationRevision.V15);
        assertFieldsEqual(
                V15InlandLakeTerrainGenerator.standard().generate(v15),
                new ElevationGenerationStage().generate(v15));

        WorldGenesis v14 = genesis(160, 991_337L, GenerationRevision.V14);
        assertFieldsEqual(
                V14BathymetryTerrainGenerator.standard().generate(v14),
                new ElevationGenerationStage().generate(v14));
    }

    private static void assertFieldsEqual(ElevationField expected, ElevationField actual) {
        assertEquals(expected.bounds(), actual.bounds());
        WorldBounds bounds = expected.bounds();
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                assertEquals(
                        expected.elevationSubunitsAt(x, y),
                        actual.elevationSubunitsAt(x, y),
                        "elevation mismatch at " + x + "," + y);
            }
        }
    }

    private static WorldGenesis genesis(int size, long seed, GenerationRevision revision) {
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
                revision,
                RngRevision.V1,
                intent);
    }
}
