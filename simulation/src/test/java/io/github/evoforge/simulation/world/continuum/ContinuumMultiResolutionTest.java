package io.github.evoforge.simulation.world.continuum;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.continuum.field.ContinuumMaterializer;
import io.github.evoforge.simulation.world.continuum.field.ContinuumSampleWindow;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarField;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPage;
import io.github.evoforge.simulation.world.continuum.model.ContinuumResolution;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import io.github.evoforge.simulation.world.continuum.page.ContinuumPageKey;
import io.github.evoforge.simulation.world.continuum.page.ContinuumPageLayout;
import io.github.evoforge.simulation.world.continuum.page.ContinuumScalarPageCache;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

final class ContinuumMultiResolutionTest {

    @Test
    void coarseSamplesAreTheSameWorldAtSharedCoordinates() {
        ContinuumWorldDomain domain = new ContinuumWorldDomain(1_000_000L, 1_000_000L);
        ContinuumScalarField field = (x, y) -> x * 0.25d + y * 0.5d;
        ContinuumMaterializer materializer = new ContinuumMaterializer(domain, field);
        ContinuumPageLayout coarseLayout = new ContinuumPageLayout(
                domain,
                16,
                16,
                new ContinuumResolution(6));
        ContinuumScalarPage coarse = materializer.materialize(coarseLayout.windowFor(new ContinuumPageKey(3L, 4L)));

        int[][] probes = {{0, 0}, {3, 7}, {15, 15}};
        for (int[] probe : probes) {
            int sampleX = probe[0];
            int sampleY = probe[1];
            long worldX = coarse.window().xAt(sampleX);
            long worldY = coarse.window().yAt(sampleY);
            ContinuumScalarPage exact = materializer.materialize(
                    new ContinuumSampleWindow(worldX, worldY, 1, 1, 1L));
            assertEquals(exact.sample(0, 0), coarse.sample(sampleX, sampleY));
            assertEquals(0L, worldX % coarseLayout.sampleStep());
            assertEquals(0L, worldY % coarseLayout.sampleStep());
        }
    }

    @Test
    void coarsePageSamplesRequestedLatticeInsteadOfCoveredExactArea() {
        ContinuumWorldDomain domain = new ContinuumWorldDomain(1_000_000L, 1_000_000L);
        AtomicLong calls = new AtomicLong();
        ContinuumMaterializer materializer = new ContinuumMaterializer(domain, (x, y) -> {
            calls.incrementAndGet();
            return x + y;
        });
        ContinuumPageLayout layout = new ContinuumPageLayout(
                domain,
                256,
                256,
                new ContinuumResolution(10));

        ContinuumScalarPage page = materializer.materialize(layout.windowFor(new ContinuumPageKey(0L, 0L)));

        assertEquals(1_024L, page.window().step());
        assertEquals(256, page.window().width());
        assertEquals(256, page.window().height());
        assertEquals(65_536L, calls.get());
        assertEquals(262_144L, layout.pageWorldSpanX());
        assertTrue(layout.pageWorldSpanX() * layout.pageWorldSpanY() > calls.get() * 1_000_000L);
    }

    @Test
    void unrelatedFineQueriesDoNotChangeCoarseResult() {
        ContinuumWorldDomain domain = new ContinuumWorldDomain(100_000L, 100_000L);
        ContinuumScalarField field = (x, y) -> (x * 31L + y * 17L) * 0.001d;
        ContinuumMaterializer materializer = new ContinuumMaterializer(domain, field);
        ContinuumPageLayout coarseLayout = new ContinuumPageLayout(
                domain,
                32,
                32,
                new ContinuumResolution(5));
        ContinuumSampleWindow coarseWindow = coarseLayout.windowFor(new ContinuumPageKey(2L, 2L));

        double[] before = materializer.materialize(coarseWindow).copySamples();
        materializer.materialize(new ContinuumSampleWindow(123L, 456L, 48, 64, 3L));
        double[] after = materializer.materialize(coarseWindow).copySamples();

        assertArrayEquals(before, after);
    }

    @Test
    void coarseCacheEvictionIsSemanticallyInvisible() {
        ContinuumWorldDomain domain = new ContinuumWorldDomain(1_000_000L, 1_000_000L);
        ContinuumMaterializer materializer = new ContinuumMaterializer(domain, (x, y) -> x * 0.125d - y * 0.25d);
        ContinuumPageLayout layout = new ContinuumPageLayout(
                domain,
                64,
                64,
                new ContinuumResolution(8));
        long pageBytes = 64L * 64L * Double.BYTES;
        ContinuumScalarPageCache cache = new ContinuumScalarPageCache(layout, materializer, 2, pageBytes * 2L);
        ContinuumPageKey firstKey = new ContinuumPageKey(0L, 0L);

        double[] first = cache.page(firstKey).copySamples();
        cache.page(new ContinuumPageKey(1L, 0L));
        cache.page(new ContinuumPageKey(2L, 0L));
        assertFalse(cache.isResident(firstKey));

        double[] reloaded = cache.page(firstKey).copySamples();
        assertArrayEquals(first, reloaded);
        assertTrue(cache.metrics().evictions() >= 2L);
    }

    @Test
    void pagePayloadStaysBoundedWhileWorldSpanExpandsWithResolution() {
        ContinuumWorldDomain domain = new ContinuumWorldDomain(1_000_000L, 1_000_000L);
        ContinuumPageKey key = new ContinuumPageKey(0L, 0L);
        ContinuumPageLayout exact = new ContinuumPageLayout(domain, 256, 256);
        ContinuumPageLayout coarse = new ContinuumPageLayout(
                domain,
                256,
                256,
                new ContinuumResolution(10));

        assertEquals(exact.payloadBytesFor(key), coarse.payloadBytesFor(key));
        assertEquals(exact.pageWorldSpanX() * 1_024L, coarse.pageWorldSpanX());
        assertTrue(coarse.pageCountX() < exact.pageCountX());
        assertTrue(coarse.pageCountY() < exact.pageCountY());
    }
}
