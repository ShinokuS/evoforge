package io.github.evoforge.simulation.world.atlas;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.definition.NormalizedValue;
import io.github.evoforge.simulation.world.genesis.GenerationRevision;
import io.github.evoforge.simulation.world.genesis.RngRevision;
import io.github.evoforge.simulation.world.genesis.WorldGenerationIntent;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.genesis.WorldSpec;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

final class ScaleAwareLocalReliefElevationGenerationTest {
    private static final WorldBounds SMALL_BOUNDS = new WorldBounds(-32, 31, -32, 31, -12, 12);
    private static final WorldBounds LARGE_BOUNDS = new WorldBounds(-256, 255, -256, 255, -12, 12);

    @Test
    void sameV12GenesisProducesSameScaleAwareMorphology() {
        WorldGenesis genesis = genesis(
                SMALL_BOUNDS,
                42L,
                GenerationRevision.V12,
                380_000,
                700_000,
                250_000,
                650_000,
                350_000);

        assertArrayEquals(snapshot(generate(genesis)), snapshot(generate(genesis)));
    }

    @Test
    void localReliefChangesV12HeightsWithoutChangingLandOceanMask() {
        WorldGenesis calm = genesis(
                SMALL_BOUNDS,
                17L,
                GenerationRevision.V12,
                420_000,
                750_000,
                200_000,
                650_000,
                0);
        WorldGenesis rolling = genesis(
                SMALL_BOUNDS,
                17L,
                GenerationRevision.V12,
                420_000,
                750_000,
                200_000,
                650_000,
                1_000_000);

        ElevationField calmField = generate(calm);
        ElevationField rollingField = generate(rolling);
        assertEquals(signMask(calmField), signMask(rollingField));
        assertFalse(Arrays.equals(snapshot(calmField), snapshot(rollingField)));
        assertEquals(expectedLandCount(SMALL_BOUNDS, 420_000), landCount(rollingField));
    }

    @Test
    void v11IgnoresLocalReliefAndRemainsRevisionIsolated() {
        ElevationField localOff = generate(genesis(
                SMALL_BOUNDS,
                23L,
                GenerationRevision.V11,
                400_000,
                600_000,
                300_000,
                800_000,
                0));
        ElevationField localMax = generate(genesis(
                SMALL_BOUNDS,
                23L,
                GenerationRevision.V11,
                400_000,
                600_000,
                300_000,
                800_000,
                1_000_000));

        assertArrayEquals(snapshot(localOff), snapshot(localMax));
    }

    @Test
    void requestedCoverageRemainsExactUnderV12LocalRelief() {
        int coveragePpm = 370_000;
        ElevationField field = generate(genesis(
                SMALL_BOUNDS,
                71L,
                GenerationRevision.V12,
                coveragePpm,
                550_000,
                650_000,
                800_000,
                700_000));

        assertEquals(expectedLandCount(SMALL_BOUNDS, coveragePpm), landCount(field));
    }

    @Test
    void compactWorldLocalReliefStaysRollingInsteadOfCellScaleNoise() {
        ElevationField baseline = generate(genesis(
                SMALL_BOUNDS,
                91L,
                GenerationRevision.V12,
                1_000_000,
                750_000,
                250_000,
                1_000_000,
                0));
        ElevationField rolling = generate(genesis(
                SMALL_BOUNDS,
                91L,
                GenerationRevision.V12,
                1_000_000,
                750_000,
                250_000,
                1_000_000,
                1_000_000));

        long[] baselineSnapshot = snapshot(baseline);
        long[] rollingSnapshot = snapshot(rolling);
        long[] delta = delta(rollingSnapshot, baselineSnapshot);
        long cell = ElevationField.SUBUNITS_PER_CELL;
        assertTrue(range(delta) > cell / 3L, "local relief should remain visibly non-flat");

        long maximumUnclippedStep = maximumUnclippedCardinalDeltaStep(
                delta,
                rollingSnapshot,
                width(SMALL_BOUNDS),
                height(SMALL_BOUNDS),
                1L,
                (long) SMALL_BOUNDS.maxZ() * cell);
        assertTrue(
                maximumUnclippedStep < cell / 2L,
                "unclipped local relief should vary smoothly; maximum step="
                        + maximumUnclippedStep);
    }

    @Test
    void localReliefRemainsVisibleInsideLargeWorldDetailWindows() {
        ElevationField baseline = generate(genesis(
                LARGE_BOUNDS,
                1234L,
                GenerationRevision.V12,
                1_000_000,
                800_000,
                200_000,
                650_000,
                0));
        ElevationField rolling = generate(genesis(
                LARGE_BOUNDS,
                1234L,
                GenerationRevision.V12,
                1_000_000,
                800_000,
                200_000,
                650_000,
                1_000_000));

        long[] delta = delta(snapshot(rolling), snapshot(baseline));
        int visibleWindows = windowsWithDeltaRangeAtLeast(
                delta,
                width(LARGE_BOUNDS),
                height(LARGE_BOUNDS),
                64,
                ElevationField.SUBUNITS_PER_CELL / 4L);

        assertTrue(
                visibleWindows >= 8,
                "large worlds should retain local relief inside multiple detailed 64x64 windows");
    }

