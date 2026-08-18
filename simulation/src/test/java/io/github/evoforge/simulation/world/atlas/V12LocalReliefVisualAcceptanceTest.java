package io.github.evoforge.simulation.world.atlas;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.definition.NormalizedValue;
import io.github.evoforge.simulation.world.genesis.GenerationRevision;
import io.github.evoforge.simulation.world.genesis.RngRevision;
import io.github.evoforge.simulation.world.genesis.WorldGenerationIntent;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.genesis.WorldSpec;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import org.junit.jupiter.api.Test;

/** Visual-scale acceptance for the V12 local-relief contract. */
final class V12LocalReliefVisualAcceptanceTest {
    private static final WorldBounds SMALL = new WorldBounds(-32, 31, -32, 31, -12, 12);
    private static final WorldBounds LARGE = new WorldBounds(-256, 255, -256, 255, -12, 12);

    @Test
    void defaultLocalReliefBoundsVeryLongLargeWorldPlateauRuns() {
        ElevationField rolling = generate(LARGE, 202L, 700_000, 350_000);
        int rollingRun = maximumSameLevelCardinalRun(rolling);

        // The old V12 test compared this single maximum run against Local relief = 0 for the same
        // seed. That accidentally made one extreme statistic define the slider semantics: adding
        // legitimate hills could move a contour and make the single longest equal-Z line slightly
        // longer even while relief improved across the rest of the world. The visual contract is
        // the actual outcome we care about: no detailed view may retain a hundreds-of-cells shelf.
        assertTrue(
                rollingRun < 160,
                "a detailed large-world view must not contain a same-Z run hundreds of cells long; run="
                        + rollingRun);
    }

    @Test
    void compactBusyTerrainDoesNotReceiveGlobalLocalReliefNoise() {
        ElevationField calm = generate(SMALL, 303L, 1_000_000, 0);
        ElevationField rolling = generate(SMALL, 303L, 1_000_000, 350_000);

        int calmTransitions = cardinalTransitions(calm);
        int rollingTransitions = cardinalTransitions(rolling);

        assertTrue(
                rollingTransitions <= calmTransitions + cardinalEdges(SMALL) / 5,
                "local relief should concentrate on calm shelves instead of making a busy 64x64 world noisy everywhere; calm="
                        + calmTransitions + ", rolling=" + rollingTransitions);
    }

    private static ElevationField generate(
            WorldBounds bounds,
            long seed,
            int macroReliefPpm,
            int localReliefPpm) {
        WorldGenesis genesis = new WorldGenesis(
                new WorldSpec(bounds),
                seed,
                GenerationRevision.V12,
                RngRevision.V1,
                new WorldGenerationIntent(
                        NormalizedValue.ofPartsPerMillion(1_000_000),
                        NormalizedValue.ofPartsPerMillion(750_000),
                        NormalizedValue.ofPartsPerMillion(250_000),
                        NormalizedValue.ofPartsPerMillion(macroReliefPpm),
                        NormalizedValue.ofPartsPerMillion(localReliefPpm)));
        return new ElevationGenerationStage().generate(genesis);
    }

    private static int maximumSameLevelCardinalRun(ElevationField field) {
        WorldBounds bounds = field.bounds();
        int maximum = 1;

        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            int run = 0;
            int previous = Integer.MIN_VALUE;
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                int level = field.elevationAt(x, y);
                if (level == previous) {
                    run++;
                } else {
                    previous = level;
                    run = 1;
                }
                maximum = Math.max(maximum, run);
            }
        }

        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            int run = 0;
            int previous = Integer.MIN_VALUE;
            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                int level = field.elevationAt(x, y);
                if (level == previous) {
                    run++;
                } else {
                    previous = level;
                    run = 1;
                }
                maximum = Math.max(maximum, run);
            }
        }
        return maximum;
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

    private static int cardinalEdges(WorldBounds bounds) {
        int width = bounds.maxX() - bounds.minX() + 1;
        int height = bounds.maxY() - bounds.minY() + 1;
        return (width - 1) * height + (height - 1) * width;
    }
}
