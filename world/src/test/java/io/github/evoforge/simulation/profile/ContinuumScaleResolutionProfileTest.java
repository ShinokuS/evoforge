package io.github.evoforge.simulation.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.continuum.field.ContinuumMaterializer;
import io.github.evoforge.simulation.world.continuum.model.ContinuumResolution;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import io.github.evoforge.simulation.world.continuum.page.ContinuumPageKey;
import io.github.evoforge.simulation.world.continuum.page.ContinuumPageLayout;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

final class ContinuumScaleResolutionProfileTest {
    private static final long LOGICAL_SIDE = 1_000_000L;
    private static final int PAGE_SIDE = 256;
    private static final long EXPECTED_SAMPLES = (long) PAGE_SIDE * PAGE_SIDE;
    private static final long MAX_MATERIALIZE_NANOS = Duration.ofSeconds(5).toNanos();

    @Test
    @Tag("scale-profile")
    void directCoarseQueriesKeepBoundedSampleWork() {
        ContinuumWorldDomain domain = new ContinuumWorldDomain(LOGICAL_SIDE, LOGICAL_SIDE);

        for (int level : List.of(0, 5, 10)) {
            AtomicLong calls = new AtomicLong();
            ContinuumMaterializer materializer = new ContinuumMaterializer(domain, (x, y) -> {
                calls.incrementAndGet();
                return x * 0.25d + y * 0.5d;
            });
            ContinuumPageLayout layout = new ContinuumPageLayout(
                    domain,
                    PAGE_SIDE,
                    PAGE_SIDE,
                    new ContinuumResolution(level));

            long started = System.nanoTime();
            var page = materializer.materialize(layout.windowFor(new ContinuumPageKey(0L, 0L)));
            long elapsed = System.nanoTime() - started;
            long footprintArea = Math.multiplyExact(layout.pageWorldSpanX(), layout.pageWorldSpanY());

            assertEquals(EXPECTED_SAMPLES, calls.get(), "sample work must follow requested lattice, level=" + level);
            assertEquals(EXPECTED_SAMPLES * Double.BYTES, layout.payloadBytesFor(new ContinuumPageKey(0L, 0L)));
            assertEquals(layout.sampleStep(), page.window().step());
            assertTrue(elapsed < MAX_MATERIALIZE_NANOS, "materialization exceeded generous 5s gate, level=" + level);

            System.out.println("continuum-resolution-profile"
                    + " logicalSide=" + LOGICAL_SIDE
                    + " level=" + level
                    + " step=" + layout.sampleStep()
                    + " pageSamples=" + PAGE_SIDE + "x" + PAGE_SIDE
                    + " worldSpan=" + layout.pageWorldSpanX() + "x" + layout.pageWorldSpanY()
                    + " coveredWorldArea=" + footprintArea
                    + " fieldCalls=" + calls.get()
                    + " payloadBytes=" + layout.payloadBytesFor(new ContinuumPageKey(0L, 0L))
                    + " materializeMs=" + elapsed / 1_000_000L);
        }
    }
}
