package io.github.evoforge.simulation.world.atlas;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.evoforge.simulation.definition.NormalizedValue;
import io.github.evoforge.simulation.world.genesis.GenerationRevision;
import io.github.evoforge.simulation.world.genesis.RngRevision;
import io.github.evoforge.simulation.world.genesis.WorldGenerationIntent;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.genesis.WorldSpec;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import org.junit.jupiter.api.Test;

final class OceanFirstElevationGenerationTest {
    private static final WorldBounds BOUNDS = new WorldBounds(-16, 15, -16, 15, -8, 8);

    @Test
    void sameGenesisProducesSameOceanFirstSurface() {
        WorldGenesis genesis = genesis(42L, 350_000, 700_000, 200_000);
        assertArrayEquals(snapshot(generate(genesis)), snapshot(generate(genesis)));
    }

    @Test
    void landCoverageControlsActualColumnsAboveSeaLevel() {
        ElevationField field = generate(genesis(7L, 350_000, 500_000, 500_000));
        assertEquals(expectedLandCount(350_000), landCount(field));
    }

    @Test
    void spatialControlsDoNotChangeRequestedCoverage() {
        ElevationField islands = generate(genesis(11L, 400_000, 50_000, 900_000));
        ElevationField continent = generate(genesis(11L, 400_000, 950_000, 100_000));

        assertEquals(expectedLandCount(400_000), landCount(islands));
        assertEquals(expectedLandCount(400_000), landCount(continent));
        assertNotEquals(signMask(islands), signMask(continent));
    }

    @Test
    void coverageEndpointsProduceAllOceanOrAllLand() {
        assertEquals(0, landCount(generate(genesis(3L, 0, 500_000, 500_000))));
        assertEquals(32 * 32, landCount(generate(genesis(3L, 1_000_000, 500_000, 500_000))));
    }

    @Test
    void oceanFirstRequiresBoundsOnBothSidesOfSeaLevel() {
        WorldGenesis invalid = new WorldGenesis(
                new WorldSpec(new WorldBounds(0, 3, 0, 3, 0, 8)),
                1L,
                GenerationRevision.V9,
                RngRevision.V1,
                WorldGenerationIntent.balanced());
        assertThrows(IllegalArgumentException.class, () -> generate(invalid));
    }

    private static WorldGenesis genesis(
            long seed, int coverage, int scale, int fragmentation) {
        return new WorldGenesis(
                new WorldSpec(BOUNDS),
                seed,
                GenerationRevision.V9,
                RngRevision.V1,
                new WorldGenerationIntent(
                        NormalizedValue.ofPartsPerMillion(coverage),
                        NormalizedValue.ofPartsPerMillion(scale),
                        NormalizedValue.ofPartsPerMillion(fragmentation)));
    }

    private static ElevationField generate(WorldGenesis genesis) {
        return new ElevationGenerationStage().generate(genesis);
    }

    private static int expectedLandCount(int coveragePpm) {
        return (int) (((long) 32 * 32 * coveragePpm + NormalizedValue.SCALE / 2L)
                / NormalizedValue.SCALE);
    }

    private static int landCount(ElevationField field) {
        int count = 0;
        for (int y = BOUNDS.minY(); y <= BOUNDS.maxY(); y++) {
            for (int x = BOUNDS.minX(); x <= BOUNDS.maxX(); x++) {
                if (field.elevationSubunitsAt(x, y) > ElevationGenerationStage.SEA_LEVEL_SUBUNITS) count++;
            }
        }
        return count;
    }

    private static long[] snapshot(ElevationField field) {
        long[] result = new long[32 * 32];
        int index = 0;
        for (int y = BOUNDS.minY(); y <= BOUNDS.maxY(); y++) {
            for (int x = BOUNDS.minX(); x <= BOUNDS.maxX(); x++) {
                result[index++] = field.elevationSubunitsAt(x, y);
            }
        }
        return result;
    }

    private static String signMask(ElevationField field) {
        StringBuilder mask = new StringBuilder(32 * 32);
        for (long value : snapshot(field)) mask.append(value > 0L ? 'L' : 'O');
        return mask.toString();
    }
}
