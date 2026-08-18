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
    private static final long CELL = ElevationField.SUBUNITS_PER_CELL;

    @Test
    void sameV12GenesisProducesSameLocalTerrain() {
        WorldGenesis genesis = genesis(
                SMALL_BOUNDS,
                42L,
                GenerationRevision.V12,
                380_000,
                700_000,
                250_000,
                650_000,
                250_000);

        assertArrayEquals(snapshot(generate(genesis)), snapshot(generate(genesis)));
    }

    @Test
    void zeroLocalReliefIsExactV11MacroBaseline() {
        ElevationField v11 = generate(genesis(
                SMALL_BOUNDS,
                23L,
                GenerationRevision.V11,
                400_000,
                600_000,
                300_000,
                800_000,
                0));
        ElevationField v12 = generate(genesis(
                SMALL_BOUNDS,
                23L,
                GenerationRevision.V12,
                400_000,
                600_000,
                300_000,
                800_000,
                0));

        assertArrayEquals(
                snapshot(v11),
                snapshot(v12),
                "the local-relief slider must have an unambiguous zero baseline");
    }

    @Test
    void v11IgnoresTheNewLocalReliefCoordinate() {
        ElevationField localOff = generate(genesis(
                SMALL_BOUNDS,
                29L,
                GenerationRevision.V11,
                400_000,
                600_000,
                300_000,
                800_000,
                0));
        ElevationField localMax = generate(genesis(
                SMALL_BOUNDS,
                29L,
                GenerationRevision.V11,
                400_000,
                600_000,
                300_000,
                800_000,
                1_000_000));

        assertArrayEquals(snapshot(localOff), snapshot(localMax));
    }

    @Test
    void localReliefPreservesLandOceanMembershipAndExactCoverage() {
        int coveragePpm = 420_000;
        ElevationField calm = generate(genesis(
                SMALL_BOUNDS,
                17L,
                GenerationRevision.V12,
                coveragePpm,
                750_000,
                200_000,
                650_000,
                0));
        ElevationField rolling = generate(genesis(
                SMALL_BOUNDS,
                17L,
                GenerationRevision.V12,
                coveragePpm,
                750_000,
                200_000,
                650_000,
                1_000_000));

        assertEquals(signMask(calm), signMask(rolling));
        assertFalse(Arrays.equals(snapshot(calm), snapshot(rolling)));
        assertEquals(expectedLandCount(SMALL_BOUNDS, coveragePpm), landCount(rolling));
    }

    @Test
    void compactWorldKeepsCalmRegionsInsteadOfPerturbingEveryCell() {
        ElevationField baseline = allLand(SMALL_BOUNDS, 91L, 0);
        ElevationField rolling = allLand(SMALL_BOUNDS, 91L, 1_000_000);
        long[] delta = delta(snapshot(rolling), snapshot(baseline));

        int unchanged = 0;
        int changed = 0;
        for (long value : delta) {
            if (value == 0L) unchanged++;
            else changed++;
            assertTrue(Math.abs(value) <= 3L * CELL, "local relief must stay cell-space bounded");
        }

        assertTrue(changed > delta.length / 8, "maximum local relief should still be clearly visible");
        assertTrue(
                unchanged > delta.length / 8,
                "quiet local bands should preserve substantial calm terrain on compact worlds");
    }

    @Test
    void compactWorldLocalLayerIsSmoothRatherThanCellScaleNoise() {
        ElevationField baseline = allLand(SMALL_BOUNDS, 101L, 0);
        ElevationField rolling = allLand(SMALL_BOUNDS, 101L, 1_000_000);
        long[] base = snapshot(baseline);
        long[] changed = snapshot(rolling);
        long[] delta = delta(changed, base);

        long maximumStep = maximumUnclippedCardinalDeltaStep(
                delta,
                changed,
                width(SMALL_BOUNDS),
                height(SMALL_BOUNDS),
                1L,
                (long) SMALL_BOUNDS.maxZ() * CELL);

        assertTrue(
                maximumStep < CELL,
                "local contribution must roll across several cells instead of producing cell noise; max step="
                        + maximumStep);
    }

    @Test
    void sliderStrengthMonotonicallyIncreasesVisibleDiscreteTerrainChanges() {
        ElevationField baseline = allLand(SMALL_BOUNDS, 1337L, 0);
        ElevationField low = allLand(SMALL_BOUNDS, 1337L, 250_000);
        ElevationField medium = allLand(SMALL_BOUNDS, 1337L, 500_000);
        ElevationField high = allLand(SMALL_BOUNDS, 1337L, 1_000_000);

        int lowChanged = discreteChangedColumns(baseline, low);
        int mediumChanged = discreteChangedColumns(baseline, medium);
        int highChanged = discreteChangedColumns(baseline, high);

        assertTrue(lowChanged <= mediumChanged, "50% must not be visually weaker than 25%");
        assertTrue(mediumChanged <= highChanged, "100% must not be visually weaker than 50%");
        assertTrue(highChanged > lowChanged, "the slider must have a clearly stronger high end");
    }

    @Test
    void largeWorldKeepsLocalReliefVisibleInDetailedWindows() {
        ElevationField baseline = allLand(LARGE_BOUNDS, 1234L, 0);
        ElevationField rolling = allLand(LARGE_BOUNDS, 1234L, 1_000_000);

        int visibleWindows = windowsWithDiscreteChangesAtLeast(
                baseline,
                rolling,
                64,
                64);

        assertTrue(
                visibleWindows >= 12,
                "local relief should change actual voxel surface levels in many detailed 64x64 windows; windows="
                        + visibleWindows);
    }

    private static ElevationField allLand(WorldBounds bounds, long seed, int localRelief) {
        return generate(genesis(
                bounds,
                seed,
                GenerationRevision.V12,
                1_000_000,
                750_000,
                250_000,
                700_000,
                localRelief));
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

    private static int discreteChangedColumns(ElevationField baseline, ElevationField changed) {
        WorldBounds bounds = baseline.bounds();
        assertEquals(bounds, changed.bounds());
        int count = 0;
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                if (baseline.elevationAt(x, y) != changed.elevationAt(x, y)) count++;
            }
        }
        return count;
    }

    private static int windowsWithDiscreteChangesAtLeast(
            ElevationField baseline,
            ElevationField changed,
            int windowSize,
            int minimumChangedColumns) {
        WorldBounds bounds = baseline.bounds();
        int matching = 0;
        for (int startY = bounds.minY(); startY <= bounds.maxY(); startY += windowSize) {
            for (int startX = bounds.minX(); startX <= bounds.maxX(); startX += windowSize) {
                int endY = Math.min(bounds.maxY(), startY + windowSize - 1);
                int endX = Math.min(bounds.maxX(), startX + windowSize - 1);
                int changedColumns = 0;
                for (int y = startY; y <= endY; y++) {
                    for (int x = startX; x <= endX; x++) {
                        if (baseline.elevationAt(x, y) != changed.elevationAt(x, y)) changedColumns++;
                    }
                }
                if (changedColumns >= minimumChangedColumns) matching++;
            }
        }
        return matching;
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

    private static int width(WorldBounds bounds) {
        return Math.toIntExact((long) bounds.maxX() - bounds.minX() + 1L);
    }

    private static int height(WorldBounds bounds) {
        return Math.toIntExact((long) bounds.maxY() - bounds.minY() + 1L);
    }
}
