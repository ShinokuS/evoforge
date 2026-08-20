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

final class V15InlandBasinTerrainGeneratorTest {

    @Test
    void basinMorphologyChangesReadableDryReliefWithoutChangingCoastOrBathymetry() {
        WorldGenesis genesis = genesis(300, 4_859_186_304_997_574_751L);
        ElevationField base = V14BathymetryTerrainGenerator.standard().generate(genesis);
        ElevationField result = V15InlandBasinTerrainGenerator.standard().generate(genesis);

        long changedDryCells = 0L;
        long maximumDrop = 0L;
        WorldBounds bounds = base.bounds();
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                long before = base.elevationSubunitsAt(x, y);
                long after = result.elevationSubunitsAt(x, y);
                assertEquals(before < 0L, after < 0L,
                        "V15 changed land/ocean membership at " + x + "," + y);
                if (before < 0L) {
                    assertEquals(before, after,
                            "V15 changed accepted V14 bathymetry at " + x + "," + y);
                } else if (after < before) {
                    changedDryCells++;
                    maximumDrop = Math.max(maximumDrop, before - after);
                }
            }
        }

        assertTrue(changedDryCells > 250L,
                "V15 basin morphology must be macro terrain, not a few-cell hydrology artifact");
        assertTrue(maximumDrop >= ElevationField.SUBUNITS_PER_CELL,
                "V15 must create a visually meaningful dry lowland depth");
    }

    @Test
    void sameGenesisReplaysBasinMorphologyExactly() {
        WorldGenesis genesis = genesis(180, 71_337L);
        ElevationField first = V15InlandBasinTerrainGenerator.standard().generate(genesis);
        ElevationField second = V15InlandBasinTerrainGenerator.standard().generate(genesis);
        WorldBounds bounds = first.bounds();
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                assertEquals(first.elevationSubunitsAt(x, y), second.elevationSubunitsAt(x, y));
            }
        }
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
