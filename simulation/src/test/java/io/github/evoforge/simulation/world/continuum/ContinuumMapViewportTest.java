package io.github.evoforge.simulation.world.continuum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.continuum.map.ContinuumMapTile;
import io.github.evoforge.simulation.world.continuum.map.ContinuumMapTileKey;
import io.github.evoforge.simulation.world.continuum.map.ContinuumMapTileService;
import io.github.evoforge.simulation.world.continuum.map.ContinuumMapViewport;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;

final class ContinuumMapViewportTest {

    @Test
    void zoomKeepsTheWorldPointUnderCursorStableAndChangesLod() {
        ContinuumMapViewport viewport = new ContinuumMapViewport(1_000_000L, 1_000_000L, 128, 13, 1, 1600, 900);
        double cursorX = 1175d;
        double cursorY = 330d;
        double beforeX = viewport.worldXAtScreen(cursorX);
        double beforeY = viewport.worldYAtScreen(cursorY);
        int beforeLevel = viewport.desiredLevel();

        viewport.zoomAt(8d, cursorX, cursorY);

        assertEquals(beforeX, viewport.worldXAtScreen(cursorX), 1e-6);
        assertEquals(beforeY, viewport.worldYAtScreen(cursorY), 1e-6);
        assertTrue(viewport.desiredLevel() < beforeLevel, "zooming in requests finer map data");
    }

    @Test
    void pendingDetailNeverCreatesBlankVisibleTilesBecauseRootFallbackExists() {
        QueueExecutor executor = new QueueExecutor();
        ContinuumMapTileService service = new ContinuumMapTileService(
                ContinuumMapViewportTest::tile,
                executor,
                13,
                128,
                96,
                2);
        ContinuumMapViewport viewport = new ContinuumMapViewport(1_000_000L, 1_000_000L, 128, 13, 1, 1600, 900);

        ContinuumMapViewport.Frame frame = viewport.requestFrame(service);

        assertEquals(frame.visibleTileCount(), frame.tiles().size());
        assertEquals(frame.visibleTileCount(), frame.fallbackCount());
        assertEquals(0, frame.exactReadyCount());
    }

    @Test
    void partiallyReadyVisibleTilesDoNotCreateSquareMixedLodFrames() {
        QueueExecutor executor = new QueueExecutor();
        ContinuumMapTileService service = new ContinuumMapTileService(
                ContinuumMapViewportTest::tile,
                executor,
                13,
                128,
                96,
                2);
        ContinuumMapViewport viewport = new ContinuumMapViewport(1_000_000L, 1_000_000L, 128, 13, 1, 1600, 900);
        ContinuumMapViewport.Frame initial = viewport.requestFrame(service);
        assertTrue(initial.visibleTileCount() > 1);

        executor.runOne();
        ContinuumMapViewport.Frame partial = viewport.requestFrame(service);

        assertEquals(0, partial.exactReadyCount(), "one ready fine tile must not be promoted on its own");
        assertEquals(partial.visibleTileCount(), partial.fallbackCount());
        assertEquals(
                1L,
                partial.tiles().stream().map(ContinuumMapViewport.DisplayTile::fallbackDepth).distinct().count(),
                "all visible targets must use one common representation depth");
    }

    @Test
    void settledViewPrewarmsTheNextFinerZoomLevel() {
        ContinuumMapTileService service = new ContinuumMapTileService(
                ContinuumMapViewportTest::tile,
                Runnable::run,
                13,
                256,
                96,
                4);
        ContinuumMapViewport viewport = new ContinuumMapViewport(1_000_000L, 1_000_000L, 128, 13, 1, 1600, 900);
        ContinuumMapViewport.Frame settled = viewport.requestFrame(service);
        int originalLevel = settled.desiredLevel();

        int guard = 0;
        while (viewport.desiredLevel() == originalLevel && guard++ < 12) {
            viewport.zoomAt(1.22d, 800d, 450d);
        }
        assertTrue(viewport.desiredLevel() < originalLevel, "test must cross to the next finer LOD");

        ContinuumMapViewport.Frame afterZoom = viewport.requestFrame(service);
        assertEquals(0, afterZoom.fallbackCount(), "adjacent LOD prefetch should make a normal next-level zoom immediately detailed");
    }

    @Test
    void panningByLessThanOneTileReusesMostAlreadyReadyTiles() {
        ContinuumMapTileService service = new ContinuumMapTileService(
                ContinuumMapViewportTest::tile,
                Runnable::run,
                13,
                256,
                96,
                2);
        ContinuumMapViewport viewport = new ContinuumMapViewport(1_000_000L, 1_000_000L, 128, 13, 1, 1600, 900);
        ContinuumMapViewport.Frame first = viewport.requestFrame(service);
        long generatedAfterFirst = service.metrics().generatedTiles();

        viewport.panPixels(-80d, 0d);
        ContinuumMapViewport.Frame second = viewport.requestFrame(service);
        long newlyGenerated = service.metrics().generatedTiles() - generatedAfterFirst;

        assertTrue(newlyGenerated < second.requestedWithPrefetch(), "overlapping pan should reuse the previous working set");
        assertEquals(second.visibleTileCount(), second.exactReadyCount());
        assertTrue(first.visibleTileCount() > 0);
    }

    @Test
    void visualCameraMovementDoesNotChangeSourceRevision() {
        ContinuumMapViewport viewport = new ContinuumMapViewport(1_000_000L, 1_000_000L, 128, 13, 1, 1600, 900);
        viewport.setSourceRevision(77L);
        viewport.panPixels(500d, -200d);
        viewport.zoomAt(2d, 800d, 450d);
        assertEquals(77L, viewport.sourceRevision());
    }

    private static ContinuumMapTile tile(ContinuumMapTileKey key) {
        return new ContinuumMapTile(key, 2, new byte[] {1, 2, 3, 4});
    }

    private static final class QueueExecutor implements Executor {
        private final Queue<Runnable> queue = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            queue.add(command);
        }

        void runOne() {
            Runnable next = queue.poll();
            if (next == null) throw new IllegalStateException("no queued work");
            next.run();
        }
    }
}