    private static WorldGenesis genesis(
            WorldBounds bounds,
            long seed,
            GenerationRevision revision,
            int coverage,
            int scale,
            int fragmentation,
            int relief,
            int localRelief) {
        return new WorldGenesis(
                new WorldSpec(bounds),
                seed,
                revision,
                RngRevision.V1,
                new WorldGenerationIntent(
                        NormalizedValue.ofPartsPerMillion(coverage),
                        NormalizedValue.ofPartsPerMillion(scale),
                        NormalizedValue.ofPartsPerMillion(fragmentation),
                        NormalizedValue.ofPartsPerMillion(relief),
                        NormalizedValue.ofPartsPerMillion(localRelief)));
    }

    private static ElevationField generate(WorldGenesis genesis) {
        return new ElevationGenerationStage().generate(genesis);
    }

    private static int expectedLandCount(WorldBounds bounds, int coveragePpm) {
        long area = (long) width(bounds) * height(bounds);
        return Math.toIntExact((area * coveragePpm + NormalizedValue.SCALE / 2L)
                / NormalizedValue.SCALE);
    }

    private static int landCount(ElevationField field) {
        int count = 0;
        for (long value : snapshot(field)) {
            if (value > ElevationGenerationStage.SEA_LEVEL_SUBUNITS) count++;
        }
        return count;
    }

    private static long[] snapshot(ElevationField field) {
        WorldBounds bounds = field.bounds();
        long[] result = new long[Math.multiplyExact(width(bounds), height(bounds))];
        int index = 0;
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                result[index++] = field.elevationSubunitsAt(x, y);
            }
        }
        return result;
    }

    private static String signMask(ElevationField field) {
        StringBuilder mask = new StringBuilder(snapshot(field).length);
        for (long value : snapshot(field)) mask.append(value > 0L ? 'L' : 'O');
        return mask.toString();
    }

    private static long[] delta(long[] changed, long[] baseline) {
        assertEquals(baseline.length, changed.length);
        long[] delta = new long[changed.length];
        for (int index = 0; index < changed.length; index++) {
            delta[index] = changed[index] - baseline[index];
        }
        return delta;
    }

    private static long range(long[] values) {
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        for (long value : values) {
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        return max - min;
    }

    private static long maximumUnclippedCardinalDeltaStep(
            long[] delta,
            long[] changed,
            int width,
            int height,
            long minimumHeight,
            long maximumHeight) {
        long maximum = 0L;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = y * width + x;
                if (x + 1 < width) {
                    maximum = Math.max(
                            maximum,
                            unclippedDeltaStep(
                                    delta,
                                    changed,
                                    index,
                                    index + 1,
                                    minimumHeight,
                                    maximumHeight));
                }
                if (y + 1 < height) {
                    maximum = Math.max(
                            maximum,
                            unclippedDeltaStep(
                                    delta,
                                    changed,
                                    index,
                                    index + width,
                                    minimumHeight,
                                    maximumHeight));
                }
            }
        }
        return maximum;
    }

    private static long unclippedDeltaStep(
            long[] delta,
            long[] changed,
            int first,
            int second,
            long minimumHeight,
            long maximumHeight) {
        if (changed[first] <= minimumHeight
                || changed[first] >= maximumHeight
                || changed[second] <= minimumHeight
                || changed[second] >= maximumHeight) {
            return 0L;
        }
        return Math.abs(delta[second] - delta[first]);
    }

    private static int windowsWithDeltaRangeAtLeast(
            long[] values,
            int width,
            int height,
            int windowSize,
            long minimumRange) {
        int matching = 0;
        for (int startY = 0; startY < height; startY += windowSize) {
            for (int startX = 0; startX < width; startX += windowSize) {
                long min = Long.MAX_VALUE;
                long max = Long.MIN_VALUE;
                int endY = Math.min(height, startY + windowSize);
                int endX = Math.min(width, startX + windowSize);
                for (int y = startY; y < endY; y++) {
                    for (int x = startX; x < endX; x++) {
                        long value = values[y * width + x];
                        min = Math.min(min, value);
                        max = Math.max(max, value);
                    }
                }
                if (max - min >= minimumRange) matching++;
            }
        }
        return matching;
    }

    private static int width(WorldBounds bounds) {
        return Math.toIntExact((long) bounds.maxX() - bounds.minX() + 1L);
    }

    private static int height(WorldBounds bounds) {
        return Math.toIntExact((long) bounds.maxY() - bounds.minY() + 1L);
    }
}
