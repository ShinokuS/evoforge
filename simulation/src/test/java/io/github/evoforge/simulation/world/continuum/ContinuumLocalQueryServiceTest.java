package io.github.evoforge.simulation.world.continuum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.continuum.field.ContinuumMaterializer;
import io.github.evoforge.simulation.world.continuum.field.ContinuumSampleWindow;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarField;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import io.github.evoforge.simulation.world.continuum.page.ContinuumPageLayout;
import io.github.evoforge.simulation.world.continuum.query.ContinuumLocalQueryBatch;
import io.github.evoforge.simulation.world.continuum.query.ContinuumLocalQueryRequest;
import io.github.evoforge.simulation.world.continuum.query.ContinuumLocalQueryService;
import io.github.evoforge.simulation.world.continuum.query.ContinuumLocalScalarView;
import io.github.evoforge.simulation.world.continuum.query.StaleContinuumQueryException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

final class ContinuumLocalQueryServiceTest {
    private static final int PAGE_SIDE = 64;
    private static final long PAGE_BYTES = (long) PAGE_SIDE * PAGE_SIDE * Double.BYTES;

    @Test
    void tenOverlappingConsumersComputeFourSharedRegionsOnce() {
        AtomicLong fieldCalls = new AtomicLong();
        ContinuumLocalQueryService service = service(countingField(fieldCalls));

        ContinuumLocalQueryBatch batch = service.queryBatch(overlappingRequests(10, 0L));

        assertEquals(10, batch.metrics().consumerRequests());
        assertEquals(40, batch.metrics().totalRegionUses());
        assertEquals(4, batch.metrics().uniqueRegions());
        assertEquals(36, batch.metrics().reusedRegionUses());
        assertEquals(4L, batch.metrics().pageLoads());
        assertEquals(4L * PAGE_SIDE * PAGE_SIDE, fieldCalls.get());
        assertEquals(10, batch.views().size());

        ContinuumLocalScalarView first = batch.views().getFirst();
        assertEquals(48L * 0.25d + 48L * 0.5d, first.sample(0, 0));
        assertEquals(79L * 0.25d + 79L * 0.5d, first.sample(31, 31));
        assertThrows(IndexOutOfBoundsException.class, () -> first.sample(32, 0));
    }

    @Test
    void oneHundredOverlappingConsumersStillComputeOnlyFourSharedRegions() {
        AtomicLong fieldCalls = new AtomicLong();
        ContinuumLocalQueryService service = service(countingField(fieldCalls));

        ContinuumLocalQueryBatch batch = service.queryBatch(overlappingRequests(100, 0L));

        assertEquals(100, batch.metrics().consumerRequests());
        assertEquals(400, batch.metrics().totalRegionUses());
        assertEquals(4, batch.metrics().uniqueRegions());
        assertEquals(396, batch.metrics().reusedRegionUses());
        assertEquals(4L, batch.metrics().pageLoads());
        assertEquals(4L * PAGE_SIDE * PAGE_SIDE, fieldCalls.get());
        assertTrue(batch.metrics().residentPayloadBytes() <= PAGE_BYTES * 8L);
    }

    @Test
    void unrelatedConsumersRequireSeparateRegions() {
        ContinuumLocalQueryService service = service(analyticField());
        List<ContinuumLocalQueryRequest> requests = List.of(
                request("near", 8L, 8L, 16, 16, 0L),
                request("far", 10_008L, 10_008L, 16, 16, 0L));

        ContinuumLocalQueryBatch batch = service.queryBatch(requests);

        assertEquals(2, batch.metrics().uniqueRegions());
        assertEquals(0, batch.metrics().reusedRegionUses());
    }

    @Test
    void oldRevisionIsRejectedAndNewRevisionGetsFreshRegionalRepresentation() {
        AtomicLong fieldCalls = new AtomicLong();
        ContinuumLocalQueryService service = service(countingField(fieldCalls));
        ContinuumLocalQueryRequest old = request("observer", 8L, 8L, 16, 16, 0L);

        service.queryBatch(List.of(old));
        long firstRevisionCalls = fieldCalls.get();
        service.advanceRevision(1L);

        assertThrows(StaleContinuumQueryException.class,
                () -> service.queryBatch(List.of(old)));

        ContinuumLocalQueryBatch current = service.queryBatch(List.of(
                request("observer", 8L, 8L, 16, 16, 1L)));
        assertEquals(1L, current.revision());
        assertEquals(firstRevisionCalls * 2L, fieldCalls.get());
    }

