package io.github.evoforge.simulation.world.atlas;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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

final class MacroMorphologyElevationGenerationTest {
    private static final WorldBounds BOUNDS = new WorldBounds(-32, 31, -32, 31, -12, 12);
    private static final int AREA = 64 * 64;

    @Test
    void sameGenesisProducesSameMacroMorphology() {
        WorldGenesis genesis = genesis(42L, GenerationRevision.V10, 380_000, 700_000, 250_000, 650_000);
        assertArrayEquals(snapshot(generate(genesis)), snapshot(generate(genesis)));
    }

    @Test
    void reliefChangesHeightsWithoutChangingLandOceanMask() {
        ElevationField flat = generate(genesis(
                17L, GenerationRevision.V10, 420_000, 750_000, 200_000, 0));
        ElevationField strong = generate(genesis(
                17L, GenerationRevision.V10, 420_000, 750_000, 200_000, 1_000_000));

        assertEquals(signMask(flat), signMask(strong));
        assertEquals(expectedLandCount(420_000), landCount(flat));
        assertEquals(expectedLandCount(420_000), landCount(strong));
        assertTrue(landRange(strong) > landRange(flat));
        assertTrue(maxLand(strong) > maxLand(flat));
    }

    @Test
    void v10PreservesV9OceanFirstLandMask() {
        WorldGenesis v9 = genesis(
                99L, GenerationRevision.V9, 350_000, 850_000, 300_000, 0);
        WorldGenesis v10 = genesis(
                99L, GenerationRevision.V10, 350_000, 850_000, 300_000, 1_000_000);

        assertEquals(signMask(generate(v9)), signMask(generate(v10)));
    }

    @Test
    void v9IgnoresReliefIntent() {
        ElevationField low = generate(genesis(
                23L, GenerationRevision.V9, 400_000, 600_000, 300_000, 0));
        ElevationField high = generate(genesis(
                23L, GenerationRevision.V9, 400_000, 600_000, 300_000, 1_000_000));

        assertArrayEquals(snapshot(low), snapshot(high));
    }

    @Test
    void requestedCoverageRemainsExactUnderMacroMorphology() {
        ElevationField field = generate(genesis(
                71L, GenerationRevision.V10, 370_000, 550_000, 650_000, 800_000));
        assertEquals(expectedLandCount(370_000), landCount(field));
    }

    private static WorldGenesis genesis(
            long seed,
            GenerationRevision revision,
            int coverage,
            int scale,
            int fragmentation,
            int relief) {
        return new WorldGenesis(
                new WorldSpec(BOUNDS),
                seed,
                revision,
                RngRevision.V1,
                new WorldGenerationIntent(
                        NormalizedValue.ofPartsPerMillion(coverage),
                        NormalizedValue.ofPartsPerMillion(scale),
                        NormalizedValue.ofPartsPerMillion(fragmentation),
                        NormalizedValue.ofPartsPerMillion(relief)));
    }

    private static ElevationField generate(WorldGenesis genesis) {
        return new ElevationGenerationStage().generate(genesis);
    }

    private static int expectedLandCount(int coveragePpm) {
        return (int) (((long) AREA * coveragePpm + NormalizedValue.SCALE / 2L)
                / NormalizedValue.SCALE);
    }

    private static int landCount(ElevationField field) {
        int count = 0;
        for (long value : snapshot(field)) {
            if (value > ElevationGenerationStage.SEA_LEVEL_SUBUNITS) count++;
        }
        return count;
    }

    private static long maxLand(ElevationField field) {
        long max = Long.MIN_VALUE;
        for (long value : snapshot(field)) {
            if (value > 0L) max = Math.max(max, value);
        }
        return max;
    }

    private static long landRange(ElevationField field) {
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        for (long value : snapshot(field)) {
            if (value <= 0L) continue;
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        return max - min;
    }

    private static long[] snapshot(ElevationField field) {
        long[] result = new long[AREA];
        int index = 0;
        for (int y = BOUNDS.minY(); y <= BOUNDS.maxY(); y++) {
            for (int x = BOUNDS.minX(); x <= BOUNDS.maxX(); x++) {
                result[index++] = field.elevationSubunitsAt(x, y);
            }
        }
        return result;
    }

    private static String signMask(ElevationField field) {
        StringBuilder mask = new StringBuilder(AREA);
        for (long value : snapshot(field)) mask.append(value > 0L ? 'L' : 'O');
        return mask.toString();
    }
}
