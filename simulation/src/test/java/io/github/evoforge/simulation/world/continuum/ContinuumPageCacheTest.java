package io.github.evoforge.simulation.world.continuum;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertTimeout;

import io.github.evoforge.simulation.world.continuum.field.ContinuumMaterializer;
import io.github.evoforge.simulation.world.continuum.field.ContinuumSampleWindow;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarField;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPage;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import io.github.evoforge.simulation.world.continuum.page.ContinuumPageCacheMetrics;
import io.github.evoforge.simulation.world.continuum.page.ContinuumPageKey;
import io.github.evoforge.simulation.world.continuum.page.ContinuumPageLayout;
import io.github.evoforge.simulation.world.continuum.page.ContinuumScalarPageCache;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ContinuumPageCacheTest {
    @Test
    void pageLayoutUsesConfigurableTechnicalDimensionsAndClipsOnlyWorldEdge() {
        ContinuumPageLayout layout = new ContinuumPageLayout(new ContinuumWorldDomain(1_001L, 770L), 256, 256);

        assertEquals(4L, layout.pageCountX());
        assertEquals(4L, layout.pageCountY());
        assertEquals(new ContinuumPageKey(2L, 1L), layout.pageAt(700L, 300L));
        assertEquals(new ContinuumSampleWindow(768L, 768L, 233, 2, 1L),
                layout.windowFor(new ContinuumPageKey(3L, 3L)));
        assertThrows(IllegalArgumentException.class,
                () -> layout.windowFor(new ContinuumPageKey(4L, 0L)));
    }

    @Test
    void samePageTwiceIsAHitAndReturnsTheResidentMaterialization() {
        Fixture fixture = fixture(10_000L, 64, 4);
        ContinuumPageKey key = new ContinuumPageKey(3L, 2L);

        ContinuumScalarPage first = fixture.cache.page(key);
        ContinuumScalarPage second = fixture.cache.page(key);

        assertSame(first, second);
        assertEquals(new ContinuumPageCacheMetrics(1L, 1L, 1L, 0L, 1, pageBytes(64), 4, pageBytes(64) * 4L),
                fixture.cache.metrics());
    }

    @Test
    void leastRecentlyUsedPageIsEvictedByExplicitPageBudget() {
        Fixture fixture = fixture(10_000L, 4, 2);
        ContinuumPageKey a = new ContinuumPageKey(0L, 0L);
        ContinuumPageKey b = new ContinuumPageKey(1L, 0L);
        ContinuumPageKey c = new ContinuumPageKey(2L, 0L);

        fixture.cache.page(a);
        fixture.cache.page(b);
        fixture.cache.page(a);
        fixture.cache.page(c);

        assertTrue(fixture.cache.isResident(a));
        assertFalse(fixture.cache.isResident(b));
        assertTrue(fixture.cache.isResident(c));
        assertEquals(List.of(a, c), fixture.cache.residentKeys());
        assertEquals(1L, fixture.cache.metrics().evictions());
    }

    @Test
    void payloadByteBudgetCanEvictBeforePageCountBudget() {
        int pageSide = 8;
        ContinuumWorldDomain domain = new ContinuumWorldDomain(10_000L, 10_000L);
        ContinuumPageLayout layout = new ContinuumPageLayout(domain, pageSide, pageSide);
        ContinuumMaterializer materializer = new ContinuumMaterializer(domain, analyticField());
        long onePage = pageBytes(pageSide);
        ContinuumScalarPageCache cache = new ContinuumScalarPageCache(layout, materializer, 10, onePage * 2L);

        cache.page(new ContinuumPageKey(0L, 0L));
        cache.page(new ContinuumPageKey(1L, 0L));
        cache.page(new ContinuumPageKey(2L, 0L));

        assertEquals(2, cache.metrics().residentPages());
        assertEquals(onePage * 2L, cache.metrics().residentPayloadBytes());
        assertEquals(1L, cache.metrics().evictions());
    }

    @Test
    void evictionAndReloadAreSemanticallyInvisible() {
        Fixture fixture = fixture(10_000L, 16, 1);
        ContinuumPageKey firstKey = new ContinuumPageKey(5L, 7L);

        double[] first = fixture.cache.page(firstKey).copySamples();
        fixture.cache.page(new ContinuumPageKey(6L, 7L));
        double[] reloaded = fixture.cache.page(firstKey).copySamples();

        assertArrayEquals(first, reloaded);
        assertEquals(2L, fixture.cache.metrics().evictions());
        assertEquals(3L, fixture.cache.metrics().loads());
    }

    @Test
    void tiledAndUntiledMaterializationUseTheSameGlobalSamples() {
        int pageSide = 256;
        ContinuumWorldDomain domain = new ContinuumWorldDomain(10_000L, 10_000L);
        ContinuumScalarField field = analyticField();
        ContinuumMaterializer materializer = new ContinuumMaterializer(domain, field);
        ContinuumPageLayout layout = new ContinuumPageLayout(domain, pageSide, pageSide);
        ContinuumScalarPageCache cache = new ContinuumScalarPageCache(
                layout, materializer, 2, pageBytes(pageSide) * 2L);

        ContinuumScalarPage untiled = materializer.materialize(new ContinuumSampleWindow(0L, 0L, 512, 256, 1L));
        ContinuumScalarPage left = cache.page(new ContinuumPageKey(0L, 0L));
        ContinuumScalarPage right = cache.page(new ContinuumPageKey(1L, 0L));

        for (int y = 0; y < 256; y++) {
            for (int x = 0; x < 256; x++) {
                assertEquals(untiled.sample(x, y), left.sample(x, y));
                assertEquals(untiled.sample(x + 256, y), right.sample(x, y));
            }
        }
    }

    @Test
    void residentBudgetIsIndependentOfLogicalWorldAreaAt10k100kAnd1m() {
        int pageSide = 256;
        long expectedResidentBytes = pageBytes(pageSide) * 2L;

        for (long side : List.of(10_000L, 100_000L, 1_000_000L)) {
            Fixture fixture = fixture(side, pageSide, 2);
            fixture.cache.page(new ContinuumPageKey(0L, 0L));
            fixture.cache.page(new ContinuumPageKey(1L, 0L));
            fixture.cache.page(new ContinuumPageKey(2L, 0L));

            assertEquals(2, fixture.cache.metrics().residentPages(), "side=" + side);
            assertEquals(expectedResidentBytes, fixture.cache.metrics().residentPayloadBytes(), "side=" + side);
            assertEquals(expectedResidentBytes, fixture.cache.metrics().maxResidentPayloadBytes(), "side=" + side);
            assertEquals(1L, fixture.cache.metrics().evictions(), "side=" + side);
        }
    }

    @Test
    void hotPageLookupDoesNotRematerializeAndHasAGenerousPerformanceSmokeGate() {
        Fixture fixture = fixture(1_000_000L, 64, 4);
        ContinuumPageKey key = new ContinuumPageKey(10L, 20L);
        fixture.cache.page(key);

        assertTimeout(Duration.ofSeconds(5), () -> {
            for (int i = 0; i < 250_000; i++) {
                fixture.cache.page(key);
            }
        });

        assertEquals(1L, fixture.cache.metrics().loads());
        assertEquals(250_000L, fixture.cache.metrics().hits());
        assertEquals(pageBytes(64), fixture.cache.metrics().residentPayloadBytes());
    }

    @Test
    void onePageMustFitInsideTheConfiguredPayloadBudget() {
        int pageSide = 16;
        ContinuumWorldDomain domain = new ContinuumWorldDomain(1_000L, 1_000L);
        ContinuumPageLayout layout = new ContinuumPageLayout(domain, pageSide, pageSide);
        ContinuumScalarPageCache cache = new ContinuumScalarPageCache(
                layout,
                new ContinuumMaterializer(domain, analyticField()),
                4,
                pageBytes(pageSide) - 1L);

        assertThrows(IllegalArgumentException.class, () -> cache.page(new ContinuumPageKey(0L, 0L)));
        assertEquals(0L, cache.metrics().loads());
        assertEquals(0, cache.metrics().residentPages());
    }

    private static Fixture fixture(long side, int pageSide, int maxPages) {
        ContinuumWorldDomain domain = new ContinuumWorldDomain(side, side);
        ContinuumPageLayout layout = new ContinuumPageLayout(domain, pageSide, pageSide);
        ContinuumMaterializer materializer = new ContinuumMaterializer(domain, analyticField());
        long budget = Math.multiplyExact(pageBytes(pageSide), (long) maxPages);
        return new Fixture(new ContinuumScalarPageCache(layout, materializer, maxPages, budget));
    }

    private static ContinuumScalarField analyticField() {
        return (x, y) -> x * 0.25d + y * 0.5d;
    }

    private static long pageBytes(int pageSide) {
        return Math.multiplyExact(Math.multiplyExact((long) pageSide, pageSide), Double.BYTES);
    }

    private record Fixture(ContinuumScalarPageCache cache) {
    }
}
