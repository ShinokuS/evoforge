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
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class ScaleAwareLocalReliefElevationGenerationTest {
    private static final WorldBounds SMALL_BOUNDS = new WorldBounds(-32, 31, -32, 31, -12, 12);
    private static final WorldBounds LARGE_BOUNDS = new WorldBounds(-256, 255, -256, 255, -12, 12);
    private static final long CELL = ElevationField.SUBUNITS_PER_CELL;

    @Test
    void sameV12GenesisProducesSameScaleStableTerrain() {
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
    void v11StillIgnoresTheNewLocalReliefCoordinate() {
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
    void compactMacroTerrainIsSmoothInsteadOfCellScaleNoise() {
        ElevationField field = allLand(SMALL_BOUNDS, 91L, 0);
        long maximumStep = maximumCardinalPreciseStep(field);
        int discreteTransitions = discreteCardinalTransitions(field);
        int cardinalEdges = cardinalEdgeCount(SMALL_BOUNDS);

        assertTrue(
                maximumStep < CELL,
                "64x64 macro terrain must not jump a whole cell between adjacent columns; max step="
                        + maximumStep);
        assertTrue(
                discreteTransitions < cardinalEdges / 2,
                "64x64 macro terrain must not become a checkerboard of Z changes; transitions="
                        + discreteTransitions + "/" + cardinalEdges);
    }

    @Test
    void largeMacroTerrainDoesNotCollapseIntoOnePlateau() {
        ElevationField field = allLand(LARGE_BOUNDS, 1234L, 0);
        int variedWindows = windowsWithAtLeastTwoDiscreteLevels(field, 64);
        int totalWindows = (width(LARGE_BOUNDS) / 64) * (height(LARGE_BOUNDS) / 64);

        assertTrue(
                variedWindows >= totalWindows / 3,
                "large worlds must retain terrain structure in detailed 64x64 windows; varied="
                        + variedWindows + "/" + totalWindows);
    }

    @Test
    void maximumLocalReliefChangesManyDiscreteCellsOnCompactWorld() {
        ElevationField baseline = allLand(SMALL_BOUNDS, 101L, 0);
        ElevationField rolling = allLand(SMALL_BOUNDS, 101L, 1_000_000);

        int changed = discreteChangedColumns(baseline, rolling);
        assertTrue(
                changed > area(SMALL_BOUNDS) / 5,
                "maximum local relief should visibly affect a substantial part of 64x64 terrain; changed="
                        + changed);
    }

    @Test
    void maximumLocalReliefRemainsVisibleAcrossLargeWorldDetailWindows() {
        ElevationField baseline = allLand(LARGE_BOUNDS, 202L, 0);
        ElevationField rolling = allLand(LARGE_BOUNDS, 202L, 1_000_000);

        int visibleWindows = windowsWithDiscreteChangesAtLeast(baseline, rolling, 64, 128);
        int totalWindows = (width(LARGE_BOUNDS) / 64) * (height(LARGE_BOUNDS) / 64);

        assertTrue(
                visibleWindows >= totalWindows / 3,
                "large worlds must show local relief in many detailed windows; visible="
                        + visibleWindows + "/" + totalWindows);
    }

    @Test
    void localReliefContributionRollsAcrossCellsInsteadOfAddingNoise() {
        ElevationField baseline = allLand(SMALL_BOUNDS, 303L, 0);
        ElevationField rolling = allLand(SMALL_BOUNDS, 303L, 1_000_000);
        long[] changed = snapshot(rolling);
        long[] delta = delta(changed, snapshot(baseline));

        long maximumDeltaStep = maximumUnclippedCardinalDeltaStep(
                delta,
                changed,
                width(SMALL_BOUNDS),
                height(SMALL_BOUNDS),
                1L,
                (long) SMALL_BOUNDS.maxZ() * CELL);

        assertTrue(
                maximumDeltaStep < CELL,
                "local relief must form broad slopes rather than one-cell noise; max delta step="
                        + maximumDeltaStep);
        for (long value : delta) {
            assertTrue(
                    Math.abs(value) <= 8L * CELL,
                    "local relief must remain bounded in terrain-cell height");
        }
    }

    @Test
    void sliderStrengthMonotonicallyIncreasesDiscreteTerrainChanges() {
        ElevationField baseline = allLand(SMALL_BOUNDS, 1337L, 0);
        ElevationField low = allLand(SMALL_BOUNDS, 1337L, 250_000);
        ElevationField medium = allLand(SMALL_BOUNDS, 1337L, 500_000);
        ElevationField high = allLand(SMALL_BOUNDS, 1337L, 1_000_000);

        int lowChanged = discreteChangedColumns(baseline, low);
        int mediumChanged = discreteChangedColumns(baseline, medium);
        int highChanged = discreteChangedColumns(baseline, high);

        assertTrue(lowChanged <= mediumChanged, "50% must not be visually weaker than 25%");
        assertTrue(mediumChanged <= highChanged, "100% must not be visually weaker than 50%");
        assertTrue(
                highChanged >= lowChanged + area(SMALL_BOUNDS) / 20,
                "the high end of the slider must be materially stronger than the low end");
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

    private static int windowsWithAtLeastTwoDiscreteLevels(ElevationField field, int windowSize) {
        WorldBounds bounds = field.bounds();
        int varied = 0;
        for (int startY = bounds.minY(); startY <= bounds.maxY(); startY += windowSize) {
            for (int startX = bounds.minX(); startX <= bounds.maxX(); startX += windowSize) {
                int endY = Math.min(bounds.maxY(), startY + windowSize - 1);
                int endX = Math.min(bounds.maxX(), startX + windowSize - 1);
                Set<Integer> levels = new HashSet<>();
                for (int y = startY; y <= endY && levels.size() < 2; y++) {
                    for (int x = startX; x <= endX && levels.size() < 2; x++) {
                        levels.add(field.elevationAt(x, y));
                    }
                }
                if (levels.size() >= 2) varied++;
            }
        }
        return varied;
    }

    private static long maximumCardinalPreciseStep(ElevationField field) {
        WorldBounds bounds = field.bounds();
        long maximum = 0L;
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                long current = field.elevationSubunitsAt(x, y);
                if (x < bounds.maxX()) {
                    maximum = Math.max(maximum, Math.abs(field.elevationSubunitsAt(x + 1, y) - current));
                }
                if (y < bounds.maxY()) {
                    maximum = Math.max(maximum, Math.abs(field.elevationSubunitsAt(x, y + 1) - current));
                }
            }
        }
        return maximum;
    }

    private static int discreteCardinalTransitions(ElevationField field) {
        WorldBounds bounds = field.bounds();
        int count = 0;
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                int current = field.elevationAt(x, y);
                if (x < bounds.maxX() && field.elevationAt(x + 1, y) != current) count++;
                if (y < bounds.maxY() && field.elevationAt(x, y + 1) != current) count++;
            }
        }
        return count;
    }

    private static int cardinalEdgeCount(WorldBounds bounds) {
        int width = width(bounds);
        int height = height(bounds);
        return (width - 1) * height + (height - 1) * width;
    }

    private static int expectedLandCount(WorldBounds bounds, int coveragePpm) {
        long area = area(bounds);
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

    private static int area(WorldBounds bounds) {
        return Math.multiplyExact(width(bounds), height(bounds));
    }

    private static int width(WorldBounds bounds) {
        return Math.toIntExact((long) bounds.maxX() - bounds.minX() + 1L);
    }

    private static int height(WorldBounds bounds) {
        return Math.toIntExact((long) bounds.maxY() - bounds.minY() + 1L);
    }
}
