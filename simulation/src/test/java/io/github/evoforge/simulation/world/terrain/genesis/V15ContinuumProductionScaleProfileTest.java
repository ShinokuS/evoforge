package io.github.evoforge.simulation.world.terrain.genesis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.continuum.field.ContinuumSampleWindow;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPage;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import io.github.evoforge.simulation.world.terrain.definition.V13MountainDefinition;
import io.github.evoforge.simulation.world.terrain.definition.V15TerrainDefinition;
import java.util.Locale;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("scale-profile")
final class V15ContinuumProductionScaleProfileTest {
    private static final long SEED = -4_774_846_722_868_265_927L;
    private static final int WINDOW_SIDE = 32;

    @Test
    void productionWorkStaysBoundedFrom500Through10000() {
        String previous = System.getProperty(V15GenerationProfiler.ENABLE_PROPERTY);
        System.setProperty(V15GenerationProfiler.ENABLE_PROPERTY, "true");
        try {
            Profile p500 = profile(500);
            Profile p1000 = profile(1_000);
            Profile p10000 = profile(10_000);

            assertBoundedRatio("prepare", p500.prepareMs(), p1000.prepareMs(), p10000.prepareMs(), 8.0);
            assertBoundedRatio(
                    "first-window",
                    p500.firstWindowMs(),
                    p1000.firstWindowMs(),
                    p10000.firstWindowMs(),
                    8.0);
            assertBoundedRatio(
                    "overlap-window",
                    p500.overlapWindowMs(),
                    p1000.overlapWindowMs(),
                    p10000.overlapWindowMs(),
                    8.0);
        } finally {
            if (previous == null) {
                System.clearProperty(V15GenerationProfiler.ENABLE_PROPERTY);
            } else {
                System.setProperty(V15GenerationProfiler.ENABLE_PROPERTY, previous);
            }
            V15GenerationProfiler.reset();
        }
    }

    private static Profile profile(int side) {
        V15GenerationProfiler.reset();
        ContinuumWorldDomain domain = new ContinuumWorldDomain(side, side);
        long started = System.nanoTime();
        V15ContinuumProductionTerrainPlan plan = V15ContinuumProductionTerrainPlan.prepare(
                domain,
                SEED,
                V15TerrainDefinition.balanced(),
                V13MountainDefinition.balanced(),
                -96,
                96);
        long prepared = System.nanoTime();

        long min = side / 2L - WINDOW_SIDE / 2L;
        ContinuumSampleWindow firstWindow = new ContinuumSampleWindow(
                min, min, WINDOW_SIDE, WINDOW_SIDE, 1L);
        ContinuumScalarPage first = plan.elevationPages().materialize(firstWindow);
        long firstDone = System.nanoTime();

        ContinuumScalarPage overlap = plan.elevationPages().materialize(
                new ContinuumSampleWindow(min + 8L, min + 8L, WINDOW_SIDE, WINDOW_SIDE, 1L));
        long overlapDone = System.nanoTime();
        ContinuumScalarPage repeated = plan.elevationPages().materialize(firstWindow);
        long finished = System.nanoTime();
        assertEquals(checksum(first), checksum(repeated), "same production window must be deterministic");

        Profile result = new Profile(
                side,
                (prepared - started) / 1_000_000d,
                (firstDone - prepared) / 1_000_000d,
                (overlapDone - firstDone) / 1_000_000d,
                (finished - overlapDone) / 1_000_000d,
                checksum(first),
                checksum(overlap),
                plan.lakes().lakeBodyCount());
        System.out.printf(
                Locale.ROOT,
                "v15-continuum-production-profile side=%d logicalCells=%d prepareMs=%.3f firstWindowMs=%.3f overlapWindowMs=%.3f repeatWindowMs=%.3f firstChecksum=%016x overlapChecksum=%016x lakeBodies=%d sampledRank=%s%n",
                side,
                (long) side * side,
                result.prepareMs(),
                result.firstWindowMs(),
                result.overlapWindowMs(),
                result.repeatWindowMs(),
                result.firstChecksum(),
                result.overlapChecksum(),
                result.lakeBodies(),
                plan.continental().landRank().usesSampledRank());
        return result;
    }

    private static void assertBoundedRatio(
            String name,
            double first,
            double second,
            double third,
            double maximumRatio) {
        double fastest = Math.max(1.0, Math.min(first, Math.min(second, third)));
        double slowest = Math.max(first, Math.max(second, third));
        assertTrue(
                slowest / fastest <= maximumRatio,
                () -> name + " scaled too strongly with logical area: "
                        + first + "ms / " + second + "ms / " + third + "ms");
    }

    private static long checksum(ContinuumScalarPage page) {
        long checksum = 0xcbf29ce484222325L;
        for (int y = 0; y < page.window().height(); y++) {
            for (int x = 0; x < page.window().width(); x++) {
                checksum ^= Double.doubleToRawLongBits(page.sample(x, y));
                checksum *= 0x100000001b3L;
            }
        }
        return checksum;
    }

    private record Profile(
            int side,
            double prepareMs,
            double firstWindowMs,
            double overlapWindowMs,
            double repeatWindowMs,
            long firstChecksum,
            long overlapChecksum,
            int lakeBodies) {}
}
