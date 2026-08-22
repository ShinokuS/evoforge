package io.github.evoforge.simulation.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.continuum.field.ContinuumMaterializer;
import io.github.evoforge.simulation.world.continuum.field.ContinuumSampleWindow;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import io.github.evoforge.simulation.world.continuum.page.ContinuumPageLayout;
import io.github.evoforge.simulation.world.continuum.query.ContinuumLocalQueryBatch;
import io.github.evoforge.simulation.world.continuum.query.ContinuumLocalQueryRequest;
import io.github.evoforge.simulation.world.continuum.query.ContinuumLocalQueryService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

final class ContinuumLocalQueryScaleProfileTest {
    private static final long LOGICAL_SIDE = 1_000_000L;
    private static final int PAGE_SIDE = 64;
    private static final long PAGE_BYTES = (long) PAGE_SIDE * PAGE_SIDE * Double.BYTES;
    private static final long EXPECTED_FIELD_CALLS = 4L * PAGE_SIDE * PAGE_SIDE;
    private static final long MAX_BATCH_NANOS = Duration.ofSeconds(5).toNanos();

    @Test
    @Tag("scale-profile")
    void overlappingConsumersReuseTheSameExpensiveRegionalWork() {
        for (int consumers : List.of(1, 10, 100)) {
            AtomicLong fieldCalls = new AtomicLong();
            ContinuumWorldDomain domain = new ContinuumWorldDomain(LOGICAL_SIDE, LOGICAL_SIDE);
            ContinuumPageLayout layout = new ContinuumPageLayout(domain, PAGE_SIDE, PAGE_SIDE);
            ContinuumMaterializer materializer = new ContinuumMaterializer(domain, (x, y) -> {
                fieldCalls.incrementAndGet();
                return x * 0.25d + y * 0.5d;
            });
            ContinuumLocalQueryService service = new ContinuumLocalQueryService(
                    layout, materializer, 8, PAGE_BYTES * 8L, 0L);

            ContinuumLocalQueryBatch result = service.queryBatch(requests(consumers));
            var metrics = result.metrics();

            System.out.printf(
                    "continuum-local-query-profile logicalSide=%d consumers=%d totalRegionUses=%d uniqueRegions=%d reusedRegionUses=%d pageLoads=%d fieldCalls=%d residentPages=%d residentPayloadBytes=%d elapsedMs=%.3f%n",
                    LOGICAL_SIDE,
                    consumers,
                    metrics.totalRegionUses(),
                    metrics.uniqueRegions(),
                    metrics.reusedRegionUses(),
                    metrics.pageLoads(),
                    fieldCalls.get(),
                    metrics.residentPages(),
                    metrics.residentPayloadBytes(),
                    metrics.elapsedNanos() / 1_000_000d);

            assertEquals(consumers * 4, metrics.totalRegionUses());
            assertEquals(4, metrics.uniqueRegions());
            assertEquals(consumers * 4 - 4, metrics.reusedRegionUses());
            assertEquals(4L, metrics.pageLoads());
            assertEquals(EXPECTED_FIELD_CALLS, fieldCalls.get(),
                    "expensive source work must depend on unique regions, not consumers");
            assertEquals(4, metrics.residentPages());
            assertEquals(PAGE_BYTES * 4L, metrics.residentPayloadBytes());
            assertTrue(metrics.elapsedNanos() < MAX_BATCH_NANOS,
                    "local-query batch exceeded generous 5s regression gate");
        }
    }

    private static List<ContinuumLocalQueryRequest> requests(int count) {
        List<ContinuumLocalQueryRequest> result = new ArrayList<>(count);
        ContinuumSampleWindow sameWindow = new ContinuumSampleWindow(48L, 48L, 32, 32, 1L);
        for (int i = 0; i < count; i++) {
            result.add(new ContinuumLocalQueryRequest("consumer-" + i, sameWindow, 0L));
        }
        return List.copyOf(result);
    }
}
