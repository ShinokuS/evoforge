package io.github.evoforge.simulation.world.continuum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.continuum.map.ContinuumMapTile;
import io.github.evoforge.simulation.world.continuum.map.ContinuumMapTileGenerator;
import io.github.evoforge.simulation.world.continuum.map.ContinuumMapTileKey;
import io.github.evoforge.simulation.world.continuum.map.ContinuumMapTileService;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class ContinuumMapTileServiceTest {

    @Test
    void identicalConcurrentMissesShareOneGenerationJob() {
        QueueExecutor executor = new QueueExecutor();
        AtomicInteger generated = new AtomicInteger();
        ContinuumMapTileGenerator generator = key -> {
            generated.incrementAndGet();
            return tile(key);
        };
        ContinuumMapTileService service = new ContinuumMapTileService(generator, executor, 4, 8, 8, 1);
        assertEquals(1, generated.get(), "root fallback is primed once");

        ContinuumMapTileKey key = new ContinuumMapTileKey(1, 2, 3, 0L);
        var first = service.request(key);
        var second = service.request(key);

        assertSame(first, second);
        assertEquals(1, executor.size());
        assertFalse(first.isDone());

        executor.runNext();
        assertTrue(first.isDone());
        assertEquals(2, generated.get(), "one root + one requested tile");
        assertEquals(1L, service.metrics().singleFlightJoins());
    }

    @Test
    void visibleRequestJumpsAheadOfQueuedPrefetch() {
        QueueExecutor executor = new QueueExecutor();
        List<ContinuumMapTileKey> generated = new ArrayList<>();
        ContinuumMapTileService service = new ContinuumMapTileService(
                key -> {
                    generated.add(key);
                    return tile(key);
                },
                executor,
                5,
                16,
                8,
                1);

        ContinuumMapTileKey alreadyRunning = new ContinuumMapTileKey(1, 1, 0, 0L);
        ContinuumMapTileKey queuedPrefetch = new ContinuumMapTileKey(1, 2, 0, 0L);
        ContinuumMapTileKey visible = new ContinuumMapTileKey(1, 3, 0, 0L);
        service.requestPrefetch(alreadyRunning);
        service.requestPrefetch(queuedPrefetch);
        service.requestVisible(visible);

        executor.runNext();
        executor.runNext();

        assertEquals(visible, generated.get(2), "visible work must run immediately after the already-running job");
        assertEquals(0, service.metrics().prefetchPendingJobs());
        assertEquals(1, service.metrics().runningJobs(), "queued prefetch is submitted only after visible work");
        assertEquals(1, executor.size());
    }

    @Test
    void obsoleteQueuedPrefetchIsDiscardedWhenCameraDemandMoves() {
        QueueExecutor executor = new QueueExecutor();
        ContinuumMapTileService service = new ContinuumMapTileService(
                ContinuumMapTileServiceTest::tile,
                executor,
                5,
                16,
                8,
                1);

        ContinuumMapTileKey running = new ContinuumMapTileKey(1, 1, 0, 0L);
        ContinuumMapTileKey obsoleteA = new ContinuumMapTileKey(1, 2, 0, 0L);
        ContinuumMapTileKey obsoleteB = new ContinuumMapTileKey(1, 3, 0, 0L);
        ContinuumMapTileKey newVisible = new ContinuumMapTileKey(1, 9, 0, 0L);
        service.requestVisible(running);
        var oldA = service.requestPrefetch(obsoleteA);
        var oldB = service.requestPrefetch(obsoleteB);

        service.retainPendingDemand(Set.of(running, newVisible));
        service.requestVisible(newVisible);

        assertTrue(oldA.isCompletedExceptionally());
        assertTrue(oldB.isCompletedExceptionally());
        assertEquals(2L, service.metrics().cancelledObsoleteJobs());
        assertEquals(1, service.metrics().visiblePendingJobs());
        assertEquals(0, service.metrics().prefetchPendingJobs());
    }

    @Test
    void coarseRootFillsTheScreenWhileFineTileIsStillPending() {
        QueueExecutor executor = new QueueExecutor();
        ContinuumMapTileService service = new ContinuumMapTileService(
                ContinuumMapTileServiceTest::tile,
                executor,
                5,
                8,
                8,
                1);
        ContinuumMapTileKey fine = new ContinuumMapTileKey(1, 6, 4, 0L);

        service.request(fine);
        ContinuumMapTile fallback = service.bestAvailable(fine).orElseThrow();
        assertEquals(5, fallback.key().level());

        executor.runNext();
        ContinuumMapTile ready = service.bestAvailable(fine).orElseThrow();
        assertEquals(fine, ready.key());
    }

    @Test
    void readyAndPendingStorageStayBoundedDuringCameraChurn() {
        QueueExecutor executor = new QueueExecutor();
        ContinuumMapTileService service = new ContinuumMapTileService(
                ContinuumMapTileServiceTest::tile,
                executor,
                8,
                5,
                6,
                2);

        for (int i = 0; i < 100; i++) {
            service.request(new ContinuumMapTileKey(0, i, 0, 0L));
        }
        assertTrue(service.metrics().pendingJobs() + service.metrics().runningJobs() <= 6);

        executor.runAll();
        assertTrue(service.metrics().residentTiles() <= 6, "5 LRU tiles + one root fallback");
        assertTrue(service.metrics().droppedPendingJobs() > 0);
    }

    @Test
    void changingRevisionCannotReuseOldReadyTile() {
        ContinuumMapTileService service = new ContinuumMapTileService(
                ContinuumMapTileServiceTest::tile,
                Runnable::run,
                4,
                8,
                8,
                1);
        ContinuumMapTileKey oldKey = new ContinuumMapTileKey(1, 1, 1, 0L);
        service.request(oldKey).join();
        assertEquals(oldKey, service.bestAvailable(oldKey).orElseThrow().key());

        service.setRevision(1L);
        assertThrows(IllegalArgumentException.class, () -> service.bestAvailable(oldKey));
        ContinuumMapTileKey newKey = new ContinuumMapTileKey(1, 1, 1, 1L);
        assertEquals(1L, service.bestAvailable(newKey).orElseThrow().key().sourceRevision());
    }

    private static ContinuumMapTile tile(ContinuumMapTileKey key) {
        return new ContinuumMapTile(key, 2, new byte[] {1, 2, 3, 4});
    }

    private static final class QueueExecutor implements Executor {
        private final Queue<Runnable> queued = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            queued.add(command);
        }

        int size() {
            return queued.size();
        }

        void runNext() {
            queued.remove().run();
        }

        void runAll() {
            while (!queued.isEmpty()) runNext();
        }
    }
}
