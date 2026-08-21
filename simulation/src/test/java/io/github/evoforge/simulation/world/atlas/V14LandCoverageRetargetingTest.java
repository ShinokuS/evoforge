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

final class V14LandCoverageRetargetingTest {

    @Test
    void preparedLandRetargetMatchesIndependentGenerationBitForBit() {
        WorldGenesis preparation = genesis(128, 0x51a7eL, 845_000);
        WorldGenesis target = genesis(128, 0x51a7eL, 812_000);

        V14OceanicBaseTerrainGenerator preparedGenerator = V14OceanicBaseTerrainGenerator.standard();
        LandCoverageRetargetableElevationGenerator.PreparedLandCoverageElevation prepared =
                preparedGenerator.prepare(preparation);
        ElevationField actual = prepared.materialize(target);
        ElevationField expected = V14OceanicBaseTerrainGenerator.standard().generate(target);

        assertEquals(expected.bounds(), actual.bounds());
        for (int y = expected.bounds().minY(); y <= expected.bounds().maxY(); y++) {
            for (int x = expected.bounds().minX(); x <= expected.bounds().maxX(); x++) {
                assertEquals(
                        expected.elevationSubunitsAt(x, y),
                        actual.elevationSubunitsAt(x, y),
                        "prepared V14 retarget changed elevation at " + x + "," + y);
            }
        }
    }

    private static WorldGenesis genesis(int side, long seed, int landPpm) {
        int min = -side / 2;
        WorldBounds bounds = new WorldBounds(
                min,
                min + side - 1,
                min,
                min + side - 1,
                -96,
                96);
        WorldGenerationIntent balanced = WorldGenerationIntent.balanced();
        WorldGenerationIntent intent = new WorldGenerationIntent(
                NormalizedValue.ofPartsPerMillion(landPpm),
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
