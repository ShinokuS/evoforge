package io.github.evoforge.visualizer.continuum;

import io.github.evoforge.simulation.world.continuum.field.ContinuumMaterializer;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarField;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPage;
import io.github.evoforge.simulation.world.continuum.model.ContinuumRandom;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import io.github.evoforge.simulation.world.continuum.page.ContinuumPageCacheMetrics;
import io.github.evoforge.simulation.world.continuum.page.ContinuumPageKey;
import io.github.evoforge.simulation.world.continuum.page.ContinuumPageLayout;
import io.github.evoforge.simulation.world.continuum.page.ContinuumScalarPageCache;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * Presentation-side state for the Phase 0 Continuum inspector.
 *
 * <p>Moving the focus changes only which technical pages are requested. It never changes the
 * coordinate-addressed field or Continuum world truth.</p>
 */
public final class ContinuumInspectorModel {
    public static final long DEFAULT_LOGICAL_SIDE = 1_000_000L;
    public static final int DEFAULT_PAGE_SIDE = 256;
    public static final int DEFAULT_MAX_RESIDENT_PAGES = 12;
    public static final long DEFAULT_SEED = 0x5EED_C0FFEE_2026L;
    private static final int REQUEST_RADIUS = 1;

    private final long seed;
    private final ContinuumPageLayout layout;
    private final ContinuumScalarPageCache cache;

    private ContinuumPageKey focus;
    private List<ContinuumPageKey> requestedKeys = List.of();
    private Map<ContinuumPageKey, Double> requestedValues = Map.of();
    private List<ContinuumPageKey> lastEvictedKeys = List.of();

    public static ContinuumInspectorModel standard() {
        return standard(DEFAULT_SEED);
    }

    public static ContinuumInspectorModel standard(long seed) {
        ContinuumRandom random = new ContinuumRandom(seed);
        ContinuumScalarField field = (x, y) -> random.sampleUnit(
                "phase0-inspector",
                x >>> 6,
                y >>> 6,
                0L);
        return new ContinuumInspectorModel(
                DEFAULT_LOGICAL_SIDE,
                DEFAULT_PAGE_SIDE,
                DEFAULT_MAX_RESIDENT_PAGES,
                seed,
                field);
    }

    ContinuumInspectorModel(
            long logicalSide,
            int pageSide,
            int maxResidentPages,
            long seed,
            ContinuumScalarField field) {
        if (logicalSide <= 0L) throw new IllegalArgumentException("logicalSide must be > 0");
        if (pageSide <= 0) throw new IllegalArgumentException("pageSide must be > 0");
        if (maxResidentPages < 9) {
            throw new IllegalArgumentException("maxResidentPages must fit the 3x3 requested neighborhood");
        }
        if (field == null) throw new IllegalArgumentException("field must not be null");

        this.seed = seed;
        ContinuumWorldDomain domain = new ContinuumWorldDomain(logicalSide, logicalSide);
        this.layout = new ContinuumPageLayout(domain, pageSide, pageSide);
        ContinuumMaterializer materializer = new ContinuumMaterializer(domain, field);
        long fullPagePayload = Math.multiplyExact(Math.multiplyExact((long) pageSide, pageSide), Double.BYTES);
        this.cache = new ContinuumScalarPageCache(
                layout,
                materializer,
                maxResidentPages,
                Math.multiplyExact(fullPagePayload, (long) maxResidentPages));
        this.focus = new ContinuumPageKey(layout.pageCountX() / 2L, layout.pageCountY() / 2L);
        refreshRequestedNeighborhood();
    }

    public long seed() {
        return seed;
    }

    public ContinuumPageKey focus() {
        return focus;
    }

    public long pageCountX() {
        return layout.pageCountX();
    }

    public long pageCountY() {
        return layout.pageCountY();
    }

    public int pageSide() {
        return layout.pageWidth();
    }

    public long logicalWidth() {
        return layout.domain().width();
    }

    public long logicalHeight() {
        return layout.domain().height();
    }

    public long focusWorldX() {
        var window = layout.windowFor(focus);
        return window.minX() + window.width() / 2L;
    }

    public long focusWorldY() {
        var window = layout.windowFor(focus);
        return window.minY() + window.height() / 2L;
    }

    public List<ContinuumPageKey> requestedKeys() {
        return requestedKeys;
    }

    public List<ContinuumPageKey> residentKeys() {
        return cache.residentKeys();
    }

    public List<ContinuumPageKey> lastEvictedKeys() {
        return lastEvictedKeys;
    }

    public ContinuumPageCacheMetrics metrics() {
        return cache.metrics();
    }

    public OptionalDouble requestedValue(ContinuumPageKey key) {
        Double value = requestedValues.get(key);
        return value == null ? OptionalDouble.empty() : OptionalDouble.of(value);
    }

    public void moveFocus(long deltaPageX, long deltaPageY) {
        jumpToPage(
                saturatingAdd(focus.pageX(), deltaPageX),
                saturatingAdd(focus.pageY(), deltaPageY));
    }

    public void jumpToPage(long pageX, long pageY) {
        long clampedX = clamp(pageX, 0L, layout.pageCountX() - 1L);
        long clampedY = clamp(pageY, 0L, layout.pageCountY() - 1L);
        ContinuumPageKey next = new ContinuumPageKey(clampedX, clampedY);
        if (next.equals(focus)) return;
        focus = next;
        refreshRequestedNeighborhood();
    }

    public void resetCenter() {
        jumpToPage(layout.pageCountX() / 2L, layout.pageCountY() / 2L);
    }

    private void refreshRequestedNeighborhood() {
        List<ContinuumPageKey> before = cache.residentKeys();
        List<ContinuumPageKey> requested = new ArrayList<>(9);
        Map<ContinuumPageKey, Double> values = new LinkedHashMap<>(9);

        for (long dy = -REQUEST_RADIUS; dy <= REQUEST_RADIUS; dy++) {
            long pageY = focus.pageY() + dy;
            if (pageY < 0L || pageY >= layout.pageCountY()) continue;
            for (long dx = -REQUEST_RADIUS; dx <= REQUEST_RADIUS; dx++) {
                long pageX = focus.pageX() + dx;
                if (pageX < 0L || pageX >= layout.pageCountX()) continue;

                ContinuumPageKey key = new ContinuumPageKey(pageX, pageY);
                ContinuumScalarPage page = cache.page(key);
                requested.add(key);
                values.put(key, page.sample(page.window().width() / 2, page.window().height() / 2));
            }
        }

        Set<ContinuumPageKey> after = new HashSet<>(cache.residentKeys());
        List<ContinuumPageKey> evicted = new ArrayList<>();
        for (ContinuumPageKey key : before) {
            if (!after.contains(key)) evicted.add(key);
        }

        requestedKeys = List.copyOf(requested);
        requestedValues = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        lastEvictedKeys = List.copyOf(evicted);
    }

    private static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(value, max));
    }

    private static long saturatingAdd(long value, long delta) {
        if (delta > 0L && value > Long.MAX_VALUE - delta) return Long.MAX_VALUE;
        if (delta < 0L && value < Long.MIN_VALUE - delta) return Long.MIN_VALUE;
        return value + delta;
    }
}
