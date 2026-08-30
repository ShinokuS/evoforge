package io.github.evoforge.simulation.world.terrain.genesis;

import io.github.evoforge.simulation.world.continuum.field.ContinuumSampleWindow;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPage;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import io.github.evoforge.simulation.world.terrain.definition.V15TerrainDefinition;
import java.util.Locale;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("scale-profile")
final class V14ContinuumBaseScaleProfileTest {
    private static final long SEED = -4_774_846_722_868_265_927L;
    private static final int WINDOW_SIDE = 32;

    @Test
    void fixedBudgetBasePreparationDoesNotFollowLogicalWorldArea() {
        String previous = System.getProperty(V15GenerationProfiler.ENABLE_PROPERTY);
        System.setProperty(V15GenerationProfiler.ENABLE_PROPERTY, "true");
        try {
            Profile p500 = profile(500);
            Profile p1000 = profile(1_000);
            Profile p10000 = profile(10_000);

            // Structural/timing sanity rather than a brittle microbenchmark: growing logical area by
            // 400x must not make fixed-budget preparation/window execution grow by anything similar.
            double slowestPrepare = Math.max(p500.prepareMs(), Math.max(p1000.prepareMs(), p10000.prepareMs()));
            double fastestPrepare = Math.max(1.0, Math.min(p500.prepareMs(), Math.min(p1000.prepareMs(), p10000.prepareMs())));
            if (slowestPrepare / fastestPrepare > 8.0) {
                throw new AssertionError("Continuum base prepare scaled too strongly with world area: "
                        + p500 + " / " + p1000 + " / " + p10000);
            }
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
        V14ContinuumBaseTerrainPlan plan = V14ContinuumBaseTerrainPlan.prepareContinuum(
                domain,
                SEED,
                V15TerrainDefinition.balanced(),
                12);
        long prepared = System.nanoTime();

        long min = side / 2L - WINDOW_SIDE / 2L;
        ContinuumScalarPage first = plan.elevationPages().materialize(
                new ContinuumSampleWindow(min, min, WINDOW_SIDE, WINDOW_SIDE, 1L));
        long firstDone = System.nanoTime();
        ContinuumScalarPage overlap = plan.elevationPages().materialize(
                new ContinuumSampleWindow(min + 8L, min + 8L, WINDOW_SIDE, WINDOW_SIDE, 1L));
        long finished = System.nanoTime();

        long checksum = checksum(first) ^ Long.rotateLeft(checksum(overlap), 17);
        Profile result = new Profile(
                side,
                (prepared - started) / 1_000_000d,
                (firstDone - prepared) / 1_000_000d,
                (finished - firstDone) / 1_000_000d,
                checksum);
        System.out.printf(
                Locale.ROOT,
                "v14-continuum-base-profile side=%d logicalCells=%d prepareMs=%.3f firstWindowMs=%.3f overlapWindowMs=%.3f checksum=%016x sampledRank=%s%n",
                side,
                (long) side * side,
                result.prepareMs(),
                result.firstWindowMs(),
                result.overlapWindowMs(),
                result.checksum(),
                plan.landRank().usesSampledRank());
        return result;
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
            long checksum) {}
}
