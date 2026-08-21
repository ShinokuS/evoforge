package io.github.evoforge.simulation.world.atlas;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.definition.NormalizedValue;
import io.github.evoforge.simulation.world.genesis.GenerationRevision;
import io.github.evoforge.simulation.world.genesis.RngRevision;
import io.github.evoforge.simulation.world.genesis.WorldGenerationIntent;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.genesis.WorldSpec;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Diagnostic profile for the expensive V15 exact-land-compensation fallback. */
final class V15LandReservationScaleProfileTest {
    private static final long[] SEEDS = {
        71_337L,
        991_337L,
        4_859_186_304_997_574_751L,
        -7_713_371_991L,
        0x5deece66dL,
        0x9e3779b97f4a7c15L
    };

    @Test
    @Tag("worldgen-scale-profile")
    void reportPredictiveReservationFallbackFrequency() {
        int side = 300;
        int secondSynthesisCount = 0;
        long checksum = 0L;

        for (long seed : SEEDS) {
            AtomicInteger baseCalls = new AtomicInteger();
            ElevationGenerator base = genesis -> {
                baseCalls.incrementAndGet();
                return V14OceanicBaseTerrainGenerator.standard().generate(genesis);
            };
            V15InlandLakeBaseTerrainGenerator generator = new V15InlandLakeBaseTerrainGenerator(
                    base,
                    InlandLakeDomainCalibrator.standard(),
                    InlandLakeDomainRecipe.balanced(),
                    InlandLakeDomainAlgorithm.standard(),
                    InlandLakeShoreConditioningAlgorithm.standard(),
                    true);

            ElevationField result = generator.generate(genesis(side, seed));
            int calls = baseCalls.get();
            assertTrue(calls == 1 || calls == 2,
                    "predictive V15 path should synthesize continental base once or fall back once");
            if (calls == 2) secondSynthesisCount++;
            checksum ^= sample(result, seed);
            System.out.printf(Locale.ROOT,
                    "V15_RESERVATION seed=%d side=%d continental_syntheses=%d%n",
                    seed,
                    side,
                    calls);
        }

        System.out.printf(Locale.ROOT,
                "V15_RESERVATION_SUMMARY seeds=%d second_synthesis=%d rate=%.3f checksum=%016x%n",
                SEEDS.length,
                secondSynthesisCount,
                secondSynthesisCount / (double) SEEDS.length,
                checksum);
        assertTrue(checksum != 0L);
    }

    private static long sample(ElevationField field, long seed) {
        WorldBounds bounds = field.bounds();
        long value = seed;
        int width = Math.toIntExact((long) bounds.maxX() - bounds.minX() + 1L);
        int height = Math.toIntExact((long) bounds.maxY() - bounds.minY() + 1L);
        for (int i = 0; i < 64; i++) {
            long mixed = mix64(seed + i * 0x9e3779b97f4a7c15L);
            int x = bounds.minX() + (int) Long.remainderUnsigned(mixed, width);
            int y = bounds.minY() + (int) Long.remainderUnsigned(mixed >>> 17, height);
            value = mix64(value ^ field.elevationSubunitsAt(x, y));
        }
        return value;
    }

    private static WorldGenesis genesis(int side, long seed) {
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

    private static long mix64(long value) {
        long mixed = value;
        mixed = (mixed ^ (mixed >>> 30)) * 0xbf58476d1ce4e5b9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94d049bb133111ebL;
        return mixed ^ (mixed >>> 31);
    }
}
