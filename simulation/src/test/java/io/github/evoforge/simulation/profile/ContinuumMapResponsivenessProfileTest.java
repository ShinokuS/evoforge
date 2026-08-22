package io.github.evoforge.simulation.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.continuum.map.ContinuumMapTileService;
import io.github.evoforge.simulation.world.continuum.map.ContinuumMapViewport;
import io.github.evoforge.simulation.world.continuum.map.ContinuumScalarMapTileGenerator;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

final class ContinuumMapResponsivenessProfileTest {
    private static final long LOGICAL_SIDE = 1_000_000L;
    private static final int TILE_SIDE = 128;
    private static final int MAX_READY = 384;
    private static final int MAX_OUTSTANDING = 192;
    private static final int WORKERS = 4;
    private static final long MAX_SETTLE_NANOS = Duration.ofSeconds(1).toNanos();

    @Test
    @Tag("scale-profile")
    void settledViewMakesNextNormalZoomImmediateAndPanFallbackShortLived() throws Exception {
        ContinuumWorldDomain domain = new ContinuumWorldDomain(LOGICAL_SIDE, LOGICAL_SIDE);
        ContinuumScalarMapTileGenerator generator = new ContinuumScalarMapTileGenerator(
                domain,
                ContinuumMapResponsivenessProfileTest::syntheticField,
                TILE_SIDE);
        ExecutorService executor = Executors.newFixedThreadPool(WORKERS);
        try {
            ContinuumMapTileService service = new ContinuumMapTileService(
                    generator,
                    executor,
                    generator.maxLevel(),
                    MAX_READY,
                    MAX_OUTSTANDING,
                    WORKERS);
            ContinuumMapViewport viewport = new ContinuumMapViewport(
                    LOGICAL_SIDE,
                    LOGICAL_SIDE,
                    TILE_SIDE,
                    generator.maxLevel(),
                    1,
                    1600,
                    900);

            long initialStarted = System.nanoTime();
            ContinuumMapViewport.Frame settled = awaitSettled(viewport, service);
            long initialSettleNanos = System.nanoTime() - initialStarted;
            assertEquals(0, settled.fallbackCount());

            int beforeLevel = viewport.desiredLevel();
            ContinuumMapViewport.Frame afterZoom = settled;
            int wheelSteps = 0;
            while (viewport.desiredLevel() == beforeLevel && wheelSteps++ < 8) {
                viewport.zoomAt(1.22d, 800d, 450d);
                afterZoom = viewport.requestFrame(service);
            }
            assertTrue(viewport.desiredLevel() < beforeLevel, "normal wheel zoom must cross one LOD in this fixture");
            assertEquals(
                    0,
                    afterZoom.fallbackCount(),
                    "a settled view should have the next finer viewport ready before the LOD switch");

            viewport.panPixels(-700d, 240d);
            ContinuumMapViewport.Frame firstPanFrame = viewport.requestFrame(service);
            long panStarted = System.nanoTime();
            ContinuumMapViewport.Frame settledAfterPan = awaitSettled(viewport, service);
            long panSettleNanos = System.nanoTime() - panStarted;
            assertEquals(0, settledAfterPan.fallbackCount());
            assertTrue(initialSettleNanos < MAX_SETTLE_NANOS, "cold visible map took over one second to settle");
            assertTrue(panSettleNanos < MAX_SETTLE_NANOS, "panned visible map took over one second to settle");

            var metrics = service.metrics();
            assertTrue(metrics.residentTiles() <= MAX_READY + 1);
            assertTrue(metrics.pendingJobs() + metrics.runningJobs() <= MAX_OUTSTANDING);

            System.out.println("continuum-map-responsiveness-profile"
                    + " initialSettleMs=" + initialSettleNanos / 1_000_000d
                    + " wheelStepsToNextLod=" + wheelSteps
                    + " nextLodImmediateFallbacks=" + afterZoom.fallbackCount()
                    + " panFirstFrameFallbacks=" + firstPanFrame.fallbackCount()
                    + " panSettleMs=" + panSettleNanos / 1_000_000d
                    + " residentTiles=" + metrics.residentTiles()
                    + " residentPayloadBytes=" + metrics.residentPayloadBytes()
                    + " cancelledObsolete=" + metrics.cancelledObsoleteJobs()
                    + " promotedVisible=" + metrics.promotedVisibleJobs());
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static ContinuumMapViewport.Frame awaitSettled(
            ContinuumMapViewport viewport,
            ContinuumMapTileService service) throws InterruptedException {
        long deadline = System.nanoTime() + MAX_SETTLE_NANOS;
        ContinuumMapViewport.Frame frame = viewport.requestFrame(service);
        while ((frame.fallbackCount() != 0 || service.metrics().pendingJobs() != 0 || service.metrics().runningJobs() != 0)
                && System.nanoTime() < deadline) {
            Thread.sleep(1L);
            frame = viewport.requestFrame(service);
        }
        return frame;
    }

    private static double syntheticField(long x, long y) {
        double broad = Math.sin(x / 91_000d) * Math.cos(y / 73_000d);
        double medium = Math.sin((x + y) / 31_000d) * 0.55d;
        double fine = Math.cos(x / 9_000d - y / 12_000d) * 0.28d;
        double diagonal = Math.sin((x * 0.65d - y * 0.35d) / 17_000d) * 0.22d;
        return Math.max(0d, Math.min(1d, 0.5d + broad * 0.23d + medium * 0.18d + fine * 0.12d + diagonal * 0.08d));
    }
}
