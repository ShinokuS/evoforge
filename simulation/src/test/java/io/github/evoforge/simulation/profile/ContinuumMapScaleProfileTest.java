package io.github.evoforge.simulation.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.continuum.map.ContinuumMapTileService;
import io.github.evoforge.simulation.world.continuum.map.ContinuumMapViewport;
import io.github.evoforge.simulation.world.continuum.map.ContinuumScalarMapTileGenerator;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

final class ContinuumMapScaleProfileTest {
    private static final int TILE_SIDE = 128;
    private static final int MAX_READY = 256;
    private static final int MAX_OUTSTANDING = 96;
    private static final long MAX_PROFILE_NANOS = Duration.ofSeconds(5).toNanos();

    @Test
    @Tag("scale-profile")
    void viewportCostFollowsVisibleTilesNotLogicalWorldArea() {
        for (long logicalSide : List.of(1_000_000L, 100_000_000L, 1_000_000_000L)) {
            ContinuumWorldDomain domain = new ContinuumWorldDomain(logicalSide, logicalSide);
            ContinuumScalarMapTileGenerator generator = new ContinuumScalarMapTileGenerator(
                    domain,
                    (x, y) -> ((x * 31L + y * 17L) & 1023L) / 1023d,
                    TILE_SIDE);
            ContinuumMapTileService service = new ContinuumMapTileService(
                    generator,
                    Runnable::run,
                    generator.maxLevel(),
                    MAX_READY,
                    MAX_OUTSTANDING,
                    2);
            ContinuumMapViewport viewport = new ContinuumMapViewport(
                    logicalSide,
                    logicalSide,
                    TILE_SIDE,
                    generator.maxLevel(),
                    1,
                    1600,
                    900);

            long started = System.nanoTime();
            ContinuumMapViewport.Frame initial = viewport.requestFrame(service);
            for (int i = 0; i < 120; i++) {
                viewport.panPixels((i & 1) == 0 ? -64d : 48d, (i % 3) - 1d);
                if (i % 20 == 0) viewport.zoomAt(1.35d, 800d, 450d);
                if (i % 20 == 10) viewport.zoomAt(1d / 1.35d, 800d, 450d);
                viewport.requestFrame(service);
            }
            long elapsed = System.nanoTime() - started;
            var metrics = service.metrics();

            assertTrue(initial.visibleTileCount() <= 96, "visible tile count must be viewport-bounded");
            assertTrue(initial.requestedWithPrefetch() <= 128, "prefetch must remain a small ring around viewport");
            assertTrue(metrics.residentTiles() <= MAX_READY + 1, "CPU cache must remain bounded");
            assertTrue(metrics.residentPayloadBytes() <= (long) (MAX_READY + 1) * TILE_SIDE * TILE_SIDE);
            assertEquals(0, metrics.pendingJobs());
            assertEquals(0, metrics.runningJobs());
            assertTrue(elapsed < MAX_PROFILE_NANOS, "map profile exceeded generous 5s gate");

            System.out.println("continuum-map-profile"
                    + " logicalSide=" + logicalSide
                    + " desiredLevel=" + initial.desiredLevel()
                    + " visibleTiles=" + initial.visibleTileCount()
                    + " prefetchedTiles=" + initial.requestedWithPrefetch()
                    + " residentTiles=" + metrics.residentTiles()
                    + " residentPayloadBytes=" + metrics.residentPayloadBytes()
                    + " generatedTiles=" + metrics.generatedTiles()
                    + " readyHits=" + metrics.readyHits()
                    + " elapsedMs=" + elapsed / 1_000_000L);
        }
    }
}
