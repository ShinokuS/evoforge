package io.github.evoforge.simulation.world.atlas;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.definition.NormalizedValue;
import io.github.evoforge.simulation.world.genesis.GenerationRevision;
import io.github.evoforge.simulation.world.genesis.RngRevision;
import io.github.evoforge.simulation.world.genesis.WorldGenerationIntent;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.genesis.WorldSpec;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Acceptance laws for V12's readable multi-scale terrain character. */
final class V12BalancedLandformElevationGenerationTest {
    private static final WorldBounds SMALL = new WorldBounds(-32, 31, -32, 31, -12, 12);
    private static final WorldBounds MEDIUM = new WorldBounds(-128, 127, -128, 127, -12, 12);
    private static final WorldBounds LARGE = new WorldBounds(-192, 191, -192, 191, -12, 12);

    @Test
    void default64TerrainIsStructuredWithoutCellScaleNoise() {
        ElevationField field = generate(SMALL, 41L, 500_000, 350_000);
        int transitions = cardinalTransitions(field);
        int edges = cardinalEdges(SMALL);
        int levels = discreteLevels(field);

        assertTrue(
                transitions < edges / 3,
                "default 64x64 terrain must read as slopes and landforms, not alternating cells; transitions="
                        + transitions + "/" + edges);
        assertTrue(
                transitions > edges / 50,
                "default 64x64 terrain must still contain visible elevation structure; transitions="
                        + transitions + "/" + edges);
        assertTrue(levels >= 3, "default 64x64 terrain must contain several discrete height levels");
    }

    @Test
    void largeWorldKeepsDetailedReliefAcrossMostWindows() {
        ElevationField field = generate(LARGE, 202L, 500_000, 350_000);
        int varied = windowsWithAtLeastLevels(field, 64, 3);
        int windows = windowCount(LARGE, 64);

        assertTrue(
                varied * 4 >= windows * 3,
                "at least 75% of detailed large-world windows must contain several height levels; varied="
                        + varied + "/" + windows);
    }

    @Test
    void balancedTerrainContainsBothLocalHighsAndLocalDepressions() {
        ElevationField field = generate(MEDIUM, 913L, 450_000, 300_000);
        int[] extrema = coarseCardinalExtrema(field, 12);

        assertTrue(extrema[0] >= 2, "terrain must contain multiple readable local highs");
        assertTrue(extrema[1] >= 2, "terrain must contain multiple readable local depressions");
    }

    @Test
    void landformSizeChangesSpatialFrequencyInsteadOfVerticalStrengthOnly() {
        ElevationField compact = generate(MEDIUM, 77L, 0, 350_000);
        ElevationField broad = generate(MEDIUM, 77L, 1_000_000, 350_000);
        int compactExtrema = sum(coarseCardinalExtrema(compact, 10));
        int broadExtrema = sum(coarseCardinalExtrema(broad, 10));

        assertTrue(
                compactExtrema > broadExtrema,
                "smaller authored landforms must produce more regional extrema than broad landforms; compact="
                        + compactExtrema + ", broad=" + broadExtrema);
    }

    @Test
    void ruggednessPermitsStrongerReadableCardinalSlopes() {
        ElevationField calm = generate(MEDIUM, 333L, 500_000, 0);
        ElevationField rugged = generate(MEDIUM, 333L, 500_000, 1_000_000);

        assertTrue(
                maximumCardinalStep(rugged) > maximumCardinalStep(calm),
                "ruggedness must materially increase the available terrain slope character");
    }

    private static ElevationField generate(
            WorldBounds bounds,
            long seed,
            int landformScalePpm,
            int ruggednessPpm) {
        WorldGenesis genesis = new WorldGenesis(
                new WorldSpec(bounds),
                seed,
                GenerationRevision.V12,
                RngRevision.V1,
                new WorldGenerationIntent(
                        NormalizedValue.ofPartsPerMillion(1_000_000),
                        NormalizedValue.ofPartsPerMillion(750_000),
                        NormalizedValue.ofPartsPerMillion(250_000),
                        NormalizedValue.ofPartsPerMillion(600_000),
                        NormalizedValue.ofPartsPerMillion(450_000),
                        NormalizedValue.ofPartsPerMillion(landformScalePpm),
                        NormalizedValue.ofPartsPerMillion(ruggednessPpm)));
        return new ElevationGenerationStage().generate(genesis);
    }

