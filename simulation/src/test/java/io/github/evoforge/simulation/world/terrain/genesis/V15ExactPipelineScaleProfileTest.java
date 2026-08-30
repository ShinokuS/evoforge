package io.github.evoforge.simulation.world.terrain.genesis;

import io.github.evoforge.simulation.world.continuum.field.ContinuumSampleWindow;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPage;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import io.github.evoforge.simulation.world.terrain.definition.V13MountainDefinition;
import io.github.evoforge.simulation.world.terrain.definition.V15TerrainDefinition;
import java.util.Locale;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("scale-profile")
final class V15ExactPipelineScaleProfileTest {
    private static final long SEED = -4_774_846_722_868_265_927L;

    @Test
    void profileAcceptedExactPipelineWithoutBlockingContinuumScaleChecks() {
        String previous = System.getProperty(V15GenerationProfiler.ENABLE_PROPERTY);
        System.setProperty(V15GenerationProfiler.ENABLE_PROPERTY, "true");
        try {
            profile(320);
            profile(500);
            if ("true".equalsIgnoreCase(System.getProperty("evoforge.v15.profile.exact1000", "false"))) {
                profile(1_000);
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

    private static void profile(int side) {
        V15GenerationProfiler.reset();
        ContinuumWorldDomain domain = new ContinuumWorldDomain(side, side);
        long started = System.nanoTime();
        V15ContinuumTerrainPlan plan = V15ContinuumTerrainPlan.prepare(
                domain,
                SEED,
                V15TerrainDefinition.balanced(),
                V13MountainDefinition.balanced(),
                -96,
                96);
        long afterPrepare = System.nanoTime();

        int windowSide = Math.min(32, side);
        long min = Math.max(0L, side / 2L - windowSide / 2L);
        ContinuumScalarPage page = plan.elevationPages().materialize(
                new ContinuumSampleWindow(min, min, windowSide, windowSide, 1L));
        long finished = System.nanoTime();

        long checksum = 0xcbf29ce484222325L;
        for (int y = 0; y < page.window().height(); y++) {
            for (int x = 0; x < page.window().width(); x++) {
                checksum ^= Double.doubleToRawLongBits(page.sample(x, y));
                checksum *= 0x100000001b3L;
            }
        }

        System.out.printf(
                Locale.ROOT,
                "v15-exact-pipeline-profile side=%d logicalCells=%d prepareMs=%.3f firstWindowMs=%.3f totalMs=%.3f checksum=%016x%n",
                side,
                (long) side * side,
                (afterPrepare - started) / 1_000_000d,
                (finished - afterPrepare) / 1_000_000d,
                (finished - started) / 1_000_000d,
                checksum);
    }
}