    @Test
    @Timeout(5)
    void simultaneousRequestsForSameMissingRegionShareOneInFlightLoad() throws Exception {
        AtomicLong fieldCalls = new AtomicLong();
        CountDownLatch firstSampleStarted = new CountDownLatch(1);
        CountDownLatch allowMaterialization = new CountDownLatch(1);
        ContinuumScalarField slowField = (x, y) -> {
            long call = fieldCalls.incrementAndGet();
            if (call == 1L) {
                firstSampleStarted.countDown();
                try {
                    if (!allowMaterialization.await(2, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("test materialization release timed out");
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(interrupted);
                }
            }
            return x + y;
        };
        ContinuumLocalQueryService service = service(slowField);
        List<ContinuumLocalQueryRequest> oneRequest = List.of(
                request("same", 8L, 8L, 16, 16, 0L));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ContinuumLocalQueryBatch> first = executor.submit(() -> service.queryBatch(oneRequest));
            assertTrue(firstSampleStarted.await(2, TimeUnit.SECONDS));
            Future<ContinuumLocalQueryBatch> second = executor.submit(() -> service.queryBatch(oneRequest));
            Thread.sleep(Duration.ofMillis(50));
            allowMaterialization.countDown();

            first.get(2, TimeUnit.SECONDS);
            second.get(2, TimeUnit.SECONDS);
        } finally {
            allowMaterialization.countDown();
            executor.shutdownNow();
        }

        assertEquals(1L, service.cacheMetrics().loads());
        assertTrue(service.cacheMetrics().sharedWaits() >= 1L);
        assertEquals((long) PAGE_SIDE * PAGE_SIDE, fieldCalls.get());
    }

    @Test
    void requestOrderDoesNotChangeConsumerResults() {
        List<ContinuumLocalQueryRequest> original = overlappingRequests(10, 0L);
        List<ContinuumLocalQueryRequest> reversed = new ArrayList<>(original);
        Collections.reverse(reversed);

        ContinuumLocalQueryBatch a = service(analyticField()).queryBatch(original);
        ContinuumLocalQueryBatch b = service(analyticField()).queryBatch(reversed);

        Map<String, ContinuumLocalScalarView> byConsumerA = a.views().stream()
                .collect(Collectors.toMap(ContinuumLocalScalarView::consumerId, Function.identity()));
        Map<String, ContinuumLocalScalarView> byConsumerB = b.views().stream()
                .collect(Collectors.toMap(ContinuumLocalScalarView::consumerId, Function.identity()));

        for (String consumer : byConsumerA.keySet()) {
            assertEquals(byConsumerA.get(consumer).sample(7, 11), byConsumerB.get(consumer).sample(7, 11));
        }
    }

    @Test
    void queryLayerWorksWithAReplacementFieldImplementation() {
        ContinuumLocalQueryRequest request = request("consumer", 8L, 8L, 4, 4, 0L);

        double first = service((x, y) -> 1.0d).queryBatch(List.of(request)).views().getFirst().sample(0, 0);
        double second = service((x, y) -> 9.0d).queryBatch(List.of(request)).views().getFirst().sample(0, 0);

        assertEquals(1.0d, first);
        assertEquals(9.0d, second);
    }

    private static ContinuumLocalQueryService service(ContinuumScalarField field) {
        ContinuumWorldDomain domain = new ContinuumWorldDomain(1_000_000L, 1_000_000L);
        ContinuumPageLayout layout = new ContinuumPageLayout(domain, PAGE_SIDE, PAGE_SIDE);
        ContinuumMaterializer materializer = new ContinuumMaterializer(domain, field);
        return new ContinuumLocalQueryService(layout, materializer, 8, PAGE_BYTES * 8L, 0L);
    }

    private static List<ContinuumLocalQueryRequest> overlappingRequests(int count, long revision) {
        List<ContinuumLocalQueryRequest> requests = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            requests.add(request("consumer-" + i, 48L, 48L, 32, 32, revision));
        }
        return List.copyOf(requests);
    }

    private static ContinuumLocalQueryRequest request(
            String id, long minX, long minY, int width, int height, long revision) {
        return new ContinuumLocalQueryRequest(
                id,
                new ContinuumSampleWindow(minX, minY, width, height, 1L),
                revision);
    }

    private static ContinuumScalarField countingField(AtomicLong calls) {
        return (x, y) -> {
            calls.incrementAndGet();
            return x * 0.25d + y * 0.5d;
        };
    }

    private static ContinuumScalarField analyticField() {
        return (x, y) -> x * 0.25d + y * 0.5d;
    }
}