    private static int cardinalTransitions(ElevationField field) {
        WorldBounds bounds = field.bounds();
        int transitions = 0;
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                int level = field.elevationAt(x, y);
                if (x < bounds.maxX() && field.elevationAt(x + 1, y) != level) transitions++;
                if (y < bounds.maxY() && field.elevationAt(x, y + 1) != level) transitions++;
            }
        }
        return transitions;
    }

    private static int discreteLevels(ElevationField field) {
        Set<Integer> levels = new HashSet<>();
        WorldBounds bounds = field.bounds();
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                levels.add(field.elevationAt(x, y));
            }
        }
        return levels.size();
    }

    private static int windowsWithAtLeastLevels(ElevationField field, int window, int requiredLevels) {
        WorldBounds bounds = field.bounds();
        int varied = 0;
        for (int startY = bounds.minY(); startY <= bounds.maxY(); startY += window) {
            for (int startX = bounds.minX(); startX <= bounds.maxX(); startX += window) {
                Set<Integer> levels = new HashSet<>();
                int endY = Math.min(bounds.maxY(), startY + window - 1);
                int endX = Math.min(bounds.maxX(), startX + window - 1);
                for (int y = startY; y <= endY && levels.size() < requiredLevels; y++) {
                    for (int x = startX; x <= endX && levels.size() < requiredLevels; x++) {
                        levels.add(field.elevationAt(x, y));
                    }
                }
                if (levels.size() >= requiredLevels) varied++;
            }
        }
        return varied;
    }

    private static int[] coarseCardinalExtrema(ElevationField field, int spacing) {
        WorldBounds bounds = field.bounds();
        int highs = 0;
        int lows = 0;
        for (int y = bounds.minY() + spacing; y <= bounds.maxY() - spacing; y += spacing) {
            for (int x = bounds.minX() + spacing; x <= bounds.maxX() - spacing; x += spacing) {
                long center = field.elevationSubunitsAt(x, y);
                long north = field.elevationSubunitsAt(x, y + spacing);
                long east = field.elevationSubunitsAt(x + spacing, y);
                long south = field.elevationSubunitsAt(x, y - spacing);
                long west = field.elevationSubunitsAt(x - spacing, y);
                if (center > north && center > east && center > south && center > west) highs++;
                if (center < north && center < east && center < south && center < west) lows++;
            }
        }
        return new int[] {highs, lows};
    }

    private static long maximumCardinalStep(ElevationField field) {
        WorldBounds bounds = field.bounds();
        long maximum = 0L;
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                long center = field.elevationSubunitsAt(x, y);
                if (x < bounds.maxX()) {
                    maximum = Math.max(maximum, Math.abs(field.elevationSubunitsAt(x + 1, y) - center));
                }
                if (y < bounds.maxY()) {
                    maximum = Math.max(maximum, Math.abs(field.elevationSubunitsAt(x, y + 1) - center));
                }
            }
        }
        return maximum;
    }

    private static int cardinalEdges(WorldBounds bounds) {
        int width = bounds.maxX() - bounds.minX() + 1;
        int height = bounds.maxY() - bounds.minY() + 1;
        return (width - 1) * height + (height - 1) * width;
    }

    private static int windowCount(WorldBounds bounds, int window) {
        int width = bounds.maxX() - bounds.minX() + 1;
        int height = bounds.maxY() - bounds.minY() + 1;
        return ((width + window - 1) / window) * ((height + window - 1) / window);
    }

    private static int sum(int[] values) {
        return values[0] + values[1];
    }
}
